package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.66 物流运费模块建表：shipping_log（每次发货的运费，按销售订单归集成本）
 * 承运商字典 logistics_company 种子（页面可维护）；ddl-auto=none 幂等 DDL。
 */
@Component
@Order(3)
public class ShippingSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ShippingSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public ShippingSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS shipping_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    outbound_doc_no VARCHAR(30),
                    sales_order_no VARCHAR(30) NOT NULL,
                    customer_name VARCHAR(50),
                    carrier VARCHAR(50),
                    tracking_no VARCHAR(50),
                    freight DECIMAL(12,2) NOT NULL,
                    borne VARCHAR(20) NOT NULL DEFAULT 'COMPANY',
                    ship_date DATE,
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_shipping_order ON shipping_log(sales_order_no)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_shipping_outbound ON shipping_log(outbound_doc_no)");
            log.info("物流运费表 shipping_log 就绪");
        } catch (Exception e) { log.warn("shipping_log 建表失败: {}", e.getMessage()); }

        // 承运商字典种子（只从无到有，页面数据字典可维护）
        String[][] seeds = {
            {"logistics_company", "自送", "SELF", "1"},
            {"logistics_company", "德邦", "DEPPON", "2"},
            {"logistics_company", "安能", "ANE", "3"},
            {"logistics_company", "顺丰", "SF", "4"},
            {"logistics_company", "跨越", "KYE", "5"},
            {"logistics_company", "其他", "OTHER", "6"},
        };
        int added = 0;
        for (String[] s : seeds) {
            var exists = jdbc.queryForList("SELECT id FROM dict_item WHERE type = ? AND value = ?", s[0], s[2]);
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO dict_item (type, label, value, sort_order, enabled, create_time) VALUES (?,?,?,?,1,?)",
                        s[0], s[1], s[2], Integer.parseInt(s[3]), System.currentTimeMillis());
                added++;
            }
        }
        if (added > 0) log.info("承运商字典种子补录 {} 条", added);
    }
}
