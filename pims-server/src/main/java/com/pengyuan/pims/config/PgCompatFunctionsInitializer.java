package com.pengyuan.pims.config;

import com.pengyuan.pims.common.SqlDdl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v12.0：PostgreSQL 兼容函数初始化器（PG 为唯一数据库，启动必建）。
 *
 * 背景：全项目原生 SQL 大量使用 strftime/date/datetime/julianday（103+ 处，
 * 17 个文件）。在 PG 中创建同名兼容函数，语义对齐「毫秒时间戳 + 'unixepoch' + '+8 hours'」
 * 的北京时间墙钟口径，使这些 SQL 零改动运行。
 *
 * 注意：'+8 hours' 的实现 = (to_timestamp(secs) AT TIME ZONE 'UTC') + interval '8 hours'，
 * 即 UTC 秒数直接加 8 小时得到北京墙钟（不依赖会话时区）。
 */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)   // 必须先于一切建表器：翻译后的 DDL 默认值可能引用 now_ms()
public class PgCompatFunctionsInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PgCompatFunctionsInitializer.class);
    private final JdbcTemplate jdbc;

    public PgCompatFunctionsInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 当前毫秒（翻译后 DDL 的 DEFAULT 当前时间）
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION now_ms() RETURNS BIGINT AS $$
                SELECT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT
            $$ LANGUAGE sql
            """);

        // 数字秒 → 北京墙钟 timestamp（+8 hours 用 UTC 直加，不依赖会话时区）
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION bj_ts(secs DOUBLE PRECISION, plus8 BOOLEAN) RETURNS TIMESTAMP AS $$
                SELECT CASE WHEN plus8
                    THEN (to_timestamp(secs) AT TIME ZONE 'UTC') + interval '8 hours'
                    ELSE (to_timestamp(secs) AT TIME ZONE 'Asia/Shanghai')
                END
            $$ LANGUAGE sql
            """);

        // 格式映射：SQLite strftime 的 % 标记 → PG to_char 模板
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION fmt_map(fmt TEXT) RETURNS TEXT AS $$
                SELECT replace(replace(replace(replace(replace(replace(replace(replace(replace($1,
                    '%Y','YYYY'), '%m','MM'), '%d','DD'), '%H','HH24'), '%M','MI'), '%S','SS'),
                    '%W',''), '%w',''), '%%','%')
            $$ LANGUAGE sql IMMUTABLE
            """);


        // strftime(fmt, 数字秒, VARIADIC 修饰)——'unixepoch' 为标识（数字一律按 epoch 处理），'+8 hours' 北京时间
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION strftime(fmt TEXT, secs BIGINT, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
            DECLARE plus8 BOOLEAN := FALSE; m TEXT; extra INTERVAL := INTERVAL '0';
            BEGIN
                FOREACH m IN ARRAY mods LOOP
                    IF m = '+8 hours' THEN plus8 := TRUE;
                    ELSIF m LIKE '%months%' OR m LIKE '%days%' THEN extra := extra + m::INTERVAL; END IF;
                END LOOP;
                IF fmt = '%s' THEN RETURN (EXTRACT(EPOCH FROM bj_ts(secs::DOUBLE PRECISION, plus8) + extra))::TEXT; END IF;
                RETURN to_char(bj_ts(secs::DOUBLE PRECISION, plus8) + extra, fmt_map(fmt));
            END $$ LANGUAGE plpgsql
            """);
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION strftime(fmt TEXT, secs DOUBLE PRECISION, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
            DECLARE plus8 BOOLEAN := FALSE; m TEXT; extra INTERVAL := INTERVAL '0';
            BEGIN
                FOREACH m IN ARRAY mods LOOP
                    IF m = '+8 hours' THEN plus8 := TRUE;
                    ELSIF m LIKE '%months%' OR m LIKE '%days%' THEN extra := extra + m::INTERVAL; END IF;
                END LOOP;
                IF fmt = '%s' THEN RETURN (EXTRACT(EPOCH FROM bj_ts(secs, plus8) + extra))::TEXT; END IF;
                RETURN to_char(bj_ts(secs, plus8) + extra, fmt_map(fmt));
            END $$ LANGUAGE plpgsql
            """);

        // strftime(fmt, 'now'|ISO文本, VARIADIC 修饰)——文本按北京墙钟解析（取 epoch 数值即等价 SQLite 的 UTC 解释）
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION strftime(fmt TEXT, val TEXT, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
            DECLARE
                plus8 BOOLEAN := FALSE; m TEXT; extra INTERVAL := INTERVAL '0';
                secs DOUBLE PRECISION;
            BEGIN
                FOREACH m IN ARRAY mods LOOP
                    IF m = '+8 hours' THEN plus8 := TRUE;
                    ELSIF m LIKE '%months%' OR m LIKE '%days%' THEN extra := extra + m::INTERVAL; END IF;
                END LOOP;
                IF lower(val) = 'now' THEN
                    secs := EXTRACT(EPOCH FROM now()) + CASE WHEN plus8 THEN 28800 ELSE 0 END;
                ELSE
                    BEGIN
                        secs := EXTRACT(EPOCH FROM to_timestamp(val, 'YYYY-MM-DD HH24:MI:SS'));
                    EXCEPTION WHEN OTHERS THEN
                        secs := EXTRACT(EPOCH FROM to_timestamp(val, 'YYYY-MM-DD'));
                    END;
                END IF;
                IF fmt = '%s' THEN RETURN secs::TEXT; END IF;
                IF lower(val) = 'now' THEN
                    RETURN to_char(bj_ts(secs, TRUE) + extra, fmt_map(fmt));
                END IF;
                -- 文本输入：格式化回其墙钟值（SQLite 对文本输入做透传格式化）
                RETURN to_char(to_timestamp(secs + CASE WHEN plus8 THEN 28800 ELSE 0 END) + extra, fmt_map(fmt));
            END $$ LANGUAGE plpgsql
            """);


        // 显式 2 参重载：unknown 字面量两参调用（strftime('%s','now')）无法自动落进 VARIADIC 重载
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION strftime(fmt TEXT, secs BIGINT) RETURNS TEXT AS $$
                SELECT strftime(fmt, secs, VARIADIC ARRAY[]::TEXT[])
            $$ LANGUAGE sql
            """);
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION strftime(fmt TEXT, secs DOUBLE PRECISION) RETURNS TEXT AS $$
                SELECT strftime(fmt, secs, VARIADIC ARRAY[]::TEXT[])
            $$ LANGUAGE sql
            """);
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION strftime(fmt TEXT, val TEXT) RETURNS TEXT AS $$
                SELECT strftime(fmt, val, VARIADIC ARRAY[]::TEXT[])
            $$ LANGUAGE sql
            """);

        // date(...)/datetime(...) = strftime 的日期/日期时间快捷形
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION date(secs BIGINT, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
                SELECT strftime('%Y-%m-%d', secs, VARIADIC mods)
            $$ LANGUAGE sql
            """);
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION date(secs DOUBLE PRECISION, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
                SELECT strftime('%Y-%m-%d', secs, VARIADIC mods)
            $$ LANGUAGE sql
            """);
                jdbc.execute("""
            CREATE OR REPLACE FUNCTION date(val TEXT, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
                SELECT strftime('%Y-%m-%d', val, VARIADIC mods)
            $$ LANGUAGE sql
            """);
jdbc.execute("""
            CREATE OR REPLACE FUNCTION datetime(val TEXT, VARIADIC mods TEXT[]) RETURNS TEXT AS $$
                SELECT strftime('%Y-%m-%d %H:%M:%S', val, VARIADIC mods)
            $$ LANGUAGE sql
            """);

        // julianday('now','+8 hours') / julianday(date文本)——库存账龄分层专用
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION julianday(val TEXT, VARIADIC mods TEXT[]) RETURNS DOUBLE PRECISION AS $$
            DECLARE
                plus8 BOOLEAN := FALSE; m TEXT;
                secs DOUBLE PRECISION;
            BEGIN
                FOREACH m IN ARRAY mods LOOP
                    IF m = '+8 hours' THEN plus8 := TRUE; END IF;
                END LOOP;
                IF lower(val) = 'now' THEN
                    secs := EXTRACT(EPOCH FROM now()) + CASE WHEN plus8 THEN 28800 ELSE 0 END;
                ELSE
                    BEGIN
                        secs := EXTRACT(EPOCH FROM to_timestamp(val, 'YYYY-MM-DD HH24:MI:SS'));
                    EXCEPTION WHEN OTHERS THEN
                        secs := EXTRACT(EPOCH FROM to_timestamp(val, 'YYYY-MM-DD'));
                    END;
                    secs := secs - 28800;  -- 墙钟 → UTC epoch（SQLite 对日期文本按 UTC 零点解释）
                END IF;
                RETURN secs / 86400.0 + 2440587.5;
            END $$ LANGUAGE plpgsql
            """);

        // instr(x, y)（SQLite 形态，单号取序号 5 处依赖）——public 新函数名，无内置冲突
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION instr(string TEXT, sub TEXT) RETURNS INTEGER AS $$
                SELECT strpos(string, sub)
            $$ LANGUAGE sql
            """);

        createGroupConcat();

        log.info("PostgreSQL 兼容函数就绪：now_ms/strftime/date/julianday/group_concat");
    }

    private void createCastIdempotent(JdbcTemplate jdbc, String srcType) {
        try { jdbc.execute("DROP CAST IF EXISTS (" + srcType + " AS BIGINT)"); } catch (Exception ignored) { }
        jdbc.execute("CREATE CAST (" + srcType + " AS BIGINT) WITH FUNCTION to_bigint_ms(" + srcType + ") AS IMPLICIT");
    }

    /** GROUP_CONCAT 聚合（SQLite 形态，仅 1 处库存台账查询使用，支持 DISTINCT） */
    private void createGroupConcat() {
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION group_concat_sfunc(state TEXT, val TEXT, delim TEXT) RETURNS TEXT AS $$
                SELECT CASE WHEN state IS NULL THEN val
                            WHEN val IS NULL THEN state
                            ELSE state || delim || val END
            $$ LANGUAGE sql
            """);
        // 单参数重载（SQLite GROUP_CONCAT(x) 默认逗号分隔形态，库存批次视图库位名聚合使用）
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION group_concat_sfunc1(state TEXT, val TEXT) RETURNS TEXT AS $$
                SELECT CASE WHEN state IS NULL THEN val
                            WHEN val IS NULL THEN state
                            ELSE state || ',' || val END
            $$ LANGUAGE sql
            """);
        try { jdbc.execute("DROP AGGREGATE IF EXISTS group_concat(TEXT)"); } catch (Exception ignored) { }
        jdbc.execute("""
            CREATE AGGREGATE group_concat(TEXT) (SFUNC = group_concat_sfunc1, STYPE = TEXT)
            """);
        // PG 无 CREATE OR REPLACE AGGREGATE——同库重启会撞已存在，先 DROP 保幂等
        try { jdbc.execute("DROP AGGREGATE IF EXISTS group_concat(TEXT, TEXT)"); } catch (Exception ignored) { }
        jdbc.execute("""
            CREATE AGGREGATE group_concat(TEXT, TEXT) (SFUNC = group_concat_sfunc, STYPE = TEXT)
            """);
    }
}
