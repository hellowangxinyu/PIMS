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

    public MrpService(SalesOrderRepository salesOrderRepo, SalesOrderItemRepository salesItemRepo,
                      MaterialRepository materialRepo, RecipeRepository recipeRepo,
                      RecipeVersionRepository versionRepo, RecipeTreeNodeRepository treeNodeRepo,
                      RawMaterialPurchaseRepository rawRepo, FinishedProductPurchaseRepository finishedRepo,
                      PurchaseOrderService purchaseOrderService, JdbcTemplate jdbc) {
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
}
