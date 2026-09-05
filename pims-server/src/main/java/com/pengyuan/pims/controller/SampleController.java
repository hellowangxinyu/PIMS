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
    private final com.pengyuan.pims.repository.UserRepository userRepo;

    public SampleController(SampleService service, com.pengyuan.pims.repository.UserRepository userRepo) {
        this.service = service;
        this.userRepo = userRepo;
    }

    /** v7.7 派发人选：启用用户（username+姓名），sample:read 即可（内勤未必有 user:read） */
    @GetMapping("/assignees")
    @SaCheckPermission(value = "sample:read")
    public Result<List<java.util.Map<String, String>>> assignees() {
        List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        for (com.pengyuan.pims.entity.User u : userRepo.findByEnabledTrue()) {
            java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("username", u.username);
            m.put("realName", u.realName != null ? u.realName : u.username);
            list.add(m);
        }
        return Result.ok(list);
    }

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

    /** 开始调色（v7.7 起前端主入口为派发+接收；本端点保留兼容，不再有页面入口） */
    @PostMapping("/{id}/coloring")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> coloring(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.startColoring(id, body.get("colorist"), body.get("colorNote")));
    }

    // ==================== v7.7 打样任务/打样配方 ====================

    /** 派发打样任务（选打样员，重复调用=改派） */
    @PostMapping("/{id}/assign")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> assign(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.assign(id, body.get("assignee")));
    }

    /** 打样员接收任务（仅 assignee 本人） */
    @PostMapping("/{id}/accept")
    @SaCheckPermission(value = "sample:write")
    public Result<SampleRequest> accept(@PathVariable Long id) {
        return Result.ok(service.accept(id));
    }

    /** 保存打样配方（首次自动生成 C 类成品物料；覆盖前旧明细快照存档） */
    @PostMapping("/{id}/formula")
    @SaCheckPermission(value = "sample:write")
    public Result<java.util.Map<String, Object>> saveFormula(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(service.saveFormula(id, body));
    }

    /** 打样配方详情（未录过返回 null） */
    @GetMapping("/{id}/formula")
    @SaCheckPermission(value = "sample:read")
    public Result<java.util.Map<String, Object>> getFormula(@PathVariable Long id) {
        return Result.ok(service.getFormula(id));
    }

    /** 打样配方列表（转制漆下拉/管理） */
    @GetMapping("/formulas")
    @SaCheckPermission(value = "sample:read")
    public Result<List<java.util.Map<String, Object>>> formulas() {
        return Result.ok(service.listFormulas());
    }

    /** 转制漆前校验：色浆必须全部能匹配已发布制浆配方（严格拦截） */
    @GetMapping("/formula/{formulaId}/convert-check")
    @SaCheckPermission(value = "recipe:write")
    public Result<java.util.Map<String, Object>> convertCheck(@PathVariable Long formulaId) {
        return Result.ok(service.convertCheck(formulaId));
    }

    /** 打样配方一键转制漆配方（建 Recipe+V1.0 DRAFT+树预填，折算标准批量 100） */
    @PostMapping("/formula/{formulaId}/to-recipe")
    @SaCheckPermission(value = "recipe:write")
    public Result<java.util.Map<String, Object>> toRecipe(@PathVariable Long formulaId, @RequestBody Map<String, Object> body) {
        return Result.ok(service.toRecipe(formulaId, body));
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
