package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v5.52 销售P1三件套建表：
 * 1) 报价单 quotation / quotation_item（状态机 DRAFT→QUOTED→ACCEPTED 转订单 / REJECTED）
 * 2) 客户信用额度 customer.credit_limit（下单前预警）
 */
@Component
@Order(3)
public class QuotationSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QuotationSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public QuotationSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS quotation (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quote_no VARCHAR(20) NOT NULL UNIQUE,
                    customer_id BIGINT NOT NULL,
                    customer_name VARCHAR(100),
                    quote_date DATE,
                    valid_until DATE,
                    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                    total_amount DECIMAL(14,2) DEFAULT 0,
                    sales_order_no VARCHAR(20),
                    remark VARCHAR(500),
                    created_by VARCHAR(50),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_quotation_cust ON quotation(customer_id)");
            log.info("报价单表 quotation 就绪");
        } catch (Exception e) { log.warn("quotation 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS quotation_item (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quotation_id BIGINT NOT NULL,
                    material_code VARCHAR(30) NOT NULL,
                    material_name VARCHAR(100),
                    qty DECIMAL(14,3) NOT NULL,
                    unit VARCHAR(10),
                    unit_price DECIMAL(12,2),
                    amount DECIMAL(14,2),
                    remark VARCHAR(500)
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_quotation_item ON quotation_item(quotation_id)");
            log.info("报价单明细表 quotation_item 就绪");
        } catch (Exception e) { log.warn("quotation_item 建表失败: {}", e.getMessage()); }

        // 客户信用额度列（幂等）
        try {
            List<?> cols = jdbc.queryForList("PRAGMA table_info(customer)");
            boolean has = cols.stream().anyMatch(c -> String.valueOf(((java.util.Map<?, ?>) c).get("name")).equalsIgnoreCase("credit_limit"));
            if (!has) {
                jdbc.execute("ALTER TABLE customer ADD COLUMN credit_limit DECIMAL(14,2)");
                log.info("customer 表新增 credit_limit 列");
            }
        } catch (Exception e) { log.warn("customer.credit_limit 加列失败: {}", e.getMessage()); }
    }
}
