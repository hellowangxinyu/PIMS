package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.63 存货计价体系建表：系统配置 / 计价方式变更留痕 / 全月平均月度价格快照
 * sys_config 仿 ai_config 的 key-value 范式（JdbcTemplate 读写）。
 * 计价方式 inventory.costing_method：SPECIFIC 个别计价（默认）/FIFO/MOVING_AVG/MONTHLY_AVG，
 * 准则口径"一经确定不得随意变更"——变更必填原因、留痕、只影响未来出库。
 */
@Component
@Order(3)
public class CostingSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CostingSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public CostingSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_config (
                    key_name TEXT PRIMARY KEY,
                    value_text TEXT
                )
                """);
            log.info("系统配置表 sys_config 就绪");
        } catch (Exception e) { log.warn("sys_config 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS costing_method_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    old_value VARCHAR(20),
                    new_value VARCHAR(20) NOT NULL,
                    reason VARCHAR(500) NOT NULL,
                    changed_by VARCHAR(50),
                    change_time TIMESTAMP
                )
                """);
            log.info("计价变更留痕表 costing_method_log 就绪");
        } catch (Exception e) { log.warn("costing_method_log 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS costing_monthly_price (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    period VARCHAR(10) NOT NULL,
                    material_code VARCHAR(20) NOT NULL,
                    price DECIMAL(14,4) NOT NULL,
                    create_time TIMESTAMP,
                    UNIQUE(period, material_code)
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_costing_monthly_period ON costing_monthly_price(period)");
            log.info("全月平均价格快照表 costing_monthly_price 就绪");
        } catch (Exception e) { log.warn("costing_monthly_price 建表失败: {}", e.getMessage()); }

        // 种子：默认个别计价（= 系统既有口径，上线零行为变化）
        try {
            var exists = jdbc.queryForList("SELECT key_name FROM sys_config WHERE key_name = 'inventory.costing_method'");
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO sys_config (key_name, value_text) VALUES ('inventory.costing_method', 'SPECIFIC')");
                log.info("计价方式默认值 SPECIFIC 已写入");
            }
        } catch (Exception e) { log.warn("计价方式种子写入失败: {}", e.getMessage()); }
    }
}
