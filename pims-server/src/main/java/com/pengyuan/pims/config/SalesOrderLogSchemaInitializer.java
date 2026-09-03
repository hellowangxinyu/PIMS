package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

/** v5.47 销售订单变更留痕表（sales_order_change_log） */
@Component
@Order(3)
public class SalesOrderLogSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderLogSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SalesOrderLogSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sales_order_change_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id BIGINT NOT NULL,
                    order_no VARCHAR(20),
                    detail VARCHAR(2000) NOT NULL,
                    operator VARCHAR(50),
                    create_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_socl_order ON sales_order_change_log(order_id)");
            log.info("销售订单变更留痕表 sales_order_change_log 就绪");
        } catch (Exception e) { log.warn("sales_order_change_log 建表失败: {}", e.getMessage()); }
    }
}
