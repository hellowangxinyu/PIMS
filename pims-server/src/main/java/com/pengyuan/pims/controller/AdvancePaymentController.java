package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.ExcelUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.AdvancePayment;
import com.pengyuan.pims.service.AdvancePaymentService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 预收/预付款（v5.36）：登记 + 冲抵应收/应付 */
@RestController
@RequestMapping("/api/advance")
public class AdvancePaymentController {

    private static final String[] AMOUNT_FIELDS = {"amount", "usedAmount"};

    private final AdvancePaymentService service;
    private final UserService userService;
    public AdvancePaymentController(AdvancePaymentService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<AdvancePayment> list = service.list();
        if (!FieldFilter.hasAmountPerm("advance")) {
            return Result.ok(FieldFilter.filterListFields(list, AMOUNT_FIELDS));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<AdvancePayment> create(@RequestBody AdvancePayment a) {
        a.createdBy = userService.currentOperatorName();  // v5.60 制单人
        return Result.ok(service.create(a));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 预收冲应收 */
    @PostMapping("/{id}/apply-ar")
    @SaCheckPermission("finance:write")
    public Result<?> applyToAr(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long arId = Long.valueOf(String.valueOf(body.get("arId")));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        service.applyToAr(id, arId, amount);
        return Result.ok("冲抵成功");
    }

    /** 预付冲应付 */
    @PostMapping("/{id}/apply-ap")
    @SaCheckPermission("finance:write")
    public Result<?> applyToAp(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long apId = Long.valueOf(String.valueOf(body.get("apId")));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        service.applyToAp(id, apId, amount);
        return Result.ok("冲抵成功");
    }

    @GetMapping("/export")
    @SaCheckPermission("finance:read")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("advance");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (AdvancePayment a : service.list()) {
            String remain = amtPerm && a.amount != null && a.usedAmount != null
                    ? a.amount.subtract(a.usedAmount).toPlainString() : "";
            if (amtPerm) {
                rows.add(new Object[]{ a.docNo, "RECEIVE".equals(a.direction) ? "预收" : "预付", a.partnerName,
                        a.amount, a.usedAmount, remain, a.payDate, statusLabel(a.status), a.remark });
            } else {
                rows.add(new Object[]{ a.docNo, "RECEIVE".equals(a.direction) ? "预收" : "预付", a.partnerName,
                        a.payDate, statusLabel(a.status), a.remark });
            }
        }
        ExcelUtil.export(response, "预收预付-" + java.time.LocalDate.now(), "预收预付",
                amtPerm ? new String[]{"单号","方向","往来单位","金额","已冲抵","剩余","日期","状态","备注"}
                        : new String[]{"单号","方向","往来单位","日期","状态","备注"},
                rows);
    }

    private String statusLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "UNUSED" -> "未使用";
            case "PARTIAL" -> "部分冲抵";
            case "USED" -> "已用完";
            default -> s;
        };
    }
}
