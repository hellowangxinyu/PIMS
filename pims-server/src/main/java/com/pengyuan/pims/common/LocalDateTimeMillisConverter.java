package com.pengyuan.pims.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * PG 迁移 v11.9：LocalDateTime ↔ 毫秒 bigint 全局转换器（autoApply）。
 *
 * 背景：全项目时间列存毫秒整数（SQLite 下由 sqlite-jdbc 的 setTimestamp 透明完成）。
 * PG 的列经 SqlDdl 翻译为 BIGINT，Hibernate 默认会把 LocalDateTime 映射为 timestamp 类型写入，
 * 类型不匹配——此转换器让 ORM 层统一写毫秒 long，SQLite 端行为与此前完全一致（同样落毫秒整数），
 * 原生 SQL/JdbcTemplate 直查仍返回毫秒 long，前端 fmtTime 兼容逻辑零改动。
 */
@Converter(autoApply = true)
public class LocalDateTimeMillisConverter implements AttributeConverter<LocalDateTime, Long> {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Override
    public Long convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.atZone(ZONE).toInstant().toEpochMilli();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(dbData), ZONE);
    }
}
