package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Employee;
import com.pengyuan.pims.service.EmployeeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 员工档案（v5.62 工资核算）：dept 决定工资计提借方科目 */
@RestController
@RequestMapping("/api/employee")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<Employee> list = service.list();
        if (!FieldFilter.hasAmountPerm("employee")) {
            return Result.ok(FieldFilter.filterListFields(list, "baseSalary"));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<Employee> create(@RequestBody Employee e) {
        return Result.ok(service.create(e));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<Employee> update(@PathVariable Long id, @RequestBody Employee e) {
        return Result.ok(service.update(id, e));
    }

    @PutMapping("/{id}/toggle")
    @SaCheckPermission("finance:write")
    public Result<Employee> toggle(@PathVariable Long id) {
        return Result.ok(service.toggle(id));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }
}
