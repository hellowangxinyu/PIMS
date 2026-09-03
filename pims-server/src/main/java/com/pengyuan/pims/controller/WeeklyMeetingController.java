package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.RdProgress;
import com.pengyuan.pims.entity.WeeklyTopic;
import com.pengyuan.pims.service.UserService;
import com.pengyuan.pims.service.WeeklyMeetingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 周度会议（v5.44）：每周议题 + 研发进度（权限 meeting:*） */
@RestController
@RequestMapping("/api/meeting")
public class WeeklyMeetingController {

    private final WeeklyMeetingService service;
    private final UserService userService;
    public WeeklyMeetingController(WeeklyMeetingService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    // ===== 每周议题 =====

    @GetMapping("/topic")
    @SaCheckPermission("meeting:read")
    public Result<java.util.List<WeeklyTopic>> listTopics() { return Result.ok(service.listTopics()); }

    @PostMapping("/topic")
    @SaCheckPermission("meeting:write")
    public Result<WeeklyTopic> createTopic(@RequestBody WeeklyTopic t) {
        return Result.ok(service.createTopic(t, userService.currentOperatorName()));
    }

    @PutMapping("/topic/{id}")
    @SaCheckPermission("meeting:write")
    public Result<WeeklyTopic> updateTopic(@PathVariable Long id, @RequestBody WeeklyTopic t) {
        return Result.ok(service.updateTopic(id, t));
    }

    /** 结案/反结案（close=true 填今天，false 清空） */
    @PostMapping("/topic/{id}/close")
    @SaCheckPermission("meeting:write")
    public Result<WeeklyTopic> closeTopic(@PathVariable Long id, @RequestParam boolean close) {
        return Result.ok(service.closeTopic(id, close));
    }

    @DeleteMapping("/topic/{id}")
    @SaCheckPermission("meeting:write")
    public Result<?> deleteTopic(@PathVariable Long id) {
        service.deleteTopic(id);
        return Result.ok("删除成功");
    }

    // ===== 研发进度 =====

    @GetMapping("/rd")
    @SaCheckPermission("meeting:read")
    public Result<java.util.List<RdProgress>> listRd() { return Result.ok(service.listRd()); }

    @PostMapping("/rd")
    @SaCheckPermission("meeting:write")
    public Result<RdProgress> createRd(@RequestBody RdProgress r) {
        return Result.ok(service.createRd(r, userService.currentOperatorName()));
    }

    @PutMapping("/rd/{id}")
    @SaCheckPermission("meeting:write")
    public Result<RdProgress> updateRd(@PathVariable Long id, @RequestBody RdProgress r) {
        return Result.ok(service.updateRd(id, r));
    }

    @PostMapping("/rd/{id}/close")
    @SaCheckPermission("meeting:write")
    public Result<RdProgress> closeRd(@PathVariable Long id, @RequestParam boolean close) {
        return Result.ok(service.closeRd(id, close));
    }

    @DeleteMapping("/rd/{id}")
    @SaCheckPermission("meeting:write")
    public Result<?> deleteRd(@PathVariable Long id) {
        service.deleteRd(id);
        return Result.ok("删除成功");
    }

    // ===== Excel 导入 / 导出（v5.44.1） =====

    /** 每周议题导出（列与导入表头一致，导出→修改→可直接导回） */
    @GetMapping("/topic/export")
    @SaCheckPermission("meeting:read")
    public void exportTopics(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (WeeklyTopic t : service.listTopics()) {
            rows.add(new Object[]{ t.owner, t.category, t.content, t.result, t.planDate, t.closedDate });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "每周议题-" + java.time.LocalDate.now(), "每周议题",
                new String[]{"责任人", "分类", "待办事项", "结果", "计划完成时间", "已结案时间"}, rows);
    }

    /** 研发进度导出 */
    @GetMapping("/rd/export")
    @SaCheckPermission("meeting:read")
    public void exportRd(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (RdProgress r : service.listRd()) {
            rows.add(new Object[]{ r.raiseDate, r.owner, r.category, r.content, r.result, r.nextDate, r.closedDate, r.progress });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "研发进度-" + java.time.LocalDate.now(), "研发进度",
                new String[]{"提出时间", "提出人", "分类", "内容", "结果", "下次跟进时间", "已结案时间", "进度"}, rows);
    }

    /** 每周议题单表导入（按表头识别，去重：责任人+待办事项一致跳过） */
    @PostMapping("/topic/import")
    @SaCheckPermission("meeting:write")
    public Result<String> importTopics(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return Result.ok(service.importTopicsFile(file, userService.currentOperatorName()));
    }

    /** 研发进度单表导入（去重：提出人+内容一致跳过） */
    @PostMapping("/rd/import")
    @SaCheckPermission("meeting:write")
    public Result<String> importRd(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return Result.ok(service.importRdFile(file, userService.currentOperatorName()));
    }

    // ===== 原双 sheet 存量导入 =====

    @PostMapping("/import")
    @SaCheckPermission("meeting:write")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.ok(service.importExcel(file, userService.currentOperatorName()));
    }
}
