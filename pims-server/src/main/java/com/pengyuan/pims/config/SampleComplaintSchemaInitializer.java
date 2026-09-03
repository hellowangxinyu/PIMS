package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.53 销售P2两件套建表：
 * 1) 打样/样品管理 sample_request（申请→调色→寄样→反馈→转单/未成交，联动研发进度 rd_progress）
 * 2) 客户投诉/质量反馈 customer_complaint（登记→原因→处理→关闭，关联批次可追溯出入库流水）
 */
@Component
@Order(3)
public class SampleComplaintSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleComplaintSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SampleComplaintSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sample_request (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sample_no VARCHAR(20) NOT NULL UNIQUE,
                    customer_id BIGINT,
                    customer_name VARCHAR(100) NOT NULL,
                    material_code VARCHAR(30),
                    material_desc VARCHAR(500) NOT NULL,
                    qty DECIMAL(14,3) DEFAULT 1,
                    unit VARCHAR(10) DEFAULT 'kg',
                    applicant VARCHAR(50),
                    apply_date DATE,
                    status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
                    colorist VARCHAR(50),
                    color_note VARCHAR(500),
                    adjust_count INTEGER DEFAULT 0,
                    send_date DATE,
                    express_no VARCHAR(50),
                    feedback_content VARCHAR(1000),
                    feedback_date DATE,
                    won_order_no VARCHAR(20),
                    loss_reason VARCHAR(200),
                    rd_progress_id BIGINT,
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sample_cust ON sample_request(customer_id)");
            log.info("打样表 sample_request 就绪");
        } catch (Exception e) { log.warn("sample_request 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS customer_complaint (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    complaint_no VARCHAR(20) NOT NULL UNIQUE,
                    customer_id BIGINT,
                    customer_name VARCHAR(100) NOT NULL,
                    material_code VARCHAR(30),
                    material_name VARCHAR(100),
                    batch_no VARCHAR(30),
                    qc_doc_no VARCHAR(20),
                    sales_order_no VARCHAR(20),
                    complaint_date DATE,
                    category VARCHAR(20),
                    description VARCHAR(2000) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                    cause VARCHAR(1000),
                    action VARCHAR(1000),
                    handler VARCHAR(50),
                    created_by VARCHAR(50),
                    resolve_date DATE,
                    close_date DATE,
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_complaint_cust ON customer_complaint(customer_id)");
            // v5.60 制单人：存量表补 created_by 列（新表建表 SQL 已含）
            try {
                boolean hasBy = jdbc.queryForList("PRAGMA table_info(customer_complaint)").stream()
                        .anyMatch(c -> "created_by".equalsIgnoreCase(String.valueOf(c.get("name"))));
                if (!hasBy) jdbc.execute("ALTER TABLE customer_complaint ADD COLUMN created_by VARCHAR(50)");
            } catch (Exception ex) { log.warn("customer_complaint 补列失败: {}", ex.getMessage()); }
            log.info("客户投诉表 customer_complaint 就绪");
        } catch (Exception e) { log.warn("customer_complaint 建表失败: {}", e.getMessage()); }
    }
}
