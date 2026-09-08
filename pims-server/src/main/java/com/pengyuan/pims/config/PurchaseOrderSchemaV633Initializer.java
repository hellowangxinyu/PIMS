package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v6.3 第三批：purchase_order 放宽 supplier_id / target_warehouse_id 非空约束。
 * 业务口径：MRP 生成的请购单"供应商/收货仓待定"是正常状态（请购→审批→转采购时补全），
 * 原建表把两列设了 NOT NULL 会挡住 MRP 建单。SQLite 不支持 DROP 约束，采用重建表：
 * 动态按现有列交集拷贝数据，保持 id/单号唯一性。
 */
@Component
public class PurchaseOrderSchemaV633Initializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSchemaV633Initializer.class);
    private final JdbcTemplate jdbc;

    public PurchaseOrderSchemaV633Initializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        // v8.0（P0-11）孤儿表恢复：上次 DROP 成功但 RENAME 失败（进程被杀/磁盘满）时，
        // purchase_order 缺失而 purchase_order_v633 存在——原逻辑直接 return，主表永久消失。
        // 恢复路径：把孤儿表改回主表名再走正常建索引。
        var orphan = jdbc.queryForList("PRAGMA table_info(purchase_order)");
        var leftover = jdbc.queryForList("PRAGMA table_info(purchase_order_v633)");
        if (orphan.isEmpty() && !leftover.isEmpty()) {
            jdbc.execute("ALTER TABLE purchase_order_v633 RENAME TO purchase_order");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_order_no ON purchase_order(order_no)");
            log.warn("purchase_order 重建中断恢复：孤儿表 purchase_order_v633 已改回主表（上次 DROP 后 RENAME 失败）");
        }
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(purchase_order)");
            if (cols.isEmpty()) return;   // 表尚不存在（新库由 JPA？ddl-auto=none——由建表方创建）
            boolean supplierNotNull = cols.stream()
                    .anyMatch(c -> "supplier_id".equals(c.get("name")) && Integer.parseInt(String.valueOf(c.get("notnull"))) == 1);
            if (!supplierNotNull) return;

            // 目标结构（两列放宽，其余照旧类型）
            jdbc.execute("""
                CREATE TABLE purchase_order_v633 (
                    id integer PRIMARY KEY AUTOINCREMENT,
                    order_no VARCHAR(20) NOT NULL,
                    supplier_id BIGINT,
                    order_date DATE,
                    status VARCHAR(20) NOT NULL,
                    total_amount NUMERIC(14,2),
                    payment_terms VARCHAR(200),
                    target_warehouse_id VARCHAR(20),
                    expected_delivery_date DATE,
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            // 按现有列交集拷贝（防历史库缺列/多列）
            List<String> existing = cols.stream().map(c -> String.valueOf(c.get("name"))).toList();
            List<String> target = List.of("id", "order_no", "supplier_id", "order_date", "status", "total_amount",
                    "payment_terms", "target_warehouse_id", "expected_delivery_date", "created_by", "remark",
                    "create_time", "update_time");
            List<String> common = target.stream().filter(existing::contains).toList();
            String colList = String.join(", ", common);
            int n = jdbc.update("INSERT INTO purchase_order_v633 (" + colList + ") SELECT " + colList + " FROM purchase_order");
            jdbc.execute("DROP TABLE purchase_order");
            jdbc.execute("ALTER TABLE purchase_order_v633 RENAME TO purchase_order");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_order_no ON purchase_order(order_no)");
            log.info("purchase_order 重建完成：supplier_id/target_warehouse_id 放宽可空，迁移 {} 行", n);
        } catch (Exception e) {
            // v8.0（P0-11）：DROP 之后的失败不再静默吞——主表可能已消失，必须让启动失败暴露（有上面的孤儿恢复兜底）
            var after = jdbc.queryForList("PRAGMA table_info(purchase_order)");
            if (after.isEmpty()) {
                log.error("purchase_order 重建失败且主表缺失！将尝试从 _v633 恢复", e);
                var v633 = jdbc.queryForList("PRAGMA table_info(purchase_order_v633)");
                if (!v633.isEmpty()) {
                    jdbc.execute("ALTER TABLE purchase_order_v633 RENAME TO purchase_order");
                    log.error("已从 purchase_order_v633 恢复主表，请检查数据完整性后重启");
                }
                throw new IllegalStateException("purchase_order 重建失败（已尝试恢复），启动中止", e);
            }
            log.warn("purchase_order 重建跳过（主表完好）: {}", e.getMessage());
        }
    }
}
