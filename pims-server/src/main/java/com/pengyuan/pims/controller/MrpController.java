package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.MrpService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MRP 简版（v6.3 第三批）：销售订单→配方展开→库存/在途比对→采购建议清单→一键生成请购单。
 * 权限沿用 purchase 域（建议/请购都是采购作业）。
 */
@RestController
@RequestMapping("/api/mrp")
public class MrpController {

    private final MrpService service;

    public MrpController(MrpService service) { this.service = service; }

    /** 采购建议分析：orderIds 为空 = 全部已确认订单 */
    @PostMapping("/suggest")
    @SaCheckPermission(value = "purchase:read")
    public Result<Map<String, Object>> suggest(@RequestBody(required = false) Map<String, Object> body) {
        List<Long> ids = null;
        if (body != null && body.get("orderIds") instanceof List<?> l) {
            ids = l.stream().map(x -> Long.valueOf(String.valueOf(x))).toList();
        }
        return Result.ok(service.suggest(ids));
    }

    /** 一键生成请购单（DRAFT，走请购审核流） */
    /** v11.1 历史用量维度：每日用量×(平均到货周期+缓冲) 低于请购点提醒采购 */
    @PostMapping("/suggest-usage")
    @SaCheckPermission("purchase:read")
    public Result<java.util.Map<String, Object>> suggestUsage(@RequestBody(required = false) java.util.Map<String, Object> body) {
        int buffer = 10;
        if (body != null && body.get("bufferDays") != null)
            buffer = Integer.parseInt(String.valueOf(body.get("bufferDays")));
        return Result.ok(service.suggestByUsage(buffer));
    }

    @PostMapping("/create-order")
    @SaCheckPermission(value = "purchase:write")
    public Result<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        return Result.ok(service.createPurchaseOrder(lines, String.valueOf(body.getOrDefault("operator", ""))));
    }
}
