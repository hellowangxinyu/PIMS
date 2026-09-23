package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * MRP 简版（v6.3 第三批）：销售订单 → 配方展开（含半成品递归到原料）→ 比对现库存/在途 → 采购建议清单。
 * 口径：
 * - 展开：B/C 类物料按 RELEASED 配方展开（比例=未发数量÷标准批量×节点用量），半成品递归展开（visited 防环）
 * - 现库存：台账正库存，排除隔离行（REJECT/TAILING/EXPIRED）
 * - 在途：APPROVED 采购单未到货量（订量−已到，负数归零）
 * - 缺口 = 需求 − 现库存 − 在途；缺口 ≤ 0 不列；建议量 = 缺口 × 1.05（损耗余量）
 */
@Service
public class MrpService {

    private static final Logger log = LoggerFactory.getLogger(MrpService.class);

    private final SalesOrderRepository salesOrderRepo;
    private final SalesOrderItemRepository salesItemRepo;
    private final MaterialRepository materialRepo;
    private final RecipeRepository recipeRepo;
    private final RecipeVersionRepository versionRepo;
    private final RecipeTreeNodeRepository treeNodeRepo;
    private final RawMaterialPurchaseRepository rawRepo;
    private final FinishedProductPurchaseRepository finishedRepo;
    private final PurchaseOrderService purchaseOrderService;
    private final JdbcTemplate jdbc;
    private final com.pengyuan.pims.repository.StatMaterialUsageRepository statUsageRepo;   // v11.1 历史用量维度
    private final com.pengyuan.pims.repository.InventoryLedgerRepository ledgerRepo;        // v11.1 当前库存

    public MrpService(SalesOrderRepository salesOrderRepo, SalesOrderItemRepository salesItemRepo,
                      MaterialRepository materialRepo, RecipeRepository recipeRepo,
                      RecipeVersionRepository versionRepo, RecipeTreeNodeRepository treeNodeRepo,
                      RawMaterialPurchaseRepository rawRepo, FinishedProductPurchaseRepository finishedRepo,
                      PurchaseOrderService purchaseOrderService, JdbcTemplate jdbc,
                      com.pengyuan.pims.repository.StatMaterialUsageRepository statUsageRepo,
                      com.pengyuan.pims.repository.InventoryLedgerRepository ledgerRepo) {
        this.salesOrderRepo = salesOrderRepo;
        this.salesItemRepo = salesItemRepo;
        this.materialRepo = materialRepo;
        this.recipeRepo = recipeRepo;
        this.versionRepo = versionRepo;
        this.treeNodeRepo = treeNodeRepo;
        this.rawRepo = rawRepo;
        this.finishedRepo = finishedRepo;
        this.purchaseOrderService = purchaseOrderService;
        this.jdbc = jdbc;
        this.statUsageRepo = statUsageRepo;
        this.ledgerRepo = ledgerRepo;
    }

    /** 采购建议分析：orderIds 为空 = 全部 CONFIRMED 订单 */
    public Map<String, Object> suggest(List<Long> orderIds) {
        List<SalesOrder> orders;
        if (orderIds == null || orderIds.isEmpty()) {
            orders = new ArrayList<>(salesOrderRepo.findByStatusOrderByCreateTimeDesc("CONFIRMED"));
        } else {
            orders = salesOrderRepo.findAllById(orderIds);
        }
        orders.removeIf(o -> "CLOSED".equals(o.status) || "DRAFT".equals(o.status));

        Map<String, BigDecimal> demand = new LinkedHashMap<>();
        Map<String, Set<String>> demandOrders = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (SalesOrder o : orders) {
            for (SalesOrderItem it : salesItemRepo.findByOrderId(o.id)) {
                if (it.materialCode == null || it.qty == null) continue;
                BigDecimal remain = it.qty.subtract(it.shippedQty == null ? BigDecimal.ZERO : it.shippedQty);
                if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
                expand(it.materialCode, remain, demand, demandOrders, names, new HashSet<>(), o.orderNo);
            }
        }

        // 现库存（排除隔离行）
        Map<String, BigDecimal> stock = new LinkedHashMap<>();
        for (var row : jdbc.queryForList(
                "SELECT material_code AS code, SUM(qty) AS q FROM inventory_ledger " +
                "WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED')) " +
                "GROUP BY material_code")) {
            stock.put(String.valueOf(row.get("code")), toBd(row.get("q")));
        }
        // 在途：APPROVED 未到货（原料+成品）
        Map<String, BigDecimal> transit = new LinkedHashMap<>();
        for (RawMaterialPurchase r : rawRepo.findAll()) {
            if (!"APPROVED".equals(r.status) || r.materialCode == null) continue;
            BigDecimal open = (r.qty == null ? BigDecimal.ZERO : r.qty)
                    .subtract(r.receivedQty == null ? BigDecimal.ZERO : r.receivedQty);
            if (open.compareTo(BigDecimal.ZERO) > 0) transit.merge(r.materialCode, open, BigDecimal::add);
        }
        for (FinishedProductPurchase f : finishedRepo.findAll()) {
            if (!"APPROVED".equals(f.status) || f.materialCode == null) continue;
            BigDecimal open = (f.qty == null ? BigDecimal.ZERO : f.qty)
                    .subtract(f.receivedQty == null ? BigDecimal.ZERO : f.receivedQty);
            if (open.compareTo(BigDecimal.ZERO) > 0) transit.merge(f.materialCode, open, BigDecimal::add);
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        for (var e : demand.entrySet()) {
            String code = e.getKey();
            BigDecimal need = e.getValue().setScale(3, RoundingMode.HALF_UP);
            BigDecimal have = stock.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal onWay = transit.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal gap = need.subtract(have).subtract(onWay);
            if (gap.compareTo(BigDecimal.ZERO) <= 0) continue;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialCode", code);
            line.put("materialName", names.getOrDefault(code, ""));
            var mat = materialRepo.findByCode(code).orElse(null);
            line.put("materialCategory", mat != null ? mat.category : "");
            line.put("need", need);
            line.put("stock", have.setScale(3, RoundingMode.HALF_UP));
            line.put("transit", onWay.setScale(3, RoundingMode.HALF_UP));
            line.put("gap", gap.setScale(3, RoundingMode.HALF_UP));
            line.put("suggested", gap.multiply(new BigDecimal("1.05")).setScale(3, RoundingMode.HALF_UP));   // 缺口+5% 损耗余量
            line.put("orders", String.join("，", demandOrders.getOrDefault(code, Set.of())));
            lines.add(line);
        }
        lines.sort((a, b) -> ((BigDecimal) b.get("gap")).compareTo((BigDecimal) a.get("gap")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders.size());
        result.put("lines", lines);
        log.info("MRP 分析: 订单 {} 张，需求物料 {} 种，建议采购 {} 项", orders.size(), demand.size(), lines.size());
        return result;
    }

    /** 递归展开：B/C 按配方展开（无配方则自身列为缺口），材料直接累加 */
    private void expand(String materialCode, BigDecimal qty, Map<String, BigDecimal> demand,
                        Map<String, Set<String>> demandOrders, Map<String, String> names, Set<Long> visited, String orderNo) {
        var matOpt = materialRepo.findByCode(materialCode);
        matOpt.ifPresent(m -> { if (m.name != null) names.putIfAbsent(materialCode, m.name); });
        demandOrders.computeIfAbsent(materialCode, k -> new LinkedHashSet<>()).add(orderNo);

        String category = matOpt.map(m -> m.category).orElse("");
        if (!"B".equals(category) && !"C".equals(category)) {
            demand.merge(materialCode, qty, BigDecimal::add);
            return;
        }
        var recipeOpt = recipeRepo.findFirstByProductCode(materialCode);
        if (recipeOpt.isEmpty()) {
            demand.merge(materialCode, qty, BigDecimal::add);   // 无配方：外购成品/待建配方，自身列缺口
            return;
        }
        Long recipeId = recipeOpt.get().id;
        if (!visited.add(recipeId)) {
            log.warn("MRP 展开遇到循环引用已截断: 配方#{}（{}），该分支按已知需求继续", recipeId, materialCode);   // v6.5 B5：不再静默
            return;
        }
        var released = versionRepo.findByRecipeIdAndStatus(recipeId, "RELEASED");
        if (released.isEmpty()) {
            visited.remove(recipeId);
            demand.merge(materialCode, qty, BigDecimal::add);
            return;
        }
        RecipeVersion v = released.get();
        BigDecimal batch = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0 ? v.batchQty : BigDecimal.ONE;
        BigDecimal ratio = qty.divide(batch, 6, RoundingMode.HALF_UP);
        for (RecipeTreeNode node : treeNodeRepo.findByVersionIdOrderBySortOrder(v.id)) {
            if (node.qty == null || node.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal need = node.qty.multiply(ratio).setScale(3, RoundingMode.HALF_UP);
            if ("SUB_RECIPE".equals(node.nodeType) && node.refRecipeId != null) {
                var subReleased = versionRepo.findByRecipeIdAndStatus(node.refRecipeId, "RELEASED");
                var subRecipe = recipeRepo.findById(node.refRecipeId);
                if (!subReleased.isEmpty() && subRecipe.isPresent() && subRecipe.get().productCode != null
                        && !subRecipe.get().productCode.isBlank()) {
                    expand(subRecipe.get().productCode, need, demand, demandOrders, names, visited, orderNo);
                    continue;
                }
            }
            if (node.materialCode != null && !node.materialCode.isBlank()) {
                if (node.materialName != null) names.putIfAbsent(node.materialCode, node.materialName);
                demand.merge(node.materialCode, need, BigDecimal::add);
                demandOrders.computeIfAbsent(node.materialCode, k -> new LinkedHashSet<>()).add(orderNo);
            }
        }
        visited.remove(recipeId);
    }

    /** 一键生成请购单（单张合并，DRAFT 状态走审核流），复用 PurchaseOrderService（锁内包事务取号） */
    public Map<String, Object> createPurchaseOrder(List<Map<String, Object>> lines, String operator) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("请先勾选建议行");
        PurchaseOrder order = new PurchaseOrder();
        order.orderDate = java.time.LocalDate.now();
        order.targetWarehouseId = "1";   // 默认自有仓，转采购时可改
        order.paymentTerms = "待定";
        order.remark = "MRP 采购建议生成（" + lines.size() + " 项）；供应商待定，请购审核后转采购时补全";
        order.createdBy = operator == null || operator.isBlank() ? "系统" : operator;
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            PurchaseOrderItem it = new PurchaseOrderItem();
            it.materialCode = String.valueOf(l.get("materialCode"));   // PO 明细无名称列（打印按编码联物料档案）
            it.qty = toBd(l.get("suggested") != null ? l.get("suggested") : l.get("gap"));
            it.unit = "kg";
            items.add(it);
        }
        PurchaseOrder saved = purchaseOrderService.create(order, items);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", saved.orderNo);
        result.put("itemCount", items.size());
        return result;
    }

    private static BigDecimal toBd(Object v) {
        return v == null ? BigDecimal.ZERO : (v instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(v)));
    }

    // ==================== v11.1 历史用量维度 ====================

    /**
     * 基于历史用量的采购建议（与"按订单配方"并列的第二维度）：
     * 每日用量 = 历史出库合计 / 统计天数（stat_material_usage 口径，与低库存报表同源）
     * 平均到货周期 = 最近到货的 采购日期→到货日期 天数均值（无历史默认 7 天）
     * 请购点 = 每日用量 × (平均到货周期 + 缓冲天数)
     * 当前可供 = 现库存 + 在途（已审核未到货）；低于请购点 → 建议量 = 请购点 - 当前可供
     * 仅统计原料类物料（A助剂/P颜料/F填料/R树脂/S溶剂）
     */
    public java.util.Map<String, Object> suggestByUsage(int bufferDays) {
        if (bufferDays < 0) bufferDays = 0;
        java.util.Set<String> rawCats = java.util.Set.of("A", "P", "F", "R", "S");
        java.util.Map<String, com.pengyuan.pims.entity.Material> materials = new java.util.LinkedHashMap<>();
        for (var m : materialRepo.findAll()) {
            if (m.code != null && m.category != null && rawCats.contains(m.category)) materials.put(m.code, m);
        }

        // 日均用量（按物料跨仓库汇总）
        java.util.Map<String, BigDecimal> usageTotal = new java.util.HashMap<>();
        java.util.Map<String, Integer> usageDays = new java.util.HashMap<>();
        for (var u : statUsageRepo.findAll()) {
            if (!materials.containsKey(u.materialCode)) continue;
            usageTotal.merge(u.materialCode, u.outQty != null ? u.outQty : BigDecimal.ZERO, BigDecimal::add);
            usageDays.merge(u.materialCode, u.usageDays != null ? u.usageDays : 0, Integer::sum);
        }

        // 当前库存（qty 合计，与低库存报表同口径）
        java.util.Map<String, BigDecimal> stockByMat = new java.util.HashMap<>();
        java.util.Map<String, String> unitByMat = new java.util.HashMap<>();
        for (Object[] row : ledgerRepo.sumQtyGroupByMaterial()) {
            if (row[0] != null) {
                stockByMat.put(String.valueOf(row[0]), toBd(row[1]));
                if (row[2] != null) unitByMat.putIfAbsent(String.valueOf(row[0]), row[2].toString());
            }
        }

        // 在途（已审核采购单未到货量）
        java.util.Map<String, BigDecimal> transitByMat = new java.util.HashMap<>();
        for (java.util.Map<String, Object> r : jdbc.queryForList(
                "SELECT material_code AS code, SUM(qty - COALESCE(received_qty, 0)) AS t " +
                "FROM raw_material_purchase WHERE status = 'APPROVED' AND qty > COALESCE(received_qty, 0) " +
                "GROUP BY material_code")) {
            if (r.get("code") != null) transitByMat.put(String.valueOf(r.get("code")), toBd(r.get("t")));
        }

        // 平均到货周期（毫秒日期→天数，按物料取最近 10 次均值）
        java.util.Map<String, java.util.List<Long>> leads = new java.util.HashMap<>();
        for (java.util.Map<String, Object> r : jdbc.queryForList(
                "SELECT pa.material_code AS code, pa.arrival_date AS ad, rp.purchase_date AS pd " +
                "FROM purchase_arrival pa JOIN raw_material_purchase rp ON rp.order_no = pa.ref_order_no " +
                "WHERE pa.status = 'APPROVED' AND pa.type = 'RAW' AND rp.purchase_date IS NOT NULL AND pa.arrival_date IS NOT NULL")) {
            String code = String.valueOf(r.get("code"));
            if (!materials.containsKey(code)) continue;
            long ad = ((Number) r.get("ad")).longValue(), pd = ((Number) r.get("pd")).longValue();
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.ofEpochDay(pd / 86400000L), java.time.LocalDate.ofEpochDay(ad / 86400000L));
            leads.computeIfAbsent(code, k -> new java.util.ArrayList<>()).add(Math.max(days, 0));
        }

        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (var e : materials.entrySet()) {
            String code = e.getKey();
            BigDecimal total = usageTotal.getOrDefault(code, BigDecimal.ZERO);
            int days = usageDays.getOrDefault(code, 0);
            if (days == 0 || total.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal avgDaily = total.divide(BigDecimal.valueOf(days), 3, java.math.RoundingMode.HALF_UP);
            if (avgDaily.compareTo(BigDecimal.ZERO) <= 0) continue;
            var leadList = leads.getOrDefault(code, java.util.List.of());
            double avgLead = leadList.isEmpty() ? 7.0
                    : leadList.stream().limit(10).mapToLong(Long::longValue).average().orElse(7.0);
            BigDecimal stock = stockByMat.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal transit = transitByMat.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal available = stock.add(transit);
            BigDecimal targetDays = BigDecimal.valueOf(avgLead + bufferDays);
            BigDecimal coverDays = available.divide(avgDaily, 1, java.math.RoundingMode.HALF_UP);
            if (coverDays.compareTo(targetDays) >= 0) continue;
            BigDecimal targetQty = avgDaily.multiply(targetDays).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal suggested = targetQty.subtract(available).setScale(2, java.math.RoundingMode.HALF_UP);
            if (suggested.compareTo(BigDecimal.ZERO) <= 0) continue;
            var m = e.getValue();
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("materialCode", code);
            row.put("materialName", m.name);
            row.put("unit", unitByMat.getOrDefault(code, ""));
            row.put("avgDailyQty", avgDaily);
            row.put("avgLeadDays", BigDecimal.valueOf(avgLead).setScale(1, java.math.RoundingMode.HALF_UP));
            row.put("stockQty", stock);
            row.put("transitQty", transit);
            row.put("coverDays", coverDays);
            row.put("targetDays", targetDays.setScale(1, java.math.RoundingMode.HALF_UP));
            row.put("suggested", suggested);
            row.put("level", coverDays.compareTo(BigDecimal.valueOf(bufferDays)) < 0 ? "RED" : "ORANGE");
            rows.add(row);
        }
        rows.sort(java.util.Comparator.comparing(r -> (BigDecimal) r.get("coverDays")));
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("bufferDays", bufferDays);
        result.put("materialCount", rows.size());
        result.put("lines", rows);
        return result;
    }
}
