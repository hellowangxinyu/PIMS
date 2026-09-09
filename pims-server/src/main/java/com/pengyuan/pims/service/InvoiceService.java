package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Invoice;
import com.pengyuan.pims.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 发票登记（v5.36）
 * 红冲方式：对原单生成一条负数对冲发票（金额全部取反），原单 status=FLUSHED 并指向红字单；
 * 汇总口径按"正常单 - 红字单"净额统计。
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository repo;
    private final WriteQueue writeQueue;
    private final JdbcTemplate jdbc;

    public InvoiceService(InvoiceRepository repo, WriteQueue writeQueue, JdbcTemplate jdbc) {
        this.repo = repo;
        this.writeQueue = writeQueue;
        this.jdbc = jdbc;
    }

    public List<Invoice> list() { return repo.findAllByOrderByCreateTimeDescIdDesc(); }

    // v6.1.5：executeTx 锁内包事务 + 查重入锁（原 @Transactional+execute 旧时序，docNo 查重在锁外 TOCTOU）
    public Invoice create(Invoice inv) {
        // v8.12：发票三必填（发票号/开票日期/购销对象——税务追溯依据）
        if (inv.invoiceNo == null || inv.invoiceNo.isBlank()) throw new IllegalArgumentException("发票号不能为空");
        if (inv.invoiceDate == null) throw new IllegalArgumentException("开票日期不能为空");
        if (inv.partnerName == null || inv.partnerName.isBlank()) throw new IllegalArgumentException("购销对象（客户/供应商名称）不能为空");
        // v5.70 P0 防呆：发票金额上限
        if (inv.amount != null && inv.amount.compareTo(new java.math.BigDecimal("99990000")) > 0) {
            throw new IllegalArgumentException("发票金额异常：" + inv.amount + "，请核对");
        }
        if (inv.direction == null || inv.direction.isBlank()) inv.direction = "OUTPUT";
        if (!"OUTPUT".equals(inv.direction) && !"INPUT".equals(inv.direction)) {
            throw new IllegalArgumentException("发票方向必须是 OUTPUT(销项) 或 INPUT(进项)");
        }
        if (inv.amount == null || inv.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("发票金额必须大于 0");
        }
        calcTax(inv);
        return writeQueue.executeTx(() -> {
            if (inv.docNo != null && !inv.docNo.isBlank() && repo.existsByDocNo(inv.docNo)) {
                throw new IllegalArgumentException("发票单号 " + inv.docNo + " 已存在");   // v6.1.5 查重入锁
            }
            if (inv.docNo == null || inv.docNo.isBlank()) {
                Integer maxSeq = repo.findMaxSeq("INV-" + LocalDate.now().toString().replace("-", "") + "-%");
                inv.docNo = String.format("INV-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return repo.save(inv);
        });
    }

    @Transactional
    public Invoice update(Long id, Invoice in) {
        // v8.12：编辑同必填
        if (in.invoiceNo == null || in.invoiceNo.isBlank()) throw new IllegalArgumentException("发票号不能为空");
        if (in.invoiceDate == null) throw new IllegalArgumentException("开票日期不能为空");
        if (in.partnerName == null || in.partnerName.isBlank()) throw new IllegalArgumentException("购销对象不能为空");
        Invoice inv = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("发票不存在"));
        if (!"NORMAL".equals(inv.status)) throw new IllegalArgumentException("已红冲发票不可编辑");
        assertNoVoucher(inv.docNo, "修改");
        inv.invoiceNo = in.invoiceNo;
        inv.partnerId = in.partnerId;
        inv.partnerName = in.partnerName;
        inv.partnerTaxNo = in.partnerTaxNo;
        if (in.amount == null || in.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("发票金额必须大于 0");
        }
        inv.amount = in.amount;
        inv.taxRate = in.taxRate == null ? 13 : in.taxRate;
        calcTax(inv);
        inv.invoiceDate = in.invoiceDate;
        inv.refOrderNo = in.refOrderNo;
        inv.remark = in.remark;
        inv.updateTime = java.time.LocalDateTime.now();
        return repo.save(inv);
    }

    @Transactional
    public void delete(Long id) {
        Invoice inv = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("发票不存在"));
        if (!"NORMAL".equals(inv.status)) throw new IllegalArgumentException("已红冲发票不可删除");
        assertNoVoucher(inv.docNo, "删除");
        // 防悬挂：被红冲引用的单不可删（红字单 refOrderNo 指向原单）
        var ref = jdbc.queryForList("SELECT id FROM invoice WHERE flush_doc_no = ?", inv.docNo);
        if (!ref.isEmpty()) throw new IllegalArgumentException("该发票存在关联红字发票，不可删除");
        repo.deleteById(id);
    }

    /** 已生成凭证的发票改/删会造成凭证与票面脱节（金额对不上），先删凭证再操作 */
    private void assertNoVoucher(String docNo, String action) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM voucher WHERE source = 'INVOICE' AND ref_doc_no = ?", Integer.class, docNo);
        if (n != null && n > 0) {
            throw new IllegalArgumentException("该发票已生成凭证，不可" + action + "；请先在总账删除对应凭证");
        }
    }

    /** 红冲：生成负数对冲发票，原单标记 FLUSHED */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public Invoice redFlush(Long id, String redInvoiceNo, String reason) {
        Invoice origin = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("发票不存在"));
        if (!"NORMAL".equals(origin.status)) throw new IllegalArgumentException("该发票已红冲");
        // v6.1.1：红字负数单不可再红冲（红字单 status=NORMAL 可被无限链式对冲，套娃生成正数单）
        if (origin.amount != null && origin.amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("红字发票不可再红冲（如需调整请对原蓝字发票处理）");
        }
        // v6.1.1：已生成凭证的发票红冲后原凭证不冲销，会造成账票脱节（与 update/delete 同口径）
        assertNoVoucher(origin.docNo, "红冲");
        return writeQueue.executeTx(() -> {
            Invoice red = new Invoice();
            red.invoiceNo = redInvoiceNo;
            red.direction = origin.direction;
            red.partnerType = origin.partnerType;
            red.partnerId = origin.partnerId;
            red.partnerName = origin.partnerName;
            red.partnerTaxNo = origin.partnerTaxNo;
            red.amount = origin.amount.negate();
            red.taxRate = origin.taxRate;
            if (origin.taxAmount != null) red.taxAmount = origin.taxAmount.negate();
            if (origin.totalAmount != null) red.totalAmount = origin.totalAmount.negate();
            red.invoiceDate = LocalDate.now();
            red.refOrderNo = origin.refOrderNo;
            red.remark = "红冲 " + origin.docNo + (reason == null || reason.isBlank() ? "" : "：" + reason);
            Integer maxSeq = repo.findMaxSeq("INV-" + LocalDate.now().toString().replace("-", "") + "-%");
            red.docNo = String.format("INV-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            red = repo.save(red);

            origin.status = "FLUSHED";
            origin.flushDocNo = red.docNo;
            origin.updateTime = java.time.LocalDateTime.now();
            repo.save(origin);
            log.info("发票红冲: 原单={} 红字单={}", origin.docNo, red.docNo);
            return red;
        });
    }

    /** 税额 = 不含税金额 × 税率%（四舍五入 2 位），价税合计 = 不含税 + 税额 */
    private void calcTax(Invoice inv) {
        int rate = inv.taxRate == null ? 13 : inv.taxRate;
        inv.taxAmount = inv.amount.multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        inv.totalAmount = inv.amount.add(inv.taxAmount);
    }

    /**
     * 客户开票汇总（销项口径）：开票净额 vs 应收立账 vs 已回款，用于"开票-回款"对账。
     * 净额 = 正常单 - 红字单（红字单金额为负直接 SUM 抵消）。
     */
    public List<Map<String, Object>> customerSummary() {
        return jdbc.queryForList("""
            SELECT i.partner_id AS partnerId, i.partner_name AS partnerName,
                   MAX(i.partner_tax_no) AS taxNo,
                   SUM(CASE WHEN i.invoice_date IS NOT NULL THEN i.amount ELSE 0 END) AS invoiceAmount,
                   SUM(CASE WHEN i.invoice_date IS NOT NULL THEN i.tax_amount ELSE 0 END) AS taxAmount,
                   SUM(CASE WHEN i.invoice_date IS NOT NULL THEN i.total_amount ELSE 0 END) AS invoiceTotal,
                   COALESCE(ar.arAmount, 0) AS arAmount,
                   COALESCE(ar.receivedAmount, 0) AS receivedAmount
            FROM invoice i
            LEFT JOIN (
                SELECT customer_id, SUM(amount) AS arAmount, SUM(received_amount) AS receivedAmount
                FROM accounts_receivable GROUP BY customer_id
            ) ar ON ar.customer_id = i.partner_id
            WHERE i.direction = 'OUTPUT' AND i.partner_type = 'CUSTOMER'
            GROUP BY i.partner_id, i.partner_name, ar.arAmount, ar.receivedAmount
            ORDER BY invoiceTotal DESC
            """);
    }
}
