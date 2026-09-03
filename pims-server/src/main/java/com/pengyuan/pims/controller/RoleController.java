package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Role;
import com.pengyuan.pims.service.RoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "user:read")
    public Result<List<Role>> list() { return Result.ok(service.listRoles()); }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "user:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getRole(id).map(Result::ok).orElse(Result.fail(500, "角色不存在"));
    }

    /** 所有可配置的权限码（前端勾选用） */
    @GetMapping("/permissions/all")
    @SaCheckPermission(value = "user:read")
    public Result<List<String>> allPermissions() {
        return Result.ok(service.getAllPermissions());
    }

    /** 权限树结构（前端 el-tree 层级选择用） */
    @GetMapping("/permissions/tree")
    @SaCheckPermission(value = "user:read")
    public Result<?> permissionTree() {
        return Result.ok(service.getPermissionTree());
    }

    /** v5.68 权限矩阵（表格化勾选用）：行=模块、列=操作+字段权限 */
    @GetMapping("/permissions/matrix")
    @SaCheckPermission(value = "user:read")
    public Result<?> permissionMatrix() {
        return Result.ok(RoleService.buildPermissionMatrix());
    }

    /** 查某角色的权限码 */
    @GetMapping("/{roleCode}/permissions")
    @SaCheckPermission(value = "user:read")
    public Result<List<String>> rolePermissions(@PathVariable String roleCode) {
        return Result.ok(service.getRolePermissions(roleCode));
    }

    @PostMapping
    @SaCheckPermission(value = "user:write")
    public Result<Role> create(@RequestBody Role role) {
        return Result.ok(service.createRole(role));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "user:write")
    public Result<Role> update(@PathVariable Long id, @RequestBody Role role) {
        return Result.ok(service.updateRole(id, role));
    }

    /** 设置角色权限（覆盖式） */
    @PutMapping("/{roleCode}/permissions")
    @SaCheckPermission(value = "user:write")
    public Result<?> setPermissions(@PathVariable String roleCode, @RequestBody List<String> permCodes) {
        service.setRolePermissions(roleCode, permCodes);
        return Result.ok("权限已更新");
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "user:write")
    public Result<?> delete(@PathVariable Long id) {
        service.deleteRole(id);
        return Result.ok("已禁用");
    }
}
