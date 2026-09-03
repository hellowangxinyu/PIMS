package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 配置表初始化器
 * 应用启动时幂等创建 ai_config（key-value 配置表，仿 sys_maintenance 模式）
 * 键：ai_base_url / ai_api_key / ai_model / ai_enabled
 */
@Component
@Order(1)
public class AiSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public AiSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_config (key_name TEXT PRIMARY KEY, value_text TEXT)");
        log.info("AI 配置表 ai_config 已就绪");
    }
}
