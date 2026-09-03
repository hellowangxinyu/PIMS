package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.36 财务扩展模块建表：发票 / 费用 / 预收预付
 * ddl-auto=none，所有新表新列必须在此手写幂等 DDL。
 * production_order 补人工/制费两列（成本核算手工补录项）。
 */
@Component
@Order(3)
public class FinanceExtSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FinanceExtSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public FinanceExtSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS invoice (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    invoice_no VARCHAR(30),
                    direction VARCHAR(20) NOT NULL DEFAULT 'OUTPUT',
                    partner_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
                    partner_id BIGINT,
                    partner_name VARCHAR(100),
                    partner_tax_no VARCHAR(30),
                    amount DECIMAL(14,2) NOT NULL DEFAULT 0,
                    tax_rate INTEGER DEFAULT 13,
                    tax_amount DECIMAL(14,2) DEFAULT 0,
                    total_amount DECIMAL(14,2) DEFAULT 0,
                    invoice_date DATE,
                    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                    flush_doc_no VARCHAR(20),
                    ref_order_no VARCHAR(30),
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("发票表 invoice 就绪");
        } catch (Exception e) { log.warn("发票表建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS expense (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    direction VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
                    expense_type VARCHAR(50) NOT NULL,
                    amount DECIMAL(14,2) NOT NULL,
                    occur_date DATE,
                    method VARCHAR(20) DEFAULT 'BANK',
                    partner VARCHAR(100),
                    handler VARCHAR(50),
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("费用表 expense 就绪");
        } catch (Exception e) { log.warn("费用表建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS advance_payment (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    direction VARCHAR(20) NOT NULL DEFAULT 'RECEIVE',
                    partner_id BIGINT,
                    partner_name VARCHAR(100),
                    amount DECIMAL(14,2) NOT NULL,
                    used_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
                    method VARCHAR(20) DEFAULT 'BANK',
                    pay_date DATE,
                    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("预收预付表 advance_payment 就绪");
        } catch (Exception e) { log.warn("预收预付表建表失败: {}", e.getMessage()); }

        // production_order 补人工/制费列（成本核算手工补录）
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(production_order)");
            if (!cols.stream().anyMatch(c -> "labor_fee".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE production_order ADD COLUMN labor_fee DECIMAL(12,2) DEFAULT 0");
            }
            if (!cols.stream().anyMatch(c -> "overhead_fee".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE production_order ADD COLUMN overhead_fee DECIMAL(12,2) DEFAULT 0");
            }
        } catch (Exception e) { log.warn("production_order 补成本列失败: {}", e.getMessage()); }

        ensureExpenseDict();
    }

    /** 费用类型字典种子（存量库幂等补录，缺哪条补哪条） */
    private void ensureExpenseDict() {
        String[][] seeds = {
            {"expense_type", "运费", "FREIGHT", "1"},
            {"expense_type", "包装费", "PACKAGING", "2"},
            {"expense_type", "水电费", "UTILITIES", "3"},
            {"expense_type", "办公费", "OFFICE", "4"},
            {"expense_type", "差旅费", "TRAVEL", "5"},
            {"expense_type", "维修费", "MAINTENANCE", "6"},
            {"expense_type", "检测费", "TESTING", "7"},
            {"expense_type", "其他支出", "OTHER", "8"},
            {"other_income_type", "废料回收", "SCRAP_SALE", "1"},
            {"other_income_type", "租金收入", "RENT", "2"},
            {"other_income_type", "利息收入", "INTEREST", "3"},
            {"other_income_type", "政府补贴", "SUBSIDY", "4"},
            {"other_income_type", "其他收入", "OTHER", "5"},
        };
        int added = 0;
        for (String[] s : seeds) {
            var exists = jdbc.queryForList(
                    "SELECT id FROM dict_item WHERE type = ? AND value = ?", s[0], s[2]);
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO dict_item (type, label, value, sort_order, enabled, create_time) VALUES (?,?,?,?,1,?)",
                        s[0], s[1], s[2], Integer.parseInt(s[3]), System.currentTimeMillis());
                added++;
            }
        }
        if (added > 0) log.info("财务费用类型字典补录 {} 条", added);
    }
}
