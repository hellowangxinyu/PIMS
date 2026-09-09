package com.pengyuan.pims;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v8.9：CommandLineRunner 在 @SpringBootTest 下会执行（三次复查纠正了 Support 旧注释的错误说法）。
 * 本用例固化这个事实：36 个初始化器在测试库上完整跑一遍且幂等（种子角色/字典/科目就位）——
 * 同时也是"初始化器在 JPA 建表后的空库上可安全执行"的回归。
 */
class InitializerRunsTest extends Support {

    @Autowired JdbcTemplate jdbc;

    @Test
    void initializersSeedData() {
        Integer roles = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role", Integer.class);
        Integer dicts = jdbc.queryForObject("SELECT COUNT(*) FROM dict_item", Integer.class);
        Integer subjects = jdbc.queryForObject("SELECT COUNT(*) FROM account_subject", Integer.class);
        assertTrue(roles != null && roles > 5, "种子角色应就位，实际=" + roles);
        assertTrue(dicts != null && dicts > 30, "种子字典应就位，实际=" + dicts);
        assertTrue(subjects != null && subjects > 10, "种子科目应就位，实际=" + subjects);
    }
}
