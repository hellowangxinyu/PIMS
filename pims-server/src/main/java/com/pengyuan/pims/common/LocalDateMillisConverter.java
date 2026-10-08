package com.pengyuan.pims.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * PG 迁移 v11.9：LocalDate ↔ 毫秒 bigint 全局转换器（autoApply）。
 *
 * 背景：DATE 类列（due_date/receipt_date/disbursement_date 等）在 SQLite 中实际存
 * 「当日零点毫秒整数」（sqlite-jdbc setDate 的落库形态），原生 SQL 直接对它做
 * /1000、strftime 运算。PG 端经 SqlDdl 翻译同样为 BIGINT，此转换器保证 ORM 写入形态一致；
 * SQLite 端写入值与此前完全相同（同步等价，无行为变化）。
 */
@Converter(autoApply = true)
public class LocalDateMillisConverter implements AttributeConverter<LocalDate, Long> {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Override
    public Long convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) return null;
        return attribute.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    @Override
    public LocalDate convertToEntityAttribute(Long dbData) {
        return dbData == null ? null
                : java.time.Instant.ofEpochMilli(dbData).atZone(ZONE).toLocalDate();
    }
}
