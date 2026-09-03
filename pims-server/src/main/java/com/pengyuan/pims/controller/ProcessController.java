package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.ProcessService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工艺路线接口：多条命名路线独立维护（GRINDING/TINTING 分类），配方绑定其中一条。
 */
@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private final ProcessService service;

    public ProcessController(ProcessService service) {
        this.service = service;
    }

    /** 路线列表（可选 recipeType 过滤） */
    @GetMapping("/routes")
    @SaCheckPermission("process:read")
    public Result listRoutes(@RequestParam(required = false) String recipeType) {
        return Result.ok(service.listRoutes(recipeType));
    }

    /** 路线详情（含工序/步骤/质检项） */
    @GetMapping("/route/{id}")
    @SaCheckPermission("process:read")
    public Result getRoute(@PathVariable Long id) {
        return Result.ok(service.getRoute(id));
    }

    /** 新建路线 */
    @PostMapping("/route")
    @SaCheckPermission("process:write")
    public Result createRoute(@RequestBody Map<String, Object> body) {
        return Result.ok(service.saveRoute(null, body));
    }

    /** 全量保存路线 */
    @PutMapping("/route/{id}")
    @SaCheckPermission("process:write")
    public Result saveRoute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.saveRoute(id, body);
        return Result.ok(null);
    }

    /** 删除路线（被配方引用/默认路线时拦截） */
    @DeleteMapping("/route/{id}")
    @SaCheckPermission("process:write")
    public Result deleteRoute(@PathVariable Long id) {
        service.deleteRoute(id);
        return Result.ok(null);
    }

    /** 复制路线 */
    @PostMapping("/route/{id}/copy")
    @SaCheckPermission("process:write")
    public Result copyRoute(@PathVariable Long id) {
        return Result.ok(service.copyRoute(id));
    }

    /** 设为该类型默认路线 */
    @PostMapping("/route/{id}/set-default")
    @SaCheckPermission("process:write")
    public Result setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return Result.ok(null);
    }

    /** 旧接口兼容：返回该类型的默认路线 */
    @GetMapping("/template/{recipeType}")
    @SaCheckPermission("process:read")
    public Result getTemplate(@PathVariable String recipeType) {
        return Result.ok(service.getDefaultTemplate(recipeType));
    }
}
