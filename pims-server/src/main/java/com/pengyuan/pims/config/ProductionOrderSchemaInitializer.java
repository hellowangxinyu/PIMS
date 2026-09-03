package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产订单表结构初始化（v5.27，ddl-auto=none 后手动维护）
 * production_order 表新增 sales_order_no 列（来源销售订单，销售订单一键转生产时记录）
 * 必须最先执行（@Order(1)）：JPA 实体已带 salesOrderNo 字段
 */
@Component
@Order(1)
public class ProductionOrderSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrderSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public ProductionOrderSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(production_order)");
            boolean has = cols.stream().anyMatch(c -> "sales_order_no".equals(c.get("name")));
            if (!has) {
                jdbc.execute("ALTER TABLE production_order ADD COLUMN sales_order_no VARCHAR(20)");
                log.info("生产订单表结构：新增 sales_order_no 列（来源销售订单）");
            }
        } catch (Exception e) {
            log.warn("生产订单表结构：sales_order_no 加列失败: {}", e.getMessage());
        }
        log.info("生产订单表结构：sales_order_no 列就绪");

        // v5.27：委外订单同字段（销售订单一键转委外）
        try {
            var ooCols = jdbc.queryForList("PRAGMA table_info(outsource_order)");
            boolean hasOo = ooCols.stream().anyMatch(c -> "sales_order_no".equals(c.get("name")));
            if (!hasOo) {
                jdbc.execute("ALTER TABLE outsource_order ADD COLUMN sales_order_no VARCHAR(20)");
                log.info("委外订单表结构：新增 sales_order_no 列（来源销售订单）");
            }
        } catch (Exception e) {
            log.warn("委外订单表结构：sales_order_no 加列失败: {}", e.getMessage());
        }
        log.info("委外订单表结构：sales_order_no 列就绪");

        // v5.27：排产顺序号（排产时分配，排产中心按此排序）
        try {
            var moCols = jdbc.queryForList("PRAGMA table_info(production_order)");
            boolean hasSeq = moCols.stream().anyMatch(c -> "schedule_seq".equals(c.get("name")));
            if (!hasSeq) {
                jdbc.execute("ALTER TABLE production_order ADD COLUMN schedule_seq INTEGER");
                log.info("生产订单表结构：新增 schedule_seq 列（排产顺序）");
            }
        } catch (Exception e) {
            log.warn("生产订单表结构：schedule_seq 加列失败: {}", e.getMessage());
        }
        try {
            var ooCols = jdbc.queryForList("PRAGMA table_info(outsource_order)");
            boolean hasSeq = ooCols.stream().anyMatch(c -> "schedule_seq".equals(c.get("name")));
            if (!hasSeq) {
                jdbc.execute("ALTER TABLE outsource_order ADD COLUMN schedule_seq INTEGER");
                log.info("委外订单表结构：新增 schedule_seq 列（排产顺序）");
            }
        } catch (Exception e) {
            log.warn("委外订单表结构：schedule_seq 加列失败: {}", e.getMessage());
        }
        log.info("排产顺序：schedule_seq 列就绪");
    }
}
