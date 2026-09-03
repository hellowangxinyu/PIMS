package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.ReturnOrder;
import com.pengyuan.pims.service.TailingReturnService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * v5.35：油尾退回单（客户退回未用完油漆 → 入油尾库）
 */
@RestController
@RequestMapping("/api/tailing-return")
public class TailingReturnController {

    private final TailingReturnService service;
    private final UserService userService;

    public TailingReturnController(TailingReturnService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission(value = "sales:read")
    public Result<List<ReturnOrder>> list(@RequestParam(required = false) String status) {
        return Result.ok(service.list(status));
    }

    @GetMapping("/returnable-outbounds")
    @SaCheckPermission(value = "sales:read")
    public Result<List<Map<String, Object>>> returnableOutbounds() {
        return Result.ok(service.listReturnableOutbounds());
    }

    @GetMapping("/warehouse-info")
    @SaCheckPermission(value = "sales:read")
    public Result<Map<String, Object>> warehouseInfo(@RequestParam(required = false) Long warehouseId) {
        return Result.ok(service.tailingWarehouseInfo(warehouseId));
    }

    /** v5.38.1：全部启用仓的油尾区信息（每仓一个油尾区，前端选仓用） */
    @GetMapping("/warehouse-options")
    @SaCheckPermission(value = "sales:read")
    public Result<List<Map<String, Object>>> warehouseOptions() {
        return Result.ok(service.tailingWarehouseOptions());
    }

    @PostMapping
    @SaCheckPermission(value = "sales:write")
    public Result<ReturnOrder> create(@RequestBody Map<String, Object> body) {
        String operator = userService.currentOperatorName();
        ReturnOrder ro = service.create(
                str(body, "customerName"),
                str(body, "refSalesOutboundNo"),
                str(body, "materialCode"),
                str(body, "materialName"),
                str(body, "batchNo"),
                str(body, "unit"),
                body.get("qty") != null ? new BigDecimal(body.get("qty").toString()) : null,
                body.get("unitPrice") != null ? new BigDecimal(body.get("unitPrice").toString()) : null,
                str(body, "settleType"),
                str(body, "warehouseId"),
                str(body, "locationId"),
                str(body, "locationName"),
                str(body, "remark"),
                operator);
        return Result.ok(ro);
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission(value = "sales:write")
    public Result<ReturnOrder> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id, userService.currentOperatorName()));
    }

    @PostMapping("/{id}/reject")
    @SaCheckPermission(value = "sales:write")
    public Result<ReturnOrder> reject(@PathVariable Long id) {
        return Result.ok(service.reject(id, userService.currentOperatorName()));
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }
}
