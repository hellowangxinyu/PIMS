package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.PurchaseOrder;
import com.pengyuan.pims.entity.PurchaseOrderItem;
import com.pengyuan.pims.service.PurchaseOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-order")
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    public PurchaseOrderController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "purchase:read")
    public Result<List<PurchaseOrder>> list() { return Result.ok(service.listAll()); }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok).orElse(Result.fail(500, "订单不存在"));
    }

    @GetMapping("/{id}/items")
    @SaCheckPermission(value = "purchase:read")
    public Result<List<PurchaseOrderItem>> items(@PathVariable Long id) {
        return Result.ok(service.getItems(id));
    }

    /** v6.4：请购单创建支持明细（Map 体：order 字段 + items 数组；原实体体收不到明细恒空） */
    @PostMapping
    @SaCheckPermission(value = "purchase:write")
    public Result<PurchaseOrder> create(@RequestBody java.util.Map<String, Object> body) {
        PurchaseOrder order = new PurchaseOrder();
        if (body.get("supplierId") != null) order.supplierId = Long.valueOf(String.valueOf(body.get("supplierId")));
        order.orderDate = body.get("orderDate") != null && !String.valueOf(body.get("orderDate")).isBlank()
                ? java.time.LocalDate.parse(String.valueOf(body.get("orderDate")).substring(0, 10)) : java.time.LocalDate.now();
        order.targetWarehouseId = body.get("targetWarehouseId") != null ? String.valueOf(body.get("targetWarehouseId")) : "1";
        order.paymentTerms = body.get("paymentTerms") != null ? String.valueOf(body.get("paymentTerms")) : null;
        order.expectedDeliveryDate = body.get("expectedDeliveryDate") != null && !String.valueOf(body.get("expectedDeliveryDate")).isBlank()
                ? java.time.LocalDate.parse(String.valueOf(body.get("expectedDeliveryDate")).substring(0, 10)) : null;
        order.remark = body.get("remark") != null ? String.valueOf(body.get("remark")) : null;
        order.createdBy = body.get("createdBy") != null ? String.valueOf(body.get("createdBy")) : null;
        java.util.List<PurchaseOrderItem> items = new java.util.ArrayList<>();
        Object raw = body.get("items");
        if (raw instanceof java.util.List<?> l) {
            for (Object o : l) {
                if (!(o instanceof java.util.Map<?, ?> m)) continue;
                PurchaseOrderItem it = new PurchaseOrderItem();
                it.materialCode = String.valueOf(m.get("materialCode"));
                it.qty = new java.math.BigDecimal(String.valueOf(m.get("qty")));
                it.unit = m.get("unit") != null ? String.valueOf(m.get("unit")) : "kg";
                it.unitPrice = m.get("unitPrice") != null ? new java.math.BigDecimal(String.valueOf(m.get("unitPrice"))) : null;
                items.add(it);
            }
        }
        return Result.ok(service.create(order, items));
    }

    /** v6.3：删除草稿请购单（MRP 误单清理） */
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }
}
