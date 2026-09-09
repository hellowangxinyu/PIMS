package com.pengyuan.pims.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本核算（v5.36）
 * 生产订单成本 = 领料成本（CONFIRMED 生产出库 Σcost） + 人工费 + 制造费用（后两项手工补录）
 * 委外订单成本 = 发料成本（CONFIRMED/SIGNED 委外出库 Σcost） + 加工费（委外入库立账应付 Σamount）
 * 理论成本 = 配方版本 BOM 成本（RecipeService.calcVersionCost，库存加权均价）按订单批量折算
 * 单位成本 = 总成本 ÷ 产出量（DONE 入库 Σqty）
 */
@Service
public class CostService {
    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.10.1

    private static final Logger log = LoggerFactory.getLogger(CostService.class);

    private final JdbcTemplate jdbc;
    private final RecipeService recipeService;

    public CostService(JdbcTemplate jdbc, RecipeService recipeService, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.jdbc = jdbc;
        this.recipeService = recipeService;
    }

    /**
     * 订单成本列表。type=PRODUCTION 生产订单 / OUTSOURCE 委外订单。
     * 每单返回：orderNo/productName/batchQty/unit/status/createTime/
     *   materialCost 领料成本 / outsourceFee 加工费 / laborFee 人工 / overheadFee 制费 /
     *   totalCost / outputQty 产出量 / unitCost 单位成本 / theoreticalCost 理论成本 / costDiff%
     */
    public List<Map<String, Object>> listOrderCost(String type) {
        boolean production = !"OUTSOURCE".equalsIgnoreCase(type);
        List<Map<String, Object>> orders = jdbc.queryForList(production
                ? "SELECT order_no, product_name, batch_qty, unit, status, recipe_version_id, create_time, labor_fee, overhead_fee FROM production_order ORDER BY create_time DESC, id DESC"
                : "SELECT order_no, product_name, batch_qty, unit, status, recipe_version_id, create_time FROM outsource_order ORDER BY create_time DESC, id DESC");

        // 领料/发料成本与产出量一次性聚合，避免逐单 N+1
        Map<String, BigDecimal> materialMap = toBdMap(jdbc.queryForList(production
                ? "SELECT production_order_no AS k, COALESCE(SUM(cost),0) AS c FROM production_outbound WHERE status='CONFIRMED' GROUP BY production_order_no"
                : "SELECT outsource_order_no AS k, COALESCE(SUM(cost),0) AS c FROM outsource_material_outbound WHERE status IN ('CONFIRMED','SIGNED') GROUP BY outsource_order_no"), "c");
        Map<String, BigDecimal> outputMap = toBdMap(jdbc.queryForList(production
                ? "SELECT production_order_no AS k, COALESCE(SUM(qty),0) AS q FROM production_inbound WHERE status='DONE' GROUP BY production_order_no"
                : "SELECT outsource_order_no AS k, COALESCE(SUM(qty),0) AS q FROM outsource_finish_inbound WHERE status='DONE' GROUP BY outsource_order_no"), "q");
        Map<String, BigDecimal> feeMap = production ? Map.of()
                : toBdMap(jdbc.queryForList(
                "SELECT outsource_order_no AS k, COALESCE(SUM(amount),0) AS f FROM accounts_payable WHERE payable_type='OUTSOURCE' GROUP BY outsource_order_no"), "f");

        // 理论成本的价格映射只建一次（内部含台账+两张采购表全量加载，逐单重建是 N×3 全表扫描）
        Map<String, BigDecimal> priceMap = orders.stream().anyMatch(o -> o.get("recipe_version_id") != null)
                ? recipeService.getMaterialPriceMap() : null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            String orderNo = str(o.get("order_no"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", production ? "PRODUCTION" : "OUTSOURCE");
            row.put("orderNo", orderNo);
            row.put("productName", o.get("product_name"));
            row.put("batchQty", toBd(o.get("batch_qty")));
            row.put("unit", o.get("unit"));
            row.put("status", o.get("status"));
            row.put("createTime", o.get("create_time"));

            BigDecimal materialCost = toBd(materialMap.get(orderNo));
            BigDecimal outsourceFee = production ? BigDecimal.ZERO : toBd(feeMap.get(orderNo));
            BigDecimal laborFee = BigDecimal.ZERO, overheadFee = BigDecimal.ZERO;
            if (production) {  // 人工/制费已随订单首查一并取出，不再逐单回表
                laborFee = toBd(o.get("labor_fee"));
                overheadFee = toBd(o.get("overhead_fee"));
            }
            BigDecimal totalCost = materialCost.add(outsourceFee).add(laborFee).add(overheadFee);
            BigDecimal outputQty = toBd(outputMap.get(orderNo));
            BigDecimal unitCost = outputQty.compareTo(BigDecimal.ZERO) > 0
                    ? totalCost.divide(outputQty, 2, RoundingMode.HALF_UP) : null;

            Long versionId = o.get("recipe_version_id") == null ? null : ((Number) o.get("recipe_version_id")).longValue();
            BigDecimal theoreticalCost = theoreticalCost(versionId, toBd(o.get("batch_qty")), priceMap);
            BigDecimal diff = null;
            if (theoreticalCost != null && theoreticalCost.compareTo(BigDecimal.ZERO) > 0 && totalCost.compareTo(BigDecimal.ZERO) > 0) {
                diff = totalCost.subtract(theoreticalCost)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(theoreticalCost, 2, RoundingMode.HALF_UP);
            }

            row.put("materialCost", materialCost);
            row.put("outsourceFee", outsourceFee);
            row.put("laborFee", laborFee);
            row.put("overheadFee", overheadFee);
            row.put("totalCost", totalCost);
            row.put("outputQty", outputQty);
            row.put("unitCost", unitCost);
            row.put("theoreticalCost", theoreticalCost);
            row.put("costDiff", diff);
            result.add(row);
        }
        return result;
    }

    /** 理论材料成本：配方版本批次成本 ×（订单批量 ÷ 配方批量）；版本无效返回 null；priceMap 为批量场景复用的价格映射 */
    private BigDecimal theoreticalCost(Long versionId, BigDecimal orderQty, Map<String, BigDecimal> priceMap) {
        if (versionId == null) return null;
        try {
            Map<String, Object> cost = recipeService.calcVersionCost(versionId, priceMap);
            BigDecimal batchCost = toBd(cost.get("batchCost"));
            BigDecimal batchQty = toBd(cost.get("batchQty"));
            if (batchQty.compareTo(BigDecimal.ZERO) <= 0 || orderQty == null || orderQty.compareTo(BigDecimal.ZERO) <= 0) {
                return batchCost.setScale(2, RoundingMode.HALF_UP);
            }
            return batchCost.multiply(orderQty).divide(batchQty, 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.debug("理论成本计算失败 versionId={}: {}", versionId, e.getMessage());
            return null;
        }
    }

    /** 生产订单人工/制费补录（成本中无系统数据源的部分，由财务手工维护） */
    // v8.10.1（A2 残）：去 @Transactional，锁内包事务
    public void updateFees(String orderNo, BigDecimal laborFee, BigDecimal overheadFee) {
        if (laborFee == null || laborFee.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("人工费不能为负");
        if (overheadFee == null || overheadFee.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("制造费用不能为负");
        writeQueue.executeTx(() -> {
            int updated = jdbc.update("UPDATE production_order SET labor_fee = ?, overhead_fee = ?, update_time = ? WHERE order_no = ?",
                    laborFee, overheadFee, System.currentTimeMillis(), orderNo);
            if (updated == 0) throw new IllegalArgumentException("生产订单不存在: " + orderNo);
            return null;
        });
    }

    // ===== 工具：SQLite 聚合可能返回 Integer/Double/BigDecimal，统一兼容转 BigDecimal（历史坑） =====

    private static BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }

    /** 聚合行列表 → {单号: 金额}，valueKey 指定取值列 */
    private static Map<String, BigDecimal> toBdMap(List<Map<String, Object>> rows, String valueKey) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) m.put(String.valueOf(r.get("k")), toBd(r.get(valueKey)));
        return m;
    }
}
