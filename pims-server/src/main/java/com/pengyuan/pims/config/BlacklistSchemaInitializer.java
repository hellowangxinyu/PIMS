package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 拉黑功能表结构初始化（v5.27，ddl-auto=none 后手动维护）
 * supplier / customer 表新增 blacklisted 列（拉黑标记），幂等安全
 * 必须最先执行（@Order(1)）：JPA 实体已带 blacklisted 字段
 */
@Component
@Order(1)
public class BlacklistSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BlacklistSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public BlacklistSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        for (String table : new String[]{"supplier", "customer"}) {
            try {
                var cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
                boolean hasBlacklisted = cols.stream().anyMatch(c -> "blacklisted".equals(c.get("name")));
                if (!hasBlacklisted) {
                    jdbc.execute("ALTER TABLE " + table + " ADD COLUMN blacklisted BOOLEAN DEFAULT 0");
                    log.info("拉黑表结构：{} 新增 blacklisted 列", table);
                }
            } catch (Exception e) {
                log.warn("拉黑表结构：{} 加列失败（表可能不存在，跳过）: {}", table, e.getMessage());
            }
        }
        log.info("拉黑表结构：blacklisted 列就绪");

        // v5.27：客户合同信息列（法定代表人/开户银行/银行账号/税号，打印销售合同时直接取用）
        try {
            var custCols = jdbc.queryForList("PRAGMA table_info(customer)");
            String[][] contractCols = {
                    {"legal_person", "VARCHAR(50)"},
                    {"bank_name", "VARCHAR(100)"},
                    {"bank_account", "VARCHAR(50)"},
                    {"tax_no", "VARCHAR(30)"},
            };
            for (String[] col : contractCols) {
                boolean has = custCols.stream().anyMatch(c -> col[0].equals(c.get("name")));
                if (!has) {
                    jdbc.execute("ALTER TABLE customer ADD COLUMN " + col[0] + " " + col[1]);
                    log.info("客户表结构：新增 {} 列（合同需方信息）", col[0]);
                }
            }
        } catch (Exception e) {
            log.warn("客户表结构：合同信息加列失败: {}", e.getMessage());
        }
        log.info("客户表结构：合同需方信息列就绪");
    }
}
