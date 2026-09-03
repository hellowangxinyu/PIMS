package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 出纳银行对账两表（v6.3）：账户档案 + 银行流水 */
@Component
public class BankReconciliationSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BankReconciliationSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public BankReconciliationSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS bank_account (
                    id integer PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(50) NOT NULL,
                    account_no VARCHAR(30),
                    bank_name VARCHAR(100),
                    opening_balance NUMERIC(16,2) DEFAULT 0,
                    enabled VARCHAR(10) DEFAULT '1',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS bank_statement (
                    id integer PRIMARY KEY AUTOINCREMENT,
                    account_id BIGINT NOT NULL,
                    tx_date DATE NOT NULL,
                    amount NUMERIC(16,2) NOT NULL,
                    balance NUMERIC(16,2),
                    summary VARCHAR(200),
                    counterparty VARCHAR(100),
                    ref_type VARCHAR(20),
                    ref_id BIGINT,
                    status VARCHAR(10) DEFAULT 'UNMATCHED',
                    import_batch VARCHAR(30),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_bank_stmt_account_date ON bank_statement(account_id, tx_date)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_bank_stmt_status ON bank_statement(account_id, status)");
            log.info("出纳对账表就绪：bank_account / bank_statement");
        } catch (Exception e) {
            log.warn("出纳对账建表失败: {}", e.getMessage());
        }
    }
}
