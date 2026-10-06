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

    // v11.7 清理：/permissions/all 平铺码与 /permissions/tree 树接口删除（角色页统一走 /permissions/matrix 矩阵接口；service.getAllPermissions/getPermissionTree 一并删除）

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
