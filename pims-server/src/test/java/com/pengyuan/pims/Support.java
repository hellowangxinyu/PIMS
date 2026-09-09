package com.pengyuan.pims;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * v8.8 测试基础设施：每个测试类独立临时 SQLite 库 + JPA 建表（ddl-auto=create-drop）。
 * 测试不跑 CommandLineRunner（@SpringBootTest 不触发），实体表由 Hibernate 按实体自动创建；
 * 业务直接调 Service（绕过 Sa-Token 登录态，Service 层不依赖会话）。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class Support {

    static Path testDb;

    static {
        try {
            testDb = Files.createTempDirectory("pims-test").resolve("test.db");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry reg) {
        String url = "jdbc:sqlite:" + testDb.toString().replace("\\", "/")
                + "?journal_mode=WAL&busy_timeout=5000&synchronous=OFF&foreign_keys=ON";
        reg.add("spring.datasource.url", () -> url);
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        reg.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
    }
}
