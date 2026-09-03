package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 价格政策表（v6.3 第二批）：阶梯价 + 物料/大类两级档位 */
@Component
public class PricePolicySchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PricePolicySchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public PricePolicySchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS price_policy (
                    id integer PRIMARY KEY AUTOINCREMENT,
                    material_code VARCHAR(30),
                    material_category VARCHAR(5),
                    min_qty NUMERIC(14,3) DEFAULT 1,
                    unit_price NUMERIC(14,2) NOT NULL,
                    effective_date DATE NOT NULL,
                    expiry_date DATE,
                    status VARCHAR(10) DEFAULT 'ENABLED',
                    remark VARCHAR(200),
                    created_by VARCHAR(50),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_price_policy_material ON price_policy(material_code, status)");
            log.info("价格政策表 price_policy 就绪");
        } catch (Exception e) {
            log.warn("price_policy 建表失败: {}", e.getMessage());
        }
    }
}
