package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v7.7 打样任务/打样配方建表：
 * 1) sample_request 补列：assignee 派发的打样员 / assign_time 派发时间 / receive_time 接收时间
 * 2) sample_formula 打样配方（与打样单 1:1；首次保存自动生成 C 类成品物料回填编码；
 *    est_cost 估算成本元/kg=移动加权均价口径；sample_location 留样位置；converted_* 转制漆回写防重复）
 * 3) sample_formula_item 配方明细（自由用量，不强制 100——转制漆时才按标准批量折算）
 * 4) sample_formula_history 覆盖更新前的旧明细 JSON 快照（纯存档无 UI，找回历史版本用）
 */
@Component
@Order(4)   // 晚于 @Order(3) 的 SampleComplaintSchemaInitializer（sample_request 建表方），补列时表必已存在
public class SampleFormulaSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleFormulaSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SampleFormulaSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        // v7.7 打样任务派发三列（存量表补列，幂等）
        try {
            for (String col : new String[]{"assignee VARCHAR(50)", "assign_time TIMESTAMP", "receive_time TIMESTAMP"}) {
                String name = col.split(" ")[0];
                boolean has = jdbc.queryForList("PRAGMA table_info(sample_request)").stream()
                        .anyMatch(c -> name.equalsIgnoreCase(String.valueOf(c.get("name"))));
                if (!has) jdbc.execute("ALTER TABLE sample_request ADD COLUMN " + col);
            }
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sample_assignee ON sample_request(assignee, status)");
            log.info("sample_request 派发列就绪");
        } catch (Exception e) { log.warn("sample_request 补列失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sample_formula (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    formula_no VARCHAR(20) NOT NULL UNIQUE,
                    sample_request_id BIGINT NOT NULL UNIQUE,
                    material_code VARCHAR(30),
                    material_name VARCHAR(100),
                    sub_category VARCHAR(10),
                    main_material VARCHAR(10),
                    color_series VARCHAR(10),
                    total_qty DECIMAL(14,3),
                    est_cost DECIMAL(14,2),
                    sample_location VARCHAR(20),
                    converted_recipe_id BIGINT,
                    converted_time TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("打样配方表 sample_formula 就绪");
        } catch (Exception e) { log.warn("sample_formula 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sample_formula_item (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    formula_id BIGINT NOT NULL,
                    material_code VARCHAR(30) NOT NULL,
                    material_name VARCHAR(100),
                    category VARCHAR(10),
                    sub_category VARCHAR(10),
                    unit VARCHAR(10) DEFAULT 'kg',
                    qty DECIMAL(14,3) NOT NULL,
                    sort_order INTEGER DEFAULT 0
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sample_formula_item ON sample_formula_item(formula_id)");
            log.info("打样配方明细表 sample_formula_item 就绪");
        } catch (Exception e) { log.warn("sample_formula_item 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sample_formula_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    formula_id BIGINT NOT NULL,
                    round INTEGER,
                    snapshot TEXT,
                    create_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sample_formula_his ON sample_formula_history(formula_id)");
            log.info("打样配方快照表 sample_formula_history 就绪");
        } catch (Exception e) { log.warn("sample_formula_history 建表失败: {}", e.getMessage()); }
    }
}
