package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.CostingService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 存货计价（v5.63）：计价方式配置/变更留痕 + 全月平均月末成本计算 */
@RestController
@RequestMapping("/api/costing")
public class CostingController {

    private final CostingService service;
    private final UserService userService;

    public CostingController(CostingService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    /** 当前计价方式 + 当月出库单数（变更警示） */
    @GetMapping("/config")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> config() { return Result.ok(service.config()); }

    /** 变更计价方式：reason 必填；当月已有出库单时首次返回 needConfirm 由前端二次确认 */
    @PutMapping("/method")
    @SaCheckPermission("finance:write")
    public Result<Map<String, Object>> changeMethod(@RequestBody Map<String, Object> body) {
        String method = String.valueOf(body.get("method"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return Result.ok(service.changeMethod(method, reason, userService.currentOperatorName(), force));
    }

    @GetMapping("/log")
    @SaCheckPermission("finance:read")
    public Result<List<Map<String, Object>>> changeLog() { return Result.ok(service.changeLog()); }

    /** 全月平均月末成本计算：回填该期出库成本 + 落价格快照（期末结账前置） */
    @PostMapping("/monthly-close")
    @SaCheckPermission("finance:write")
    public Result<Map<String, Object>> monthlyClose(@RequestBody Map<String, String> body) {
        return Result.ok(service.monthlyClose(body.get("period"), userService.currentOperatorName()));
    }

    /** 某期是否已算 + 出库单数 + 价格快照（期末结账页展示） */
    @GetMapping("/monthly-status")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> monthlyStatus(@RequestParam String period) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("period", period);
        result.put("method", service.method());
        result.put("done", service.monthlyDone(period));
        result.put("outboundCount", service.periodOutboundCount(period));
        result.put("prices", service.monthlyPrices(period));
        return Result.ok(result);
    }
}
