package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.SampleRequest;
import com.pengyuan.pims.service.SampleService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** v5.53 打样/样品管理（申请→调色→寄样→反馈→转单） */
@RestController
@RequestMapping("/api/sample")
public class SampleController {

    private final SampleService service;

    public SampleController(SampleService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "sample:read")
    public Result<List<SampleRequest>> list(@RequestParam(required = false) Long customerId,
                                            @RequestParam(required = false) String status) {
        return Result.ok(service.list(customerId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "sample:read")
    public Result<SampleRequest> detail(@PathVariable Long id) { return Result.ok(service.getById(id)); }

    @PostMapping
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> create(@RequestBody SampleRequest s) {
        if (s.applicant == null || s.applicant.isBlank()) s.applicant = StpUtil.getLoginIdAsString();
        return Result.ok(service.create(s));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> update(@PathVariable Long id, @RequestBody SampleRequest s) { return Result.ok(service.update(id, s)); }

    /** 开始调色（自动在研发进度建条目；ADJUST 回炉也可调用） */
    @PostMapping("/{id}/coloring")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> coloring(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.startColoring(id, body.get("colorist"), body.get("colorNote")));
    }

    /** 寄样 */
    @PostMapping("/{id}/send")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> send(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LocalDate sendDate = body.get("sendDate") != null && !body.get("sendDate").toString().isBlank()
                ? LocalDate.parse(body.get("sendDate").toString().substring(0, 10)) : null;
        return Result.ok(service.send(id, sendDate, (String) body.get("expressNo")));
    }

    /** 客户反馈：satisfied=true 客户满意 / false 需调整（回炉调色） */
    @PostMapping("/{id}/feedback")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> feedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean satisfied = Boolean.parseBoolean(String.valueOf(body.get("satisfied")));
        LocalDate feedbackDate = body.get("feedbackDate") != null && !body.get("feedbackDate").toString().isBlank()
                ? LocalDate.parse(body.get("feedbackDate").toString().substring(0, 10)) : null;
        return Result.ok(service.feedback(id, satisfied, (String) body.get("content"), feedbackDate));
    }

    /** 转单（填成交的销售订单号） */
    @PostMapping("/{id}/win")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> win(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.win(id, body.get("orderNo")));
    }

    /** 标记未成交 */
    @PostMapping("/{id}/lose")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> lose(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.lose(id, body.get("reason")));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "sample:write")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
}
