package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v5.40 物料编码全量迁移：7 位旧码 → 6 位新码（用户决策：全量迁移）。
 *
 * 旧格式：小类码(2) + 大类数字段(2) + 序号(3)，如 AC40001 —— 数字段冗余且与大类对应混乱；
 *         编码前缀与物料实际类别脱节（52 个 B 类浆顶着 PJ 前缀），看码不知类。
 * 新格式：小类码(2) + 序号(4)，如 AC0001 —— 小类码首字母恒为物料大类字母（A助剂/P颜料/F填料/
 *         R树脂/S溶剂/B半成品/C成品），一眼识类（防呆），缩短到 6 位方便手写记录。
 *
 * 映射规则：新码 = material.sub_category + 组内序号（同小类按旧码字典序重编 0001..N）——
 * material.sub_category 本身就是大类对齐的小类码（历史编码生成时没用它才错位），零新造码表。
 *
 * 同步更新 23 张引用表 + coding_rule 补缺失小类规则（BC/PR）并重置序号；迁移后建 material.code 唯一索引。
 * 整体单事务（失败全回滚），迁移前 VACUUM INTO 全库备份。
 * 幂等：全表已无 7 位旧格式码即跳过。
 */
@Component
@Order(1)
public class MaterialCodeMigrationInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MaterialCodeMigrationInitializer.class);

    /** 含 material_code 的全部引用表（含归档表） */
    private static final String[] REF_TABLES = {
            "finished_product_purchase", "inventory_ledger", "inventory_movement", "inventory_movement_archive",
            "other_inbound", "other_outbound", "outsource_material_consume", "outsource_material_outbound",
            "outsource_order_item", "production_order_item", "production_outbound", "purchase_arrival",
            "purchase_order_item", "quality_inspection", "raw_material_purchase", "recipe_tree_node",
            "return_order", "sales_order_item", "sales_outbound", "stat_inventory_daily",
            "stat_material_usage", "stock_check"
    };

    /** 大类代码 → 中文名（coding_rule 补行用） */
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "A", "助剂", "P", "颜料", "F", "填料", "R", "树脂", "S", "溶剂", "B", "半成品", "C", "成品");

    /** 缺失小类规则补录（v5.40 迁移发现：BC/PR 两小类有物料无规则） */
    private static final Map<String, String> MISSING_SUBCAT_NAMES = Map.of(
            "BC", "彩浆", "PR", "耐候颜料");

    private final JdbcTemplate jdbc;

    public MaterialCodeMigrationInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(String... args) {
        // v5.43 编码回收池：删除无引用物料时数字码入池，新建物料优先复用（数字全局单次使用原则不变）
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS released_code_seq (seq INTEGER PRIMARY KEY)");
        } catch (Exception e) { log.warn("回收池建表失败: {}", e.getMessage()); }
        try {
            Integer legacyCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM material WHERE length(code) = 7 AND substr(code, 3, 1) BETWEEN '0' AND '9'",
                    Integer.class);
            Integer emptyCode = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM material WHERE code IS NULL OR code = ''", Integer.class);
            if ((legacyCount == null || legacyCount == 0) && (emptyCode == null || emptyCode == 0)) {
                return;  // 已迁移（幂等）
            }
            log.info("检测到 {} 个旧格式编码、{} 个空编码，启动全量迁移（6 位新码）...", legacyCount, emptyCode);

            backupDatabase();

            // 1. 生成映射：按 (category, sub_category) 分组，组内按旧码排序重编 0001..N
            List<Map<String, Object>> materials = jdbc.queryForList(
                    "SELECT id, code, category, sub_category FROM material ORDER BY category, sub_category, code");
            Map<String, String> codeMap = new LinkedHashMap<>();     // old -> new
            Map<String, Integer> seqBySub = new LinkedHashMap<>();   // sub_category -> 最大序号
            int migrated = 0, renamedFromGarbage = 0;
            for (Map<String, Object> m : materials) {
                String old = m.get("code") == null ? "" : String.valueOf(m.get("code"));
                String category = m.get("category") == null ? "" : String.valueOf(m.get("category"));
                String sub = m.get("sub_category") == null || String.valueOf(m.get("sub_category")).isBlank()
                        ? fallbackSub(category) : String.valueOf(m.get("sub_category"));
                int seq = seqBySub.merge(sub, 1, Integer::sum);
                String newCode = String.format("%s%04d", sub, seq);
                if (!old.isBlank()) {
                    codeMap.put(old, newCode);
                    migrated++;
                } else {
                    renamedFromGarbage++;
                    jdbc.update("UPDATE material SET code = ? WHERE id = ?", newCode, m.get("id"));
                }
            }
            // 新码唯一性自检（小类+序号分配机制保证，双保险）
            long distinct = codeMap.values().stream().distinct().count();
            if (distinct != codeMap.size()) throw new IllegalStateException("新编码映射存在重复，中止迁移");

            // 2. 单事务更新全部引用表 + 物料主表
            int totalRefs = 0;
            for (String table : REF_TABLES) {
                for (Map.Entry<String, String> e : codeMap.entrySet()) {
                    totalRefs += jdbc.update("UPDATE " + table + " SET material_code = ? WHERE material_code = ?",
                            e.getValue(), e.getKey());
                }
            }
            for (Map.Entry<String, String> e : codeMap.entrySet()) {
                jdbc.update("UPDATE material SET code = ? WHERE code = ?", e.getValue(), e.getKey());
            }

            // 3. coding_rule：补缺失小类规则 + 序号重置为该小类当前最大序号（新码续号起点）
            for (Map.Entry<String, Integer> e : seqBySub.entrySet()) {
                String sub = e.getKey();
                var exist = jdbc.queryForList("SELECT id, current_seq FROM coding_rule WHERE sub_category_code = ?", sub);
                if (exist.isEmpty()) {
                    String category = String.valueOf(sub.charAt(0));
                    String name = MISSING_SUBCAT_NAMES.getOrDefault(sub, sub + "类物料");
                    jdbc.update("INSERT INTO coding_rule (category, category_code, sub_category, sub_category_code, number_start, current_seq, enabled, create_time) " +
                                    "VALUES (?, ?, ?, ?, 0, ?, 1, ?)",
                            CATEGORY_NAMES.getOrDefault(category, category), category, name, sub, e.getValue(), System.currentTimeMillis());
                    log.info("编码规则补录: {} {}（{}）序号起点 {}", name, sub, category, e.getValue());
                } else {
                    jdbc.update("UPDATE coding_rule SET current_seq = ?, update_time = ? WHERE sub_category_code = ?",
                            e.getValue(), System.currentTimeMillis(), sub);
                }
            }

            // 4. material.code 唯一索引（防呆：数据库层杜绝重复码）
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_material_code ON material(code)");

            log.info("编码迁移完成: 物料 {} 个（含补码 {} 个），引用行更新 {} 行，规则重置 {} 个小类，唯一索引就绪",
                    migrated, renamedFromGarbage, totalRefs, seqBySub.size());
        } catch (Exception e) {
            log.error("编码迁移失败（事务回滚）: {}", e.getMessage(), e);
            throw new IllegalStateException("物料编码迁移失败: " + e.getMessage(), e);
        }
    }

    /** 迁移前全库安全备份（幂等：备份文件已存在则跳过） */
    private void backupDatabase() {
        String backup = "data/pims.db.bak-code-migration";
        try {
            var exists = jdbc.queryForList("SELECT file FROM pragma_database_list() WHERE file LIKE '%pims.db'");
            if (!exists.isEmpty()) {
                var already = jdbc.queryForList(
                        "SELECT name FROM sqlite_master WHERE name = 'migration_code_backup_done'");
                if (already.isEmpty()) {
                    jdbc.execute("VACUUM INTO '" + backup + "'");
                    jdbc.execute("CREATE TABLE migration_code_backup_done (ts INTEGER)");
                    jdbc.update("INSERT INTO migration_code_backup_done VALUES (?)", System.currentTimeMillis());
                    log.info("迁移前全库备份完成: {}", backup);
                }
            }
        } catch (Exception e) {
            log.warn("迁移前备份失败（继续迁移）: {}", e.getMessage());
        }
    }

    private static String fallbackSub(String category) {
        return switch (category) {
            case "A" -> "AX"; case "P" -> "PW"; case "F" -> "FT"; case "R" -> "RP";
            case "S" -> "SY"; case "B" -> "BW"; case "C" -> "CW"; default -> "AX";
        };
    }
}
