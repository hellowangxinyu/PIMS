package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * v5.80 性能索引补齐（2026-08-29 全项目性能审查）：
 * 1) sys_role_permission.role_code —— 每次 API 鉴权（Sa-Token 权限加载）都按角色查，411 行无索引全表扫
 * 2) recipe_tree_node.version_id —— 配方树按版本查询（展开/对比/保存）高频，无索引
 * 3) quality_inspection_item.inspection_id —— 判定弹窗/打印加载检测项（现有 idx_qc_item_order 需核实列）
 * 4) inventory_ledger.qc_status —— 油尾库存/隔离判定按 qc_status 全表过滤（tailingOptions 等）
 * 5) inventory_ledger.material_code 已含于 idx_ledger_lookup？若无单独批号索引则补 batch_no
 * 全部 CREATE INDEX IF NOT EXISTS，幂等。
 */
@Configuration
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
public class PerfIndexSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfIndexSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public PerfIndexSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        String[][] indexes = {
                {"idx_sys_role_perm_role", "sys_role_permission", "CREATE INDEX IF NOT EXISTS idx_sys_role_perm_role ON sys_role_permission(role_code)"},
                {"idx_rtn_version", "recipe_tree_node", "CREATE INDEX IF NOT EXISTS idx_rtn_version ON recipe_tree_node(version_id)"},
                {"idx_qc_item_inspection", "quality_inspection_item", "CREATE INDEX IF NOT EXISTS idx_qc_item_inspection ON quality_inspection_item(inspection_id)"},
                {"idx_ledger_qc_status", "inventory_ledger", "CREATE INDEX IF NOT EXISTS idx_ledger_qc_status ON inventory_ledger(qc_status)"},
                {"idx_ledger_batch", "inventory_ledger", "CREATE INDEX IF NOT EXISTS idx_ledger_batch ON inventory_ledger(batch_no)"},
                {"idx_arrival_ref", "purchase_arrival", "CREATE INDEX IF NOT EXISTS idx_arrival_ref ON purchase_arrival(ref_order_no, material_code)"},
                {"idx_soi_order", "sales_order_item", "CREATE INDEX IF NOT EXISTS idx_soi_order ON sales_order_item(order_id)"},
                {"idx_poi_order", "production_order_item", "CREATE INDEX IF NOT EXISTS idx_poi_order ON production_order_item(order_id)"},
                {"idx_voucher_period", "voucher", "CREATE INDEX IF NOT EXISTS idx_voucher_period ON voucher(period, status)"},
                {"idx_oplog_time", "operation_log", "CREATE INDEX IF NOT EXISTS idx_oplog_time ON operation_log(create_time)"},
        };
        int created = 0;
        for (String[] ix : indexes) {
            try {
                Integer before = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?", Integer.class, ix[0]);
                if (before == null || before == 0) {
                    jdbc.execute(ix[2]);
                    log.info("性能索引：{} 已创建（{}）", ix[0], ix[1]);
                    created++;
                }
            } catch (Exception e) {
                log.warn("性能索引 {} 创建跳过: {}", ix[0], e.getMessage());
            }
        }
        // 操作日志分表（按月）也补 create_time 索引
        try {
            var tables = jdbc.queryForList(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'operation_log_%'");
            for (var t : tables) {
                String tn = String.valueOf(t.get("name"));
                String idxName = "idx_" + tn + "_ctime";
                Integer has = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?", Integer.class, idxName);
                if (has == null || has == 0) {
                    jdbc.execute("CREATE INDEX IF NOT EXISTS " + idxName + " ON " + tn + "(create_time)");
                    created++;
                }
            }
        } catch (Exception e) {
            log.warn("操作日志分表索引跳过: {}", e.getMessage());
        }
        // v6.1 安全：sys_user 加 must_change_pwd 列（强制改密标记）
        try {
            var ucols = jdbc.queryForList("PRAGMA table_info(sys_user)");
            boolean hasFlag = ucols.stream().anyMatch(c -> "must_change_pwd".equals(c.get("name")));
            if (!hasFlag) {
                jdbc.execute("ALTER TABLE sys_user ADD COLUMN must_change_pwd BOOLEAN DEFAULT 0");
                jdbc.update("UPDATE sys_user SET must_change_pwd = 1 WHERE username IN ('admin','buyer')");
                log.info("安全加固：sys_user 新增 must_change_pwd 列，admin/buyer 已标记强制改密");
            }
        } catch (Exception e) {
            log.warn("must_change_pwd 加列跳过: {}", e.getMessage());
        }

        log.info("性能索引初始化完成：本次新建 {} 个", created);
    }
}
