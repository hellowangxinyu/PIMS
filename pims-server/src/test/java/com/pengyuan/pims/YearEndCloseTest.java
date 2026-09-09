package com.pengyuan.pims;

import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.entity.VoucherEntry;
import com.pengyuan.pims.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** v8.8 核心测试 5：年结（P0-1 回归——年结凭证必须能落库，docNo/period 齐） */
class YearEndCloseTest extends Support {

    @Autowired VoucherService voucherService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM voucher_entry");
        jdbc.update("DELETE FROM voucher");
        jdbc.update("DELETE FROM account_period");
        for (String[] s : new String[][]{
                {"1001", "库存现金", "DR", "ASSET"}, {"2101", "应付账款", "CR", "LIAB"},
                {"6001", "主营业务收入", "CR", "PL"}, {"6401", "主营业务成本", "DR", "PL"},
                {"3104", "本年利润", "CR", "EQ"}, {"3105", "利润分配", "CR", "EQ"}}) {
            jdbc.update("INSERT OR IGNORE INTO account_subject (code, name, direction, category, status) VALUES (?,?,?,?, 'ENABLED')", s[0], s[1], s[2], s[3]);
        }
    }

    private void posted(LocalDate d, String dr, String cr, String drSub, String crSub) {
        Voucher v = new Voucher();
        v.voucherDate = d;
        v.remark = "年结";
        VoucherEntry e1 = new VoucherEntry();
        e1.subjectCode = drSub; e1.subjectName = drSub; e1.digest = "t"; e1.debit = new BigDecimal(dr); e1.credit = BigDecimal.ZERO;
        VoucherEntry e2 = new VoucherEntry();
        e2.subjectCode = crSub; e2.subjectName = crSub; e2.digest = "t"; e2.debit = BigDecimal.ZERO; e2.credit = new BigDecimal(cr);
        var es = new ArrayList<VoucherEntry>(); es.add(e1); es.add(e2); v.entries = es;
        Voucher saved = voucherService.create(v);
        voucherService.post(saved.id, "tester");
    }

    @Test
    void yearEndGeneratesTransferVoucherWithDocNo() {
        // 2025 全年：收入 100（损益贷方=利润 100 → 3104 有余额）
        posted(LocalDate.of(2025, 6, 1), "100", "100", "1001", "6001");
        // 结平损益 + 逐月结账 1-11 月（无凭证月跳过），12 月结转+结账
        for (int m = 1; m <= 11; m++) {
            String pm = String.format("2025-%02d", m);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM voucher WHERE period=?", Integer.class, pm) > 0) {
                voucherService.transferProfit(pm, "tester");
                // 结转凭证记账
                for (var vrow : jdbc.queryForList("SELECT id, status FROM voucher WHERE period=? AND source='TRANSFER'", pm)) {
                    if (!"POSTED".equals(String.valueOf(vrow.get("status")))) {
                        voucherService.post(((Number) vrow.get("id")).longValue(), "tester");
                    }
                }
                voucherService.closePeriod(pm, "tester");
            }
        }
        // v8.8：损益已在 6 月结转，12 月可能"无需结转"——捕获继续（年结前置校验的是 PL 余额为零）
        try {
            voucherService.transferProfit("2025-12", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("无需结转"), "只允许'无需结转'通过: " + e.getMessage());
        }

        // 年结（P0-1：此前必炸——docNo/period NOT NULL 未设）
        Map<String, Object> result = assertDoesNotThrow(() -> voucherService.yearEndClose("2025", "tester"),
                "年结必须能跑通（P0-1 回归）");
        // 年结凭证已生成且编号合法
        var transferVouchers = jdbc.queryForList(
                "SELECT doc_no, period, status FROM voucher WHERE source='YEAR_END'");
        assertFalse(transferVouchers.isEmpty(), "必须生成年结凭证");
        for (var tv : transferVouchers) {
            assertNotNull(tv.get("doc_no"), "docNo 不能为空（P0-1）");
            assertEquals("2025-12", String.valueOf(tv.get("period")));
        }
        assertEquals("已年结", result.get("status"));
    }
}
