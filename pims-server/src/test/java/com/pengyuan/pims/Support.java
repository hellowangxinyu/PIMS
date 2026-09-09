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
 * 事实（v8.9 三次复查纠正）：@SpringBootTest 下 CommandLineRunner 会执行——36 个初始化器在测试库
 * 完整跑一遍且幂等（InitializerRunsTest 固化此行为）。实体表由 ddl-auto=create-drop 先建，
 * 初始化器随后补建非实体表与种子。业务直接调 Service（绕过 Sa-Token 登录态）。
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
