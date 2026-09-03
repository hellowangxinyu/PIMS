package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.CodingRule;
import com.pengyuan.pims.service.CodingRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coding-rule")
public class CodingRuleController {

    private final CodingRuleService service;
    public CodingRuleController(CodingRuleService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "material:read")
    public Result<List<CodingRule>> list() { return Result.ok(service.listAll()); }

    @PostMapping
    @SaCheckPermission(value = "material:write")
    public Result<CodingRule> create(@RequestBody CodingRule rule) { return Result.ok(service.create(rule)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "material:write")
    public Result<CodingRule> update(@PathVariable Long id, @RequestBody CodingRule rule) {
        return Result.ok(service.update(id, rule));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "material:write")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }

    /** 根据小类代码生成物料编码 */
    @PostMapping("/generate")
    @SaCheckPermission(value = "material:write")
    public Result<String> generate(@RequestParam String subCategoryCode) {
        return Result.ok(service.generateCode(subCategoryCode));
    }
}
