package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.DictItem;
import com.pengyuan.pims.service.DictItemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dict")
public class DictItemController {

    private final DictItemService service;
    public DictItemController(DictItemService service) { this.service = service; }

    @GetMapping
    public Result<List<DictItem>> list(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) return Result.ok(service.listByType(type));
        return Result.ok(service.listAll());
    }

    @PostMapping
    @SaCheckPermission(value = "dict:write")
    public Result<DictItem> create(@RequestBody DictItem item) { return Result.ok(service.create(item)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "dict:write")
    public Result<DictItem> update(@PathVariable Long id, @RequestBody DictItem item) {
        return Result.ok(service.update(id, item));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "dict:write")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
}
