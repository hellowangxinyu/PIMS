package com.pengyuan.pims;

import com.pengyuan.pims.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/** v8.8 核心测试 2：库存扣减金额守恒（v8.2 P0-3 修复的回归） */
class InventoryConservationTest extends Support {

    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setupWarehouse() {
        jdbc.update("DELETE FROM inventory_ledger WHERE material_code = 'T-CONSV'");
        Integer wc = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse", Integer.class);
        if (wc == null || wc == 0) {
            jdbc.update("INSERT INTO warehouse (id, code, name, enabled) VALUES (1, 'TWH', '测试仓', 1)");
            jdbc.update("INSERT INTO warehouse_zone (id, warehouse_id, code, name, enabled) VALUES (1, 1, 'TZN', '测试分库', 1)");
            jdbc.update("INSERT INTO warehouse_location (id, zone_id, code, name, enabled) VALUES (1, 1, 'T-01', '测试库位', 1)");
        }
    }

    @Test
    void outboundAmountConserved() {
        BigDecimal qty = new BigDecimal("10");
        BigDecimal price = new BigDecimal("3.35");   // 非整数倍单价（P0-3 触发条件）
        inventoryService.purchaseInbound("OTHER_IN", "T-CONSV-001", "T-CONSV", "守恒测试料",
                "B-CONSV-1", "1", "1", qty, price, "tester");
        var row = jdbc.queryForMap("SELECT qty, amount FROM inventory_ledger WHERE material_code='T-CONSV' AND batch_no='B-CONSV-1'");
        BigDecimal amt0 = new BigDecimal(String.valueOf(row.get("amount")));
        assertEquals(0, new BigDecimal("33.50").compareTo(amt0), "入库金额=量×价（2位）");

        // 出库 3kg：扣减金额应= round(3×3.35,2)=10.05，剩余金额=33.50-10.05=23.45（非重估 7×3.35=23.45 恰好同值——用更刁的价）
        BigDecimal outQty = new BigDecimal("3");
        inventoryService.outbound("OTHER_OUT", "T-CONSV-OUT", "T-CONSV", "B-CONSV-1", "1", "1", outQty, "tester");
        var row2 = jdbc.queryForMap("SELECT qty, amount FROM inventory_ledger WHERE material_code='T-CONSV' AND batch_no='B-CONSV-1'");
        BigDecimal qtyAfter = new BigDecimal(String.valueOf(row2.get("qty")));
        BigDecimal amtAfter = new BigDecimal(String.valueOf(row2.get("amount")));
        assertEquals(0, new BigDecimal("7").compareTo(qtyAfter));
        BigDecimal expect = amt0.subtract(outQty.multiply(price).setScale(2, RoundingMode.HALF_UP));
        assertEquals(expect, amtAfter, "出库按本次金额等额扣减（守恒），期望 " + expect);

        // 再出 7 清零
        inventoryService.outbound("OTHER_OUT", "T-CONSV-OUT2", "T-CONSV", "B-CONSV-1", "1", "1", new BigDecimal("7"), "tester");
        var row3 = jdbc.queryForMap("SELECT qty, amount FROM inventory_ledger WHERE material_code='T-CONSV' AND batch_no='B-CONSV-1'");
        assertEquals(0, new BigDecimal(String.valueOf(row3.get("qty"))).compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal(String.valueOf(row3.get("amount"))).compareTo(BigDecimal.ZERO), "清零后金额归零");
    }

    /** v9.0（P1-5）回归：1.5×2 两次出库各扣 5.03（真值 5.025，各多扣半分），
     *  末次清库按剩余金额 23.44 全额带走（非 7×3.35=23.45），期末金额精确归零——尾差归末次，Σ守恒 */
    @Test
    void tailDiffAbsorbedByLastOutbound() {
        inventoryService.purchaseInbound("OTHER_IN", "T-CONSV-T1", "T-CONSV", "守恒测试料",
                "B-CONSV-T1", "1", "1", new BigDecimal("10"), new BigDecimal("3.35"), "tester");
        inventoryService.outbound("OTHER_OUT", "T-CONSV-T1-O1", "T-CONSV", "B-CONSV-T1", "1", "1", new BigDecimal("1.5"), "tester");
        inventoryService.outbound("OTHER_OUT", "T-CONSV-T1-O2", "T-CONSV", "B-CONSV-T1", "1", "1", new BigDecimal("1.5"), "tester");
        var row = jdbc.queryForMap("SELECT amount FROM inventory_ledger WHERE material_code='T-CONSV' AND batch_no='B-CONSV-T1'");
        assertEquals(0, new BigDecimal("23.44").compareTo(new BigDecimal(String.valueOf(row.get("amount")))),
                "两次 1.5 出库后剩余金额 33.50-5.03-5.03=23.44");
        // 末次清库：担掉累积尾差，金额精确归零（旧代码此处 23.44-23.45=-0.01 被静默钳零）
        inventoryService.outbound("OTHER_OUT", "T-CONSV-T1-O3", "T-CONSV", "B-CONSV-T1", "1", "1", new BigDecimal("7"), "tester");
        var row2 = jdbc.queryForMap("SELECT qty, amount FROM inventory_ledger WHERE material_code='T-CONSV' AND batch_no='B-CONSV-T1'");
        assertEquals(0, new BigDecimal("0").compareTo(new BigDecimal(String.valueOf(row2.get("qty")))));
        assertEquals(0, new BigDecimal("0.00").compareTo(new BigDecimal(String.valueOf(row2.get("amount")))),
                "清库后金额精确归零（尾差归末次出库）");
    }

    /** v9.0（P1-5）回归：脏台账负值超过 1 分必须抛错暴露（原静默钳零吞掉差异），且事务回滚数量不动 */
    @Test
    void dirtyLedgerBeyondCentThrows() {
        inventoryService.purchaseInbound("OTHER_IN", "T-CONSV-T2", "T-CONSV", "守恒测试料",
                "B-CONSV-T2", "1", "1", new BigDecimal("10"), new BigDecimal("3.35"), "tester");
        jdbc.update("UPDATE inventory_ledger SET amount = 1.00 WHERE material_code='T-CONSV' AND batch_no='B-CONSV-T2'");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                inventoryService.outbound("OTHER_OUT", "T-CONSV-T2-O1", "T-CONSV", "B-CONSV-T2", "1", "1", new BigDecimal("1"), "tester"));
        assertTrue(ex.getMessage().contains("台账金额异常"), "异常应指向台账金额异常: " + ex.getMessage());
        var row = jdbc.queryForMap("SELECT qty FROM inventory_ledger WHERE material_code='T-CONSV' AND batch_no='B-CONSV-T2'");
        assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(String.valueOf(row.get("qty")))),
                "抛错后事务回滚，台账数量不变");
    }
}
