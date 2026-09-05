package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.InventoryLedger;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.entity.QualityInspection;
import com.pengyuan.pims.entity.RawMaterialPurchase;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import com.pengyuan.pims.repository.QualityInspectionRepository;
import com.pengyuan.pims.repository.RawMaterialPurchaseRepository;
import com.pengyuan.pims.service.InventoryService;
import com.pengyuan.pims.service.OpeningStockService;
import com.pengyuan.pims.service.UserService;
import com.pengyuan.pims.common.OpeningImportException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * 库存查询入口：物料/仓库/批次多维汇总、批次追溯、价格走势、期初导入。
 * 批次视图为库位级台账（含质检信息持久化）；价格权限走 purchase:price。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;
    private final RawMaterialPurchaseRepository purchaseRepo;
    private final QualityInspectionRepository qcRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final OpeningStockService openingStockService;
    private final UserService userService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;   // v7.4 周转天数
    public InventoryController(InventoryService service, RawMaterialPurchaseRepository purchaseRepo,
                               QualityInspectionRepository qcRepo, InventoryLedgerRepository ledgerRepo,
                               OpeningStockService openingStockService, UserService userService,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.service = service;
        this.purchaseRepo = purchaseRepo;
        this.qcRepo = qcRepo;
        this.ledgerRepo = ledgerRepo;
        this.openingStockService = openingStockService;
        this.userService = userService;
        this.jdbc = jdbc;
    }

    /** 下载期初导入模板 */
    @GetMapping("/opening/template")
    @SaCheckPermission(value = "inventory:write")
    public void openingTemplate(HttpServletResponse response) throws IOException {
        openingStockService.downloadTemplate(response);
    }

    /** 期初数据导入（全量校验，任一错整体拒绝；通过则单事务落库） */
    @PostMapping("/opening/import")
    @SaCheckPermission(value = "inventory:write")
    public Result<?> openingImport(@RequestParam("file") MultipartFile file) {
        try {
            int count = openingStockService.importOpening(file, userService.currentOperatorName());
            return Result.ok(Map.of("count", count));
        } catch (OpeningImportException e) {
            return new Result<>(400, e.getMessage(), e.getErrors());
        }
    }

    /** 查询库存（分页 + 关键字 + 仓库筛选），并填充质检状态与检测结果（v5.9 后端分页） */
    @GetMapping
    @SaCheckPermission(value = "inventory:read")
    public Result<?> all(@RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "25") int pageSize,
                         @RequestParam(defaultValue = "") String keyword,
                         @RequestParam(required = false) String warehouseId) {
        var rows = service.searchLedger(keyword, warehouseId, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", enrichQcInfo(rows.getContent()), "total", rows.getTotalElements()));
    }

    @GetMapping("/material/{code}")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<InventoryLedger>> byMaterial(@PathVariable String code) {
        return Result.ok(enrichQcInfo(service.queryByMaterial(code)));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @SaCheckPermission(value = "inventory:read")
    public Result<?> byWarehouse(@PathVariable String warehouseId,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "200") int size) {
        // v7.1：默认分页 200 行（原全表返回；不传分页参数的老调用方也自动受上限保护）
        var r = service.queryByWarehousePaged(warehouseId, keyword, page, Math.min(size, 1000));
        r.put("rows", enrichQcInfo((List<InventoryLedger>) r.get("rows")));
        return Result.ok(r);
    }

    /** 库存批次选项（按物料+仓库查询，按批号聚合可用量，供退货出库等选批号，v5.4） */
    @GetMapping("/batch")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<java.util.Map<String, Object>>> batch(@RequestParam String materialCode,
                                                             @RequestParam(required = false) String warehouseId) {
        return Result.ok(service.queryBatchOptions(materialCode, warehouseId));
    }

    /**
     * v5.22：库存查询双视图（按编码聚合 / 按编码+批次聚合），替代原台账明细直出。
     * view=code：同一编码跨批次/库位合计总量（含批次数量列）；
     * view=batch：同一编码同一批次跨库位合计余量（含质检信息，按 物料|批次 精确匹配）。
     */
    @GetMapping("/summary")
    @SaCheckPermission(value = "inventory:read")
    public Result<?> summary(@RequestParam(defaultValue = "code") String view,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "25") int pageSize,
                             @RequestParam(defaultValue = "") String keyword,
                             @RequestParam(required = false) String warehouseId,
                             @RequestParam(required = false) String zoneId) {
        String kw = keyword == null ? "" : keyword.trim();
        String wh = warehouseId == null ? "" : warehouseId.trim();
        String zn = zoneId == null ? "" : zoneId.trim();   // v7.5 分库过滤（空=全库）
        var pr = org.springframework.data.domain.PageRequest.of(page - 1, pageSize);
        if ("batch".equals(view)) {
            var rows = ledgerRepo.sumByBatch(kw, wh, zn, pr);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] r : rows.getContent()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialCode", r[0]);
                m.put("materialName", r[1]);
                m.put("batchNo", r[2]);
                m.put("unit", r[3]);
                m.put("qty", r[4]);
                m.put("availableQty", r[5]);
                m.put("unitPrice", r[6]);
                m.put("amount", r[7]);
                m.put("inboundDate", msToDate(r[8]));
                m.put("expiryDate", msToDate(r[9]));
                // v5.31：批次所在库位（跨库位也精确到库位）
                m.put("locationNames", r.length > 10 && r[10] != null ? r[10].toString() : "");
                // v5.35：台账持久化质检状态优先（油尾批号显示「油尾」，不被历史质检单覆盖）
                // v5.32：SQL 侧 COALESCE('') 防 sqlite 类型推断炸，此处归一化回 null（保持 enrich 回填语义不变）
                m.put("qcStatus", r.length > 11 && r[11] != null && !r[11].toString().isEmpty() ? r[11].toString() : null);
                // v5.32：批次质检完整信息（台账持久化 qc_inspection_no/qc_result/qc_inspector/qc_date，库存页展示+展开看检测项）
                m.put("qcInspectionNo", r.length > 12 && r[12] != null && !r[12].toString().isEmpty() ? r[12].toString() : null);
                m.put("qcResult", r.length > 13 && r[13] != null && !r[13].toString().isEmpty() ? r[13].toString() : null);
                m.put("qcInspector", r.length > 14 && r[14] != null && !r[14].toString().isEmpty() ? r[14].toString() : null);
                m.put("qcDate", r.length > 15 ? msToDate(r[15]) : null);
                m.put("category", r.length > 16 ? nz(r[16]) : null);         // v7.5 大类
                m.put("subCategory", r.length > 17 ? nz(r[17]) : null);     // v7.5 小类
                list.add(m);
            }
            enrichQcForMaps(list);
            return Result.ok(java.util.Map.of("rows", list, "total", rows.getTotalElements()));
        }
        // view=code
        var rows = ledgerRepo.sumByCode(kw, wh, zn, pr);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : rows.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialCode", r[0]);
            m.put("materialName", r[1]);
            m.put("unit", r[2]);
            m.put("batchCount", r[3]);
            m.put("qty", r[4]);
            m.put("availableQty", r[5]);
            m.put("unitPrice", r[6]);
            m.put("amount", r[7]);
            m.put("inboundDate", msToDate(r[8]));
            m.put("stockDays", stockDays().get(r[0]));   // v7.4 周转天数（null=无出库/无库存）
            m.put("category", r.length > 9 ? nz(r[9]) : null);         // v7.5 大类
            m.put("subCategory", r.length > 10 ? nz(r[10]) : null);    // v7.5 小类
            list.add(m);
        }
        return Result.ok(java.util.Map.of("rows", list, "total", rows.getTotalElements()));
    }

    /**
     * v7.4 各物料库存周转天数：近 90 天出库量÷当前库存=年化周转次数，
     * 周转天数=365÷年化周转（越小周转越快；无出库返回 null 表示"零出库呆滞"，无库存返回 null=已清仓）。
     * 口径与 /api/report/turnover 一致：销售/生产/委外确认单 UNION，REWORK 返工不计，隔离行不计库存。
     */
    private Map<String, Object> stockDays() {
        long since = System.currentTimeMillis() - 90L * 86400_000L;
        String outSql = """
                SELECT material_code AS code, SUM(qty) AS q FROM (
                  SELECT material_code, qty FROM sales_outbound WHERE status='CONFIRMED' AND create_time >= ?
                  UNION ALL SELECT material_code, qty FROM production_outbound WHERE status='CONFIRMED' AND create_time >= ?
                  UNION ALL SELECT material_code, qty FROM outsource_material_outbound WHERE status IN ('CONFIRMED','SIGNED') AND create_time >= ?
                  UNION ALL SELECT material_code, qty FROM other_outbound WHERE status='CONFIRMED' AND (reason IS NULL OR reason <> 'REWORK') AND create_time >= ?
                ) GROUP BY material_code
                """;
        Map<String, Object> out = new java.util.HashMap<>();
        for (var r : jdbc.queryForList(outSql, since, since, since, since)) {
            out.put(String.valueOf(r.get("code")), r.get("q"));
        }
        Map<String, Object> stock = new java.util.HashMap<>();
        for (var r : jdbc.queryForList("""
                SELECT material_code AS code, SUM(qty) AS q FROM inventory_ledger
                WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED'))
                GROUP BY material_code
                """)) {
            stock.put(String.valueOf(r.get("code")), r.get("q"));
        }
        Map<String, Object> result = new java.util.HashMap<>();
        for (var e : out.entrySet()) {
            Object stockRaw = stock.get(e.getKey());
            if (stockRaw == null) continue;   // 已清仓
            double outQty = ((Number) e.getValue()).doubleValue();
            double stockQty = ((Number) stockRaw).doubleValue();
            if (outQty <= 0 || stockQty <= 0) continue;
            double annualTurnover = outQty / stockQty * (365.0 / 90.0);
            result.put(e.getKey(), Math.round(365.0 / annualTurnover));
        }
        return result;
    }

    /** 空串归一 null（SQL COALESCE('') 防类型推断的回转） */
    private String nz(Object v) {
        return v == null || v.toString().isEmpty() ? null : v.toString();
    }

    /** 毫秒时间戳转 yyyy-MM-dd（native SQL 日期列返回毫秒，与实体 LocalDate 序列化格式保持一致） */
    private String msToDate(Object ms) {
        if (ms == null) return null;
        long t = ((Number) ms).longValue();
        return java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.of("+8")).toLocalDate().toString();
    }

    /** 全部质检记录按 物料|批次 分组取最新（enrichQcInfo / enrichQcForMaps 共用） */
    private Map<String, QualityInspection> buildLatestQcMap() {
        Map<String, QualityInspection> latestQc = new HashMap<>();
        for (QualityInspection qc : qcRepo.findAll()) {
            String key = (qc.materialCode == null ? "" : qc.materialCode) + "|" + (qc.batchNo == null ? "" : qc.batchNo);
            QualityInspection exist = latestQc.get(key);
            if (exist == null || (qc.createTime != null && exist.createTime != null
                    && qc.createTime.isAfter(exist.createTime))) {
                latestQc.put(key, qc);
            }
        }
        return latestQc;
    }

    /**
     * 为库存记录补充质检信息。
     * 新数据：质检信息已随入库持久化到 ledger（qcStatus/qcInspectionNo/qcResult/qcInspector/qcDate）。
     * 历史存量：qcStatus 为 null 的行，按物料+批次从质检表兜底匹配（仅展示，不回写）。
     */
    private List<InventoryLedger> enrichQcInfo(List<InventoryLedger> list) {
        if (list == null || list.isEmpty()) return list;
        // 仅对持久化字段为空的历史数据做兜底匹配
        boolean needFallback = list.stream().anyMatch(l -> l.qcStatus == null);
        if (!needFallback) return list;

        Map<String, QualityInspection> latestQc = buildLatestQcMap();
        for (InventoryLedger l : list) {
            if (l.qcStatus != null) continue; // 已有持久化质检信息，跳过
            // v5.18 修复：只按「物料|批次」精确匹配，禁止跨批次兜底——
            // 来料质检单创建时批次为空（批号在合格入库时才生成），跨批次兜底会让历史批次
            // 一直跟着最新入库批次的检测结果变化（FS31005 B20260716-001 显示最新批次结果）
            QualityInspection qc = latestQc.get((l.materialCode == null ? "" : l.materialCode) + "|" + (l.batchNo == null ? "" : l.batchNo));
            if (qc != null) {
                l.qcStatus = qc.status;
                l.qcResult = qc.resultRemark;
                l.qcInspectionNo = qc.inspectionNo;
                l.qcInspector = qc.inspector;
                l.qcDate = qc.inspectDate;
            }
        }
        return list;
    }

    /** 聚合行（Map）质检信息补充：只按 物料|批次 精确匹配，禁止跨批次兜底（v5.19 修复）；v5.35 台账已有状态时优先 */
    private void enrichQcForMaps(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return;
        // 与 enrichQcInfo 同款短路：页内行都已持久化质检信息时不再全量加载质检表
        if (list.stream().noneMatch(m -> m.get("qcStatus") == null)) return;
        Map<String, QualityInspection> latestQc = buildLatestQcMap();
        for (Map<String, Object> m : list) {
            if (m.get("qcStatus") != null) continue; // 台账持久化状态优先（含 REJECT/TAILING）
            QualityInspection qc = latestQc.get((m.get("materialCode") == null ? "" : m.get("materialCode")) + "|"
                    + (m.get("batchNo") == null ? "" : m.get("batchNo")));
            if (qc != null) {
                m.put("qcStatus", qc.status);
                m.put("qcResult", qc.resultRemark);
                m.put("qcInspectionNo", qc.inspectionNo);
                m.put("qcInspector", qc.inspector);
                m.put("qcDate", qc.inspectDate);
            }
        }
    }

    @GetMapping("/total/{materialCode}")
    @SaCheckPermission(value = "inventory:read")
    public Result<BigDecimal> totalOwned(@PathVariable String materialCode) {
        return Result.ok(service.queryTotalOwned(materialCode));
    }

    @GetMapping("/movements/{materialCode}")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<InventoryMovement>> movements(@PathVariable String materialCode) {
        return Result.ok(service.queryMovements(materialCode));
    }

    @GetMapping("/trace")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<InventoryMovement>> trace(
            @RequestParam String materialCode, @RequestParam String batchNo) {
        return Result.ok(service.queryBatchTrace(materialCode, batchNo));
    }

    @GetMapping("/low-stock")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<InventoryLedger>> lowStock() {
        return Result.ok(service.queryLowStock());
    }

    // ==================== 导出（v5.23） ====================

    /** 库存导出：双视图（view=code 按编码聚合 / view=batch 按批次聚合），当前筛选条件全量，价格列与页面一致直接导出 */
    @GetMapping("/export")
    @SaCheckPermission(value = "inventory:read")
    public void export(@RequestParam(defaultValue = "code") String view,
                       @RequestParam(defaultValue = "") String keyword,
                       @RequestParam(required = false) String warehouseId,
                       @RequestParam(required = false) String zoneId,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        String kw = keyword == null ? "" : keyword.trim();
        String wh = warehouseId == null ? "" : warehouseId.trim();
        String zn = zoneId == null ? "" : zoneId.trim();
        var pr = org.springframework.data.domain.PageRequest.of(0, 100000);
        if ("batch".equals(view)) {
            var rows = ledgerRepo.sumByBatch(kw, wh, zn, pr);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] r : rows.getContent()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialCode", r[0]);
                m.put("materialName", r[1]);
                m.put("batchNo", r[2]);
                m.put("unit", r[3]);
                m.put("qty", r[4]);
                m.put("availableQty", r[5]);
                m.put("unitPrice", r[6]);
                m.put("amount", r[7]);
                m.put("inboundDate", msToDate(r[8]));
                m.put("expiryDate", msToDate(r[9]));
                m.put("category", r.length > 16 ? nz(r[16]) : null);
                m.put("subCategory", r.length > 17 ? nz(r[17]) : null);
                list.add(m);
            }
            enrichQcForMaps(list);
            List<Object[]> out = new ArrayList<>();
            for (Map<String, Object> m : list) {
                out.add(new Object[]{
                        m.get("materialCode"), m.get("materialName"), m.get("batchNo"), m.get("unit"),
                        m.get("qty"), m.get("availableQty"), m.get("unitPrice"), m.get("amount"),
                        m.get("inboundDate"), m.get("expiryDate"), qcStatusText((String) m.get("qcStatus")),
                        m.get("qcInspectionNo"), m.get("qcResult"), m.get("qcInspector"), m.get("qcDate"),
                        m.get("category"), m.get("subCategory")
                });
            }
            com.pengyuan.pims.common.ExcelUtil.export(response, "库存-按批次-" + java.time.LocalDate.now(), "库存按批次",
                    new String[]{"物料编码", "品名", "批号", "单位", "库存量", "可用量", "单价", "总价",
                            "最早入库日期", "过期日期", "质检状态", "质检单号", "检测结果", "检验员", "检验日期", "大类", "小类"},
                    out);
            return;
        }
        // view=code
        var rows = ledgerRepo.sumByCode(kw, wh, zn, pr);
        List<Object[]> out = new ArrayList<>();
        for (Object[] r : rows.getContent()) {
            out.add(new Object[]{r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], msToDate(r[8]),
                    r.length > 9 ? nz(r[9]) : null, r.length > 10 ? nz(r[10]) : null});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "库存-按编码-" + java.time.LocalDate.now(), "库存按编码",
                new String[]{"物料编码", "品名", "单位", "批次数量", "库存总量", "可用总量", "均价", "总价", "最新入库日期", "大类", "小类"},
                out);
    }

    /** 质检状态中文（导出用） */
    private String qcStatusText(String s) {
        return switch (s == null ? "" : s) {
            case "PASS" -> "合格";
            case "CONCESSION" -> "让步接收";
            case "REJECT" -> "退货";
            case "PENDING" -> "待检";
            default -> s;
        };
    }

    /**
     * 物料价格走势图数据。
     * v5.18 修复：原实现只查采购单表（raw_material_purchase），而多数物料只有入库单
     * （台账 unit_price）没有采购单记录，导致"库存有价格但走势图空白"。
     * 现合并两个数据源：采购单价格（带供应商）+ 台账入库价格，按日期+价格去重后升序返回。
     */
    @GetMapping("/price-trend/{materialCode}")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<Map<String, Object>>> priceTrend(@PathVariable String materialCode) {
        // key: date|price —— 同一事件在两张表重复登记时去重（采购单优先，带供应商信息）
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (RawMaterialPurchase r : purchaseRepo.findByMaterialCodeAndUnitPriceIsNotNullOrderByPurchaseDateAsc(materialCode)) {
            if (r.unitPrice == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.purchaseDate == null ? "" : r.purchaseDate.toString());
            m.put("price", r.unitPrice);
            m.put("supplier", r.supplierName);
            m.put("source", "采购单");
            merged.putIfAbsent(m.get("date") + "|" + r.unitPrice, m);
        }
        for (InventoryLedger l : ledgerRepo.findByMaterialCodeAndUnitPriceIsNotNullOrderByCreateTimeAsc(materialCode)) {
            if (l.unitPrice == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", l.createTime == null ? "" : l.createTime.toLocalDate().toString());
            m.put("price", l.unitPrice);
            m.put("supplier", null);
            m.put("source", "入库单");
            merged.putIfAbsent(m.get("date") + "|" + l.unitPrice, m);
        }
        List<Map<String, Object>> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparing(m -> (String) m.get("date")));
        return Result.ok(list);
    }
}
