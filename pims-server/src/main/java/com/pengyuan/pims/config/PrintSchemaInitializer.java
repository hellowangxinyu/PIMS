package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 打印计数表结构初始化（v5.26，ddl-auto=none 后手动维护）
 * 4 张单据表新增 print_count 列（每次打印 +1），幂等安全
 * 必须最先执行（@Order(1)）：JPA 实体已带 print_count 字段，
 * 其他 Initializer 若在此之前触发 JPA 查询会报 no such column
 */
@Component
@Order(1)
public class PrintSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PrintSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public PrintSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 表名 → 单号列（print_count 按单号归属计数）
        String[][] tables = {
                {"production_order", "order_no"},
                {"outsource_order", "order_no"},
                {"recipe", "recipe_no"},
                {"quality_inspection", "inspection_no"},
                {"sales_outbound", "doc_no"},
        };
        for (String[] t : tables) {
            String table = t[0];
            String noCol = t[1];
            try {
                var cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
                boolean hasPrintCount = cols.stream().anyMatch(c -> "print_count".equals(c.get("name")));
                if (!hasPrintCount) {
                    jdbc.execute("ALTER TABLE " + table + " ADD COLUMN print_count INTEGER NOT NULL DEFAULT 0");
                    log.info("打印计数表结构：{} 新增 print_count 列", table);
                }
            } catch (Exception e) {
                log.warn("打印计数表结构：{} 加列失败（表可能不存在，跳过）: {}", table, e.getMessage());
            }
        }
        log.info("打印计数表结构：print_count 列就绪");
    }
}
