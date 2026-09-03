package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.OperationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 操作日志接口（log:read）
 * - GET /api/log                         热数据查询（默认近 30 天，强制时间范围防全表扫描）
 * - GET /api/log/archives                归档月份列表
 * - GET /api/log/archives/{month}        按月份查归档日志
 * v5.66.4：支持按动作（增删改查）和单号筛选
 */
@RestController
@RequestMapping("/api/log")
public class OperationLogController {

    private final OperationLogService service;
    public OperationLogController(OperationLogService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "log:read")
    public Result<Map<String, Object>> search(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return Result.ok(service.search(username, method, path, module, action, bizNo, startDate, endDate, page, pageSize));
    }

    @GetMapping("/modules")
    @SaCheckPermission(value = "log:read")
    public Result<List<Map<String, String>>> modules() {
        return Result.ok(service.modules());
    }

    @GetMapping("/archives")
    @SaCheckPermission(value = "log:read")
    public Result<List<Map<String, Object>>> archives() {
        return Result.ok(service.archives());
    }

    @GetMapping("/archives/{month}")
    @SaCheckPermission(value = "log:read")
    public Result<Map<String, Object>> searchArchive(
            @PathVariable String month,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return Result.ok(service.searchArchive(month, username, method, path, module, action, bizNo, page, pageSize));
    }
}
