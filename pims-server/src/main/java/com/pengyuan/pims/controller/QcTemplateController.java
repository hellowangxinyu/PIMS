package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.QcTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 质检模板管理接口（v5.32：按物料大类区分检测内容）
 * 权限复用 qc:read / qc:write
 */
@RestController
@RequestMapping("/api/qc-template")
public class QcTemplateController {

    private final QcTemplateService service;

    public QcTemplateController(QcTemplateService service) {
        this.service = service;
    }

    /** 全部模板（含检测项） */
    @GetMapping("/list")
    @SaCheckPermission(value = "qc:read")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(service.listAll());
    }

    /** 模板详情（含检测项） */
    @GetMapping("/{id}")
    @SaCheckPermission(value = "qc:read")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    /** 新建模板（聚合保存：主表+检测项） */
    @PostMapping
    @SaCheckPermission(value = "qc:write")
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        return Result.ok(service.save(null, body));
    }

    /** 更新模板（检测项先删后插） */
    @PutMapping("/{id}")
    @SaCheckPermission(value = "qc:write")
    public Result<Long> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(service.save(id, body));
    }

    /** 删除模板（默认模板拦截；历史质检单已快照不受影响） */
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "qc:write")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /** 设为该类别默认模板（同类别互斥） */
    @PostMapping("/{id}/set-default")
    @SaCheckPermission(value = "qc:write")
    public Result<Void> setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return Result.ok();
    }
}
