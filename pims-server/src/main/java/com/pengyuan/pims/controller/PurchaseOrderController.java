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
    private final com.pengyuan.pims.service.UserService userService;
    public PurchaseOrderController(PurchaseOrderService service, com.pengyuan.pims.service.UserService userService) {
        this.service = service;
        this.userService = userService;
    }

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
                // v6.5 B1：null 安全解析（原 String.valueOf(null) 存成字符串 "null" 绕过 isBlank 校验入库；
                // qty 缺省 new BigDecimal("null") 抛 NumberFormatException 变 500）
                Object code = m.get("materialCode");
                if (code == null || String.valueOf(code).isBlank()) continue;
                it.materialCode = String.valueOf(code).trim();
                Object qty = m.get("qty");
                if (qty == null || String.valueOf(qty).isBlank()) continue;
                try {
                    it.qty = new java.math.BigDecimal(String.valueOf(qty));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("明细 " + it.materialCode + " 数量格式错误：" + qty);
                }
                it.unit = m.get("unit") != null ? String.valueOf(m.get("unit")) : "kg";
                it.unitPrice = m.get("unitPrice") != null ? new java.math.BigDecimal(String.valueOf(m.get("unitPrice"))) : null;
                items.add(it);
            }
        }
        return Result.ok(service.create(order, items));
    }

    /** v6.5 B3：编辑请购单头（DRAFT；MRP 单补供应商/仓库/交期） */
    @PutMapping("/{id}/header")
    @SaCheckPermission(value = "purchase:write")
    public Result<PurchaseOrder> updateHeader(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        Long supplierId = body.get("supplierId") != null && !String.valueOf(body.get("supplierId")).isBlank()
                ? Long.valueOf(String.valueOf(body.get("supplierId"))) : null;
        java.time.LocalDate dlv = body.get("expectedDeliveryDate") != null && !String.valueOf(body.get("expectedDeliveryDate")).isBlank()
                ? java.time.LocalDate.parse(String.valueOf(body.get("expectedDeliveryDate")).substring(0, 10)) : null;
        return Result.ok(service.updateHeader(id, supplierId,
                body.get("targetWarehouseId") != null ? String.valueOf(body.get("targetWarehouseId")) : null,
                dlv, body.get("remark") != null ? String.valueOf(body.get("remark")) : null));
    }

    /** v8.6（N1）：批量改明细单价（DRAFT 态；MRP 请购补价入口） */
    @PutMapping("/{id}/items")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> updateItems(@PathVariable Long id, @RequestBody java.util.List<java.util.Map<String, Object>> items) {
        return Result.ok(service.updateItemPrices(id, items));
    }

    /** v6.5 B3：审核 DRAFT→APPROVED */
    @PostMapping("/{id}/audit")
    @SaCheckPermission(value = "purchase:write")
    public Result<PurchaseOrder> audit(@PathVariable Long id) {
        return Result.ok(service.audit(id));
    }

    /** v6.5 B3：转采购（按明细逐物料生成采购单，请购单关闭） */
    @PostMapping("/{id}/to-purchase")
    @SaCheckPermission(value = "purchase:write")
    public Result<java.util.Map<String, Object>> toPurchase(@PathVariable Long id) {
        return Result.ok(service.toPurchase(id, userService.currentOperatorName()));
    }

    /** v6.3：删除草稿请购单（MRP 误单清理） */
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }
}
