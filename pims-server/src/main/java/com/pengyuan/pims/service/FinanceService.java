package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 财务中枢：应收/应付立账与核销、收款单/付款单、发票（含红冲）、退货红冲（按订单 FIFO 冲减）。
 * 关键口径：到货审核即立应付（货到即负债）；销售出库确认即立应收；收款单可直接核销 AR 或独立记账。
 */
@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private final AccountsReceivableRepository arRepo;
    private final AccountsPayableRepository apRepo;
    private final PaymentReceiptRepository receiptRepo;
    private final PaymentDisbursementRepository disbursementRepo;
    private final SupplierRepository supplierRepo;
    private final CustomerRepository customerRepo;
    private final PurchaseArrivalRepository arrivalRepo;
    // v5.24：全局写锁（财务单号生成+保存共用，防并发撞号）
    private final com.pengyuan.pims.repository.AdvancePaymentRepository advRepo;
    private final PeriodGuard periodGuard;
    private final WriteQueue writeQueue;

    public FinanceService(AccountsReceivableRepository arRepo, AccountsPayableRepository apRepo,
                          PaymentReceiptRepository receiptRepo, PaymentDisbursementRepository disbursementRepo,
                          SupplierRepository supplierRepo, CustomerRepository customerRepo,
                          PurchaseArrivalRepository arrivalRepo,
                          com.pengyuan.pims.repository.AdvancePaymentRepository advRepo,
                          WriteQueue writeQueue, PeriodGuard periodGuard) {
        this.arRepo = arRepo;
        this.apRepo = apRepo;
        this.receiptRepo = receiptRepo;
        this.disbursementRepo = disbursementRepo;
        this.supplierRepo = supplierRepo;
        this.customerRepo = customerRepo;
        this.arrivalRepo = arrivalRepo;
        this.writeQueue = writeQueue;
        this.periodGuard = periodGuard;
        this.advRepo = advRepo;
    }

    public List<AccountsReceivable> listAR() {
        List<AccountsReceivable> list = arRepo.findAll();
        // 关联填充客户名称，避免前端显示ID（v4.8：批量加载名称映射，替代逐行 findById 的 N+1 查询）
        java.util.Map<Long, String> nameMap = new java.util.HashMap<>();
        for (Customer c : customerRepo.findAll()) {
            if (c.id != null) nameMap.put(c.id, c.name);
        }
        for (AccountsReceivable ar : list) {
            if (ar.customerId != null) ar.customerName = nameMap.get(ar.customerId);
        }
        return list;
    }

    /**
     * 按供应商付款条件计算应付到期日（v5.6）：
     * 款到发货/货到付款=当天（货到付款：收货才立应付，立账即到期）；
     * 账期N天=立账日+N天；月结=次月1号；两月结=+2月1号；三月结=+3月1号；自定义账期N天=+N天
     * @param supplierId 供应商 ID（可空）
     * @param fallback   供应商无付款条件时的默认到期日
     */
    public java.time.LocalDate calcApDueDate(Long supplierId, java.time.LocalDate fallback) {
        return calcApDueDate(supplierId, fallback, java.time.LocalDate.now());
    }

    /**
     * v6.1.4：带基准日版本——账期从立账日（到货日）起算，补录历史到货的到期日不再按今天推。
     * 无显式基准时（旧调用）默认今天，行为不变。
     */
    public java.time.LocalDate calcApDueDate(Long supplierId, java.time.LocalDate fallback, java.time.LocalDate baseDate) {
        if (supplierId == null) return fallback;
        Supplier supplier = supplierRepo.findById(supplierId).orElse(null);
        if (supplier == null || supplier.paymentTerms == null || supplier.paymentTerms.isBlank()) {
            return fallback;
        }
        // v6.1.5 修复：baseDate 真正生效（v6.1.4 加了形参但方法体仍用 now()，补录历史到货到期日依旧按今天推）
        java.time.LocalDate today = baseDate != null ? baseDate : java.time.LocalDate.now();
        switch (supplier.paymentTerms) {
            case "PREPAID": return today;
            case "COD": return today; // 货到付款：收货（立账）当天到期
            case "CREDIT_30": return today.plusDays(30);
            case "CREDIT_60": return today.plusDays(60);
            case "MONTHLY": return today.plusMonths(1).withDayOfMonth(1);
            case "TWO_MONTH": return today.plusMonths(2).withDayOfMonth(1);
            case "THREE_MONTH": return today.plusMonths(3).withDayOfMonth(1);
            default:
                // 自定义账期，如「账期45天」
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(supplier.paymentTerms);
                if (supplier.paymentTerms.contains("天") && m.find()) {
                    return today.plusDays(Long.parseLong(m.group(1)));
                }
                return fallback;
        }
    }

    public List<AccountsPayable> listAP() {
        List<AccountsPayable> list = apRepo.findAll();
        // 关联填充供应商名称，避免前端显示ID（v4.8：批量加载名称映射，替代逐行 findById 的 N+1 查询）
        java.util.Map<Long, String> nameMap = new java.util.HashMap<>();
        for (Supplier s : supplierRepo.findAll()) {
            if (s.id != null) nameMap.put(s.id, s.name);
        }
        for (AccountsPayable ap : list) {
            if (ap.supplierId != null) ap.supplierName = nameMap.get(ap.supplierId);
        }
        // v5.27：按到货单立账后，批量补充到货单信息（单号/物料/数量）便于核对
        java.util.Map<Long, com.pengyuan.pims.entity.PurchaseArrival> arrivalMap = new java.util.HashMap<>();
        List<Long> arrivalIds = new java.util.ArrayList<>();
        for (AccountsPayable ap : list) {
            if (ap.arrivalId != null) arrivalIds.add(ap.arrivalId);
        }
        if (!arrivalIds.isEmpty()) {
            for (com.pengyuan.pims.entity.PurchaseArrival a : arrivalRepo.findAllById(arrivalIds)) {
                if (a.id != null) arrivalMap.put(a.id, a);
            }
            for (AccountsPayable ap : list) {
                com.pengyuan.pims.entity.PurchaseArrival a = ap.arrivalId != null ? arrivalMap.get(ap.arrivalId) : null;
                if (a != null) {
                    ap.arrivalInfo = "到货单#" + a.id + " " + (a.materialName != null ? a.materialName : "")
                            + (a.qty != null ? " ×" + a.qty : "");
                }
            }
        }
        return list;
    }
    public Optional<AccountsReceivable> getARById(Long id) { return arRepo.findById(id); }
    public Optional<AccountsPayable> getAPById(Long id) { return apRepo.findById(id); }

    /**
     * 应收总表：按客户维度聚合应收账款（不分订单）
     * 返回每个客户的：应收总额、已收总额、剩余未收、单据数、状态汇总
     * v5.54：SQL GROUP BY 直出（替代 findAll 全量加载+内存分组）
     * v7.6：加周转指标（start/end 为 yyyy-MM-dd 可空）——
     *   期间立账 billed / 期初余额 opening / 期末余额 closing（=累计立账−累计收款）/
     *   周转率 billed÷((期初+期末)/2) / 周转天数 365÷周转率（与库存周转同基数）；
     *   期间外客户（期间无立账且期初期末均 0）不输出周转值
     */
    public List<java.util.Map<String, Object>> listARTotalByCustomer(String start, String end) {
        boolean hasPeriod = start != null && !start.isBlank() && end != null && !end.isBlank();
        String endEx = hasPeriod ? java.time.LocalDate.parse(end).plusDays(1).toString() : null;
        java.util.Map<Long, java.math.BigDecimal> billed = hasPeriod
                ? groupToMap(arRepo.billedByCustomer(start, endEx)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> openBilled = hasPeriod
                ? groupToMap(arRepo.cumBilledByCustomer(start)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> closeBilled = hasPeriod
                ? groupToMap(arRepo.cumBilledByCustomer(endEx)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> openRecv = hasPeriod
                ? groupToMap(receiptRepo.cumReceivedByCustomer(start)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> closeRecv = hasPeriod
                ? groupToMap(receiptRepo.cumReceivedByCustomer(endEx)) : java.util.Map.of();

        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object[] r : arRepo.totalByCustomer()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("customerId", r[0]);
            row.put("customerName", r[1]);
            java.math.BigDecimal total = money(r[2]);
            java.math.BigDecimal received = money(r[3]);
            row.put("totalAmount", total);
            row.put("receivedAmount", received);
            row.put("docCount", ((Number) r[4]).intValue());
            row.put("unpaidCount", ((Number) r[5]).intValue());
            row.put("partialCount", ((Number) r[6]).intValue());
            row.put("paidCount", ((Number) r[7]).intValue());
            row.put("remainingAmount", total.subtract(received));
            row.put("receivedRate", total.compareTo(BigDecimal.ZERO) > 0
                    ? received.multiply(new BigDecimal("100")).divide(total, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            if (hasPeriod) {
                Long cid = ((Number) r[0]).longValue();
                java.math.BigDecimal b = billed.getOrDefault(cid, BigDecimal.ZERO);
                java.math.BigDecimal op = openBilled.getOrDefault(cid, BigDecimal.ZERO)
                        .subtract(openRecv.getOrDefault(cid, BigDecimal.ZERO));
                java.math.BigDecimal cl = closeBilled.getOrDefault(cid, BigDecimal.ZERO)
                        .subtract(closeRecv.getOrDefault(cid, BigDecimal.ZERO));
                fillTurnover(row, b, op, cl);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 应付总表：按供应商维度聚合应付账款（不分订单）
     * 返回每个供应商的：应付总额、已付总额、剩余未付、单据数、状态汇总
     * v5.54：SQL GROUP BY 直出（替代 findAll 全量加载+内存分组）
     * v7.6：加周转指标（口径同应收总表，余额=累计立账−累计付款）
     */
    public List<java.util.Map<String, Object>> listAPTotalBySupplier(String start, String end) {
        // v11.4 支持单日期筛选：只传截止日 = 期初清零看累计（对账口径）；都空 = 全部
        boolean hasPeriod = (start != null && !start.isBlank()) || (end != null && !end.isBlank());
        String effStart = (start != null && !start.isBlank()) ? start : "1970-01-01";
        String endEx = (end != null && !end.isBlank())
                ? java.time.LocalDate.parse(end).plusDays(1).toString() : "9999-01-01";
        java.util.Map<Long, java.math.BigDecimal> billed = hasPeriod
                ? groupToMap(apRepo.billedBySupplier(effStart, endEx)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> openBilled = hasPeriod
                ? groupToMap(apRepo.cumBilledBySupplier(effStart)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> closeBilled = hasPeriod
                ? groupToMap(apRepo.cumBilledBySupplier(endEx)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> openPaid = hasPeriod
                ? groupToMap(disbursementRepo.cumPaidBySupplier(effStart)) : java.util.Map.of();
        java.util.Map<Long, java.math.BigDecimal> closePaid = hasPeriod
                ? groupToMap(disbursementRepo.cumPaidBySupplier(endEx)) : java.util.Map.of();

        // v11.3 超期应付（已过账期未付余额），用于总表排序与展示
        java.util.Map<Long, java.math.BigDecimal> overdueBySupplier = new java.util.HashMap<>();
        for (Object[] o : apRepo.overdueBySupplier(java.time.LocalDate.now())) {
            overdueBySupplier.put(((Number) o[0]).longValue(), money(o[1]));
        }
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object[] r : apRepo.totalBySupplier()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("supplierId", r[0]);
            row.put("supplierName", r[1]);
            java.math.BigDecimal total = money(r[2]);
            java.math.BigDecimal paid = money(r[3]);
            row.put("totalAmount", total);
            row.put("paidAmount", paid);
            row.put("docCount", ((Number) r[4]).intValue());
            row.put("unpaidCount", ((Number) r[5]).intValue());
            row.put("partialCount", ((Number) r[6]).intValue());
            row.put("paidCount", ((Number) r[7]).intValue());
            row.put("remainingAmount", total.subtract(paid));
            row.put("overdueAmount", overdueBySupplier.getOrDefault(((Number) r[0]).longValue(), BigDecimal.ZERO));
            row.put("paidRate", total.compareTo(BigDecimal.ZERO) > 0
                    ? paid.multiply(new BigDecimal("100")).divide(total, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            if (hasPeriod) {
                Long sid = ((Number) r[0]).longValue();
                java.math.BigDecimal b = billed.getOrDefault(sid, BigDecimal.ZERO);
                java.math.BigDecimal op = openBilled.getOrDefault(sid, BigDecimal.ZERO)
                        .subtract(openPaid.getOrDefault(sid, BigDecimal.ZERO));
                java.math.BigDecimal cl = closeBilled.getOrDefault(sid, BigDecimal.ZERO)
                        .subtract(closePaid.getOrDefault(sid, BigDecimal.ZERO));
                fillTurnover(row, b, op, cl);
            }
            result.add(row);
        }
        // v11.3 超期应付从少到多（无超期的自然排前，超期多的排后面）
        result.sort(java.util.Comparator.comparing(m ->
                (java.math.BigDecimal) m.getOrDefault("overdueAmount", BigDecimal.ZERO)));
        return result;
    }

    /** v7.6 行级周转四件套：billed/opening/closing + 周转率/周转天数（平均余额≤0 或期间无立账 → null，前端显示 —） */
    private void fillTurnover(java.util.Map<String, Object> row, java.math.BigDecimal billed,
                              java.math.BigDecimal opening, java.math.BigDecimal closing) {
        row.put("billedAmount", billed);
        row.put("openingBalance", opening);
        row.put("closingBalance", closing);
        java.math.BigDecimal avg = opening.add(closing).divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
        if (avg.compareTo(BigDecimal.ZERO) > 0 && billed.compareTo(BigDecimal.ZERO) > 0) {
            java.math.BigDecimal turnover = billed.divide(avg, 2, java.math.RoundingMode.HALF_UP);
            row.put("turnover", turnover);
            row.put("turnoverDays", new BigDecimal("365").divide(turnover, 1, java.math.RoundingMode.HALF_UP));
        } else {
            row.put("turnover", null);
            row.put("turnoverDays", null);
        }
    }

    /** v7.6 GROUP BY 结果（id列, 金额列）转 Map<id, 金额>（sqlite 聚合列兼容转 BigDecimal） */
    private static java.util.Map<Long, java.math.BigDecimal> groupToMap(List<Object[]> rows) {
        java.util.Map<Long, java.math.BigDecimal> m = new java.util.HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) m.put(((Number) r[0]).longValue(), money(r[1]));
        }
        return m;
    }

    /** SQLite 聚合列兼容转金额（COALESCE(SUM,0) 可能返回 Integer/Double，统一 2 位小数） */
    private static java.math.BigDecimal money(Object v) {
        if (v == null) return BigDecimal.ZERO.setScale(2);
        if (v instanceof BigDecimal bd) return bd.setScale(2, java.math.RoundingMode.HALF_UP);
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        try { return new BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return BigDecimal.ZERO.setScale(2); }
    }

    // ============ 收款单 / 付款单查询 ============
    public List<PaymentReceipt> listReceipts() { return receiptRepo.findByOrderByCreateTimeDesc(); }
    public List<PaymentDisbursement> listDisbursements() { return disbursementRepo.findByOrderByCreateTimeDesc(); }

    /**
     * 创建收款单：记录一笔收款流水，同时回写冲减关联AR的已收金额。
     * 两种模式：
     *   1. 指定 arId → 仅冲减该张 AR
     *   2. 仅指定 customerId（arId 为空）→ 按该客户未结清 AR 列表 FIFO 自动冲减，
     *      arDocNo 记录被冲减的 AR 单号（多张以逗号分隔）
     * @param r 收款单
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public PaymentReceipt createReceipt(PaymentReceipt r) {
        // v9.0（P1-4 审计）：收款单业务日期落在已结账期间则拒绝
        periodGuard.checkOpen(r.receiptDate);
        // v5.24：单号生成+冲减+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (r.amount == null || r.amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("收款金额必须大于 0");
            }
            if (r.customerId == null) {
                throw new IllegalArgumentException("请选择客户");
            }
            if (r.receiptDate == null) r.receiptDate = LocalDate.now();
            if (r.docNo != null && !r.docNo.isBlank() && receiptRepo.existsByDocNo(r.docNo)) {
                throw new IllegalArgumentException("收款单号 " + r.docNo + " 已存在");   // v6.1.4 查重
            }
            if (r.docNo == null || r.docNo.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                Integer maxSeq = receiptRepo.findMaxSeq("PR-" + LocalDate.now().toString().replace("-", "") + "-%");
                r.docNo = String.format("PR-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
    
            if (r.arId != null) {
                // 模式1：指定单张AR冲减
                AccountsReceivable ar = arRepo.findById(r.arId)
                        .orElseThrow(() -> new IllegalArgumentException("应收单不存在"));
                if ("PAID".equals(ar.status)) {
                    throw new IllegalArgumentException("应收单已结清，不可再收款");
                }
                // v6.1 防呆：单张核销不得超过未收余额（收超曾直接置 PAID 且超额无预收挂账）
                BigDecimal openAr = ar.amount.subtract(ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO);
                if (r.amount.compareTo(openAr) > 0) {
                    throw new IllegalArgumentException("收款金额 ￥" + r.amount + " 超过未收余额 ￥" + openAr + "，多收请走预收");
                }
                ar.receivedAmount = (ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO).add(r.amount);
                if (ar.receivedAmount.compareTo(ar.amount) >= 0) ar.status = "PAID";
                else ar.status = "PARTIAL";
                ar.updateTime = java.time.LocalDateTime.now();
                arRepo.save(ar);
                r.arDocNo = ar.docNo;
                log.info("收款单 {} 冲减 AR {} 金额 {}", r.docNo, ar.docNo, r.amount);
            } else {
                // 模式2：按客户未结清AR列表 FIFO 冲减
                List<AccountsReceivable> unpaidList = arRepo.findByCustomerIdAndStatusNotOrderByCreateTimeAsc(r.customerId, "PAID");
                if (unpaidList.isEmpty()) {
                    throw new IllegalArgumentException("该客户没有未结清的应收单，无需收款");
                }
                BigDecimal remaining = r.amount;
                java.util.List<String> hitDocNos = new java.util.ArrayList<>();
                for (AccountsReceivable ar : unpaidList) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal unpaid = ar.amount.subtract(ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO);
                    if (unpaid.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal apply = remaining.min(unpaid);
                    ar.receivedAmount = (ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO).add(apply);
                    if (ar.receivedAmount.compareTo(ar.amount) >= 0) ar.status = "PAID";
                    else ar.status = "PARTIAL";
                    ar.updateTime = java.time.LocalDateTime.now();
                    arRepo.save(ar);
                    hitDocNos.add(ar.docNo);
                    remaining = remaining.subtract(apply);
                }
                r.arDocNo = String.join(",", hitDocNos);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    // v9.2（P2-3 审计）：超收差额自动落预收单——原仅 log，差额只在客户对账单体现为负余额，与预存模块不闭环
                    String custName = customerRepo.findById(r.customerId).map(c -> c.name).orElse(null);
                    var adv = createAdvanceTx("RECEIVE", r.customerId, custName, remaining,
                            "收款单 " + r.docNo + " 超收自动转预收");
                    r.remark = (r.remark == null || r.remark.isBlank() ? "" : r.remark + ";") + "超收转预收 " + adv.docNo;
                    log.info("收款单 {} 超收 {} 自动落预收单 {}", r.docNo, remaining, adv.docNo);
                }
                log.info("收款单 {} 按客户 {} FIFO 冲减 AR: {}", r.docNo, r.customerId, r.arDocNo);
            }
            return receiptRepo.save(r);
        });
    }

    /**
     * 创建付款单：记录一笔付款流水，同时回写冲减关联AP的已付金额。
     * 两种模式：
     *   1. 指定 apId → 仅冲减该张 AP
     *   2. 仅指定 supplierId（apId 为空）→ 按该供应商未结清 AP 列表 FIFO 自动冲减，
     *      apDocNo 记录被冲减的 AP 单号（多张以逗号分隔）
     * @param d 付款单
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public PaymentDisbursement createDisbursement(PaymentDisbursement d) {
        // v9.0（P1-4 审计）：付款单业务日期落在已结账期间则拒绝
        periodGuard.checkOpen(d.payDate);
        // v5.24：单号生成+冲减+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (d.amount == null || d.amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("付款金额必须大于 0");
            }
            if (d.supplierId == null) {
                throw new IllegalArgumentException("请选择供应商");
            }
            if (d.payDate == null) d.payDate = LocalDate.now();
            if (d.docNo != null && !d.docNo.isBlank() && disbursementRepo.existsByDocNo(d.docNo)) {
                throw new IllegalArgumentException("付款单号 " + d.docNo + " 已存在");   // v6.1.4 查重
            }
            if (d.docNo == null || d.docNo.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                Integer maxSeq = disbursementRepo.findMaxSeq("PD-" + LocalDate.now().toString().replace("-", "") + "-%");
                d.docNo = String.format("PD-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
    
            if (d.apId != null) {
                // 模式1：指定单张AP冲减
                AccountsPayable ap = apRepo.findById(d.apId)
                        .orElseThrow(() -> new IllegalArgumentException("应付单不存在"));
                if ("PAID".equals(ap.status)) {
                    throw new IllegalArgumentException("应付单已结清，不可再付款");
                }
                // v6.1 防呆：单张核销不得超过未付余额
                BigDecimal openAp = ap.amount.subtract(ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO);
                if (d.amount.compareTo(openAp) > 0) {
                    throw new IllegalArgumentException("付款金额 ￥" + d.amount + " 超过未付余额 ￥" + openAp + "，多付请走预付");
                }
                ap.paidAmount = (ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO).add(d.amount);
                if (ap.paidAmount.compareTo(ap.amount) >= 0) ap.status = "PAID";
                else ap.status = "PARTIAL";
                ap.updateTime = java.time.LocalDateTime.now();
                apRepo.save(ap);
                d.apDocNo = ap.docNo;
                log.info("付款单 {} 冲减 AP {} 金额 {}", d.docNo, ap.docNo, d.amount);
            } else {
                // 模式2：按供应商未结清AP列表 FIFO 冲减
                List<AccountsPayable> unpaidList = apRepo.findBySupplierIdAndStatusNotOrderByCreateTimeAsc(d.supplierId, "PAID");
                if (unpaidList.isEmpty()) {
                    throw new IllegalArgumentException("该供应商没有未结清的应付单，无需付款");
                }
                BigDecimal remaining = d.amount;
                java.util.List<String> hitDocNos = new java.util.ArrayList<>();
                for (AccountsPayable ap : unpaidList) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal unpaid = ap.amount.subtract(ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO);
                    if (unpaid.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal apply = remaining.min(unpaid);
                    ap.paidAmount = (ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO).add(apply);
                    if (ap.paidAmount.compareTo(ap.amount) >= 0) ap.status = "PAID";
                    else ap.status = "PARTIAL";
                    ap.updateTime = java.time.LocalDateTime.now();
                    apRepo.save(ap);
                    hitDocNos.add(ap.docNo);
                    remaining = remaining.subtract(apply);
                }
                d.apDocNo = String.join(",", hitDocNos);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    // v9.2（P2-3 审计）：超付差额自动落预付单（原仅提示，与预存模块不闭环）
                    String supName = supplierRepo.findById(d.supplierId).map(sp -> sp.name).orElse(null);
                    var adv = createAdvanceTx("PAY", d.supplierId, supName, remaining,
                            "付款单 " + d.docNo + " 超付自动转预付");
                    d.remark = (d.remark == null || d.remark.isBlank() ? "" : d.remark + ";") + "超付转预付 " + adv.docNo;
                    log.info("付款单 {} 超付 {} 自动落预付单 {}", d.docNo, remaining, adv.docNo);
                }
                log.info("付款单 {} 按供应商 {} FIFO 冲减 AP: {}", d.docNo, d.supplierId, d.apDocNo);
            }
            return disbursementRepo.save(d);
        });
    }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public AccountsReceivable createAR(AccountsReceivable ar) {
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (ar.docNo != null && !ar.docNo.isBlank() && arRepo.existsByDocNo(ar.docNo)) {
                throw new IllegalArgumentException("应收单号 " + ar.docNo + " 已存在");   // v6.1.4 查重
            }
            if (ar.docNo == null || ar.docNo.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                Integer maxSeq = arRepo.findMaxSeq("AR-" + LocalDate.now().toString().replace("-", "") + "-%");
                ar.docNo = String.format("AR-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return arRepo.save(ar);
        });
    }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public AccountsPayable createAP(AccountsPayable ap) {
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (ap.docNo != null && !ap.docNo.isBlank() && apRepo.existsByDocNo(ap.docNo)) {
                throw new IllegalArgumentException("应付单号 " + ap.docNo + " 已存在");   // v6.1.4 查重
            }
            if (ap.docNo == null || ap.docNo.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                Integer maxSeq = apRepo.findMaxSeq("AP-" + LocalDate.now().toString().replace("-", "") + "-%");
                ap.docNo = String.format("AP-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return apRepo.save(ap);
        });
    }

    // v8.1（P0-5）：锁内包事务——核销读-校验-写整体串行，防并发超额
    public void receivePayment(Long arId, BigDecimal amount) {        periodGuard.checkCurrentOpen();        writeQueue.executeTx(() -> {

        AccountsReceivable ar = arRepo.findById(arId).orElseThrow(() -> new IllegalArgumentException("应收单不存在"));
        // v6.1 防呆：收款金额必须为正（负数曾是"无流水冲减已收"后门），且不得超过应收余额
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("收款金额必须大于 0");
        }
        {
            BigDecimal remaining = ar.amount.subtract(ar.receivedAmount);
            if (amount.compareTo(remaining) > 0) {
                throw new IllegalArgumentException(String.format(
                    "收款金额 ¥%.2f 超过应收余额 ¥%.2f（应收 ¥%.2f 已收 ¥%.2f）",
                    amount, remaining, ar.amount, ar.receivedAmount));
            }
        }
        ar.receivedAmount = ar.receivedAmount.add(amount);
        if (ar.receivedAmount.compareTo(ar.amount) >= 0) ar.status = "PAID";
        else ar.status = "PARTIAL";
        arRepo.save(ar);
   
        });
    }

    // v8.1（P0-5）：锁内包事务——核销读-校验-写整体串行，防并发超额
    public void makePayment(Long apId, BigDecimal amount) {        periodGuard.checkCurrentOpen();        writeQueue.executeTx(() -> {

        AccountsPayable ap = apRepo.findById(apId).orElseThrow(() -> new IllegalArgumentException("应付单不存在"));
        // v6.1 防呆：付款金额必须为正，且不得超过应付余额
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("付款金额必须大于 0");
        }
        {
            BigDecimal remaining = ap.amount.subtract(ap.paidAmount);
            if (amount.compareTo(remaining) > 0) {
                throw new IllegalArgumentException(String.format(
                    "付款金额 ¥%.2f 超过应付余额 ¥%.2f（应付 ¥%.2f 已付 ¥%.2f）",
                    amount, remaining, ap.amount, ap.paidAmount));
            }
        }
        ap.paidAmount = ap.paidAmount.add(amount);
        if (ap.paidAmount.compareTo(ap.amount) >= 0) ap.status = "PAID";
        else ap.status = "PARTIAL";
        apRepo.save(ap);
   
        });
    }

    // ============ 退货红冲（按订单号 FIFO 冲减未结清应收/应付）============

    /**
     * 销售退货冲减应收：按 salesOrderNo 查该订单下未结清 AR，按创建时间 FIFO 累计 receivedAmount。
     * 红冲逻辑：退货视同"客户已收回款"，把退货金额累计到 receivedAmount，复用现有状态流转。
     * @param salesOrderNo 销售订单号
     * @param amount       退货金额（必须 > 0）
     * @param refDocNo     退货入库单号（写入 remark 便于追溯）
     * @return 实际冲减金额（可能小于入参：当该订单 AR 已全部结清时返回 0）
     */
    // v8.1（P0-5）：锁内包事务——核销读-校验-写整体串行，防并发超额
    public BigDecimal applySalesReturn(String salesOrderNo, BigDecimal amount, String refDocNo) {        periodGuard.checkCurrentOpen();        return writeQueue.executeTx(() -> {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退货冲减金额必须大于 0");
        }
        List<AccountsReceivable> list = arRepo.findBySalesOrderNoOrderByCreateTimeAsc(salesOrderNo);
        BigDecimal remaining = amount;
        for (AccountsReceivable ar : list) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if ("PAID".equals(ar.status)) continue; // 已结清跳过
            // 本单可冲减额度 = 应收金额 - 已收金额
            BigDecimal available = ar.amount.subtract(ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal apply = remaining.min(available);
            ar.receivedAmount = (ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO).add(apply);
            if (ar.receivedAmount.compareTo(ar.amount) >= 0) ar.status = "PAID";
            else ar.status = "PARTIAL";
            ar.remark = (ar.remark == null ? "" : ar.remark + " | ") + "退货冲减 " + refDocNo + " 金额 " + apply;
            ar.updateTime = java.time.LocalDateTime.now();
            arRepo.save(ar);
            remaining = remaining.subtract(apply);
            log.info("销售退货冲减 AR: ar={} apply={} remaining={}", ar.docNo, apply, remaining);
        }
        BigDecimal applied = amount.subtract(remaining);
        log.info("销售退货冲减完成: order={} 申请={} 实际冲减={}", salesOrderNo, amount, applied);
        return applied;
   
        });
    }

    /**
     * 采购退货冲减应付：按 purchaseOrderNo 查该订单下未结清 AP，按创建时间 FIFO 累计 paidAmount。
     * 红冲逻辑：退货视同"已向供应商付款"，把退货金额累计到 paidAmount，复用现有状态流转。
     * @param purchaseOrderNo 采购订单号
     * @param amount          退货金额（必须 > 0）
     * @param refDocNo        退货入库单号（写入 remark 便于追溯）
     * @param arrivalId       到货单 ID（v5.27 可空；非空则精准冲减该到货单对应的 AP，空则按订单号 FIFO）
     * @return 实际冲减金额
     */
    // v8.1（P0-5）：锁内包事务——核销读-校验-写整体串行，防并发超额
    public BigDecimal applyPurchaseReturn(String purchaseOrderNo, BigDecimal amount, String refDocNo, Long arrivalId) {        periodGuard.checkCurrentOpen();        return writeQueue.executeTx(() -> {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退货冲减金额必须大于 0");
        }
        // v5.27：按到货单立账后，退货只冲退货批次对应的 AP（同单其他到货批次不受影响）
        List<AccountsPayable> list = arrivalId != null
                ? apRepo.findByArrivalId(arrivalId)
                : apRepo.findByPurchaseOrderNoOrderByCreateTimeAsc(purchaseOrderNo);
        BigDecimal applied = applyApList(list, amount, refDocNo);
        log.info("采购退货冲减完成: order={} 申请={} 实际冲减={}", purchaseOrderNo, amount, applied);
        return applied;
   
        });
    }

    /**
     * v5.27：按供应商冲减应付（手工库存退货单未关联采购单时使用）
     * 红冲逻辑：退货视同"已向供应商付款"，按该供应商未结清 AP 按创建时间 FIFO 累计 paidAmount
     * @param supplierId 供应商 ID
     * @param amount     退货金额（必须 > 0）
     * @param refDocNo   退货出库单号（写入 remark 便于追溯）
     * @return 实际冲减金额
     */
    // v8.1（P0-5）：锁内包事务——核销读-校验-写整体串行，防并发超额
    public BigDecimal applyPurchaseReturnBySupplier(Long supplierId, BigDecimal amount, String refDocNo) {        periodGuard.checkCurrentOpen();        return writeQueue.executeTx(() -> {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退货冲减金额必须大于 0");
        }
        List<AccountsPayable> list = apRepo.findBySupplierIdAndStatusNotOrderByCreateTimeAsc(supplierId, "PAID");
        BigDecimal applied = applyApList(list, amount, refDocNo);
        log.info("采购退货冲减完成: supplier={} 申请={} 实际冲减={}", supplierId, amount, applied);
        return applied;
   
        });
    }

    /** 公共 FIFO 冲减逻辑：按创建时间顺序累计 paidAmount，红冲视同已付款 */
    private BigDecimal applyApList(List<AccountsPayable> list, BigDecimal amount, String refDocNo) {
        BigDecimal remaining = amount;
        for (AccountsPayable ap : list) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if ("PAID".equals(ap.status)) continue;
            BigDecimal available = ap.amount.subtract(ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal apply = remaining.min(available);
            ap.paidAmount = (ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO).add(apply);
            if (ap.paidAmount.compareTo(ap.amount) >= 0) ap.status = "PAID";
            else ap.status = "PARTIAL";
            ap.remark = (ap.remark == null ? "" : ap.remark + " | ") + "退货冲减 " + refDocNo + " 金额 " + apply;
            ap.updateTime = java.time.LocalDateTime.now();
            apRepo.save(ap);
            remaining = remaining.subtract(apply);
            log.info("采购退货冲减 AP: ap={} apply={} remaining={}", ap.docNo, apply, remaining);
        }
        return amount.subtract(remaining);
    }

    /**
     * v5.52 客户信用检查：当前应收欠款（未结清应收金额-已收）+ 拟下单金额 与信用额度比较。
     * 额度空/0 视为不限额（exceed=false）。只做事前预警，不拦截保存。
     */
    public java.util.Map<String, Object> creditCheck(Long customerId, BigDecimal orderAmount) {
        Customer c = customerRepo.findById(customerId).orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        BigDecimal creditLimit = c.creditLimit;
        BigDecimal arBalance = BigDecimal.ZERO;
        for (AccountsReceivable ar : arRepo.findByCustomerIdAndStatusNotOrderByCreateTimeAsc(customerId, "PAID")) {
            arBalance = arBalance.add(ar.amount.subtract(ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO));
        }
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("creditLimit", creditLimit);
        r.put("arBalance", arBalance);
        r.put("orderAmount", orderAmount != null ? orderAmount : BigDecimal.ZERO);
        r.put("projected", arBalance.add(orderAmount != null ? orderAmount : BigDecimal.ZERO));
        boolean exceed = creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0
                && arBalance.add(orderAmount != null ? orderAmount : BigDecimal.ZERO).compareTo(creditLimit) > 0;
        r.put("exceed", exceed);
        return r;
    }

    /**
     * v9.2（P2-3 审计）：超收/超付差额自动生成预收/预付单（调用方须已持锁——均在 executeTx 内）。
     * 编号与 AdvancePaymentService.create 同规：ADV-YYYYMMDD-NNNN 按天流水。
     */
    private com.pengyuan.pims.entity.AdvancePayment createAdvanceTx(String direction, Long partnerId,
                                                                     String partnerName, BigDecimal amount, String remark) {
        com.pengyuan.pims.entity.AdvancePayment adv = new com.pengyuan.pims.entity.AdvancePayment();
        adv.direction = direction;
        adv.partnerId = partnerId;
        adv.partnerName = partnerName;
        adv.amount = amount;
        adv.usedAmount = BigDecimal.ZERO;
        adv.payDate = LocalDate.now();
        adv.remark = remark;
        Integer maxSeq = advRepo.findMaxSeq("ADV-" + LocalDate.now().toString().replace("-", "") + "-%");
        adv.docNo = String.format("ADV-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
        return advRepo.save(adv);
    }
}
