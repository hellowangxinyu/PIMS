package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 异常订单处置表结构初始化（ddl-auto=none 后手动维护）
 * production_order_exception：投入产出比 < 95% 的已完工订单处置记录（按 orderNo 唯一）
 */
@Component
@Order(2)
public class ProductionOrderExceptionSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrderExceptionSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public ProductionOrderExceptionSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS production_order_exception (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  order_no VARCHAR(20) NOT NULL UNIQUE," +
                "  io_ratio DECIMAL(8,2)," +
                "  input_qty DECIMAL(14,3)," +
                "  output_qty DECIMAL(14,3)," +
                "  reason VARCHAR(200)," +
                "  measure VARCHAR(500)," +
                "  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                "  handler VARCHAR(50)," +
                "  remark VARCHAR(500)," +
                "  created_by VARCHAR(50)," +
                "  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  update_time TIMESTAMP," +
                "  closed_time TIMESTAMP" +
                ")");
            log.info("异常订单处置表 production_order_exception 就绪");
        } catch (Exception e) {
            log.warn("异常订单处置表建表失败: {}", e.getMessage());
        }
    }
}
