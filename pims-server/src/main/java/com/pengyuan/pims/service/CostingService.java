package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.InventoryLedger;
import com.pengyuan.pims.repository.AccountPeriodRepository;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 存货计价服务（v5.63）—— 全部出库单成本的唯一分流点
 *
 * 四档计价（sys_config: inventory.costing_method）：
 *  SPECIFIC   个别计价（默认=既有口径）：出库成本=所选批次台账价
 *  FIFO       先进先出：与个别计价同源（批次价），前端引导默认选最早批次
 *  MOVING_AVG 移动加权平均：物料全部在库 Σamount÷Σqty 实时计算
 *  MONTHLY_AVG 全月平均：月中出库按上月快照价暂估，月末"存货成本计算"回填+落快照
 *
 * 准则口径：计价方式一经确定不得随意变更——changeMethod 必填原因、留痕、只影响未来出库。
 * 全月平均月末计算：均价 =（当前台账Σamount + 该期出库Σ原cost）÷（当前Σqty + 该期出库Σqty）
 * ——数学上严格等于（期初+本期入库）加权平均（金额/数量守恒倒推），无需历史快照。
 */
@Service
public class CostingService {

    public static final String KEY = "inventory.costing_method";
    private static final Set<String> METHODS = Set.of("SPECIFIC", "FIFO", "MOVING_AVG", "MONTHLY_AVG");

    private final JdbcTemplate jdbc;
    private final InventoryLedgerRepository ledgerRepo;
    private final AccountPeriodRepository periodRepo;
    private final WriteQueue writeQueue;

    public CostingService(JdbcTemplate jdbc, InventoryLedgerRepository ledgerRepo,
                          AccountPeriodRepository periodRepo, WriteQueue writeQueue) {
        this.jdbc = jdbc;
        this.ledgerRepo = ledgerRepo;
        this.periodRepo = periodRepo;
        this.writeQueue = writeQueue;
    }

    public String method() {
        try {
            var rows = jdbc.queryForList("SELECT value_text FROM sys_config WHERE key_name = ?", KEY);
            String v = rows.isEmpty() ? null : String.valueOf(rows.get(0).get("value_text"));
            return v != null && METHODS.contains(v) ? v : "SPECIFIC";
        } catch (Exception e) { return "SPECIFIC"; }
    }

    /** 计价设置页数据：当前方式 + 当月出库单数（变更警示用）+ 各方式说明 */
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", method());
        result.put("monthOutboundCount", monthOutboundCount());
        return result;
    }

    /**
     * 变更计价方式。准则口径不得随意变更：
     *  - reason 必填；当月已有出库单时需前端二次强确认（force）
     *  - 只影响未来出库，绝不重算历史单据
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public Map<String, Object> changeMethod(String newMethod, String reason, String operator, boolean force) {
        if (newMethod == null || !METHODS.contains(newMethod)) {
            throw new IllegalArgumentException("计价方式必须是 SPECIFIC/FIFO/MOVING_AVG/MONTHLY_AVG");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("变更原因必填（准则要求计价方法变更留痕披露）");
        String current = method();
        if (current.equals(newMethod)) throw new IllegalArgumentException("当前已是该计价方式，无需变更");
        long monthCount = monthOutboundCount();
        if (monthCount > 0 && !force) {
            Map<String, Object> warn = new LinkedHashMap<>();
            warn.put("needConfirm", true);
            warn.put("monthOutboundCount", monthCount);
            warn.put("message", "当月已有 " + monthCount + " 张出库单按原方式计价，切换后新出库按新方式——新旧口径将混用，建议月末结账后、下月初切换。确认仍要切换请再次提交。");
            return warn;
        }
        writeQueue.executeTx(() -> {
            jdbc.update("INSERT INTO sys_config (key_name, value_text) VALUES (?, ?) " +
                    "ON CONFLICT(key_name) DO UPDATE SET value_text = excluded.value_text", KEY, newMethod);
            jdbc.update("INSERT INTO costing_method_log (old_value, new_value, reason, changed_by, change_time) VALUES (?,?,?,?,?)",
                    current, newMethod, reason, operator, System.currentTimeMillis());
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed", true);
        result.put("from", current);
        result.put("to", newMethod);
        return result;
    }

    public List<Map<String, Object>> changeLog() {
        return jdbc.queryForList("SELECT old_value, new_value, reason, changed_by, change_time FROM costing_method_log ORDER BY id DESC");
    }

    /**
     * 出库取价（全部出库单成本收口）。preloaded 为该物料批量预取的台账行（可空）。
     * SPECIFIC/FIFO=批次价；MOVING_AVG=在库加权均价；MONTHLY_AVG=上月快照价（回退移动加权，再回退批次价）。
     */
    public BigDecimal unitPrice(String materialCode, String batchNo, String warehouseId, List<InventoryLedger> preloaded) {
        String method = method();
        List<InventoryLedger> ledgers = preloaded != null ? preloaded
                : (materialCode == null ? List.of() : ledgerRepo.findByMaterialCode(materialCode));
        BigDecimal batchPrice = batchPriceOf(ledgers, batchNo, warehouseId);
        return switch (method) {
            case "MOVING_AVG" -> {
                BigDecimal avg = movingAvg(ledgers);
                yield avg != null ? avg : batchPrice;
            }
            case "MONTHLY_AVG" -> {
                BigDecimal snap = lastMonthSnapshot(materialCode);
                if (snap != null) yield snap;
                BigDecimal avg = movingAvg(ledgers);
                yield avg != null ? avg : batchPrice;
            }
            default -> batchPrice;   // SPECIFIC / FIFO：批次价（FIFO 差别仅在前端引导选最早批次）
        };
    }

    /** FIFO 自动出库路径：批次扣减价 ldPrice 在加权模式下替换为配置价（台账仍按批次价扣减，单据成本按计价方式） */
    public BigDecimal autoPrice(String materialCode, String batchNo, BigDecimal ldPrice) {
        String method = method();
        if ("SPECIFIC".equals(method) || "FIFO".equals(method)) return ldPrice;
        return unitPrice(materialCode, batchNo, null, null);
    }

    /** 在库移动加权均价 = Σ(qty>0 行的 amount) ÷ Σ(qty)（无在库时返回 null）
     *  v6.1（中#12）：不合格/油尾/过期隔离行不参与计价——隔离货不可正常流转，混入均价会稀释/污染正常批次成本
     *  （委外仓行无隔离状态，所有权自有，照常参与） */
    private BigDecimal movingAvg(List<InventoryLedger> ledgers) {
        BigDecimal amt = BigDecimal.ZERO, qty = BigDecimal.ZERO;
        for (InventoryLedger l : ledgers) {
            if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (isQuarantined(l.qcStatus)) continue;
            qty = qty.add(l.qty);
            amt = amt.add(l.amount != null ? l.amount : (l.unitPrice != null ? l.qty.multiply(l.unitPrice) : BigDecimal.ZERO));
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return null;
        return amt.divide(qty, 4, RoundingMode.HALF_UP);
    }

    /** v6.1（中#12）：隔离状态行（质检不合格/油尾/过期复检隔离）不参与计价 */
    private static boolean isQuarantined(String qcStatus) {
        return "REJECT".equals(qcStatus) || "TAILING".equals(qcStatus) || "EXPIRED".equals(qcStatus);
    }

    /** 上月快照价（全月平均月中暂估用；无快照返回 null） */
    private BigDecimal lastMonthSnapshot(String materialCode) {
        if (materialCode == null) return null;
        LocalDate now = LocalDate.now();
        LocalDate firstOfThisMonth = now.withDayOfMonth(1);
        String lastMonth = firstOfThisMonth.minusMonths(1).toString().substring(0, 7);
        try {
            var rows = jdbc.queryForList("SELECT price FROM costing_monthly_price WHERE period = ? AND material_code = ?",
                    lastMonth, materialCode);
            return rows.isEmpty() ? null : toBd(rows.get(0).get("price"));
        } catch (Exception e) { return null; }
    }

    private BigDecimal batchPriceOf(List<InventoryLedger> ledgers, String batchNo, String warehouseId) {
        if (batchNo == null) return null;
        return ledgers.stream()
                .filter(l -> batchNo.equals(l.batchNo))
                .filter(l -> l.unitPrice != null && warehouseId != null && warehouseId.equals(l.warehouseId))
                .map(l -> l.unitPrice).findFirst()
                .orElseGet(() -> ledgers.stream()
                        .filter(l -> batchNo.equals(l.batchNo))
                        .filter(l -> l.unitPrice != null)
                        .map(l -> l.unitPrice).findFirst().orElse(null));
    }

    /** 当月（自然月）四类出库单总数（变更警示） */
    private long monthOutboundCount() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        long from = first.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            Long c = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM sales_outbound WHERE create_time >= ?)
                     + (SELECT COUNT(*) FROM production_outbound WHERE create_time >= ?)
                     + (SELECT COUNT(*) FROM outsource_material_outbound WHERE create_time >= ?)
                     + (SELECT COUNT(*) FROM other_outbound WHERE create_time >= ?)
                """, Long.class, from, from, from, from);
            return c == null ? 0 : c;
        } catch (Exception e) { return 0; }
    }

    // ===== 全月平均月末计算 =====

    /**
     * 全月平均存货成本计算（幂等前提：该期未算过）：
     * 1. 该期四类出库单（CONFIRMED）按物料汇总数量与原成本
     * 2. 均价 =（当前台账Σamount + 该期出库Σ原cost）÷（当前Σqty + 该期Σqty）——守恒倒推，严格等于（期初+本期入库）加权
     * 3. 回填该期全部出库行 cost=qty×均价、unit_price=均价
     * 4. 落 costing_monthly_price 快照（下月暂估用）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public Map<String, Object> monthlyClose(String period, String operator) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException("期间格式应为 YYYY-MM");
        if (periodRepo.findByPeriod(period).filter(p -> Boolean.TRUE.equals(p.closed)).isPresent()) {
            throw new IllegalArgumentException(period + " 已结账，不能重算存货成本");
        }
        // v8.1（P0-6）：幂等检查+取数+计算+回填整段进 executeTx——原实现只锁回填，
        // 计算期间有人出入库会污染均价基数且并发双算可双双通过幂等检查
        return writeQueue.executeTx(() -> {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM costing_monthly_price WHERE period = ?", Integer.class, period);
        if (existing != null && existing > 0) {
            throw new IllegalArgumentException(period + " 存货成本已计算过（" + existing + " 个物料）；重复重算会使基数为已回填的均价，如确需重算请先清理该期快照");
        }

        // 期间毫秒边界（常量传参，裸列比较）
        long from = LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5, 7)), 1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long to = LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5, 7)), 1)
                .plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        // 该期出库按物料聚合（qty + 原成本）
        Map<String, BigDecimal[]> outByCode = new LinkedHashMap<>();   // code -> [qty, cost]
        String unionSql = """
            SELECT material_code AS code, SUM(qty) AS q, SUM(COALESCE(cost,0)) AS c FROM (
              SELECT material_code, qty, cost FROM sales_outbound WHERE status='CONFIRMED' AND create_time >= ? AND create_time < ?
              UNION ALL SELECT material_code, qty, cost FROM production_outbound WHERE status='CONFIRMED' AND create_time >= ? AND create_time < ?
              UNION ALL SELECT material_code, qty, cost FROM outsource_material_outbound WHERE status IN ('CONFIRMED','SIGNED') AND create_time >= ? AND create_time < ?
              UNION ALL SELECT material_code, qty, cost FROM other_outbound WHERE status='CONFIRMED' AND create_time >= ? AND create_time < ?
            ) GROUP BY material_code
            """;
        for (Map<String, Object> row : jdbc.queryForList(unionSql, from, to, from, to, from, to, from, to)) {
            outByCode.put(String.valueOf(row.get("code")),
                    new BigDecimal[]{toBd(row.get("q")), toBd(row.get("c"))});
        }
        if (outByCode.isEmpty()) throw new IllegalArgumentException(period + " 无已确认出库单，无需计算");

        // 均价（守恒倒推）+ 快照 + 回填
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        Map<String, BigDecimal[]> ledgerByCode = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT material_code AS code, SUM(qty) AS q, SUM(COALESCE(amount,0)) AS a FROM inventory_ledger " +
                "WHERE qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED') GROUP BY material_code")) {
            ledgerByCode.put(String.valueOf(row.get("code")), new BigDecimal[]{toBd(row.get("q")), toBd(row.get("a"))});
        }
        for (Map.Entry<String, BigDecimal[]> en : outByCode.entrySet()) {
            String code = en.getKey();
            BigDecimal[] out = en.getValue();
            BigDecimal[] led = ledgerByCode.getOrDefault(code, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal totalQty = led[0].add(out[0]);
            BigDecimal totalAmt = led[1].add(out[1]);
            BigDecimal price = totalQty.compareTo(BigDecimal.ZERO) > 0
                    ? totalAmt.divide(totalQty, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            prices.put(code, price);
        }

        for (Map.Entry<String, BigDecimal> en : prices.entrySet()) {
            jdbc.update("INSERT INTO costing_monthly_price (period, material_code, price, create_time) VALUES (?,?,?,?) " +
                    "ON CONFLICT(period, material_code) DO UPDATE SET price = excluded.price",
                    period, en.getKey(), en.getValue(), System.currentTimeMillis());
        }
        for (String table : List.of("sales_outbound", "production_outbound", "outsource_material_outbound", "other_outbound")) {
            for (Map.Entry<String, BigDecimal> en : prices.entrySet()) {
                jdbc.update("UPDATE " + table + " SET unit_price = ?, cost = ROUND(qty * ?, 2) WHERE material_code = ? " +
                        "AND status IN ('CONFIRMED','SIGNED') AND create_time >= ? AND create_time < ?",
                        en.getValue(), en.getValue(), en.getKey(), from, to);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("materialCount", prices.size());
        result.put("outboundCount", outByCode.size());
        result.put("prices", prices);
        result.put("operator", operator);
        return result;
        });
    }

    /** 该期全月平均成本是否已计算（期末结账前置校验用） */
    public boolean monthlyDone(String period) {
        try {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM costing_monthly_price WHERE period = ?", Integer.class, period);
            return c != null && c > 0;
        } catch (Exception e) { return false; }
    }

    /** 某期出库单数（前端期间行展示用） */
    public int periodOutboundCount(String period) {
        long from = LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5, 7)), 1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long to = LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5, 7)), 1)
                .plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            Long c = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM sales_outbound WHERE status='CONFIRMED' AND create_time >= ? AND create_time < ?)
                     + (SELECT COUNT(*) FROM production_outbound WHERE status='CONFIRMED' AND create_time >= ? AND create_time < ?)
                     + (SELECT COUNT(*) FROM outsource_material_outbound WHERE status IN ('CONFIRMED','SIGNED') AND create_time >= ? AND create_time < ?)
                     + (SELECT COUNT(*) FROM other_outbound WHERE status='CONFIRMED' AND create_time >= ? AND create_time < ?)
                """, Long.class, from, to, from, to, from, to, from, to);
            return c == null ? 0 : c.intValue();
        } catch (Exception e) { return 0; }
    }

    public List<Map<String, Object>> monthlyPrices(String period) {
        return jdbc.queryForList("SELECT material_code, price FROM costing_monthly_price WHERE period = ? ORDER BY material_code", period);
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Double d) return BigDecimal.valueOf(d);
        return new BigDecimal(String.valueOf(v));
    }
}
