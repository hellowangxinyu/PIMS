package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.User;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping("/roles")
    @SaCheckPermission(value = "user:read")
    public Result<?> roles() { return Result.ok(UserService.ROLE_NAMES); }

    @GetMapping("/permissions")
    @SaCheckPermission(value = "user:read")
    public Result<?> permissions() { return Result.ok(UserService.ROLE_PERMISSIONS); }

    @GetMapping
    @SaCheckPermission(value = "user:read")
    public Result<List<User>> list() { return Result.ok(service.listAll()); }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "user:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok).orElse(Result.fail(500, "用户不存在"));
    }

    @PostMapping
    @SaCheckPermission(value = "user:write")
    public Result<User> create(@RequestBody User u) { return Result.ok(service.create(u)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "user:write")
    public Result<User> update(@PathVariable Long id, @RequestBody User u) {
        return Result.ok(service.update(id, u));
    }

    @PutMapping("/{id}/reset-password")
    @SaCheckPermission(value = "user:write")
    public Result<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.resetPassword(id, body.get("password"));
        return Result.ok("密码已重置");
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "user:delete")
    public Result<?> delete(@PathVariable Long id) { service.delete(id); return Result.ok("已禁用"); }
}
