package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * v5.81 质检模板三维匹配 + 包装标准：
 * 1) qc_template 加 sub_category / main_material / color_series（空=不限，存量模板留空=大类通用，平滑过渡）
 * 2) recipe 加 qc_template_id（绑定质检模板）/ packaging_standard_id（包装标准，进理论成本）
 * 3) 新表 packaging_standard（桶/袋/托盘等包装档案：规格+每件容量+单价）
 */
@Configuration
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20)
public class QcPackagingSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QcPackagingSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public QcPackagingSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        // 1) qc_template 三维匹配列
        addColumn("qc_template", "sub_category", "VARCHAR(50)");
        addColumn("qc_template", "main_material", "VARCHAR(20)");
        addColumn("qc_template", "color_series", "VARCHAR(20)");
        // 2) recipe 绑定列
        addColumn("recipe", "qc_template_id", "BIGINT");
        addColumn("recipe", "packaging_standard_id", "BIGINT");
        // 3) 包装标准档案表
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS packaging_standard (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(100) NOT NULL,
                        pack_type VARCHAR(20),
                        spec VARCHAR(100),
                        capacity_kg DECIMAL(10,3),
                        unit_price DECIMAL(14,2) NOT NULL DEFAULT 0,
                        remark VARCHAR(500),
                        enabled INTEGER NOT NULL DEFAULT 1,
                        create_time INTEGER,
                        update_time INTEGER
                    )""");
            // 包装类型字典
            Integer has = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dict_item WHERE type='packaging_type'", Integer.class);
            if (has == null || has == 0) {
                String[][] ds = {{"铁桶", "IRON_DRUM"}, {"塑料桶", "PLASTIC_DRUM"}, {"吨桶", "IBC"}, {"编织袋", "BAG"}, {"托盘", "PALLET"}, {"其他", "OTHER"}};
                int sort = 1;
                for (String[] d : ds) {
                    jdbc.update("INSERT INTO dict_item (type,label,value,sort_order,enabled,create_time) VALUES ('packaging_type',?,?,?,1,?)",
                            d[0], d[1], sort++);
                }
                log.info("包装标准：packaging_type 字典已初始化");
            }
            // v5.82 组合包装明细表（一套包装 = 桶+袋+托盘等多个物料）
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS packaging_standard_item (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        packaging_id INTEGER NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        pack_type VARCHAR(20),
                        spec VARCHAR(100),
                        qty DECIMAL(10,3) NOT NULL DEFAULT 1,
                        unit_price DECIMAL(14,2) NOT NULL DEFAULT 0,
                        sort_order INTEGER DEFAULT 1
                    )""");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_psi_packaging ON packaging_standard_item(packaging_id)");
            log.info("包装标准表就绪（含组合明细）");
        } catch (Exception e) {
            log.warn("包装标准表创建跳过: {}", e.getMessage());
        }
    }

    private void addColumn(String table, String col, String type) {
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
            boolean has = cols.stream().anyMatch(c -> col.equals(c.get("name")));
            if (!has) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + type);
                log.info("{} 新增 {} 列", table, col);
            }
        } catch (Exception e) {
            log.warn("{} 加列 {} 跳过: {}", table, col, e.getMessage());
        }
    }
}
