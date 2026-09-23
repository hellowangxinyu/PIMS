package com.pengyuan.pims;

import com.pengyuan.pims.entity.PurchaseArrival;
import com.pengyuan.pims.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** v11.0 到货调价：改含税单价 → 重算应付（arrivalId 精确匹配）；已付款禁止；原因必填 */
class ArrivalPriceAdjustTest extends Support {

    @Autowired PurchaseService purchaseService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM purchase_arrival WHERE ref_order_no LIKE 'T-ADJ%'");
        jdbc.update("DELETE FROM raw_material_purchase WHERE order_no LIKE 'T-ADJ%'");
        jdbc.update("DELETE FROM accounts_payable WHERE purchase_order_no LIKE 'T-ADJ%'");
        // 已审核采购单：单价 10，数量 10 → 到货后 AP 应为 100
        jdbc.update("INSERT INTO raw_material_purchase (id, order_no, status, material_code, material_name, qty, unit_price, purchase_date, supplier_id, is_free) " +
                "VALUES (9101, 'T-ADJ-A', 'APPROVED', 'T-ADJ-MAT', '调价测试料', 10, 10, 0, 1, 0)");
    }

    /** 手工建到货 + 审核 → 产生 APPROVED 到货单与 AP（100 元） */
    private Long makeApprovedArrival() throws Exception {
        PurchaseArrival pa = new PurchaseArrival();
        pa.type = "RAW";
        pa.refOrderNo = "T-ADJ-A";
        pa.materialCode = "T-ADJ-MAT";
        pa.qty = new BigDecimal("10");
        pa.unitPrice = new BigDecimal("10");
        pa.taxRate = new BigDecimal("13");
        pa.warehouseId = "4";
        var saved = purchaseService.createArrival(pa);
        // 直接置为已审核状态触发 AP 立账逻辑等价路径：调用审核
        purchaseService.auditArrival(saved.id);
        return saved.id;
    }

    private BigDecimal apAmount(Long arrivalId) {
        var rows = jdbc.queryForList("SELECT amount FROM accounts_payable WHERE arrival_id = ?", arrivalId);
        return rows.isEmpty() ? null : new BigDecimal(String.valueOf(rows.get(0).get("amount")));
    }

    @Test
    void adjustRecalculatesAP() throws Exception {
        Long arrivalId = makeApprovedArrival();
        assertEquals(0, new BigDecimal("100").compareTo(apAmount(arrivalId)), "到货审核后 AP=10×10=100");

        purchaseService.adjustArrivalPrice(arrivalId, new BigDecimal("12"), "供应商对账补差", "tester");

        assertEquals(0, new BigDecimal("120").compareTo(apAmount(arrivalId)), "调价后 AP 重算=10×12=120");
        var paRow = jdbc.queryForMap("SELECT unit_price FROM purchase_arrival WHERE id = ?", arrivalId);
        assertEquals(0, new BigDecimal("12").compareTo(new BigDecimal(String.valueOf(paRow.get("unit_price")))),
                "到货单单价已更新");
        // 双方留痕
        var apRemark = jdbc.queryForMap("SELECT remark FROM accounts_payable WHERE arrival_id = ?", arrivalId);
        assertTrue(String.valueOf(apRemark.get("remark")).contains("调价"), "AP 调价留痕");
    }

    @Test
    void adjustRequiresReason() throws Exception {
        Long arrivalId = makeApprovedArrival();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> purchaseService.adjustArrivalPrice(arrivalId, new BigDecimal("12"), "  ", "tester"));
        assertTrue(ex.getMessage().contains("原因"), ex.getMessage());
        assertEquals(0, new BigDecimal("100").compareTo(apAmount(arrivalId)), "拦下后 AP 不变");
    }

    @Test
    void adjustRejectedAfterPayment() throws Exception {
        Long arrivalId = makeApprovedArrival();
        // 模拟已付款：直接置 paid_amount
        jdbc.update("UPDATE accounts_payable SET paid_amount = 50, status = 'PARTIAL' WHERE arrival_id = ?", arrivalId);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> purchaseService.adjustArrivalPrice(arrivalId, new BigDecimal("12"), "对账调价", "tester"));
        assertTrue(ex.getMessage().contains("已付款"), ex.getMessage());
        var paRow = jdbc.queryForMap("SELECT unit_price FROM purchase_arrival WHERE id = ?", arrivalId);
        assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(String.valueOf(paRow.get("unit_price")))),
                "已付款拦下后单价不变");
    }

    @Test
    void draftArrivalRejected() {
        // 未审核到货单（直接造 DRAFT）不可调价
        PurchaseArrival pa = new PurchaseArrival();
        pa.type = "RAW";
        pa.refOrderNo = "T-ADJ-A";
        pa.materialCode = "T-ADJ-MAT";
        pa.qty = new BigDecimal("1");
        pa.warehouseId = "4";
        var saved = purchaseService.createArrival(pa);   // DRAFT
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> purchaseService.adjustArrivalPrice(saved.id, new BigDecimal("12"), "x", "tester"));
        assertTrue(ex.getMessage().contains("已审核"), ex.getMessage());
    }
}
