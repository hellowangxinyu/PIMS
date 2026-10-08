package com.pengyuan.pims.common;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * PG 迁移 v11.9：元数据查询方言助手。
 * 替代 SQLite 专用的 PRAGMA table_info / sqlite_master 查询，
 * 返回结构对齐 SQLite 形态（列名键 "name"），调用方处理逻辑零改动。
 */
public final class DbMeta {

    private DbMeta() {}

    /**
     * 表字段清单（等价 SQLite PRAGMA table_info(table)），返回每行含 name 键。
     * 用于「缺列则 ALTER ADD」的幂等补列逻辑。
     */
    public static List<Map<String, Object>> columns(JdbcTemplate jdbc, String table) {
        if (SqlDdl.isPostgreSQL(jdbc)) {
            return jdbc.queryForList(
                    "SELECT column_name AS name FROM information_schema.columns " +
                    "WHERE table_schema = current_schema() AND table_name = ?", table);
        }
        return jdbc.queryForList("PRAGMA table_info(" + table + ")");
    }

    /** 是否存在某表（等价 sqlite_master 点名查询） */
    public static boolean tableExists(JdbcTemplate jdbc, String table) {
        if (SqlDdl.isPostgreSQL(jdbc)) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = current_schema() AND table_name = ?", Integer.class, table);
            return n != null && n > 0;
        }
        return !jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?", table).isEmpty();
    }

    /** 全部业务表名（等价 sqlite_master 全表列举，返回行含 name 键），排除 PG 系统表 */
    public static List<Map<String, Object>> allTables(JdbcTemplate jdbc) {
        if (SqlDdl.isPostgreSQL(jdbc)) {
            return jdbc.queryForList(
                    "SELECT table_name AS name FROM information_schema.tables " +
                    "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'");
        }
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");
    }

    /** 索引定义文本（等价 sqlite_master 的 sql 字段），用于判断旧索引定义是否含新列 */
    public static String indexDefinition(JdbcTemplate jdbc, String indexName) {
        if (SqlDdl.isPostgreSQL(jdbc)) {
            var rows = jdbc.queryForList("SELECT indexdef AS sql FROM pg_indexes WHERE indexname = ?", indexName);
            return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("sql"));
        }
        var rows = jdbc.queryForList("SELECT sql FROM sqlite_master WHERE type='index' AND name = ?", indexName);
        return rows.isEmpty() ? null : (String) rows.get(0).get("sql");
    }

    /** 是否存在某索引 */
    public static boolean indexExists(JdbcTemplate jdbc, String indexName) {
        if (SqlDdl.isPostgreSQL(jdbc)) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                    Integer.class, indexName);
            return n != null && n > 0;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?",
                Integer.class, indexName);
        return n != null && n > 0;
    }

    /** 按 LIKE 模式列举表名（行含 name 键），操作日志分表索引用 */
    public static List<Map<String, Object>> tablesLike(JdbcTemplate jdbc, String likePattern) {
        if (SqlDdl.isPostgreSQL(jdbc)) {
            return jdbc.queryForList(
                    "SELECT table_name AS name FROM information_schema.tables " +
                    "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' AND table_name LIKE ?",
                    likePattern);
        }
        return jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE ?", likePattern);
    }
}
