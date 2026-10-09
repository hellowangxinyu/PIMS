package com.pengyuan.pims.common;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * v12.0：元数据查询助手（PostgreSQL 唯一数据库）。
 * 统一走 information_schema / pg_indexes，返回结构保持每行含 "name" 键，调用方零改动。
 */
public final class DbMeta {

    private DbMeta() {}

    /**
     * 表字段清单（等价原 PRAGMA table_info(table)），返回每行含 name 键。
     * 用于「缺列则 ALTER ADD」的幂等补列逻辑。
     */
    public static List<Map<String, Object>> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList(
                "SELECT column_name AS name FROM information_schema.columns " +
                "WHERE table_schema = current_schema() AND table_name = ?", table);
    }

    /** 是否存在某表 */
    public static boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = current_schema() AND table_name = ?", Integer.class, table);
        return n != null && n > 0;
    }

    /** 全部业务表名（返回行含 name 键），PG 系统表不在 current_schema 内天然排除 */
    public static List<Map<String, Object>> allTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name AS name FROM information_schema.tables " +
                "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'");
    }

    /** 索引定义文本（pg_indexes.indexdef），用于判断旧索引定义是否含新列 */
    public static String indexDefinition(JdbcTemplate jdbc, String indexName) {
        var rows = jdbc.queryForList("SELECT indexdef AS sql FROM pg_indexes WHERE indexname = ?", indexName);
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("sql"));
    }

    /** 是否存在某索引 */
    public static boolean indexExists(JdbcTemplate jdbc, String indexName) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                Integer.class, indexName);
        return n != null && n > 0;
    }

    /** 按 LIKE 模式列举表名（行含 name 键），操作日志分表索引用 */
    public static List<Map<String, Object>> tablesLike(JdbcTemplate jdbc, String likePattern) {
        return jdbc.queryForList(
                "SELECT table_name AS name FROM information_schema.tables " +
                "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' AND table_name LIKE ?",
                likePattern);
    }
}
