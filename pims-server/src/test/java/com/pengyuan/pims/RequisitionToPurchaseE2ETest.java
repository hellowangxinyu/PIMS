package com.pengyuan.pims;

import com.pengyuan.pims.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** v8.8 核心测试 3：MRP→请购→转采购 端到端（N1 死路回归——无价拦截/补价/转出） */
class RequisitionToPurchaseE2ETest extends Support {

    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired JdbcTemplate jdbc;

    Long reqId;

    @BeforeEach
    void setup() {
        // 供应商 + 物料 + 无价请购单（模拟 MRP 产物）
        jdbc.update("DELETE FROM purchase_order WHERE remark LIKE '%E2E%'");
        jdbc.update("DELETE FROM purchase_order_item WHERE order_id IN (SELECT id FROM purchase_order WHERE remark LIKE '%E2E%')");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM supplier", Integer.class) == 0) {
            jdbc.update("INSERT INTO supplier (id, code, name, enabled) VALUES (1, 'SUP-E2E', 'E2E供应商', 1)");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE code='T-E2E'", Integer.class) == 0) {
            jdbc.update("INSERT INTO material (code, name, category, sub_category, enabled) VALUES ('T-E2E', '端到端测试料', 'A', 'AC', 1)");
        }
        var order = new java.util.HashMap<String, Object>();
        // 直接建库最快：插一张 DRAFT 请购 + 无价明细
        jdbc.update("INSERT INTO purchase_order (order_no, supplier_id, status, remark, create_time) VALUES ('PO-E2E-1', 1, 'DRAFT', 'E2E测试', 0)");
        reqId = jdbc.queryForObject("SELECT id FROM purchase_order WHERE order_no='PO-E2E-1'", Long.class);
        jdbc.update("INSERT INTO purchase_order_item (order_id, material_code, qty, unit) VALUES (?, 'T-E2E', 5, 'kg')", reqId);
    }

    @Test
    void noPriceBlockedThenPriceThenConverted() {
        // ① 审核
        purchaseOrderService.audit(reqId);
        // ② 无价转采购 → 必须拦截
        Exception ex = assertThrows(Exception.class, () -> purchaseOrderService.toPurchase(reqId, "tester"));
        assertTrue(ex.getMessage().contains("未填单价"), "无价必须拦截: " + ex.getMessage());
        // ③ 补价
        var item = new java.util.HashMap<String, Object>();
        item.put("materialCode", "T-E2E");
        item.put("unitPrice", 4.5);
        var items = (java.util.List<java.util.Map<String, Object>>) (java.util.List<?>) List.of(item);
        purchaseOrderService.updateItemPrices(reqId, items);
        Object price = jdbc.queryForObject("SELECT unit_price FROM purchase_order_item WHERE order_id=" + reqId, Object.class);
        assertEquals(0, new java.math.BigDecimal("4.5").compareTo(new java.math.BigDecimal(String.valueOf(price))));
        // ④ 转采购 → 成功
        var result = purchaseOrderService.toPurchase(reqId, "tester");
        assertNotNull(result.get("purchaseOrders"));
        // ⑤ 幂等：再转拦截
        assertThrows(Exception.class, () -> purchaseOrderService.toPurchase(reqId, "tester"));
    }
}
