package com.pengyuan.pims;

import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.entity.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v8.9 核心测试 7：FieldFilter 遇 LocalDate/LocalDateTime 实体不炸且真的脱敏
 * （回归 v8.3 发现的存量地雷：裸 ObjectMapper 遇日期类型 convertValue 抛异常被 catch 后
 * 返回未脱敏原对象——脱敏静默失效。JavaTimeModule 注册后必须两端都验证。）
 */
class FieldFilterDateTest {

    static class DummyDoc {   // 模拟带日期的领料/采购实体
        public String docNo = "T-001";
        public LocalDate docDate = LocalDate.of(2026, 9, 9);
        public LocalDateTime createTime = LocalDateTime.of(2026, 9, 9, 10, 0);
        public BigDecimal unitPrice = new BigDecimal("9.99");
        public BigDecimal cost = new BigDecimal("99.90");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dateEntityFilteredWithoutCrash() {
        Object out = FieldFilter.filterFields(new DummyDoc(), "unitPrice", "cost");
        assertInstanceOf(Map.class, out, "必须成功转 Map（原实现此处抛异常回退原对象）");
        Map<String, Object> m = (Map<String, Object>) out;
        assertFalse(m.containsKey("unitPrice"), "敏感字段必须被移除");
        assertFalse(m.containsKey("cost"));
        assertEquals("T-001", m.get("docNo"));
        assertNotNull(m.get("docDate"), "日期字段应存在（JavaTimeModule）");
        assertNotNull(m.get("createTime"));
    }

    @Test
    void voucherEntityFiltered() {
        Voucher v = new Voucher();   // 真实实体也验一遍
        v.docNo = "VCH-T";
        v.voucherDate = LocalDate.of(2026, 1, 1);
        v.createTime = LocalDateTime.now();
        v.totalDebit = new BigDecimal("1");
        Object out = FieldFilter.filterFields(v, "totalDebit");
        assertInstanceOf(Map.class, out);
        assertFalse(((Map<String, Object>) out).containsKey("totalDebit"));
    }
}
