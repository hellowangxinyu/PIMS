package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.44 周度会议模块建表：每周议题（weekly_topic）+ 研发进度（rd_progress）。
 * 字段与管委会现行 Excel（涂料管委会周度会议事项2026年.xlsx）逐列对应，便于存量导入。
 * 分类字典 weekly_topic_category：生产/调色/配方/客诉/物料/财务/物流/体系。
 */
@Component
@Order(3)
public class WeeklyMeetingSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WeeklyMeetingSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public WeeklyMeetingSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS weekly_topic (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner VARCHAR(50) NOT NULL,
                    category VARCHAR(20) NOT NULL,
                    content VARCHAR(2000) NOT NULL,
                    result VARCHAR(2000),
                    plan_date DATE,
                    closed_date DATE,
                    created_by VARCHAR(50),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_weekly_topic_plan ON weekly_topic(plan_date)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_weekly_topic_closed ON weekly_topic(closed_date)");
            log.info("每周议题表 weekly_topic 就绪");
        } catch (Exception e) { log.warn("weekly_topic 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rd_progress (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    raise_date DATE,
                    owner VARCHAR(50) NOT NULL,
                    category VARCHAR(20) NOT NULL,
                    content VARCHAR(2000) NOT NULL,
                    result VARCHAR(2000),
                    next_date DATE,
                    closed_date DATE,
                    progress VARCHAR(500),
                    created_by VARCHAR(50),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_rd_progress_closed ON rd_progress(closed_date)");
            log.info("研发进度表 rd_progress 就绪");
        } catch (Exception e) { log.warn("rd_progress 建表失败: {}", e.getMessage()); }

        // 分类字典种子（与 Excel「分类选项」一致，幂等补录）
        String[][] seeds = {{"生产"}, {"调色"}, {"配方"}, {"客诉"}, {"物料"}, {"财务"}, {"物流"}, {"体系"}};
        int added = 0;
        for (int i = 0; i < seeds.length; i++) {
            var exists = jdbc.queryForList(
                    "SELECT id FROM dict_item WHERE type='weekly_topic_category' AND label=?", seeds[i][0]);
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO dict_item (type, label, value, sort_order, enabled, create_time) VALUES (?,?,?,?,1,?)",
                        "weekly_topic_category", seeds[i][0], seeds[i][0], i + 1, System.currentTimeMillis());
                added++;
            }
        }
        if (added > 0) log.info("周度会议分类字典补录 {} 条", added);
    }
}
