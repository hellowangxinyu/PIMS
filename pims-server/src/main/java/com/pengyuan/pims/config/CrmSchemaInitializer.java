package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.50 CRM 模块建表：联系人（crm_contact）/ 商机管道（crm_opportunity）/ 跟进记录（crm_follow_up）。
 * 商机阶段：LEAD 初步接触 → QUOTED 已报价 → SAMPLING 样品测试 → NEGOTIATING 商务谈判 → WON 成交 / LOST 流失。
 */
@Component
@Order(3)
public class CrmSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CrmSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public CrmSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS crm_contact (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id BIGINT,
                    company_name VARCHAR(100) NOT NULL,
                    name VARCHAR(50) NOT NULL,
                    title VARCHAR(50),
                    phone VARCHAR(30),
                    wechat VARCHAR(50),
                    email VARCHAR(100),
                    is_primary INTEGER DEFAULT 0,
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_crm_contact_cust ON crm_contact(customer_id)");
            log.info("CRM 联系人表 crm_contact 就绪");
        } catch (Exception e) { log.warn("crm_contact 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS crm_opportunity (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title VARCHAR(100) NOT NULL,
                    company_name VARCHAR(100) NOT NULL,
                    customer_id BIGINT,
                    product_interest VARCHAR(200),
                    expect_amount DECIMAL(14,2),
                    expect_date DATE,
                    stage VARCHAR(20) NOT NULL DEFAULT 'LEAD',
                    owner VARCHAR(50),
                    won_order_no VARCHAR(20),
                    loss_reason VARCHAR(200),
                    remark VARCHAR(500),
                    created_by VARCHAR(50),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_crm_opp_stage ON crm_opportunity(stage)");
            log.info("CRM 商机表 crm_opportunity 就绪");
        } catch (Exception e) { log.warn("crm_opportunity 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS crm_follow_up (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    opportunity_id BIGINT,
                    customer_id BIGINT,
                    follow_date DATE NOT NULL,
                    method VARCHAR(20) NOT NULL DEFAULT 'PHONE',
                    content VARCHAR(2000) NOT NULL,
                    next_date DATE,
                    operator VARCHAR(50),
                    create_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_crm_fu_opp ON crm_follow_up(opportunity_id)");
            log.info("CRM 跟进记录表 crm_follow_up 就绪");
        } catch (Exception e) { log.warn("crm_follow_up 建表失败: {}", e.getMessage()); }
    }
}
