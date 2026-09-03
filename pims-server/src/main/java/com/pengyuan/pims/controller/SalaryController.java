package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengyuan.pims.common.ExcelUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.SalaryItem;
import com.pengyuan.pims.entity.SalarySheet;
import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.service.SalaryService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 工资核算（v5.62）：月度工资单 + 计提/发放凭证 */
@RestController
@RequestMapping("/api/salary")
public class SalaryController {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final SalaryService service;
    private final UserService userService;

    public SalaryController(SalaryService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<SalarySheet> list = service.list();
        if (!FieldFilter.hasAmountPerm("salary")) {
            return Result.ok(filterAmounts(list));
        }
        return Result.ok(list);
    }

    /** 新建期间工资单（自动带出在职员工） */
    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<SalarySheet> create(@RequestBody Map<String, String> body) {
        return Result.ok(service.create(body.get("period"), userService.currentOperatorName()));
    }

    /** 保存明细（仅草稿） */
    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<SalarySheet> update(@PathVariable Long id, @RequestBody Map<String, List<SalaryItem>> body) {
        return Result.ok(service.update(id, body.get("items")));
    }

    @PutMapping("/{id}/confirm")
    @SaCheckPermission("finance:write")
    public Result<SalarySheet> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id, userService.currentOperatorName()));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }

    /** 生成计提凭证（DRAFT，到会计凭证页记账） */
    @PostMapping("/{id}/accrual-voucher")
    @SaCheckPermission("finance:write")
    public Result<Voucher> accrualVoucher(@PathVariable Long id) {
        return Result.ok(service.genAccrualVoucher(id));
    }

    /** 生成发放凭证（DRAFT） */
    @PostMapping("/{id}/pay-voucher")
    @SaCheckPermission("finance:write")
    public Result<Voucher> payVoucher(@PathVariable Long id) {
        return Result.ok(service.genPayVoucher(id));
    }

    @GetMapping("/export")
    @SaCheckPermission("finance:read")
    public void export(@RequestParam String period, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        boolean amt = FieldFilter.hasAmountPerm("salary");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (SalarySheet s : service.list()) {
            if (!s.period.equals(period)) continue;
            for (SalaryItem i : s.items) {
                rows.add(new Object[]{ i.employeeName, deptLabel(i.dept), i.base, i.bonus, i.piecework,
                        i.deduction, amt ? i.gross : "", i.socialIns, i.incomeTax, amt ? i.net : "" });
            }
            if (amt) {
                rows.add(new Object[]{ "合计", "", s.totalGross, "", "", "", s.totalGross, "", "", s.totalNet });
            }
        }
        ExcelUtil.export(response, "工资表-" + period, "工资表",
                new String[]{"姓名","部门","基本工资","奖金补贴","计件","扣款","应发","代扣社保","代扣个税","实发"}, rows);
    }

    private String deptLabel(String dept) {
        return switch (dept == null ? "" : dept) {
            case "PRODUCTION" -> "生产";
            case "SALES" -> "销售";
            case "TECH" -> "技术";
            case "QC" -> "质检";
            case "OTHER" -> "其他";
            default -> "行政";
        };
    }

    /** 工资金额脱敏（明细嵌套在 items 里，手工转 Map 深度过滤，照 VoucherController 模式） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterAmounts(List<SalarySheet> list) {
        String[] itemAmountFields = { "base", "bonus", "piecework", "deduction", "socialIns", "incomeTax", "gross", "net" };
        return list.stream().map(s -> {
            Map<String, Object> map = mapper.convertValue(s, Map.class);
            map.remove("totalGross");
            map.remove("totalNet");
            Object items = map.get("items");
            if (items instanceof List<?> is) {
                for (Object o : is) {
                    if (o instanceof Map) {
                        for (String f : itemAmountFields) ((Map<String, Object>) o).remove(f);
                    }
                }
            }
            return map;
        }).toList();
    }
}
