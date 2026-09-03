package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.ShippingLog;
import com.pengyuan.pims.service.ShippingService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 物流运费（v5.66）：发货运费登记，按销售订单归集成本 */
@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingService service;
    private final UserService userService;

    public ShippingController(ShippingService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("sales:read")
    public Result<?> list() {
        List<ShippingLog> list = service.list();
        if (!FieldFilter.hasAmountPerm("shipping")) {
            return Result.ok(FieldFilter.filterListFields(list, "freight"));
        }
        return Result.ok(list);
    }

    /** 发货单 → 订单号/客户（快捷登记预填） */
    @GetMapping("/resolve-outbound")
    @SaCheckPermission("sales:read")
    public Result<Map<String, Object>> resolveOutbound(@RequestParam String outboundDocNo) {
        return Result.ok(service.resolveOutbound(outboundDocNo));
    }

    @PostMapping
    @SaCheckPermission("sales:write")
    public Result<ShippingLog> create(@RequestBody ShippingLog s) {
        if (s.createdBy == null || s.createdBy.isBlank()) s.createdBy = userService.currentOperatorName();  // v5.60 制单人
        return Result.ok(service.create(s));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("sales:write")
    public Result<ShippingLog> update(@PathVariable Long id, @RequestBody ShippingLog s) {
        return Result.ok(service.update(id, s));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("sales:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }
}
