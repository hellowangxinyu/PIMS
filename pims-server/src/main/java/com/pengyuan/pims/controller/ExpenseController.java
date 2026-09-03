package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.ExcelUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Expense;
import com.pengyuan.pims.service.ExpenseService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 费用管理（v5.36）：无往来单据的其他收支 */
@RestController
@RequestMapping("/api/expense")
public class ExpenseController {

    private final ExpenseService service;
    private final UserService userService;
    public ExpenseController(ExpenseService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<Expense> list = service.list();
        if (!FieldFilter.hasAmountPerm("expense")) {
            return Result.ok(FieldFilter.filterListFields(list, "amount"));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<Expense> create(@RequestBody Expense e) {
        e.createdBy = userService.currentOperatorName();  // v5.60 制单人
        if (e.handler == null || e.handler.isBlank()) e.handler = e.createdBy;  // 经手人未填时默认制单人
        return Result.ok(service.create(e));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<Expense> update(@PathVariable Long id, @RequestBody Expense e) {
        return Result.ok(service.update(id, e));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = {"expense:delete", "finance:write"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    @GetMapping("/export")
    @SaCheckPermission("finance:read")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("expense");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Expense e : service.list()) {
            if (amtPerm) {
                rows.add(new Object[]{ e.docNo, "INCOME".equals(e.direction) ? "其他收入" : "支出", e.expenseType,
                        e.amount, e.occurDate, methodLabel(e.method), e.partner, e.handler, e.remark });
            } else {
                rows.add(new Object[]{ e.docNo, "INCOME".equals(e.direction) ? "其他收入" : "支出", e.expenseType,
                        e.occurDate, methodLabel(e.method), e.partner, e.handler, e.remark });
            }
        }
        ExcelUtil.export(response, "费用单-" + java.time.LocalDate.now(), "费用单",
                amtPerm ? new String[]{"单号","方向","费用类型","金额","发生日期","支付方式","往来对象","经手人","备注"}
                        : new String[]{"单号","方向","费用类型","发生日期","支付方式","往来对象","经手人","备注"},
                rows);
    }

    private String methodLabel(String m) {
        if (m == null) return "";
        return switch (m) {
            case "BANK" -> "银行转账";
            case "CASH" -> "现金";
            case "ACCEPTANCE" -> "承兑";
            case "WECHAT" -> "微信";
            default -> m;
        };
    }
}
