package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.67 任务督办建表：task（主表）+ task_progress（进度汇报日志）
 */
@Component
@Order(3)
public class TaskSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public TaskSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS task (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(25) NOT NULL UNIQUE,
                    title VARCHAR(200) NOT NULL,
                    description VARCHAR(2000),
                    owner VARCHAR(50) NOT NULL,
                    collaborators VARCHAR(500),
                    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    due_date DATE,
                    completed_at TIMESTAMP,
                    completed_note VARCHAR(1000),
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_task_status ON task(status)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_task_owner ON task(owner)");
            log.info("任务督办表 task 就绪");
        } catch (Exception e) { log.warn("task 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS task_progress (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id BIGINT NOT NULL,
                    reporter VARCHAR(50) NOT NULL,
                    content VARCHAR(1000),
                    action_type VARCHAR(20) NOT NULL,
                    create_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_task_progress_task ON task_progress(task_id)");
            log.info("任务进度表 task_progress 就绪");
        } catch (Exception e) { log.warn("task_progress 建表失败: {}", e.getMessage()); }
    }
}
