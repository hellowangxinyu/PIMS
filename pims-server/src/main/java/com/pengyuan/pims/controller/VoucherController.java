package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengyuan.pims.common.ExcelUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.service.UserService;
import com.pengyuan.pims.service.VoucherService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 记账凭证（v5.61 总账体系）：凭证 CRUD/记账 + 业务转凭证 + 结转损益 + 期间结账 */
@RestController
@RequestMapping("/api/voucher")
public class VoucherController {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final VoucherService service;
    private final UserService userService;

    public VoucherController(VoucherService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<Voucher> list = service.list();
        if (!FieldFilter.hasAmountPerm("voucher")) {
            return Result.ok(filterAmounts(list));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<Voucher> create(@RequestBody Voucher v) {
        if (v.createdBy == null || v.createdBy.isBlank()) v.createdBy = userService.currentOperatorName();  // v5.60 制单人
        return Result.ok(service.create(v));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<Voucher> update(@PathVariable Long id, @RequestBody Voucher v) {
        return Result.ok(service.update(id, v));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 记账（DRAFT → POSTED），启用预留权限码 finance:audit */
    @PutMapping("/{id}/post")
    @SaCheckPermission(value = {"voucher:audit", "finance:audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<Voucher> post(@PathVariable Long id) {
        return Result.ok(service.post(id, userService.currentOperatorName()));
    }

    /** 反记账（POSTED → DRAFT），启用预留权限码 finance:reverse-audit */
    @PutMapping("/{id}/unpost")
    @SaCheckPermission(value = {"voucher:reverse-audit", "finance:reverse-audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<Voucher> unpost(@PathVariable Long id) {
        return Result.ok(service.unpost(id));
    }

    /** 业务单据转凭证：生成预览数据（前端弹窗可微调，确认后 POST /api/voucher 落库） */
    @PostMapping("/generate")
    @SaCheckPermission("finance:write")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String sourceType = String.valueOf(body.get("sourceType"));
        Long refId = Long.valueOf(String.valueOf(body.get("refId")));
        return Result.ok(service.generate(sourceType, refId));
    }

    /** 结转损益：生成结转凭证（DRAFT） */
    @PostMapping("/transfer-profit")
    @SaCheckPermission("finance:write")
    public Result<Map<String, Object>> transferProfit(@RequestBody Map<String, String> body) {
        return Result.ok(service.transferProfit(body.get("period"), userService.currentOperatorName()));
    }

    /** 期间状态列表（各月凭证数/草稿数/结账状态/未结转损益） */
    @GetMapping("/period-status")
    @SaCheckPermission("finance:read")
    public Result<?> periodStatus() { return Result.ok(service.periodStatus()); }

    /** v7.0 年结：结平本年利润→未分配利润 + 12 月月结（前置校验 1-11 月已结/损益已转） */
    @PostMapping("/year-end-close")
    @SaCheckPermission("finance:audit")
    public Result<Map<String, Object>> yearEndClose(@RequestParam String year) {
        return Result.ok(service.yearEndClose(year, userService.currentOperatorName()));
    }

    @PutMapping("/close-period")
    @SaCheckPermission("finance:write")
    public Result<?> closePeriod(@RequestBody Map<String, String> body) {
        service.closePeriod(body.get("period"), userService.currentOperatorName());
        return Result.ok(body.get("period") + " 已结账");
    }

    @PutMapping("/reopen-period")
    @SaCheckPermission("finance:write")
    public Result<?> reopenPeriod(@RequestBody Map<String, String> body) {
        service.reopenPeriod(body.get("period"));
        return Result.ok("已反结账");
    }

    @GetMapping("/export")
    @SaCheckPermission("finance:read")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("voucher");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Voucher v : service.list()) {
            StringBuilder sb = new StringBuilder();
            if (v.entries != null) {
                for (var e : v.entries) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(e.subjectCode).append(" ").append(e.subjectName)
                      .append(" 借").append(e.debit == null ? 0 : e.debit).append(" 贷").append(e.credit == null ? 0 : e.credit);
                }
            }
            if (amtPerm) {
                rows.add(new Object[]{ v.docNo, v.voucherDate, "POSTED".equals(v.status) ? "已记账" : "草稿",
                        sourceLabel(v.source), v.refDocNo, v.createdBy, v.totalDebit, sb.toString(), v.remark });
            } else {
                rows.add(new Object[]{ v.docNo, v.voucherDate, "POSTED".equals(v.status) ? "已记账" : "草稿",
                        sourceLabel(v.source), v.refDocNo, v.createdBy, sb.toString(), v.remark });
            }
        }
        ExcelUtil.export(response, "记账凭证-" + java.time.LocalDate.now(), "记账凭证",
                amtPerm ? new String[]{"凭证号","日期","状态","来源","来源单号","制单人","合计金额","分录","备注"}
                        : new String[]{"凭证号","日期","状态","来源","来源单号","制单人","分录","备注"},
                rows);
    }

    private String sourceLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "RECEIPT" -> "收款单";
            case "PAYMENT" -> "付款单";
            case "EXPENSE" -> "费用单";
            case "INVOICE" -> "发票";
            case "TRANSFER" -> "结转损益";
            default -> "手工";
        };
    }

    /** 凭证金额脱敏（分录金额嵌套在 entries 里，FieldFilter 处理不了嵌套，手工转 Map 深度过滤） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterAmounts(List<Voucher> list) {
        return list.stream().map(v -> {
            Map<String, Object> map = mapper.convertValue(v, Map.class);
            map.remove("totalDebit");
            map.remove("totalCredit");
            Object entries = map.get("entries");
            if (entries instanceof List<?> es) {
                for (Object o : es) {
                    if (o instanceof Map) {
                        ((Map<String, Object>) o).remove("debit");
                        ((Map<String, Object>) o).remove("credit");
                    }
                }
            }
            return map;
        }).toList();
    }
}
