package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Task;
import com.pengyuan.pims.service.TaskService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 任务督办（v5.67）：领导安排工作 → 员工汇报进度 */
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskService service;
    private final UserService userService;

    public TaskController(TaskService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("task:read")
    public Result<List<Task>> list() {
        // v5.67.1 权限细化：有 task:write 的领导看全部；普通员工只看自己相关（被指派/自己创建）的
        boolean isManager = com.pengyuan.pims.common.FieldFilter.hasPerm("task:write");
        return Result.ok(service.list(userService.currentUsername(), isManager));
    }

    /** 可选执行人列表 */
    @GetMapping("/users")
    @SaCheckPermission("task:read")
    public Result<List<Map<String, String>>> users() { return Result.ok(service.users()); }

    @PostMapping
    @SaCheckPermission("task:write")
    public Result<Task> create(@RequestBody Task t) {
        t.createdBy = userService.currentOperatorName();   // 指派人=当前用户
        return Result.ok(service.create(t));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("task:write")
    public Result<Task> update(@PathVariable Long id, @RequestBody Task t) {
        return Result.ok(service.update(id, t));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("task:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }

    /** 开始执行（owner/collaborator 或 task:write） */
    @PostMapping("/{id}/start")
    @SaCheckPermission("task:read")
    public Result<Task> start(@PathVariable Long id) {
        return Result.ok(service.start(id, userService.currentOperatorName()));
    }

    /** 汇报进度 */
    @PostMapping("/{id}/report")
    @SaCheckPermission("task:read")
    public Result<Task> report(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.report(id, body.get("content"), userService.currentOperatorName()));
    }

    /** 完成任务 */
    @PostMapping("/{id}/complete")
    @SaCheckPermission("task:read")
    public Result<Task> complete(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        return Result.ok(service.complete(id, note, userService.currentOperatorName()));
    }

    /** 取消任务（task:write） */
    @PostMapping("/{id}/cancel")
    @SaCheckPermission("task:write")
    public Result<Task> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return Result.ok(service.cancel(id, reason, userService.currentOperatorName()));
    }

    /** 重开任务（task:write） */
    @PostMapping("/{id}/reopen")
    @SaCheckPermission("task:write")
    public Result<Task> reopen(@PathVariable Long id) {
        return Result.ok(service.reopen(id, userService.currentOperatorName()));
    }

    /** 我的待办任务数（Dashboard 用） */
    @GetMapping("/my-count")
    @SaCheckPermission("task:read")
    public Result<Map<String, Integer>> myCount() {
        var user = userService.currentUsername();
        return Result.ok(service.myTaskCount(user));
    }
}
