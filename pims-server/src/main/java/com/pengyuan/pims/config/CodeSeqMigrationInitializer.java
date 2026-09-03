package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * v5.42 编码序号全局连续重排（用户定稿：字母只区分类别，数字 0001-9999 全局连续单次使用）。
 *
 * 重排规则：全部物料按现有编码全局排序，第 i 个 → 其小类码 + (i+1)。
 * 效果：AC0001、BW0002、RP0003…任何两个物料的数字都不相同，工人只看数字绝不拿错料；容量 9999。
 *
 * 同步更新 23 张引用表；coding_rule 每小类 currentSeq = 该小类最大序号（全局游标=各行最大值）。
 * 单事务失败全回滚；幂等标志表 migration_code_step_done。
 */
@Component
@Order(1)
public class CodeSeqMigrationInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CodeSeqMigrationInitializer.class);

    // v5.41 曾按步长 7 跳号；v5.42 用户定稿改连续号（本次迁移同时兼容两种来源状态）

    /** 含 material_code 的全部引用表（含归档表），与 MaterialCodeMigrationInitializer 保持一致 */
    private static final String[] REF_TABLES = {
            "finished_product_purchase", "inventory_ledger", "inventory_movement", "inventory_movement_archive",
            "other_inbound", "other_outbound", "outsource_material_consume", "outsource_material_outbound",
            "outsource_order_item", "production_order_item", "production_outbound", "purchase_arrival",
            "purchase_order_item", "quality_inspection", "raw_material_purchase", "recipe_tree_node",
            "return_order", "sales_order_item", "sales_outbound", "stat_inventory_daily",
            "stat_material_usage", "stock_check"
    };

    private final JdbcTemplate jdbc;

    public CodeSeqMigrationInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            // 幂等标志（v5.42 连续号重排，与 v5.41 跳号重排各自独立标志）
            var done = jdbc.queryForList("SELECT name FROM sqlite_master WHERE name = 'migration_code_seq_done'");
            if (!done.isEmpty()) return;
            // v5.40 旧格式迁移须先完成（存在 7 位码时不动，待下次启动链式执行）
            Integer legacy = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM material WHERE length(code) = 7 AND substr(code, 3, 1) BETWEEN '0' AND '9'",
                    Integer.class);
            if (legacy != null && legacy > 0) {
                log.warn("存在 {} 个 7 位旧格式编码，跳过跳号重排（待旧格式迁移完成后下次启动执行）", legacy);
                return;
            }

            // 全部物料按现有编码全局排序，分配全局唯一跳号序号
            List<Map<String, Object>> materials = jdbc.queryForList(
                    "SELECT code FROM material WHERE code IS NOT NULL AND code != '' AND length(code) = 6 ORDER BY code");
            if (materials.isEmpty()) return;

            Map<String, String> codeMap = new TreeMap<>();  // old -> new
            Set<Integer> usedSeqs = new HashSet<>();
            int idx = 0;
            for (Map<String, Object> m : materials) {
                String old = String.valueOf(m.get("code"));
                idx++;
                int seq = idx;
                String newCode = old.substring(0, 2) + String.format("%04d", seq);
                codeMap.put(old, newCode);
                usedSeqs.add(seq);
            }
            // 序号全局唯一由构造保证；双保险校验
            if (usedSeqs.size() != codeMap.size()) throw new IllegalStateException("跳号序号存在重复，中止重排");
            log.info("连续号重排: {} 个物料，序号 1 → {}", codeMap.size(), idx);

            // 两阶段更新：新旧码同为 6 位且新序号(7的倍数)与未更新的旧序号重叠——直接改会撞
            // stat_inventory_daily(idx_material_code)/material(idx_material_code) 的唯一索引；
            // 先全部借道 "ZZ"+新码 临时前缀（物料码首字母仅 A/P/F/R/S/B/C，ZZ 绝不冲突），再统一去掉前缀
            int totalRefs = 0;
            for (String table : REF_TABLES) {
                for (Map.Entry<String, String> e : codeMap.entrySet()) {
                    totalRefs += jdbc.update("UPDATE " + table + " SET material_code = ? WHERE material_code = ?",
                            "ZZ" + e.getValue(), e.getKey());
                }
                jdbc.update("UPDATE " + table + " SET material_code = substr(material_code, 3) WHERE material_code LIKE 'ZZ%'");
            }
            for (Map.Entry<String, String> e : codeMap.entrySet()) {
                jdbc.update("UPDATE material SET code = ? WHERE code = ?", "ZZ" + e.getValue(), e.getKey());
            }
            jdbc.update("UPDATE material SET code = substr(code, 3) WHERE code LIKE 'ZZ%'");
            // coding_rule：每小类 currentSeq = 该小类当前最大序号（全局游标 = 各行最大值）
            var subs = jdbc.queryForList(
                    "SELECT substr(code,1,2) AS sub, MAX(CAST(substr(code,3) AS INTEGER)) AS mx " +
                    "FROM material GROUP BY substr(code,1,2)");
            for (var r : subs) {
                jdbc.update("UPDATE coding_rule SET current_seq = ?, update_time = ? WHERE sub_category_code = ?",
                        r.get("mx"), System.currentTimeMillis(), r.get("sub"));
            }

            jdbc.execute("CREATE TABLE migration_code_seq_done (ts INTEGER, materials INTEGER, refs INTEGER)");
            jdbc.update("INSERT INTO migration_code_seq_done VALUES (?, ?, ?)", System.currentTimeMillis(), codeMap.size(), totalRefs);
            log.info("连续号重排完成: 物料 {} 个（数字 1-{} 全局连续单次使用），引用行更新 {} 行，规则游标重置 {} 个小类",
                    codeMap.size(), idx, totalRefs, subs.size());
        } catch (Exception e) {
            log.error("连续号重排失败（事务回滚）: {}", e.getMessage(), e);
            throw new IllegalStateException("编码连续号重排失败: " + e.getMessage(), e);
        }
    }
}
