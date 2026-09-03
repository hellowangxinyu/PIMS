package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Quotation;
import com.pengyuan.pims.entity.QuotationItem;
import com.pengyuan.pims.entity.SalesOrder;
import com.pengyuan.pims.entity.SalesOrderItem;
import com.pengyuan.pims.repository.QuotationItemRepository;
import com.pengyuan.pims.repository.QuotationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * v5.52 报价单：DRAFT 草稿 → QUOTED 已报价（有效期内在前端正常，过期动态标"已失效"）
 * → ACCEPTED 已转销售订单 / REJECTED 客户未接受。
 * 转订单复用 SalesOrderService.create（单号/合同号自动生成 + 禁用物料校验）。
 */
@Service
public class QuotationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuotationService.class);

    private final QuotationRepository quoteRepo;
    private final QuotationItemRepository itemRepo;
    private final SalesOrderService salesOrderService;
    private final WriteQueue writeQueue;

    public QuotationService(QuotationRepository quoteRepo, QuotationItemRepository itemRepo,
                            SalesOrderService salesOrderService, WriteQueue writeQueue) {
        this.quoteRepo = quoteRepo;
        this.itemRepo = itemRepo;
        this.salesOrderService = salesOrderService;
        this.writeQueue = writeQueue;
    }

    public List<Quotation> list(Long customerId, String status) {
        List<Quotation> list;
        if (customerId != null && status != null && !status.isBlank()) list = quoteRepo.findByCustomerIdAndStatusOrderByCreateTimeDesc(customerId, status);
        else if (customerId != null) list = quoteRepo.findByCustomerIdOrderByCreateTimeDesc(customerId);
        else if (status != null && !status.isBlank()) list = quoteRepo.findByStatusOrderByCreateTimeDesc(status);
        else list = quoteRepo.findAllByOrderByCreateTimeDesc();
        fillAggregation(list);
        return list;
    }

    /** 品名摘要 + 动态过期标记 */
    private void fillAggregation(List<Quotation> list) {
        if (list.isEmpty()) return;
        java.util.Map<Long, List<QuotationItem>> itemsByQuote = itemRepo.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(i -> i.quotationId));
        LocalDate today = LocalDate.now();
        for (Quotation q : list) {
            List<QuotationItem> items = itemsByQuote.getOrDefault(q.id, List.of());
            q.materialNames = items.stream().map(i -> i.materialName != null ? i.materialName : i.materialCode)
                    .limit(5).reduce((a, b) -> a + "，" + b).orElse("");
            q.expired = "QUOTED".equals(q.status) && q.validUntil != null && q.validUntil.isBefore(today);
        }
    }

    public Quotation getById(Long id) {
        Quotation q = quoteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("报价单不存在"));
        fillAggregation(List.of(q));
        return q;
    }

    public List<QuotationItem> getItems(Long quotationId) { return itemRepo.findByQuotationId(quotationId); }

    @Transactional
    public Quotation create(Quotation q, List<QuotationItem> items) {
        return writeQueue.execute(() -> {
            if (q.customerId == null) throw new IllegalArgumentException("请选择客户");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("请至少添加一条报价明细");
            Integer maxSeq = quoteRepo.findMaxSeq("BJ-" + LocalDate.now().toString().replace("-", "") + "-%");
            q.quoteNo = String.format("BJ-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            q.status = "DRAFT";
            q.quoteDate = LocalDate.now();
            if (q.validUntil == null) q.validUntil = LocalDate.now().plusDays(30);
            q.totalAmount = BigDecimal.ZERO;
            Quotation saved = quoteRepo.save(q);
            saved.totalAmount = saveItems(saved, items);
            return quoteRepo.save(saved);
        });
    }

    @Transactional
    public Quotation update(Long id, Quotation in, List<QuotationItem> items) {
        return writeQueue.execute(() -> {
            Quotation q = quoteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("报价单不存在"));
            if (!"DRAFT".equals(q.status)) throw new IllegalArgumentException("只有草稿状态的报价单可编辑");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("请至少添加一条报价明细");
            q.customerId = in.customerId;
            q.customerName = in.customerName;
            q.validUntil = in.validUntil;
            q.remark = in.remark;
            itemRepo.deleteByQuotationId(id);
            itemRepo.flush();
            q.totalAmount = saveItems(q, items);
            q.updateTime = java.time.LocalDateTime.now();
            return quoteRepo.save(q);
        });
    }

    /** 保存明细并返回合计（amount 缺失时按 数量×单价 补算） */
    private BigDecimal saveItems(Quotation q, List<QuotationItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationItem item : items) {
            if (item.materialCode == null || item.materialCode.isBlank()) continue;
            if (item.qty == null || item.qty.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("物料 " + item.materialCode + " 数量必须大于0");
            if (item.amount == null && item.unitPrice != null) item.amount = item.unitPrice.multiply(item.qty);
            if (item.amount != null) total = total.add(item.amount);
            item.quotationId = q.id;
            itemRepo.save(item);
        }
        return total;
    }

    @Transactional
    public void delete(Long id) {
        Quotation q = quoteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("报价单不存在"));
        if (!"DRAFT".equals(q.status)) throw new IllegalArgumentException("只有草稿状态的报价单可删除");
        itemRepo.deleteByQuotationId(id);
        quoteRepo.delete(q);
    }

    /** 提交报价：DRAFT → QUOTED */
    @Transactional
    public Quotation submit(Long id) {
        Quotation q = quoteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("报价单不存在"));
        if (!"DRAFT".equals(q.status)) throw new IllegalArgumentException("只有草稿状态的报价单可提交");
        if (itemRepo.findByQuotationId(id).isEmpty()) throw new IllegalArgumentException("报价单无明细，不可提交");
        q.status = "QUOTED";
        q.updateTime = java.time.LocalDateTime.now();
        return quoteRepo.save(q);
    }

    /** 客户未接受：QUOTED → REJECTED */
    @Transactional
    public Quotation reject(Long id) {
        Quotation q = quoteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("报价单不存在"));
        if (!"QUOTED".equals(q.status)) throw new IllegalArgumentException("只有已报价状态可标记未接受");
        q.status = "REJECTED";
        q.updateTime = java.time.LocalDateTime.now();
        return quoteRepo.save(q);
    }

    /**
     * 报价转销售订单：QUOTED 且未过期 → 生成 DRAFT 订单（单号/合同号自动），报价单标记 ACCEPTED 并回填订单号。
     * WriteQueue 可重入（ReentrantLock），嵌套调用 SalesOrderService.create 安全。
     */
    @Transactional
    public Quotation toOrder(Long id) {
        return writeQueue.execute(() -> {
            Quotation q = quoteRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("报价单不存在"));
            if (!"QUOTED".equals(q.status)) throw new IllegalArgumentException("只有已报价状态可转订单");
            if (q.validUntil != null && q.validUntil.isBefore(LocalDate.now()))
                throw new IllegalArgumentException("报价已过有效期（" + q.validUntil + "），请复制新建报价单");
            List<QuotationItem> items = itemRepo.findByQuotationId(id);
            if (items.isEmpty()) throw new IllegalArgumentException("报价单无明细");

            SalesOrder order = new SalesOrder();
            order.customerId = q.customerId;
            order.customerName = q.customerName;
            order.sourceWarehouseId = "";
            order.remark = "报价单 " + q.quoteNo + " 转入" + (q.remark != null && !q.remark.isBlank() ? "；" + q.remark : "");
            List<SalesOrderItem> orderItems = items.stream().map(i -> {
                SalesOrderItem oi = new SalesOrderItem();
                oi.materialCode = i.materialCode;
                oi.materialName = i.materialName;
                oi.qty = i.qty;
                oi.unit = i.unit;
                oi.unitPrice = i.unitPrice;
                return oi;
            }).toList();
            SalesOrder created = salesOrderService.create(order, orderItems);

            q.status = "ACCEPTED";
            q.salesOrderNo = created.orderNo;
            q.updateTime = java.time.LocalDateTime.now();
            log.info("报价单 {} 转销售订单 {}", q.quoteNo, created.orderNo);
            return quoteRepo.save(q);
        });
    }
}
