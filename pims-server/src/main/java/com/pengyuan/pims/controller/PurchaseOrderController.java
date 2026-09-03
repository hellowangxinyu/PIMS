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

    @PostMapping
    @SaCheckPermission(value = "purchase:write")
    public Result<PurchaseOrder> create(@RequestBody PurchaseOrder order) {
        return Result.ok(service.create(order, List.of()));
    }

    /** v6.3：删除草稿请购单（MRP 误单清理） */
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }
}
