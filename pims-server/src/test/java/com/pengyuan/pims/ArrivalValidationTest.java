package com.pengyuan.pims;

import com.pengyuan.pims.entity.PurchaseArrival;
import com.pengyuan.pims.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** v8.9 核心测试 6：到货三道防线（幽灵单/DRAFT 单/物料不在明细） */
class ArrivalValidationTest extends Support {

    @Autowired PurchaseService purchaseService;
    @Autowired JdbcTemplate jdbc;

    Long approvedOrder, draftOrder;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM purchase_arrival WHERE ref_order_no LIKE 'T-AVV%'");
        jdbc.update("DELETE FROM raw_material_purchase WHERE order_no LIKE 'T-AVV%'");
        jdbc.update("INSERT INTO raw_material_purchase (id, order_no, status, material_code, qty, unit_price, purchase_date) " +
                "VALUES (9001, 'T-AVV-A', 'APPROVED', 'T-AVV-MAT', 10, 5, 0)");
        jdbc.update("INSERT INTO raw_material_purchase (id, order_no, status, material_code, qty, unit_price, purchase_date) " +
                "VALUES (9002, 'T-AVV-D', 'DRAFT', 'T-AVV-MAT', 10, 5, 0)");
    }

    private PurchaseArrival arrival(String orderNo, String mat) {
        PurchaseArrival pa = new PurchaseArrival();
        pa.type = "RAW";
        pa.refOrderNo = orderNo;
        pa.materialCode = mat;
        pa.qty = new BigDecimal("1");
        pa.warehouseId = "4";
        return pa;
    }

    @Test
    void ghostOrderRejected() {
        Exception ex = assertThrows(Exception.class, () -> purchaseService.createArrival(arrival("NO-SUCH-ORDER", "T-AVV-MAT")));
        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());
    }

    @Test
    void draftOrderRejected() {
        Exception ex = assertThrows(Exception.class, () -> purchaseService.createArrival(arrival("T-AVV-D", "T-AVV-MAT")));
        assertTrue(ex.getMessage().contains("尚未审核"), "DRAFT 单必须拦: " + ex.getMessage());
    }

    @Test
    void materialNotInOrderRejected() {
        Exception ex = assertThrows(Exception.class, () -> purchaseService.createArrival(arrival("T-AVV-A", "NOT-IN-ORDER")));
        assertTrue(ex.getMessage().contains("不在采购订单"), ex.getMessage());
    }

    @Test
    void approvedOrderWithMaterialAccepted() {
        assertDoesNotThrow(() -> purchaseService.createArrival(arrival("T-AVV-A", "T-AVV-MAT")));
    }
}
