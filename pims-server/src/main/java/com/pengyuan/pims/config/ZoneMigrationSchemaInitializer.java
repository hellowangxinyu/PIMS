package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * v5.38 隔离仓改分库结构迁移：
 * 「不合格品库 WH-UNQ」「油尾库 WH-TAILING」从一级仓库降级为宿主仓（WH-OWN-PY 芃远自有仓）下的隔离分库。
 *
 * 迁移内容（幂等，按 zone 是否已挂宿主仓判断）：
 * 1. warehouse_zone 补 zone_type 列（null=普通 / UNQUALIFIED / TAILING）
 * 2. 旧隔离仓的分库（隔离区/油尾区）改挂宿主仓并打 zone_type（库位只挂 zone_id，自动跟随）
 * 3. 台账 warehouse_id '旧隔离仓id' → '宿主仓id'（locationId 不变，唯一键不冲突——迁移前校验撞键）
 * 4. 旧隔离仓 enabled=0 保留（历史单据引用），不得删除
 *
 * 隔离判断锚点从「仓库 type」下移到「库位→分库 type」（台账行 locationId 恒有效：
 * 调拨只操作无库位行、隔离货由质检/油尾单据化入库时带隔离库位）。
 */
@Component
@Order(0)
public class ZoneMigrationSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ZoneMigrationSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public ZoneMigrationSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(warehouse_zone)");
            if (!cols.stream().anyMatch(c -> "zone_type".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE warehouse_zone ADD COLUMN zone_type VARCHAR(20)");
                log.info("分库表补 zone_type 列完成");
            }
        } catch (Exception e) { log.warn("warehouse_zone 补列失败: {}", e.getMessage()); }

        migrateSpecialWarehouse("WH-UNQ", "UNQUALIFIED");
        migrateSpecialWarehouse("WH-TAILING", "TAILING");

        // v5.38.3：遗留 UNQUALIFIED 类型分库清理（服务器曾有人手工建过第二个不合格品库）——
        // 统一转为 UNQUALIFIED_RAW；同仓已有 RAW 且自身无台账的空库直接禁用，避免重复分库
        try {
            var leftovers = jdbc.queryForList(
                    "SELECT z.id, z.warehouse_id, z.name FROM warehouse_zone z WHERE z.zone_type = 'UNQUALIFIED' AND z.enabled = 1");
            for (var z : leftovers) {
                Long zid = ((Number) z.get("id")).longValue();
                Long whId = ((Number) z.get("warehouse_id")).longValue();
                var hasRaw = jdbc.queryForList(
                        "SELECT id FROM warehouse_zone WHERE warehouse_id = ? AND zone_type = 'UNQUALIFIED_RAW' AND enabled = 1 AND id <> ?", whId, zid);
                var hasStock = jdbc.queryForList(
                        "SELECT id FROM inventory_ledger WHERE location_id IN (SELECT id FROM warehouse_location WHERE zone_id = ?) AND qty > 0 LIMIT 1", zid);
                if (!hasRaw.isEmpty() && hasStock.isEmpty()) {
                    jdbc.update("UPDATE warehouse_zone SET enabled = 0, update_time = ? WHERE id = ?", System.currentTimeMillis(), zid);
                    log.info("遗留不合格品分库「{}」为空库且同仓已有原材料不合格品库，已禁用", z.get("name"));
                } else {
                    jdbc.update("UPDATE warehouse_zone SET zone_type = 'UNQUALIFIED_RAW', update_time = ? WHERE id = ?", System.currentTimeMillis(), zid);
                    log.info("遗留不合格品分库「{}」已转为原材料不合格品库", z.get("name"));
                }
            }
        } catch (Exception e) { log.warn("遗留 UNQUALIFIED 分库清理失败: {}", e.getMessage()); }

        // v5.38.3：台账冗余分库名对齐（分库改名后 zone_name 同步，幂等全量对齐）
        try {
            int aligned = jdbc.update(
                    "UPDATE inventory_ledger SET zone_name = (" +
                    "  SELECT z.name FROM warehouse_location l JOIN warehouse_zone z ON l.zone_id = z.id " +
                    "  WHERE l.id = inventory_ledger.location_id) " +
                    "WHERE location_id IS NOT NULL AND zone_name IS NOT NULL AND zone_name <> (" +
                    "  SELECT z.name FROM warehouse_location l JOIN warehouse_zone z ON l.zone_id = z.id " +
                    "  WHERE l.id = inventory_ledger.location_id)");
            if (aligned > 0) log.info("台账分库名对齐: {} 行 zone_name 更新", aligned);
        } catch (Exception e) { log.warn("台账分库名对齐失败: {}", e.getMessage()); }

        // v5.46：流水表补库位列（新流水精确到库位；历史流水保留不动）+ 清理零量无库位遗留行
        for (String t : new String[]{"inventory_movement", "inventory_movement_archive"}) {
            try {
                var cols = jdbc.queryForList("PRAGMA table_info(" + t + ")");
                if (!cols.isEmpty() && !cols.stream().anyMatch(c -> "location_id".equals(c.get("name")))) {
                    jdbc.execute("ALTER TABLE " + t + " ADD COLUMN location_id VARCHAR(20)");
                    log.info("{} 补 location_id 列完成", t);
                }
            } catch (Exception e) { log.warn("{} 补列失败: {}", t, e.getMessage()); }
        }
        try {
            int cleaned = jdbc.update("DELETE FROM inventory_ledger WHERE qty = 0 AND location_id IS NULL AND batch_no IS NULL");
            if (cleaned > 0) log.info("清理零量无批次无库位台账遗留 {} 行", cleaned);
        } catch (Exception e) { log.warn("零量行清理失败: {}", e.getMessage()); }

        // v5.38.2：油尾退回单补入库目标仓列（体系路由用）
        try {
            var roCols = jdbc.queryForList("PRAGMA table_info(return_order)");
            if (!roCols.stream().anyMatch(c -> "warehouse_id".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE return_order ADD COLUMN warehouse_id VARCHAR(20)");
                log.info("return_order 补 warehouse_id 列完成");
            }
        } catch (Exception e) { log.warn("return_order 补列失败: {}", e.getMessage()); }
    }

    /** 单个隔离仓迁移：分库改挂宿主仓 + 台账仓库归属改写 + 旧仓禁用 */
    private void migrateSpecialWarehouse(String whCode, String zoneType) {
        try {
            List<Map<String, Object>> whs = jdbc.queryForList(
                    "SELECT id FROM warehouse WHERE code = ?", whCode);
            if (whs.isEmpty()) return;  // 新库或已迁移（旧仓不存在/已删）
            String oldId = String.valueOf(whs.get(0).get("id"));

            List<Map<String, Object>> hosts = jdbc.queryForList(
                    "SELECT id FROM warehouse WHERE code = 'WH-OWN-PY' AND enabled = 1");
            if (hosts.isEmpty()) {
                log.warn("{} 迁移跳过：宿主仓 WH-OWN-PY 不存在", whCode);
                return;
            }
            String hostId = String.valueOf(hosts.get(0).get("id"));
            if (oldId.equals(hostId)) return;  // 数据异常保护

            // 已迁移幂等标记：旧仓分库已挂宿主仓
            Integer migrated = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM warehouse_zone WHERE warehouse_id = ? AND zone_type = ?",
                    Integer.class, Long.valueOf(hostId), zoneType);
            if (migrated != null && migrated > 0) {
                ensureDisabled(oldId);
                return;
            }

            // 撞键校验：迁移目标 (material_code, batch_no, hostId, location_id) 已被非隔离台账占用则中止
            List<Map<String, Object>> conflicts = jdbc.queryForList(
                    "SELECT material_code, batch_no, location_id FROM inventory_ledger l " +
                    "WHERE l.warehouse_id = ? AND l.location_id IN " +
                    "(SELECT l2.location_id FROM inventory_ledger l2 WHERE l2.warehouse_id = ? AND l2.location_id IS NOT NULL)",
                    Long.valueOf(hostId), Long.valueOf(oldId));
            if (!conflicts.isEmpty()) {
                log.error("{} 台账迁移中止：{} 行与宿主仓现有台账撞键（同物料同批号同库位），需人工处理: {}",
                        whCode, conflicts.size(), conflicts.subList(0, Math.min(3, conflicts.size())));
                return;
            }

            // 1. 分库改挂宿主仓 + 打类型
            int zones = jdbc.update("UPDATE warehouse_zone SET warehouse_id = ?, zone_type = ?, update_time = ? WHERE warehouse_id = ?",
                    Long.valueOf(hostId), zoneType, System.currentTimeMillis(), Long.valueOf(oldId));
            // 2. 台账归属改写（库位不变，随分库落到宿主仓）
            int ledgers = jdbc.update("UPDATE inventory_ledger SET warehouse_id = ? WHERE warehouse_id = ?",
                    hostId, oldId);
            // 3. 旧仓禁用保留
            ensureDisabled(oldId);

            log.info("{} 迁移完成: {} 个分库改挂宿主仓 {}，{} 行台账归属改写，旧仓已禁用保留",
                    whCode, zones, hostId, ledgers);
        } catch (Exception e) {
            log.warn("{} 迁移失败: {}", whCode, e.getMessage(), e);
        }
    }

    private void ensureDisabled(String oldId) {
        jdbc.update("UPDATE warehouse SET enabled = 0 WHERE id = ? AND enabled = 1", Long.valueOf(oldId));
    }
}
