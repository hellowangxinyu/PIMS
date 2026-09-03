package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 配方变更日志表 + 配方版本生效日期列（v6.3 第二批） */
@Component
public class RecipeChangeLogSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RecipeChangeLogSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public RecipeChangeLogSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS recipe_change_log (
                    id integer PRIMARY KEY AUTOINCREMENT,
                    recipe_id BIGINT,
                    recipe_no VARCHAR(30),
                    product_name VARCHAR(100),
                    version_id BIGINT,
                    version_no VARCHAR(10),
                    action VARCHAR(20),
                    detail VARCHAR(1000),
                    operator VARCHAR(50),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_recipe_change_log ON recipe_change_log(recipe_id, create_time)");
            var cols = jdbc.queryForList("PRAGMA table_info(recipe_version)");
            if (!cols.stream().anyMatch(c -> "effective_date".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE recipe_version ADD COLUMN effective_date DATE");
                log.info("配方版本：新增 effective_date 列");
            }
            log.info("配方变更日志表 recipe_change_log 就绪");
        } catch (Exception e) {
            log.warn("recipe_change_log 建表失败: {}", e.getMessage());
        }
    }
}
