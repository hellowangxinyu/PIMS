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

import static org.junit.jupiter.api.Assertions.*;

/** v8.8 核心测试 4：期间结账闭环（结账锁定 → 期间禁改 → 反结账解锁） */
class PeriodCloseTest extends Support {

    @Autowired VoucherService voucherService;
    @Autowired com.pengyuan.pims.service.InventoryService inventoryService;
    @Autowired com.pengyuan.pims.service.FinanceService financeService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM voucher_entry");
        jdbc.update("DELETE FROM voucher");
        jdbc.update("DELETE FROM account_period WHERE period = '2026-08'");
        jdbc.update("DELETE FROM account_period WHERE period = ?", java.time.LocalDate.now().toString().substring(0, 7));
    }

    private Voucher makePostedVoucher(LocalDate d, String dr, String cr, String drSub, String crSub) {
        Voucher v = new Voucher();
        v.voucherDate = d;
        v.remark = "结账测试";
        VoucherEntry e1 = new VoucherEntry();
        e1.subjectCode = drSub; e1.subjectName = drSub; e1.digest = "t"; e1.debit = new BigDecimal(dr); e1.credit = BigDecimal.ZERO;
        VoucherEntry e2 = new VoucherEntry();
        e2.subjectCode = crSub; e2.subjectName = crSub; e2.digest = "t"; e2.debit = BigDecimal.ZERO; e2.credit = new BigDecimal(cr);
        var es = new ArrayList<VoucherEntry>(); es.add(e1); es.add(e2); v.entries = es;
        Voucher saved = voucherService.create(v);
        return voucherService.post(saved.id, "tester");
    }

    @Test
    void closeLocksPeriodAndReopenUnlocks() {
        // 科目：现金科目 1001 + 损益类 6001（结转用）+ 2101 应付
        for (String[] s : new String[][]{
                {"1001", "库存现金", "DR", "ASSET"}, {"2101", "应付账款", "CR", "LIAB"},
                {"6001", "主营业务收入", "CR", "PL"}, {"6401", "主营业务成本", "DR", "PL"}}) {
            jdbc.update("INSERT OR IGNORE INTO account_subject (code, name, direction, category, status) VALUES (?,?,?,?, 'ENABLED')", s[0], s[1], s[2], s[3]);
        }
        // 2026-08 一张已记账凭证（收入）
        makePostedVoucher(LocalDate.of(2026, 8, 10), "100", "100", "1001", "6001");

        // 结转损益（6001 余额结平）
        // v8.8：transferProfit 生成 DRAFT 结转凭证——须记账后才能结账（结账前置防线）
        voucherService.transferProfit("2026-08", "tester");
        for (var vrow : jdbc.queryForList("SELECT id FROM voucher WHERE period='2026-08' AND source='TRANSFER' AND status='DRAFT'")) {
            voucherService.post(((Number) vrow.get("id")).longValue(), "tester");
        }
        // 结账
        assertDoesNotThrow(() -> voucherService.closePeriod("2026-08", "tester"));

        // 结账后该期间禁改：新增凭证被拦
        Exception ex = assertThrows(Exception.class, () -> {
            Voucher v = new Voucher();
            v.voucherDate = LocalDate.of(2026, 8, 20);
            v.remark = "结账后";
            VoucherEntry e1 = new VoucherEntry();
            e1.subjectCode = "1001"; e1.subjectName = "1001"; e1.digest = "t"; e1.debit = new BigDecimal("1"); e1.credit = BigDecimal.ZERO;
            VoucherEntry e2 = new VoucherEntry();
            e2.subjectCode = "2101"; e2.subjectName = "2101"; e2.digest = "t"; e2.debit = BigDecimal.ZERO; e2.credit = new BigDecimal("1");
            var es = new ArrayList<VoucherEntry>(); es.add(e1); es.add(e2); v.entries = es;
            voucherService.create(v);
        });
        assertTrue(ex.getMessage().contains("已结账"), "结账期间必须禁改: " + ex.getMessage());

        // 反结账 → 解锁
        voucherService.reopenPeriod("2026-08");
        assertDoesNotThrow(() -> {
            Voucher v = new Voucher();
            v.voucherDate = LocalDate.of(2026, 8, 21);
            v.remark = "反结账后";
            VoucherEntry e1 = new VoucherEntry();
            e1.subjectCode = "1001"; e1.subjectName = "1001"; e1.digest = "t"; e1.debit = new BigDecimal("1"); e1.credit = BigDecimal.ZERO;
            VoucherEntry e2 = new VoucherEntry();
            e2.subjectCode = "2101"; e2.subjectName = "2101"; e2.digest = "t"; e2.debit = BigDecimal.ZERO; e2.credit = new BigDecimal("1");
            var es = new ArrayList<VoucherEntry>(); es.add(e1); es.add(e2); v.entries = es;
            voucherService.create(v);
        }, "反结账后期间应可写");
    }

    /** v9.0（P1-4 审计）：结账期间业务写路径全拦截——出入库（无业务日期按记账时刻）、
     *  收款单（按单据业务日期）。口径：已结账期间绝对禁补录，调整先反结账 */
    @Test
    void closedPeriodBlocksBusinessWrites() {
        // 基础仓/分库/库位（入库须指定库位，铁律）
        Integer wc = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse", Integer.class);
        if (wc == null || wc == 0) {
            jdbc.update("INSERT INTO warehouse (id, code, name, enabled) VALUES (1, 'TWH', '测试仓', 1)");
            jdbc.update("INSERT INTO warehouse_zone (id, warehouse_id, code, name, enabled) VALUES (1, 1, 'TZN', '测试分库', 1)");
            jdbc.update("INSERT INTO warehouse_location (id, zone_id, code, name, enabled) VALUES (1, 1, 'T-01', '测试库位', 1)");
        }
        jdbc.update("DELETE FROM inventory_ledger WHERE material_code = 'T-LOCK'");
        String cur = java.time.LocalDate.now().toString().substring(0, 7);
        jdbc.update("INSERT INTO account_period (period, closed) VALUES (?, 1)", cur);
        try {
            IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () ->
                    inventoryService.purchaseInbound("OTHER_IN", "T-LOCK-1", "T-LOCK", "锁期测试料",
                            "B-LOCK-1", "1", "1", new BigDecimal("1"), new BigDecimal("1"), "tester"));
            assertTrue(e1.getMessage().contains("已结账"), e1.getMessage());

            com.pengyuan.pims.entity.PaymentReceipt r = new com.pengyuan.pims.entity.PaymentReceipt();
            r.receiptDate = java.time.LocalDate.now();
            r.amount = new BigDecimal("1");
            r.customerId = 1L;
            IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> financeService.createReceipt(r));
            assertTrue(e2.getMessage().contains("已结账"), e2.getMessage());
        } finally {
            jdbc.update("DELETE FROM account_period WHERE period = ?", cur);
        }
    }
}
