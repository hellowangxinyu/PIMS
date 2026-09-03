package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.CrmContact;
import com.pengyuan.pims.entity.CrmFollowUp;
import com.pengyuan.pims.entity.CrmOpportunity;
import com.pengyuan.pims.service.CrmService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** CRM 客户经营（v5.50）：联系人 / 商机管道 / 跟进记录（权限 crm:*） */
@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService service;
    private final UserService userService;
    public CrmController(CrmService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    // ===== 联系人 =====

    @GetMapping("/contact")
    @SaCheckPermission("crm:read")
    public Result<List<CrmContact>> listContacts() { return Result.ok(service.listContacts()); }

    @PostMapping("/contact")
    @SaCheckPermission("crm:write")
    public Result<CrmContact> createContact(@RequestBody CrmContact c) { return Result.ok(service.createContact(c)); }

    @PutMapping("/contact/{id}")
    @SaCheckPermission("crm:write")
    public Result<CrmContact> updateContact(@PathVariable Long id, @RequestBody CrmContact c) {
        return Result.ok(service.updateContact(id, c));
    }

    @DeleteMapping("/contact/{id}")
    @SaCheckPermission("crm:write")
    public Result<?> deleteContact(@PathVariable Long id) {
        service.deleteContact(id);
        return Result.ok("删除成功");
    }

    // ===== 商机 =====

    @GetMapping("/opportunity")
    @SaCheckPermission("crm:read")
    public Result<List<CrmOpportunity>> listOpportunities() { return Result.ok(service.listOpportunities()); }

    @GetMapping("/pipeline-summary")
    @SaCheckPermission("crm:read")
    public Result<Map<String, Object>> pipelineSummary() { return Result.ok(service.pipelineSummary()); }

    @PostMapping("/opportunity")
    @SaCheckPermission("crm:write")
    public Result<CrmOpportunity> createOpportunity(@RequestBody CrmOpportunity o) {
        return Result.ok(service.createOpportunity(o, userService.currentOperatorName()));
    }

    @PutMapping("/opportunity/{id}")
    @SaCheckPermission("crm:write")
    public Result<CrmOpportunity> updateOpportunity(@PathVariable Long id, @RequestBody CrmOpportunity o) {
        return Result.ok(service.updateOpportunity(id, o));
    }

    /** 阶段流转（body：{stage, wonOrderNo?, lossReason?}） */
    @PostMapping("/opportunity/{id}/stage")
    @SaCheckPermission("crm:write")
    public Result<CrmOpportunity> changeStage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.changeStage(id, body.get("stage"), body.get("wonOrderNo"), body.get("lossReason")));
    }

    @DeleteMapping("/opportunity/{id}")
    @SaCheckPermission("crm:write")
    public Result<?> deleteOpportunity(@PathVariable Long id) {
        service.deleteOpportunity(id);
        return Result.ok("删除成功");
    }

    // ===== 跟进记录 =====

    @GetMapping("/follow-up")
    @SaCheckPermission("crm:read")
    public Result<List<CrmFollowUp>> listFollowUps(@RequestParam(required = false) Long opportunityId,
                                                   @RequestParam(required = false) Long customerId) {
        return Result.ok(service.listFollowUps(opportunityId, customerId));
    }

    @PostMapping("/follow-up")
    @SaCheckPermission("crm:write")
    public Result<CrmFollowUp> createFollowUp(@RequestBody CrmFollowUp f) {
        return Result.ok(service.createFollowUp(f, userService.currentOperatorName()));
    }

    @DeleteMapping("/follow-up/{id}")
    @SaCheckPermission("crm:write")
    public Result<?> deleteFollowUp(@PathVariable Long id) {
        service.deleteFollowUp(id);
        return Result.ok("删除成功");
    }
}
