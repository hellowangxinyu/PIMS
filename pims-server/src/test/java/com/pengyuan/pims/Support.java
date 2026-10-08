package com.pengyuan.pims;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;

/**
 * v8.8 测试基础设施；v11.9 PG 迁移：切真实 PostgreSQL 嵌入式实例（zonky，首次运行自动下载 PG 二进制）。
 * 测的就是生产路径——33 个 SchemaInitializer（DDL 走 SqlDdl 翻译）+ PG 触发器 + 时间转换器
 * 全部在真实 PG 上执行。事实：@SpringBootTest 下 CommandLineRunner 会执行且幂等
 * （InitializerRunsTest 固化此行为）。业务直接调 Service（绕过 Sa-Token 登录态）。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class Support {

    static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.start();
        } catch (Exception e) {
            throw new IllegalStateException("嵌入式 PostgreSQL 启动失败: " + e.getMessage(), e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { PG.close(); } catch (Exception ignored) { }
        }));
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + PG.getPort() + "/postgres");
        reg.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        reg.add("spring.datasource.username", () -> "postgres");
        reg.add("spring.datasource.password", () -> "");
        reg.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        reg.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
    }
}
