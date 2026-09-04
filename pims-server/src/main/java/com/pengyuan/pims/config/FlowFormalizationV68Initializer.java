package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v6.8 流程正规化：
 * ① 字典 outbound_reason 补「REWORK 返工领料」（存量库 seed 幂等跳过，须 upsert）
 * ② production_outbound 补 supplement_type 列（补领原因：COLOR_ADJUST 色差调整 / OVER_CONSUME 超耗补充）
 */
@Component
public class FlowFormalizationV68Initializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowFormalizationV68Initializer.class);
    private final JdbcTemplate jdbc;

    public FlowFormalizationV68Initializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM dict_item WHERE type = 'outbound_reason' AND value = 'REWORK'", Integer.class);
            if (n == null || n == 0) {
                jdbc.update("INSERT INTO dict_item (type, label, value, sort_order, enabled) VALUES ('outbound_reason', '返工领料', 'REWORK', 6, 1)");
                log.info("v6.8：outbound_reason 字典新增「返工领料 REWORK」");
            }
            var cols = jdbc.queryForList("PRAGMA table_info(production_outbound)");
            if (!cols.isEmpty() && cols.stream().noneMatch(c -> "supplement_type".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE production_outbound ADD COLUMN supplement_type VARCHAR(20)");
                log.info("v6.8：production_outbound 新增 supplement_type 列（补领原因）");
            }
        } catch (Exception e) {
            log.warn("v6.8 流程正规化初始化跳过: {}", e.getMessage());
        }
    }
}
