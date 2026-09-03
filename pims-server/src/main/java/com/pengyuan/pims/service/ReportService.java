package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.DictItem;
import com.pengyuan.pims.entity.InventoryLedger;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.OutsourceOrder;
import com.pengyuan.pims.entity.ProductionOrder;
import com.pengyuan.pims.entity.QualityInspection;
import com.pengyuan.pims.entity.StatFinanceSummary;
import com.pengyuan.pims.entity.StatMaterialUsage;
import com.pengyuan.pims.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报表聚合服务
 * 为前端SVG图表提供结构化统计数据
 */
@Service
public class ReportService {

    private final RawMaterialPurchaseRepository purchaseRepo;
    private final StatInventoryDailyRepository inventoryDailyRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final AccountsReceivableRepository arRepo;
    private final AccountsPayableRepository apRepo;
    private final StatFinanceSummaryRepository financeSummaryRepo;
    private final ProductionOrderRepository productionRepo;
    private final OutsourceOrderRepository outsourceRepo;
    private final WarehouseRepository warehouseRepo;
    private final ProductionInboundRepository prodInRepo;
    private final OutsourceFinishInboundRepository outsourceInRepo;
    private final StatMaterialUsageRepository statUsageRepo;
    private final MaterialRepository materialRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;   // v6.3 周转率聚合
    private final DictItemRepository dictItemRepo;
    private final SalesOutboundRepository salesOutRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final SalesOrderItemRepository salesOrderItemRepo;
    private final QualityInspectionRepository qcRepo;
    private final ProductionOutboundRepository prodOutRepo;
    private final PaymentReceiptRepository payReceiptRepo;
    private final PaymentDisbursementRepository payDisbursRepo;
    private final FinishedProductPurchaseRepository finishedPurchaseRepo;
    private final CustomerRepository customerRepo;
    private final SupplierRepository supplierRepo;

    public ReportService(RawMaterialPurchaseRepository purchaseRepo,
                         StatInventoryDailyRepository inventoryDailyRepo,
                         InventoryLedgerRepository ledgerRepo,
                         AccountsReceivableRepository arRepo,
                         AccountsPayableRepository apRepo,
                         StatFinanceSummaryRepository financeSummaryRepo,
                         ProductionOrderRepository productionRepo,
                         OutsourceOrderRepository outsourceRepo,
                         WarehouseRepository warehouseRepo,
                         ProductionInboundRepository prodInRepo,
                         OutsourceFinishInboundRepository outsourceInRepo,
                         StatMaterialUsageRepository statUsageRepo,
                         MaterialRepository materialRepo,
                         DictItemRepository dictItemRepo,
                         SalesOutboundRepository salesOutRepo,
                         SalesOrderRepository salesOrderRepo,
                         SalesOrderItemRepository salesOrderItemRepo,
                         QualityInspectionRepository qcRepo,
                         ProductionOutboundRepository prodOutRepo,
                         PaymentReceiptRepository payReceiptRepo,
                         PaymentDisbursementRepository payDisbursRepo,
                         FinishedProductPurchaseRepository finishedPurchaseRepo,
                         CustomerRepository customerRepo,
                         SupplierRepository supplierRepo,
                         org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.purchaseRepo = purchaseRepo;
        this.inventoryDailyRepo = inventoryDailyRepo;
        this.ledgerRepo = ledgerRepo;
        this.arRepo = arRepo;
        this.apRepo = apRepo;
        this.financeSummaryRepo = financeSummaryRepo;
        this.productionRepo = productionRepo;
        this.outsourceRepo = outsourceRepo;
        this.warehouseRepo = warehouseRepo;
        this.prodInRepo = prodInRepo;
        this.outsourceInRepo = outsourceInRepo;
        this.statUsageRepo = statUsageRepo;
        this.materialRepo = materialRepo;
        this.jdbc = jdbc;
        this.dictItemRepo = dictItemRepo;
        this.salesOutRepo = salesOutRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.salesOrderItemRepo = salesOrderItemRepo;
        this.qcRepo = qcRepo;
        this.prodOutRepo = prodOutRepo;
        this.payReceiptRepo = payReceiptRepo;
        this.payDisbursRepo = payDisbursRepo;
        this.finishedPurchaseRepo = finishedPurchaseRepo;
        this.customerRepo = customerRepo;
        this.supplierRepo = supplierRepo;
    }

    /** 计算起始日期字符串（N个月前的1号） */
    private String sinceDate(int months) {
        return LocalDate.now().minusMonths(months).withDayOfMonth(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    // ==================== 采购报表 ====================

    public Map<String, Object> purchaseReport(int months) {
    return (Map<String, Object>) cached("purchaseReport:" + months, () -> doPurchaseReport(months));
}

private Map<String, Object> doPurchaseReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        // 月度采购金额趋势
        List<Object[]> monthly = purchaseRepo.monthlyAmountSince(since);
        List<String> periods = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (Object[] row : monthly) {
            periods.add((String) row[0]);
            amounts.add(toBigDecimal(row[1]));
        }
        result.put("monthlyTrend", Map.of("labels", periods, "values", amounts));

        // 供应商TOP10
        List<Object[]> topSuppliers = purchaseRepo.topSuppliersSince(since);
        List<Map<String, Object>> supplierRank = new ArrayList<>();
        for (Object[] row : topSuppliers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0] != null ? row[0].toString() : "未知");
            item.put("value", toBigDecimal(row[1]));
            supplierRank.add(item);
        }
        result.put("supplierRank", supplierRank);

        // 物料分类占比
        List<Object[]> categories = purchaseRepo.categoryAmountSince(since);
        List<Map<String, Object>> categoryDist = new ArrayList<>();
        for (Object[] row : categories) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", categoryLabel((String) row[0]));
            item.put("value", toBigDecimal(row[1]));
            categoryDist.add(item);
        }
        result.put("categoryDist", categoryDist);

        return result;
    }

    // ==================== 库存报表 ====================

    public Map<String, Object> inventoryReport(int months) {
    return (Map<String, Object>) cached("inventoryReport:" + months, () -> doInventoryReport(months));
}

    /**
     * v6.3 第三批：库存周转率。
     * 口径（实用版）：近 N 天总出库量 ÷ 当前库存 × (365/N) = 年化周转次数；
     * 按大类汇总 + 物料明细（库存>0 或 有出库），排除隔离行。周转越低越呆滞。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> turnoverReport(int days) {
        return (Map<String, Object>) cached("turnoverReport:" + days, () -> {
            long since = System.currentTimeMillis() - days * 86400_000L;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("days", days);

            // 期间出库量（按物料，各出库单据 UNION）
            Map<String, BigDecimal> outQty = new LinkedHashMap<>();
            String outSql = """
                SELECT material_code AS code, SUM(qty) AS q FROM (
                  SELECT material_code, qty FROM sales_outbound WHERE status='CONFIRMED' AND create_time >= ?
                  UNION ALL SELECT material_code, qty FROM production_outbound WHERE status='CONFIRMED' AND create_time >= ?
                  UNION ALL SELECT material_code, qty FROM outsource_material_outbound WHERE status IN ('CONFIRMED','SIGNED') AND create_time >= ?
                  UNION ALL SELECT material_code, qty FROM other_outbound WHERE status='CONFIRMED' AND create_time >= ?
                ) GROUP BY material_code
                """;
            for (var row : jdbc.queryForList(outSql, since, since, since, since)) {
                outQty.put(String.valueOf(row.get("code")), toBigDecimal(row.get("q")));
            }
            // 当前库存（排除隔离）
            Map<String, BigDecimal[]> stock = new LinkedHashMap<>();   // code -> [qty, amount]
            for (var row : jdbc.queryForList(
                    "SELECT material_code AS code, SUM(qty) AS q, SUM(COALESCE(amount,0)) AS a FROM inventory_ledger " +
                    "WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED')) GROUP BY material_code")) {
                stock.put(String.valueOf(row.get("code")), new BigDecimal[]{toBigDecimal(row.get("q")), toBigDecimal(row.get("a"))});
            }
            // 物料档案（大类）
            Map<String, Object[]> mats = new LinkedHashMap<>();   // code -> [name, category]
            for (var m : materialRepo.findAll()) mats.put(m.code, new Object[]{m.name, m.category});

            java.util.Set<String> codes = new java.util.LinkedHashSet<>();
            codes.addAll(outQty.keySet());
            codes.addAll(stock.keySet());

            BigDecimal factor = new BigDecimal(365.0 / days);
            List<Map<String, Object>> details = new ArrayList<>();
            Map<String, BigDecimal[]> byCategory = new LinkedHashMap<>();   // cat -> [out, stockQty]
            for (String code : codes) {
                BigDecimal out = outQty.getOrDefault(code, BigDecimal.ZERO);
                BigDecimal[] st = stock.getOrDefault(code, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                Object[] m = mats.get(code);
                String cat = m != null && m[1] != null ? String.valueOf(m[1]) : "其他";
                if (st[0].compareTo(BigDecimal.ZERO) <= 0 && out.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal turnover = st[0].compareTo(BigDecimal.ZERO) > 0
                        ? out.divide(st[0], 4, java.math.RoundingMode.HALF_UP).multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP)
                        : null;   // 无库存但有出库（已清仓）周转无意义
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("materialCode", code);
                d.put("materialName", m != null && m[0] != null ? String.valueOf(m[0]) : "");
                d.put("materialCategory", cat);
                d.put("outQty", out.setScale(3, java.math.RoundingMode.HALF_UP));
                d.put("stockQty", st[0].setScale(3, java.math.RoundingMode.HALF_UP));
                d.put("stockAmount", st[1].setScale(2, java.math.RoundingMode.HALF_UP));
                d.put("turnover", turnover);
                d.put("stockDays", turnover != null && turnover.compareTo(BigDecimal.ZERO) > 0
                        ? java.math.BigDecimal.valueOf(365).divide(turnover, 0, java.math.RoundingMode.HALF_UP).intValue() : null);
                details.add(d);
                BigDecimal[] agg = byCategory.computeIfAbsent(cat, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                agg[0] = agg[0].add(out);
                agg[1] = agg[1].add(st[0]);
            }
            // 大类汇总周转
            List<Map<String, Object>> catRows = new ArrayList<>();
            for (var e : byCategory.entrySet()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("materialCategory", e.getKey());
                c.put("outQty", e.getValue()[0].setScale(3, java.math.RoundingMode.HALF_UP));
                c.put("stockQty", e.getValue()[1].setScale(3, java.math.RoundingMode.HALF_UP));
                c.put("turnover", e.getValue()[1].compareTo(BigDecimal.ZERO) > 0
                        ? e.getValue()[0].divide(e.getValue()[1], 4, java.math.RoundingMode.HALF_UP).multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP)
                        : null);
                catRows.add(c);
            }
            catRows.sort((a, b) -> ((BigDecimal) b.get("outQty")).compareTo((BigDecimal) a.get("outQty")));
            // 呆滞提示：有库存但期间零出库
            details.sort((a, b) -> {
                BigDecimal ta = a.get("turnover") == null ? new BigDecimal("-1") : (BigDecimal) a.get("turnover");
                BigDecimal tb = b.get("turnover") == null ? new BigDecimal("-1") : (BigDecimal) b.get("turnover");
                return ta.compareTo(tb);
            });
            result.put("byCategory", catRows);
            result.put("details", details.size() > 300 ? details.subList(0, 300) : details);
            return result;
        });
    }

private Map<String, Object> doInventoryReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        // 月度出入库趋势
        List<Object[]> monthlyIO = inventoryDailyRepo.monthlyInOutSince(since);
        List<String> periods = new ArrayList<>();
        List<BigDecimal> inList = new ArrayList<>();
        List<BigDecimal> outList = new ArrayList<>();
        for (Object[] row : monthlyIO) {
            periods.add((String) row[0]);
            inList.add(toBigDecimal(row[1]));
            outList.add(toBigDecimal(row[2]));
        }
        result.put("monthlyTrend", Map.of("labels", periods, "inQty", inList, "outQty", outList));

        // 物料吞吐TOP10
        List<Object[]> topMaterials = inventoryDailyRepo.topMaterialsSince(since);
        List<Map<String, Object>> materialRank = new ArrayList<>();
        for (Object[] row : topMaterials) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0] != null ? row[0].toString() : "未知");
            item.put("value", toBigDecimal(row[1]));
            materialRank.add(item);
        }
        result.put("materialRank", materialRank);

        // 仓库库存分布
        List<Object[]> warehouseData = ledgerRepo.sumByWarehouse();
        List<Map<String, Object>> warehouseDist = new ArrayList<>();
        for (Object[] row : warehouseData) {
            Map<String, Object> item = new LinkedHashMap<>();
            String whId = row[0] != null ? row[0].toString() : "未知";
            item.put("name", warehouseName(whId));
            item.put("value", toBigDecimal(row[1]));
            warehouseDist.add(item);
        }
        result.put("warehouseDist", warehouseDist);

        return result;
    }

    // ==================== 财务报表 ====================

    public Map<String, Object> financeReport(int months) {
    return (Map<String, Object>) cached("financeReport:" + months, () -> doFinanceReport(months));
}

private Map<String, Object> doFinanceReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        // 月度应收趋势
        List<Object[]> arMonthly = arRepo.monthlyArSince(since);
        List<String> periods = new ArrayList<>();
        List<BigDecimal> arAmounts = new ArrayList<>();
        List<BigDecimal> arReceived = new ArrayList<>();
        for (Object[] row : arMonthly) {
            periods.add((String) row[0]);
            arAmounts.add(toBigDecimal(row[1]));
            arReceived.add(toBigDecimal(row[2]));
        }

        // 月度应付趋势
        List<Object[]> apMonthly = apRepo.monthlyApSince(since);
        List<BigDecimal> apAmounts = new ArrayList<>();
        List<BigDecimal> apPaid = new ArrayList<>();
        // 应付可能与应收月份不完全对齐，这里简化处理：以应收月份为基准
        Map<String, BigDecimal> apMap = new LinkedHashMap<>();
        Map<String, BigDecimal> apPaidMap = new LinkedHashMap<>();
        for (Object[] row : apMonthly) {
            apMap.put((String) row[0], toBigDecimal(row[1]));
            apPaidMap.put((String) row[0], toBigDecimal(row[2]));
        }
        for (String p : periods) {
            apAmounts.add(apMap.getOrDefault(p, BigDecimal.ZERO));
            apPaid.add(apPaidMap.getOrDefault(p, BigDecimal.ZERO));
        }

        result.put("monthlyTrend", Map.of(
                "labels", periods,
                "arAmount", arAmounts,
                "apAmount", apAmounts
        ));

        // 收支对比（应收 vs 应付）
        result.put("compare", Map.of(
                "labels", periods,
                "income", arAmounts,
                "expense", apAmounts
        ));

        // 回款率/付款率（全局汇总）
        StatFinanceSummary fin = financeSummaryRepo.findById(1L).orElse(null);
        BigDecimal arTotal = BigDecimal.ZERO, arRecv = BigDecimal.ZERO;
        BigDecimal apTotal = BigDecimal.ZERO, apPd = BigDecimal.ZERO;
        if (fin != null) {
            arTotal = fin.arTotal;
            arRecv = fin.arReceived;
            apTotal = fin.apTotal;
            apPd = fin.apPaid;
        }
        BigDecimal arRate = arTotal.compareTo(BigDecimal.ZERO) > 0
                ? arRecv.multiply(BigDecimal.valueOf(100)).divide(arTotal, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal apRate = apTotal.compareTo(BigDecimal.ZERO) > 0
                ? apPd.multiply(BigDecimal.valueOf(100)).divide(apTotal, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        result.put("summary", Map.of(
                "arTotal", arTotal, "arReceived", arRecv, "arRate", arRate,
                "apTotal", apTotal, "apPaid", apPd, "apRate", apRate
        ));

        return result;
    }

    // ==================== 生产报表 ====================

    public Map<String, Object> productionReport(int months) {
    return (Map<String, Object>) cached("productionReport:" + months, () -> doProductionReport(months));
}

/** 生产进度表：生产 + 委外订单的完成进度（已确认入库量 ÷ 计划批量），进度条展示用 */
public Map<String, Object> productionProgress() {
    return (Map<String, Object>) cached("productionProgress", () -> doProductionProgress());
}

/** v5.26：生产进度（批量查询入库量，避免逐单 N+1；60 秒缓存） */
private Map<String, Object> doProductionProgress() {
    // v5.26：批量取全部已入库订单的入库量（GROUP BY 一次取回）
    Map<String, Double> inQtyByOrder = new HashMap<>();
    for (Object[] row : prodInRepo.sumDoneQtyGroupByOrderNo()) {
        inQtyByOrder.put((String) row[0], ((Number) row[1]).doubleValue());
    }
    for (Object[] row : outsourceInRepo.sumDoneQtyGroupByOrderNo()) {
        inQtyByOrder.put((String) row[0], ((Number) row[1]).doubleValue());
    }
    List<Map<String, Object>> all = new ArrayList<>();
    for (ProductionOrder o : productionRepo.findAll()) {
        double inQty = o.orderNo != null ? inQtyByOrder.getOrDefault(o.orderNo, 0.0) : 0;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "生产");
        row.put("orderNo", o.orderNo);
        row.put("productName", o.productName);
        row.put("batchQty", o.batchQty);
        row.put("inQty", inQty);
        row.put("status", o.status);
        row.put("progress", calcProgress(o.status, o.batchQty, inQty));
        row.put("time", o.createTime);
        all.add(row);
    }
    for (OutsourceOrder o : outsourceRepo.findAll()) {
        double inQty = o.orderNo != null ? inQtyByOrder.getOrDefault(o.orderNo, 0.0) : 0;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "委外");
        row.put("orderNo", o.orderNo);
        row.put("productName", o.productName);
        row.put("batchQty", o.batchQty);
        row.put("inQty", inQty);
        row.put("status", o.status);
        row.put("progress", calcProgress(o.status, o.batchQty, inQty));
        row.put("time", o.createTime);
        all.add(row);
    }
    // 按创建时间倒序（null 排最后），最多 100 条
    all.sort(Comparator.comparing((Map<String, Object> m) -> m.get("time") == null ? LocalDateTime.MIN : (LocalDateTime) m.get("time")).reversed());
    if (all.size() > 100) all = all.subList(0, 100);
    for (Map<String, Object> row : all) row.remove("time");

    int completed = 0;
    double progressSum = 0;
    for (Map<String, Object> row : all) {
        if (((Number) row.get("progress")).intValue() >= 100) completed++;
        progressSum += ((Number) row.get("progress")).intValue();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("rows", all);
    result.put("total", all.size());
    result.put("completed", completed);
    result.put("avgProgress", all.isEmpty() ? 0 : (int) Math.round(progressSum / all.size()));
    return result;
}

/** 进度百分比：已完成订单恒为 100；其余按 已入库 ÷ 计划批量 */
private int calcProgress(String status, BigDecimal batchQty, double inQty) {
    if ("COMPLETED".equals(status)) return 100;
    if (batchQty == null || batchQty.doubleValue() <= 0) return 0;
    return (int) Math.min(100, Math.round(inQty / batchQty.doubleValue() * 100));
}

private Map<String, Object> doProductionReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        // 月度生产订单数
        List<Object[]> prodMonthly = productionRepo.monthlyCountByStatusSince(since);
        // 月度委外订单数
        List<Object[]> outMonthly = outsourceRepo.monthlyCountByStatusSince(since);

        // 合并所有月份
        Set<String> allPeriods = new TreeSet<>();
        Map<String, Integer> prodCount = new LinkedHashMap<>();
        Map<String, Integer> outCount = new LinkedHashMap<>();
        for (Object[] row : prodMonthly) {
            String p = (String) row[0];
            allPeriods.add(p);
            prodCount.merge(p, ((Number) row[2]).intValue(), Integer::sum);
        }
        for (Object[] row : outMonthly) {
            String p = (String) row[0];
            allPeriods.add(p);
            outCount.merge(p, ((Number) row[2]).intValue(), Integer::sum);
        }

        List<String> periods = new ArrayList<>(allPeriods);
        List<Integer> prodCounts = new ArrayList<>();
        List<Integer> outCounts = new ArrayList<>();
        for (String p : periods) {
            prodCounts.add(prodCount.getOrDefault(p, 0));
            outCounts.add(outCount.getOrDefault(p, 0));
        }
        result.put("monthlyTrend", Map.of("labels", periods, "production", prodCounts, "outsource", outCounts));

        // 完工率统计（生产+委外）
        int prodTotal = 0, prodCompleted = 0;
        for (Object[] row : prodMonthly) {
            int cnt = ((Number) row[2]).intValue();
            prodTotal += cnt;
            if ("COMPLETED".equals(row[1])) prodCompleted += cnt;
        }
        int outTotal = 0, outCompleted = 0;
        for (Object[] row : outMonthly) {
            int cnt = ((Number) row[2]).intValue();
            outTotal += cnt;
            if ("COMPLETED".equals(row[1])) outCompleted += cnt;
        }
        result.put("completionRate", Map.of(
                "prodTotal", prodTotal, "prodCompleted", prodCompleted,
                "outTotal", outTotal, "outCompleted", outCompleted
        ));

        // 月度产量对比
        List<Object[]> prodQty = productionRepo.monthlyQtySince(since);
        List<Object[]> outQty = outsourceRepo.monthlyQtySince(since);
        Map<String, BigDecimal> prodQtyMap = new LinkedHashMap<>();
        Map<String, BigDecimal> outQtyMap = new LinkedHashMap<>();
        for (Object[] row : prodQty) prodQtyMap.put((String) row[0], toBigDecimal(row[1]));
        for (Object[] row : outQty) outQtyMap.put((String) row[0], toBigDecimal(row[1]));

        List<BigDecimal> prodQtys = new ArrayList<>();
        List<BigDecimal> outQtys = new ArrayList<>();
        for (String p : periods) {
            prodQtys.add(prodQtyMap.getOrDefault(p, BigDecimal.ZERO));
            outQtys.add(outQtyMap.getOrDefault(p, BigDecimal.ZERO));
        }
        result.put("monthlyQty", Map.of("labels", periods, "production", prodQtys, "outsource", outQtys));

        // 批次得率趋势 + 损耗分析（基于入库单据：实际产出 vs 理论产出）
        java.time.LocalDateTime sinceTime = LocalDate.now().minusMonths(months).withDayOfMonth(1).atStartOfDay();
        List<Map<String, Object>> yieldPoints = new ArrayList<>();
        // 按产品聚合的损耗：理论产出、实际产出、损耗量
        Map<String, BigDecimal[]> lossMap = new LinkedHashMap<>();
        for (var doc : prodInRepo.findConfirmedSince(sinceTime)) {
            collectYield(yieldPoints, lossMap, doc.batchNo != null ? doc.batchNo : doc.docNo,
                    doc.productName, "生产", doc.qty, doc.theoreticalQty, doc.yieldRate, doc.createTime);
        }
        for (var doc : outsourceInRepo.findConfirmedSince(sinceTime)) {
            collectYield(yieldPoints, lossMap, doc.batchNo != null ? doc.batchNo : doc.docNo,
                    doc.productName, "委外", doc.qty, doc.theoreticalQty, doc.yieldRate, doc.createTime);
        }
        // 得率趋势按时间正序，最多取近 30 个批次
        yieldPoints.sort(Comparator.comparing(m -> (java.time.LocalDateTime) m.get("time")));
        if (yieldPoints.size() > 30) yieldPoints = new ArrayList<>(yieldPoints.subList(yieldPoints.size() - 30, yieldPoints.size()));
        yieldPoints.forEach(m -> m.remove("time"));
        result.put("yieldTrend", yieldPoints);

        // 损耗分析：按产品聚合，损耗量 = 理论 - 实际（正数为损耗，负数为超产）
        List<Map<String, Object>> lossList = new ArrayList<>();
        for (var e : lossMap.entrySet()) {
            BigDecimal theoretical = e.getValue()[0];
            BigDecimal actual = e.getValue()[1];
            BigDecimal loss = theoretical.subtract(actual);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productName", e.getKey());
            item.put("theoreticalQty", theoretical.setScale(2, RoundingMode.HALF_UP));
            item.put("actualQty", actual.setScale(2, RoundingMode.HALF_UP));
            item.put("lossQty", loss.setScale(2, RoundingMode.HALF_UP));
            item.put("lossRate", theoretical.compareTo(BigDecimal.ZERO) > 0
                    ? loss.multiply(BigDecimal.valueOf(100)).divide(theoretical, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            lossList.add(item);
        }
        lossList.sort((a, b) -> toBigDecimal(b.get("lossQty")).compareTo(toBigDecimal(a.get("lossQty"))));
        if (lossList.size() > 10) lossList = new ArrayList<>(lossList.subList(0, 10));
        result.put("lossAnalysis", lossList);

        return result;
    }

    /** 汇总单张入库单的得率点与产品损耗 */
    private void collectYield(List<Map<String, Object>> yieldPoints, Map<String, BigDecimal[]> lossMap,
                              String batchNo, String productName, String type,
                              BigDecimal qty, BigDecimal theoreticalQty, BigDecimal yieldRate,
                              java.time.LocalDateTime time) {
        if (yieldRate != null) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("batchNo", batchNo != null ? batchNo : "-");
            point.put("productName", productName != null ? productName : "-");
            point.put("type", type);
            point.put("qty", qty);
            point.put("theoreticalQty", theoreticalQty);
            point.put("yieldRate", yieldRate);
            point.put("time", time);
            yieldPoints.add(point);
        }
        if (theoreticalQty != null && qty != null && productName != null) {
            BigDecimal[] agg = lossMap.computeIfAbsent(productName, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            agg[0] = agg[0].add(theoreticalQty);
            agg[1] = agg[1].add(qty);
        }
    }

    // ==================== 低库存预警报表 ====================

    /**
     * 低库存预警：仅统计原材料（物料大类 A/P/F/R/S，助剂/颜料/填料/树脂/溶剂）。
     * 口径说明：
     *  - 历史用量：出库异动 docType ∈ (PRODUCTION_OUT, OUTSOURCE_OUT, OTHER_OUT, SALES_OUT)
     *    （调拨 TRANSFER / 盘盈亏 ADJUSTMENT 不算实际用量）
     *  - 月均用量 = 总用量 ÷ 实际使用月份数（有出库记录的去重月份，非自然月）
     *  - 日均用量 = 总用量 ÷ 实际使用天数（有出库记录的去重天数）
     *  - 安全库存 = 日均用量 × 30 天（一个月安全储备）
     *  - 可用天数 = 当前库存量(qty合计) ÷ 日均用量
     *  - 预警：可用天数 < 15 天进入报表；< 10 天为红色严重预警
     * v4.8：用量数据改读 stat_material_usage 汇总表（SQLite 触发器增量维护），
     * 不再全表扫描 inventory_movement，数据量大后报表仍保持毫秒级
     */
    public Map<String, Object> lowStockReport() {
    return (Map<String, Object>) cached("lowStockReport", () -> doLowStockReport());
}

private Map<String, Object> doLowStockReport() {
        // 原材料主档（启用中，大类 A/P/F/R/S）
        Map<String, Material> rawMaterials = new HashMap<>();
        for (Material m : materialRepo.findAll()) {
            if (m.category != null && "APFRS".contains(m.category) && Boolean.TRUE.equals(m.enabled)) {
                rawMaterials.put(m.code, m);
            }
        }
        // v4.9：小类代码 → 中文名称映射（数据字典 material_sub_category，展示用）
        Map<String, String> subCategoryNames = new HashMap<>();
        for (DictItem d : dictItemRepo.findByTypeAndEnabledTrueOrderBySortOrderAsc("material_sub_category")) {
            subCategoryNames.put(d.value, d.label);
        }

        // 从汇总表读取用量（v5.34：按 物料|仓库 维度）——key = materialCode + "|" + warehouseId
        Map<String, BigDecimal> usageTotal = new HashMap<>();
        Map<String, Integer> usageMonths = new HashMap<>();
        Map<String, Integer> usageDays = new HashMap<>();
        for (StatMaterialUsage u : statUsageRepo.findAll()) {
            if (!rawMaterials.containsKey(u.materialCode)) continue;
            String whId = u.warehouseId == null ? "" : u.warehouseId;
            String key = u.materialCode + "|" + whId;
            usageTotal.merge(key, u.outQty, BigDecimal::add);
            usageMonths.merge(key, 1, Integer::sum);
            usageDays.merge(key, u.usageDays == null ? 0 : u.usageDays, Integer::sum);
        }

        // 单位（按物料）
        Map<String, String> unitMap = new HashMap<>();
        for (Object[] row : ledgerRepo.sumQtyGroupByMaterial()) {
            if (row[2] != null) unitMap.putIfAbsent((String) row[0], row[2].toString());
        }
        // v5.33：按物料+仓库的库存分布；v5.34 预警按仓库计算——仓库名映射 + 每仓库存
        Map<String, String> whNames = new HashMap<>();
        for (com.pengyuan.pims.entity.Warehouse w : warehouseRepo.findAll()) {
            whNames.put(String.valueOf(w.id), w.name);
        }
        Map<String, java.util.LinkedHashMap<String, BigDecimal>> stockByWhId = new HashMap<>();
        for (Object[] row : ledgerRepo.sumQtyGroupByMaterialAndWarehouse()) {
            String code = (String) row[0];
            String whId = row[1] != null ? row[1].toString() : "";
            stockByWhId.computeIfAbsent(code, k -> new java.util.LinkedHashMap<>())
                    .merge(whId, toBigDecimal(row[2]), BigDecimal::add);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : usageTotal.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            String code = parts[0];
            String whId = parts.length > 1 ? parts[1] : "";
            BigDecimal total = e.getValue();
            int months = usageMonths.getOrDefault(e.getKey(), 0);
            int days = usageDays.getOrDefault(e.getKey(), 0);
            if (months == 0 || days == 0) continue;
            BigDecimal avgDaily = total.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
            if (avgDaily.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal avgMonthly = total.divide(BigDecimal.valueOf(months), 3, RoundingMode.HALF_UP);
            // v5.34：当前库存/安全库存/可用天数均按该仓库计算
            BigDecimal stock = stockByWhId.getOrDefault(code, new java.util.LinkedHashMap<>())
                    .getOrDefault(whId, BigDecimal.ZERO);
            BigDecimal safeStock = avgDaily.multiply(BigDecimal.valueOf(30)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal availableDays = stock.divide(avgDaily, 1, RoundingMode.HALF_UP);
            // 预警过滤：该仓库可用天数 < 15 天
            if (availableDays.compareTo(BigDecimal.valueOf(15)) >= 0) continue;

            Material m = rawMaterials.get(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", code);
            row.put("materialName", m != null ? m.name : null);
            row.put("brand", m != null ? m.brand : null);   // v5.71.5 物料牌号（生成采购单带出）
            row.put("category", m != null ? m.category : null);
            // v4.9：小类显示中文（如 AC → 助剂类小类中文），无映射时回退代码
            row.put("subCategory", m != null ? m.subCategory : null);
            row.put("subCategoryName", m != null && m.subCategory != null
                    ? subCategoryNames.getOrDefault(m.subCategory, m.subCategory) : null);
            row.put("unit", unitMap.getOrDefault(code, ""));
            // v5.34：仓库维度
            row.put("warehouseId", whId);
            row.put("warehouseName", whNames.getOrDefault(whId, whId.isEmpty() ? "未知仓" : whId));
            row.put("currentQty", stock.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros());
            row.put("safeStock", safeStock);
            row.put("avgMonthlyQty", avgMonthly);
            row.put("avgDailyQty", avgDaily);
            row.put("availableDays", availableDays);
            row.put("level", availableDays.compareTo(BigDecimal.TEN) < 0 ? "RED" : "ORANGE");
            rows.add(row);
        }
        // 按可用天数升序（最紧急在前），无库存(=0)自然排最前
        rows.sort(Comparator.comparing(r -> (BigDecimal) r.get("availableDays")));

        long redCount = rows.stream().filter(r -> "RED".equals(r.get("level"))).count();
        return Map.of(
                "rows", rows,
                "warningCount", rows.size(),
                "redCount", redCount,
                "totalRawCount", rawMaterials.size()
        );
    }

    // ==================== 批次过期预警报表（v5.23） ====================

    /**
     * 批次过期预警：统计在库（qty>0）且有过期日期的批次。
     * 口径：
     *  - 剩余天数 = 过期日期 − 今天（LocalDate 直接比较，当天到期不算过期）
     *  - 已过期（剩余 < 0 天，红色 EXPIRED）与 30 天内到期（0 ≤ 剩余 ≤ 30，橙色 WARNING）进入报表
     *  - 同（物料+批号）跨库位/仓库聚合总量，过期日期取最早（最保守）
     * 数据源：inventory_ledger 实体查询（几百行量级，内存聚合，避开毫秒时间戳 SQL 转换坑）
     */
    public Map<String, Object> expiryReport() {
        return (Map<String, Object>) cached("expiryReport", () -> doExpiryReport());
    }

    private Map<String, Object> doExpiryReport() {
        Map<String, String> whNames = new HashMap<>();
        for (com.pengyuan.pims.entity.Warehouse w : warehouseRepo.findAll()) {
            whNames.put(String.valueOf(w.id), w.name);
        }
        // 按 物料|批号 聚合在库且有过期日期的台账行（v5.30 排除质检不合格行——不合格品库隔离；
        // v5.55 改 SQL 预过滤，替代 findAll 全表加载后内存过滤）
        Map<String, List<InventoryLedger>> byBatch = new LinkedHashMap<>();
        for (InventoryLedger l : ledgerRepo.findExpiryCandidates()) {
            String key = (l.materialCode == null ? "" : l.materialCode) + "|" + (l.batchNo == null ? "" : l.batchNo);
            byBatch.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal expiredQtySum = BigDecimal.ZERO;
        BigDecimal expiringQtySum = BigDecimal.ZERO;
        for (List<InventoryLedger> list : byBatch.values()) {
            BigDecimal qty = list.stream().map(l -> l.qty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = list.stream()
                    .map(l -> l.amount != null ? l.amount
                            : l.qty.multiply(l.unitPrice != null ? l.unitPrice : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 批次内最早过期日期（跨库位最保守）
            LocalDate expiry = list.stream().map(l -> l.expiryDate).min(LocalDate::compareTo).orElse(null);
            if (expiry == null) continue;
            long remainDays = ChronoUnit.DAYS.between(today, expiry);
            boolean expired = remainDays < 0;
            boolean expiring = !expired && remainDays <= 30;
            if (!expired && !expiring) continue; // 30 天外不预警
            InventoryLedger first = list.get(0);
            String warehouses = list.stream()
                    .map(l -> whNames.getOrDefault(String.valueOf(l.warehouseId), String.valueOf(l.warehouseId)))
                    .distinct().collect(java.util.stream.Collectors.joining("/"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", first.materialCode);
            row.put("materialName", first.materialName);
            row.put("batchNo", first.batchNo);
            row.put("qty", qty.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros());
            row.put("unit", first.unit);
            row.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
            row.put("expiryDate", expiry.toString());
            row.put("remainDays", remainDays);
            row.put("level", expired ? "EXPIRED" : "WARNING");
            row.put("warehouses", warehouses);
            rows.add(row);
            if (expired) expiredQtySum = expiredQtySum.add(qty);
            else expiringQtySum = expiringQtySum.add(qty);
        }
        // 按剩余天数升序（最紧急在前：已过期负值最先）
        rows.sort(Comparator.comparingLong(r -> (Long) r.get("remainDays")));
        long expiredCount = rows.stream().filter(r -> "EXPIRED".equals(r.get("level"))).count();
        return Map.of(
                "rows", rows,
                "expiredCount", expiredCount,
                "expiringCount", rows.size() - expiredCount,
                "expiredQty", expiredQtySum.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros(),
                "expiringQty", expiringQtySum.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros(),
                "totalBatchCount", byBatch.size()
        );
    }

    // ==================== 销售报表 ====================

    /**
     * 销售报表（出库口径）：月度收入/成本/数量趋势 + 客户TOP10 + 产品TOP10 + 订单状态分布 + 制单人排行。
     * 收入 = 实发数量 × 订单明细单价（出库单只存成本单价，售价在销售订单明细）
     */
    public Map<String, Object> salesReport(int months) {
    return (Map<String, Object>) cached("salesReport:" + months, () -> doSalesReport(months));
}

private Map<String, Object> doSalesReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        List<Object[]> monthly = salesOutRepo.monthlySalesSince(since);
        List<String> periods = new ArrayList<>();
        List<BigDecimal> incomes = new ArrayList<>();
        List<BigDecimal> costs = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        for (Object[] row : monthly) {
            periods.add((String) row[0]);
            incomes.add(toBigDecimal(row[1]));
            costs.add(toBigDecimal(row[2]));
            qtys.add(toBigDecimal(row[3]));
        }
        result.put("monthlyTrend", Map.of("labels", periods, "income", incomes, "cost", costs, "qty", qtys));

        result.put("customerRank", toRank(salesOutRepo.customerRankSince(since)));
        result.put("materialRank", toRank(salesOutRepo.materialRankSince(since)));
        result.put("salesmanRank", toRank(salesOutRepo.salesmanRankSince(since)));

        // 订单状态分布（订单口径金额）
        List<Object[]> statusRows = salesOrderRepo.statusAmountSince(since);
        List<Map<String, Object>> statusDist = new ArrayList<>();
        for (Object[] row : statusRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", row[0] != null ? row[0].toString() : "未知");
            item.put("count", ((Number) row[1]).longValue());
            item.put("amount", toBigDecimal(row[2]));
            statusDist.add(item);
        }
        result.put("statusDist", statusDist);
        return result;
    }

    // ==================== 毛利分析 ====================

    /** 毛利分析：月度收入/成本/毛利 + 产品/客户毛利TOP10 + 总毛利率 */
    public Map<String, Object> marginReport(int months) {
    return (Map<String, Object>) cached("marginReport:" + months, () -> doMarginReport(months));
}

private Map<String, Object> doMarginReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        List<Object[]> monthly = salesOutRepo.monthlySalesSince(since);
        List<String> periods = new ArrayList<>();
        List<BigDecimal> incomes = new ArrayList<>();
        List<BigDecimal> costs = new ArrayList<>();
        List<BigDecimal> margins = new ArrayList<>();
        for (Object[] row : monthly) {
            periods.add((String) row[0]);
            BigDecimal income = toBigDecimal(row[1]);
            BigDecimal cost = toBigDecimal(row[2]);
            incomes.add(income);
            costs.add(cost);
            margins.add(income.subtract(cost));
        }
        result.put("monthlyTrend", Map.of("labels", periods, "income", incomes, "cost", costs, "margin", margins));

        // 产品/客户毛利TOP10（排行查询已带成本列）
        result.put("productMargin", toRankWithMargin(salesOutRepo.materialRankSince(since)));
        result.put("customerMargin", toRankWithMargin(salesOutRepo.customerRankSince(since)));

        BigDecimal totalIncome = incomes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMargin = totalIncome.subtract(totalCost);
        result.put("summary", Map.of(
                "totalIncome", totalIncome,
                "totalCost", totalCost,
                "totalMargin", totalMargin,
                "marginRate", totalIncome.compareTo(BigDecimal.ZERO) > 0
                        ? totalMargin.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO));
        return result;
    }

    // ==================== 销售订单执行率 ====================

    /** 销售订单执行清单：明细级发货完成率（系统无承诺交期，按完成率口径） */
    public Map<String, Object> orderExecReport() {
    return (Map<String, Object>) cached("orderExecReport", () -> doOrderExecReport());
}

private Map<String, Object> doOrderExecReport() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int completed = 0, partial = 0, none = 0;
        for (Object[] row : salesOrderItemRepo.orderExecList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderNo", row[0]);
            item.put("customerName", row[1]);
            item.put("status", row[2]);
            item.put("orderDate", msToDate(row[3]));
            item.put("expectedShipDate", msToDate(row[4]));
            item.put("materialCode", row[5]);
            item.put("materialName", row[6]);
            BigDecimal qty = toBigDecimal(row[7]);
            BigDecimal shipped = toBigDecimal(row[8]);
            BigDecimal returned = toBigDecimal(row[9]);
            item.put("qty", qty);
            item.put("shippedQty", shipped);
            item.put("returnQty", returned);
            BigDecimal rate = qty.compareTo(BigDecimal.ZERO) > 0
                    ? shipped.multiply(BigDecimal.valueOf(100)).divide(qty, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            item.put("rate", rate);
            if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) completed++;
            else if (rate.compareTo(BigDecimal.ZERO) > 0) partial++;
            else none++;
            rows.add(item);
        }
        result.put("rows", rows);
        result.put("summary", Map.of("total", rows.size(), "completed", completed, "partial", partial, "none", none));
        return result;
    }

    // ==================== 质检报表 ====================

    /** 质检报表：月度单数与合格率 + 判定分布 + 不合格物料TOP10 + 质检明细 */
    public Map<String, Object> qcReport(int months) {
    return (Map<String, Object>) cached("qcReport:" + months, () -> doQcReport(months));
}

private Map<String, Object> doQcReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        // 月度单数与合格率（PASS/CONCESSION 视为合格）
        List<Object[]> monthly = qcRepo.monthlyResultSince(since);
        Map<String, int[]> countMap = new LinkedHashMap<>();
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        for (Object[] row : monthly) {
            String p = (String) row[0];
            String status = (String) row[1];
            int cnt = ((Number) row[2]).intValue();
            int[] agg = countMap.computeIfAbsent(p, k -> new int[2]);
            agg[0] += cnt;
            if ("PASS".equals(status) || "CONCESSION".equals(status)) agg[1] += cnt;
            statusCount.merge(status, cnt, Integer::sum);
        }
        List<String> periods = new ArrayList<>(countMap.keySet());
        List<Integer> totals = new ArrayList<>();
        List<BigDecimal> passRates = new ArrayList<>();
        for (var e : countMap.entrySet()) {
            totals.add(e.getValue()[0]);
            passRates.add(e.getValue()[1] == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(e.getValue()[1] * 100.0 / e.getValue()[0]).setScale(1, RoundingMode.HALF_UP));
        }
        result.put("monthlyTrend", Map.of("labels", periods, "total", totals, "passRate", passRates));

        // 判定分布
        Map<String, String> statusLabels = Map.of("PENDING", "待质检", "PASS", "合格", "CONCESSION", "让步接收", "REJECT", "不合格", "TAILING", "油尾");
        List<Map<String, Object>> resultDist = new ArrayList<>();
        for (var e : statusCount.entrySet()) {
            resultDist.add(Map.of("name", statusLabels.getOrDefault(e.getKey(), e.getKey()), "value", e.getValue()));
        }
        result.put("resultDist", resultDist);

        // 不合格物料TOP10
        result.put("rejectMaterialRank", toRank(qcRepo.rejectMaterialTop(since)));

        // 质检明细（近N个月，v5.9 改 SQL 过滤替代 findAll+内存过滤；前端筛选+分页）
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Object[] q : qcRepo.detailSince(since)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("inspectionNo", q[0]);
            item.put("refDocType", q[1]);
            item.put("refDocNo", q[2]);
            item.put("materialName", q[3]);
            item.put("materialCode", q[4]);
            item.put("batchNo", q[5]);
            item.put("category", categoryLabel(q[6] != null ? q[6].toString() : null));
            item.put("qty", toBigDecimal(q[7]));
            item.put("status", q[8]);
            item.put("inspector", q[9]);
            item.put("inspectDate", msToDate(q[10]));
            item.put("resultRemark", q[11]);
            detail.add(item);
        }
        result.put("detail", detail);
        return result;
    }

    // ==================== 应收应付账龄分析 ====================

    /** 账龄分析：应收/应付未结清金额按到期日分层（未到期/1-30/31-60/61-90/90+） */
    public Map<String, Object> agingReport() {
    return (Map<String, Object>) cached("agingReport", () -> doAgingReport());
}

private Map<String, Object> doAgingReport() {
        // v5.55 注：曾改用 native agingList 直查，但 sqlite-jdbc 对 NUMERIC 声明列的 getObject 会截断小数
        // （received_amount 67.65 → 67），账龄金额差分踩坑；AR/AP 表量级小，实体路径无此问题，维持实体读取。
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        Map<Long, String> custNames = new HashMap<>();
        for (var c : customerRepo.findAll()) custNames.put(c.id, c.name);
        Map<Long, String> supNames = new HashMap<>();
        for (var s : supplierRepo.findAll()) supNames.put(s.id, s.name);

        // 应收
        List<String> bucketNames = List.of("未到期", "逾期1-30天", "逾期31-60天", "逾期61-90天", "逾期90天以上");
        List<Map<String, Object>> arDetail = new ArrayList<>();
        BigDecimal[] arBuckets = new BigDecimal[5];
        BigDecimal arTotal = BigDecimal.ZERO, arOverdue = BigDecimal.ZERO;
        for (var ar : arRepo.findAll()) {
            if ("PAID".equals(ar.status)) continue;
            BigDecimal remain = ar.amount.subtract(ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO);
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
            int idx = agingBucket(today, ar.dueDate);
            arBuckets[idx] = arBuckets[idx] == null ? remain : arBuckets[idx].add(remain);
            arTotal = arTotal.add(remain);
            if (idx > 0) arOverdue = arOverdue.add(remain);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docNo", ar.docNo);
            item.put("partnerName", custNames.getOrDefault(ar.customerId, "客户#" + ar.customerId));
            item.put("salesOrderNo", ar.salesOrderNo);
            item.put("amount", ar.amount);
            item.put("receivedAmount", ar.receivedAmount != null ? ar.receivedAmount : BigDecimal.ZERO);
            item.put("remain", remain);
            item.put("dueDate", ar.dueDate != null ? ar.dueDate.toString() : null);
            item.put("status", ar.status);
            arDetail.add(item);
        }
        result.put("arBuckets", toBucketList(bucketNames, arBuckets));
        result.put("arDetail", arDetail);

        // 应付
        List<Map<String, Object>> apDetail = new ArrayList<>();
        BigDecimal[] apBuckets = new BigDecimal[5];
        BigDecimal apTotal = BigDecimal.ZERO, apOverdue = BigDecimal.ZERO;
        for (var ap : apRepo.findAll()) {
            if ("PAID".equals(ap.status)) continue;
            BigDecimal remain = ap.amount.subtract(ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO);
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
            int idx = agingBucket(today, ap.dueDate);
            apBuckets[idx] = apBuckets[idx] == null ? remain : apBuckets[idx].add(remain);
            apTotal = apTotal.add(remain);
            if (idx > 0) apOverdue = apOverdue.add(remain);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docNo", ap.docNo);
            item.put("partnerName", supNames.getOrDefault(ap.supplierId, "供应商#" + ap.supplierId));
            item.put("purchaseOrderNo", ap.purchaseOrderNo != null ? ap.purchaseOrderNo : ap.outsourceOrderNo);
            item.put("amount", ap.amount);
            item.put("paidAmount", ap.paidAmount != null ? ap.paidAmount : BigDecimal.ZERO);
            item.put("remain", remain);
            item.put("dueDate", ap.dueDate != null ? ap.dueDate.toString() : null);
            item.put("status", ap.status);
            apDetail.add(item);
        }
        result.put("apBuckets", toBucketList(bucketNames, apBuckets));
        result.put("apDetail", apDetail);

        result.put("summary", Map.of(
                "arTotal", arTotal, "arOverdue", arOverdue,
                "apTotal", apTotal, "apOverdue", apOverdue));
        return result;
    }

    // ==================== 库存分析报表 ====================

    /** 库存分析：批次库龄分层 + 呆滞TOP + 大类金额汇总 + 实际领用vs标准用量 */
    public Map<String, Object> stockAnalysisReport() {
    return (Map<String, Object>) cached("stockAnalysisReport", () -> doStockAnalysisReport());
}

private Map<String, Object> doStockAnalysisReport() {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        // v5.9：库龄分层 + 呆滞TOP + 大类金额 全部改 SQL GROUP BY 聚合（替代 findAll 全内存计算）
        String[] ageNames = {"0-30天", "31-90天", "91-180天", "180天以上"};
        BigDecimal[] ageQty = new BigDecimal[4];
        BigDecimal[] ageAmount = new BigDecimal[4];
        for (Object[] row : ledgerRepo.ageDistGroup()) {
            int bucket = ((Number) row[0]).intValue();
            ageQty[bucket] = toBigDecimal(row[1]);
            ageAmount[bucket] = toBigDecimal(row[2]);
        }
        result.put("ageDist", toBucketList(ageNames, ageQty, ageAmount));
        BigDecimal stockTotal = BigDecimal.ZERO;
        for (BigDecimal a : ageAmount) stockTotal = stockTotal.add(a != null ? a : BigDecimal.ZERO);
        result.put("stockAmountTotal", stockTotal);

        // 呆滞 TOP20（SQL 已按 库龄×金额 排序截取）
        List<Map<String, Object>> dormant = new ArrayList<>();
        for (Object[] row : ledgerRepo.dormantTop20()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialCode", row[0]);
            item.put("materialName", row[1]);
            item.put("batchNo", row[2]);
            item.put("days", ((Number) row[3]).longValue());
            item.put("qty", toBigDecimal(row[4]));
            item.put("amount", toBigDecimal(row[5]));
            item.put("warehouseName", warehouseName(row[6] != null ? row[6].toString() : null));
            dormant.add(item);
        }
        result.put("dormantRank", dormant);

        // 大类金额汇总（SQL JOIN material 取大类，无档案归'其他'）
        List<Map<String, Object>> categoryAmount = new ArrayList<>();
        for (Object[] row : ledgerRepo.categoryAmountGroup()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", "OTHER".equals(row[0]) ? "其他" : categoryLabel(row[0] != null ? row[0].toString() : null));
            item.put("qty", toBigDecimal(row[1]));
            item.put("amount", toBigDecimal(row[2]));
            categoryAmount.add(item);
        }
        categoryAmount.sort((a, b) -> toBigDecimal(b.get("amount")).compareTo(toBigDecimal(a.get("amount"))));
        result.put("categoryAmount", categoryAmount);

        // 实际领用 vs 标准用量（近12个月）
        String since = sinceDate(12);
        List<Map<String, Object>> usage = new ArrayList<>();
        for (Object[] row : prodOutRepo.usageVsStandardSince(since)) {
            BigDecimal actual = toBigDecimal(row[4]);
            BigDecimal standard = toBigDecimal(row[5]);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderNo", row[0]);
            item.put("productName", row[1]);
            item.put("materialCode", row[2]);
            item.put("materialName", row[3]);
            item.put("actual", actual);
            item.put("standard", standard);
            item.put("diff", actual.subtract(standard));
            usage.add(item);
        }
        result.put("usageCompare", usage);
        return result;
    }

    // ==================== 领料差异分析（v5.64）====================

    /**
     * 领料差异分析：实际净领料 vs 配方计划用量——持续定向偏差 = 配方用量与实际工艺不符的信号。
     * 三级视图：byProduct（配方维度，核心）/ byMaterial（原料维度）/ rows（订单×物料明细）。
     * 差异率按金额加权（=Σ实际金额/Σ计划金额−1，同价折算纯量差，跨物料单位可加总）；
     * 单行差异率 = (实际−计划)/计划（多领为正、少领为负）；|加权差异率|≥5% 标预警。
     */
    public Map<String, Object> materialVarianceReport() {
        return (Map<String, Object>) cached("materialVarianceReport", () -> doMaterialVarianceReport());
    }

    private Map<String, Object> doMaterialVarianceReport() {
        List<Map<String, Object>> rows = new ArrayList<>();
        // 聚合桶：key → [订单Set, 行数, 多领, 少领, 相符, Σ计划金额, Σ实际金额, Σ差异金额]
        java.util.Map<String, java.util.Map<String, Object>> byProduct = new LinkedHashMap<>();
        java.util.Map<String, java.util.Map<String, Object>> byMaterial = new LinkedHashMap<>();

        for (Object[] r : prodOutRepo.findMaterialVarianceRows()) {
            String orderNo = (String) r[0];
            String productName = (String) r[1];
            String productKey = (String) r[2];
            String materialCode = (String) r[3];
            String materialName = (String) r[4];
            BigDecimal planned = toBigDecimal(r[6]);
            BigDecimal actual = toBigDecimal(r[7]);
            BigDecimal plannedAmt = toBigDecimal(r[8]);
            BigDecimal actualAmt = toBigDecimal(r[9]);
            if (planned.compareTo(BigDecimal.ZERO) <= 0) continue;   // 计划量为 0 的异常行不统计

            BigDecimal diff = actual.subtract(planned);
            BigDecimal diffRate = diff.divide(planned, 4, RoundingMode.HALF_UP);
            BigDecimal diffAmount = actualAmt.subtract(plannedAmt);
            // 行判定容差 ±2%（领料正常称量误差内视为相符）
            String flag = diffRate.abs().compareTo(new BigDecimal("0.02")) < 0 ? "MATCH"
                    : (diffRate.signum() > 0 ? "OVER" : "UNDER");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", orderNo);
            row.put("productCode", productKey);
            row.put("productName", productName);
            row.put("materialCode", materialCode);
            row.put("materialName", materialName);
            row.put("unit", r[5]);
            row.put("plannedQty", planned);
            row.put("actualQty", actual);
            row.put("diffQty", diff);
            row.put("diffRate", diffRate);
            row.put("plannedAmount", plannedAmt);
            row.put("actualAmount", actualAmt);
            row.put("diffAmount", diffAmount);
            row.put("flag", flag);
            rows.add(row);

            accumulateVariance(byProduct, productKey, productName, orderNo, flag, plannedAmt, actualAmt, diffAmount);
            accumulateVariance(byMaterial, materialCode, materialName, orderNo, flag, plannedAmt, actualAmt, diffAmount);
        }
        rows.sort((a, b) -> toBigDecimal(b.get("diffRate")).abs().compareTo(toBigDecimal(a.get("diffRate")).abs()));

        List<Map<String, Object>> productList = new ArrayList<>(byProduct.values());
        List<Map<String, Object>> materialList = new ArrayList<>(byMaterial.values());
        for (List<Map<String, Object>> list : List.of(productList, materialList)) {
            for (Map<String, Object> m : list) {
                m.put("orderCount", ((java.util.Set<?>) m.get("orders")).size());
                m.remove("orders");
                BigDecimal planned = toBigDecimal(m.get("plannedAmount"));
                BigDecimal actual = toBigDecimal(m.get("actualAmount"));
                m.put("weightedRate", planned.compareTo(BigDecimal.ZERO) > 0
                        ? actual.divide(planned, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE) : BigDecimal.ZERO);
            }
        }
        // 预警优先、差异金额降序
        java.util.Comparator<Map<String, Object>> cmp = java.util.Comparator
                .comparing((Map<String, Object> m) -> toBigDecimal(m.get("weightedRate")).abs()).reversed();
        productList.sort(cmp);
        materialList.sort(cmp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("byProduct", productList);
        result.put("byMaterial", materialList);
        result.put("productWarnCount", productList.stream()
                .filter(m -> toBigDecimal(m.get("weightedRate")).abs().compareTo(new BigDecimal("0.05")) >= 0).count());
        result.put("materialWarnCount", materialList.stream()
                .filter(m -> toBigDecimal(m.get("weightedRate")).abs().compareTo(new BigDecimal("0.05")) >= 0).count());
        return result;
    }

    /** 差异聚合桶累加（byProduct/byMaterial 共用） */
    @SuppressWarnings("unchecked")
    private void accumulateVariance(java.util.Map<String, java.util.Map<String, Object>> bucket, String key,
                                    String name, String orderNo, String flag,
                                    BigDecimal plannedAmt, BigDecimal actualAmt, BigDecimal diffAmount) {
        java.util.Map<String, Object> m = bucket.computeIfAbsent(key, k -> {
            java.util.Map<String, Object> n = new LinkedHashMap<>();
            n.put("key", k);
            n.put("name", name);
            n.put("orders", new java.util.HashSet<String>());
            n.put("lineCount", 0);
            n.put("overCount", 0);
            n.put("underCount", 0);
            n.put("matchCount", 0);
            n.put("plannedAmount", BigDecimal.ZERO);
            n.put("actualAmount", BigDecimal.ZERO);
            n.put("diffAmount", BigDecimal.ZERO);
            return n;
        });
        ((java.util.Set<String>) m.get("orders")).add(orderNo);
        m.put("lineCount", (Integer) m.get("lineCount") + 1);
        if ("OVER".equals(flag)) m.put("overCount", (Integer) m.get("overCount") + 1);
        else if ("UNDER".equals(flag)) m.put("underCount", (Integer) m.get("underCount") + 1);
        else m.put("matchCount", (Integer) m.get("matchCount") + 1);
        m.put("plannedAmount", toBigDecimal(m.get("plannedAmount")).add(plannedAmt));
        m.put("actualAmount", toBigDecimal(m.get("actualAmount")).add(actualAmt));
        m.put("diffAmount", toBigDecimal(m.get("diffAmount")).add(diffAmount));
    }

    // ==================== 采购分析报表 ====================

    /** 采购分析：同物料多供应商比价 + 到货完成率 + 未到齐明细 */
    public Map<String, Object> purchaseAnalysisReport() {
    return (Map<String, Object>) cached("purchaseAnalysisReport", () -> doPurchaseAnalysisReport());
}

private Map<String, Object> doPurchaseAnalysisReport() {
        Map<String, Object> result = new LinkedHashMap<>();
        String since = sinceDate(12);

        // 比价明细（近12个月）+ 按物料聚合供应商价格对比
        List<Map<String, Object>> priceRows = new ArrayList<>();
        Map<String, Map<String, Object>> priceMap = new LinkedHashMap<>();
        for (Object[] row : purchaseRepo.priceCompareSince(since)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialCode", row[0]);
            item.put("materialName", row[1]);
            item.put("brand", row[2]);
            item.put("supplierName", row[3]);
            item.put("unitPrice", toBigDecimal(row[4]));
            item.put("qty", toBigDecimal(row[5]));
            item.put("totalAmount", toBigDecimal(row[6]));
            item.put("purchaseDate", row[7]);
            priceRows.add(item);

            Map<String, Object> agg = priceMap.computeIfAbsent((String) row[0], k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialCode", row[0]);
                m.put("materialName", row[1]);
                m.put("brand", row[2]);
                m.put("suppliers", new LinkedHashMap<String, Map<String, Object>>());
                return m;
            });
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> suppliers = (Map<String, Map<String, Object>>) agg.get("suppliers");
            String sup = row[3] != null ? row[3].toString() : "未知";
            Map<String, Object> s = suppliers.computeIfAbsent(sup, k -> {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("supplierName", sup);
                sm.put("latestPrice", BigDecimal.ZERO);
                sm.put("totalValue", BigDecimal.ZERO);
                sm.put("totalQty", BigDecimal.ZERO);
                sm.put("count", 0);
                return sm;
            });
            if (((Integer) s.get("count")) == 0) s.put("latestPrice", toBigDecimal(row[4])); // 明细按日期倒序，首条即最新
            s.put("totalValue", toBigDecimal(s.get("totalValue")).add(toBigDecimal(row[4]).multiply(toBigDecimal(row[5]))));
            s.put("totalQty", toBigDecimal(s.get("totalQty")).add(toBigDecimal(row[5])));
            s.put("count", (Integer) s.get("count") + 1);
        }
        List<Map<String, Object>> priceCompare = new ArrayList<>();
        for (var e : priceMap.entrySet()) {
            Map<String, Object> m = e.getValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> supList = new ArrayList<>(((Map<String, Map<String, Object>>) m.get("suppliers")).values());
            BigDecimal totalQty = supList.stream().map(x -> toBigDecimal(x.get("totalQty"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Map<String, Object> s : supList) {
                s.put("avgPrice", toBigDecimal(s.get("totalQty")).compareTo(BigDecimal.ZERO) > 0
                        ? toBigDecimal(s.get("totalValue")).divide(toBigDecimal(s.get("totalQty")), 3, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
                s.remove("totalValue");
            }
            supList.sort((a, b) -> toBigDecimal(a.get("avgPrice")).compareTo(toBigDecimal(b.get("avgPrice"))));
            m.put("supplierCount", supList.size());
            m.put("totalQty", totalQty);
            m.put("suppliers", supList);
            priceCompare.add(m);
        }
        priceCompare.sort(Comparator.comparing(m -> (String) m.get("materialCode")));
        result.put("priceRows", priceRows);
        result.put("priceCompare", priceCompare);

        // 到货完成率（原料+成品，非草稿；v5.9 改 SQL 聚合替代 findAll）
        BigDecimal totalQty = BigDecimal.ZERO, receivedQty = BigDecimal.ZERO;
        int doneCount = 0, incompleteCount = 0;
        for (Object[] row : purchaseRepo.completionStats()) {
            totalQty = totalQty.add(toBigDecimal(row[0]));
            receivedQty = receivedQty.add(toBigDecimal(row[1]));
            doneCount += ((Number) row[2]).intValue();
            incompleteCount += ((Number) row[3]).intValue() - ((Number) row[2]).intValue();
        }
        for (Object[] row : finishedPurchaseRepo.completionStats()) {
            totalQty = totalQty.add(toBigDecimal(row[0]));
            receivedQty = receivedQty.add(toBigDecimal(row[1]));
            doneCount += ((Number) row[2]).intValue();
            incompleteCount += ((Number) row[3]).intValue() - ((Number) row[2]).intValue();
        }
        result.put("completion", Map.of(
                "totalQty", totalQty, "receivedQty", receivedQty,
                "rate", totalQty.compareTo(BigDecimal.ZERO) > 0
                        ? receivedQty.multiply(BigDecimal.valueOf(100)).divide(totalQty, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                "doneCount", doneCount, "incompleteCount", incompleteCount));

        // 未到齐明细（原料+成品合并）
        List<Map<String, Object>> incomplete = new ArrayList<>();
        for (Object[] row : purchaseRepo.incompleteList()) incomplete.add(toIncomplete(row));
        for (Object[] row : finishedPurchaseRepo.incompleteList()) incomplete.add(toIncomplete(row));
        incomplete.sort((a, b) -> ((String) b.get("orderNo")).compareTo((String) a.get("orderNo")));
        result.put("incompleteList", incomplete);
        return result;
    }

    private Map<String, Object> toIncomplete(Object[] row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderNo", row[0]);
        item.put("supplierName", row[1]);
        item.put("materialCode", row[2]);
        item.put("materialName", row[3]);
        item.put("qty", toBigDecimal(row[4]));
        item.put("receivedQty", toBigDecimal(row[5]));
        item.put("totalAmount", toBigDecimal(row[6]));
        item.put("purchaseDate", row[7]);
        return item;
    }

    // ==================== 委外报表 ====================

    /** 委外报表：月度加工费 + 代工厂排行 + 代工厂得率 */
    public Map<String, Object> outsourceReport(int months) {
    return (Map<String, Object>) cached("outsourceReport:" + months, () -> doOutsourceReport(months));
}

private Map<String, Object> doOutsourceReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        List<Object[]> monthly = outsourceRepo.monthlyFeeSince(since);
        List<String> periods = new ArrayList<>();
        List<BigDecimal> fees = new ArrayList<>();
        BigDecimal totalFee = BigDecimal.ZERO;
        for (Object[] row : monthly) {
            periods.add((String) row[0]);
            BigDecimal fee = toBigDecimal(row[1]);
            fees.add(fee);
            totalFee = totalFee.add(fee);
        }
        result.put("monthlyFee", Map.of("labels", periods, "values", fees));
        result.put("totalFee", totalFee);
        result.put("processorRank", toRank(outsourceRepo.processorFeeSince(since)));

        List<Map<String, Object>> processorYield = new ArrayList<>();
        for (Object[] row : outsourceInRepo.processorYield()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processor", row[0] != null ? row[0].toString() : "未知");
            item.put("batchCount", ((Number) row[1]).longValue());
            item.put("avgYield", toBigDecimal(row[2]));
            item.put("totalQty", toBigDecimal(row[3]));
            processorYield.add(item);
        }
        result.put("processorYield", processorYield);
        return result;
    }

    // ==================== 经营看板 ====================

    /** 经营看板：当月/近N月 销售、采购、加工费、收款、付款、库存金额 KPI + 月度对比 */
    public Map<String, Object> overviewReport(int months) {
    return (Map<String, Object>) cached("overviewReport:" + months, () -> doOverviewReport(months));
}

private Map<String, Object> doOverviewReport(int months) {
        String since = sinceDate(months);
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, BigDecimal> salesMap = new LinkedHashMap<>();
        Map<String, BigDecimal> costMap = new LinkedHashMap<>();
        for (Object[] row : salesOutRepo.monthlySalesSince(since)) {
            salesMap.put((String) row[0], toBigDecimal(row[1]));
            costMap.put((String) row[0], toBigDecimal(row[2]));
        }
        Map<String, BigDecimal> purchaseMap = new LinkedHashMap<>();
        for (Object[] row : purchaseRepo.monthlyAmountSince(since)) {
            purchaseMap.merge((String) row[0], toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : finishedPurchaseRepo.monthlyAmountSince(since)) {
            purchaseMap.merge((String) row[0], toBigDecimal(row[1]), BigDecimal::add);
        }
        Map<String, BigDecimal> feeMap = toPeriodMap(outsourceRepo.monthlyFeeSince(since));
        Map<String, BigDecimal> receiptMap = toPeriodMap(payReceiptRepo.monthlyAmountSince(since));
        Map<String, BigDecimal> paymentMap = toPeriodMap(payDisbursRepo.monthlyAmountSince(since));

        // 月份基准：合并全部序列
        Set<String> allPeriods = new TreeSet<>();
        allPeriods.addAll(salesMap.keySet());
        allPeriods.addAll(purchaseMap.keySet());
        allPeriods.addAll(feeMap.keySet());
        allPeriods.addAll(receiptMap.keySet());
        allPeriods.addAll(paymentMap.keySet());
        List<String> periods = new ArrayList<>(allPeriods);
        List<BigDecimal> salesList = new ArrayList<>(), purchaseList = new ArrayList<>(),
                feeList = new ArrayList<>(), receiptList = new ArrayList<>(), paymentList = new ArrayList<>();
        for (String p : periods) {
            salesList.add(salesMap.getOrDefault(p, BigDecimal.ZERO));
            purchaseList.add(purchaseMap.getOrDefault(p, BigDecimal.ZERO));
            feeList.add(feeMap.getOrDefault(p, BigDecimal.ZERO));
            receiptList.add(receiptMap.getOrDefault(p, BigDecimal.ZERO));
            paymentList.add(paymentMap.getOrDefault(p, BigDecimal.ZERO));
        }
        result.put("monthlyTrend", Map.of(
                "labels", periods,
                "sales", salesList, "purchase", purchaseList, "fee", feeList,
                "receipt", receiptList, "payment", paymentList));

        // KPI：当月 + 累计
        String current = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        BigDecimal salesTotal = salesList.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costTotal = costMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("kpi", Map.of(
                "month", current,
                "sales", salesMap.getOrDefault(current, BigDecimal.ZERO),
                "purchase", purchaseMap.getOrDefault(current, BigDecimal.ZERO),
                "fee", feeMap.getOrDefault(current, BigDecimal.ZERO),
                "receipt", receiptMap.getOrDefault(current, BigDecimal.ZERO),
                "payment", paymentMap.getOrDefault(current, BigDecimal.ZERO)));
        result.put("summary", Map.of(
                "salesTotal", salesTotal,
                "costTotal", costTotal,
                "margin", salesTotal.subtract(costTotal),
                "purchaseTotal", purchaseMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        return result;
    }

    // ==================== 工具方法 ====================

    /** v5.9 报表短缓存：60s TTL 自愈（写操作无需手动失效），报表接口从全表聚合降到毫秒级 */
    private final Map<String, Object> reportCache = new ConcurrentHashMap<>();
    private final Map<String, Long> reportCacheTime = new ConcurrentHashMap<>();
    private static final long REPORT_CACHE_TTL_MS = 60_000L;

    @SuppressWarnings("unchecked")
    private Object cached(String key, java.util.function.Supplier<Object> loader) {
        long now = System.currentTimeMillis();
        Long t = reportCacheTime.get(key);
        if (t != null && now - t < REPORT_CACHE_TTL_MS) {
            Object v = reportCache.get(key);
            if (v != null) return v;
        }
        Object v = loader.get();
        reportCache.put(key, v);
        reportCacheTime.put(key, now);
        return v;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        return new BigDecimal(obj.toString());
    }

    /** Object[]{name, value} 排名行转 List<Map> */
    private List<Map<String, Object>> toRank(List<Object[]> rows) {
        List<Map<String, Object>> rank = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0] != null ? row[0].toString() : "未知");
            item.put("value", toBigDecimal(row[1]));
            rank.add(item);
        }
        return rank;
    }

    /** Object[]{name, income, cost} 排行行转毛利结构 */
    private List<Map<String, Object>> toRankWithMargin(List<Object[]> rows) {
        List<Map<String, Object>> rank = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0] != null ? row[0].toString() : "未知");
            BigDecimal income = toBigDecimal(row[1]);
            BigDecimal cost = toBigDecimal(row[2]);
            item.put("income", income);
            item.put("cost", cost);
            item.put("margin", income.subtract(cost));
            item.put("marginRate", income.compareTo(BigDecimal.ZERO) > 0
                    ? income.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            rank.add(item);
        }
        return rank;
    }

    /** Object[]{period, value} 转 月份→金额 Map */
    private Map<String, BigDecimal> toPeriodMap(List<Object[]> rows) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : rows) map.put((String) row[0], toBigDecimal(row[1]));
        return map;
    }

    /** 收付账龄桶：到期日距今 0=未到期 / 1-30 / 31-60 / 61-90 / 90+ */
    private int agingBucket(LocalDate today, LocalDate dueDate) {
        if (dueDate == null) return 0;
        long days = ChronoUnit.DAYS.between(dueDate, today);
        if (days <= 0) return 0;
        if (days <= 30) return 1;
        if (days <= 60) return 2;
        if (days <= 90) return 3;
        return 4;
    }

    /** 库存库龄桶：入库距今 0-30 / 31-90 / 91-180 / 180+ */
    private int ageBucket(LocalDate today, LocalDate inboundDate) {
        if (inboundDate == null) return 3;
        long days = ChronoUnit.DAYS.between(inboundDate, today);
        if (days <= 30) return 0;
        if (days <= 90) return 1;
        if (days <= 180) return 2;
        return 3;
    }

    /** 金额桶数组转 List<Map{name, value}> */
    private List<Map<String, Object>> toBucketList(List<String> names, BigDecimal[] buckets) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            list.add(Map.of("name", names.get(i), "value", buckets[i] != null ? buckets[i] : BigDecimal.ZERO));
        }
        return list;
    }

    /** 数量+金额桶数组转 List<Map{name, qty, amount}> */
    private List<Map<String, Object>> toBucketList(String[] names, BigDecimal[] qtys, BigDecimal[] amounts) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            list.add(Map.of("name", names[i],
                    "qty", qtys[i] != null ? qtys[i] : BigDecimal.ZERO,
                    "amount", amounts[i] != null ? amounts[i] : BigDecimal.ZERO));
        }
        return list;
    }

    /** 毫秒时间戳（UTC+8 零点存储）转 yyyy-MM-dd 字符串；SQLite JDBC 可能返回 java.sql.Date，兼容处理 */
    private String msToDate(Object ms) {
        if (ms == null) return null;
        if (ms instanceof Number) {
            long v = ((Number) ms).longValue();
            return java.time.Instant.ofEpochMilli(v)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        }
        if (ms instanceof java.sql.Date) return ((java.sql.Date) ms).toLocalDate().toString();
        return ms.toString();
    }

    /** 物料大类代码转中文标签 */
    private String categoryLabel(String code) {
        if (code == null) return "其他";
        return switch (code) {
            case "A" -> "助剂";
            case "P" -> "颜料";
            case "F" -> "填料";
            case "R" -> "树脂";
            case "S" -> "溶剂";
            case "B" -> "半成品";
            case "C" -> "成品";
            default -> code;
        };
    }

    /** 仓库ID转名称 */
    private String warehouseName(String whId) {
        try {
            return warehouseRepo.findById(Long.parseLong(whId))
                    .map(w -> w.name)
                    .orElse(whId);
        } catch (NumberFormatException e) {
            return whId;
        }
    }
}
