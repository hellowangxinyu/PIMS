package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.service.FinanceService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * 财务查询与资金操作入口：AR/AP 台账、收款/付款核销与单据、金额权限字段过滤（finance:amount 系列）。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService service;
    private final UserService userService;
    public FinanceController(FinanceService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping("/ar")
    @SaCheckPermission(value = "finance:read")
    public Result<?> listAR() {
        List<AccountsReceivable> list = service.listAR();
        if (!FieldFilter.hasAmountPerm("finance-ar")) {
            return Result.ok(FieldFilter.filterListFields(list, FieldFilter.FINANCE_AMOUNT_FIELDS));
        }
        return Result.ok(list);
    }

    @GetMapping("/ap")
    @SaCheckPermission(value = "finance:read")
    public Result<?> listAP() {
        List<AccountsPayable> list = service.listAP();
        if (!FieldFilter.hasAmountPerm("finance-ar")) {
            return Result.ok(FieldFilter.filterListFields(list, FieldFilter.FINANCE_AMOUNT_FIELDS));
        }
        return Result.ok(list);
    }

    /** 应收总表：按客户维度聚合（不分订单） */
    @GetMapping("/ar/total")
    @SaCheckPermission(value = "finance:read")
    public Result<?> listARTotal() {
        return Result.ok(service.listARTotalByCustomer());
    }

    /** 应付总表：按供应商维度聚合（不分订单） */
    @GetMapping("/ap/total")
    @SaCheckPermission(value = "finance:read")
    public Result<?> listAPTotal() {
        return Result.ok(service.listAPTotalBySupplier());
    }

    @PostMapping("/ar")
    @SaCheckPermission(value = "finance:write")
    public Result<AccountsReceivable> createAR(@RequestBody AccountsReceivable ar) {
        return Result.ok(service.createAR(ar));
    }

    @PostMapping("/ap")
    @SaCheckPermission(value = "finance:write")
    public Result<AccountsPayable> createAP(@RequestBody AccountsPayable ap) {
        return Result.ok(service.createAP(ap));
    }

    @PostMapping("/ar/{id}/receive")
    @SaCheckPermission(value = "finance:write")
    public Result<?> receivePayment(@PathVariable Long id, @RequestParam String amount) {
        // v6.1.4：double 传参有浮点尾差（0.1+0.2 类误差可进账），改字符串直转 BigDecimal
        service.receivePayment(id, new BigDecimal(amount));
        return Result.ok("回款成功");
    }

    @PostMapping("/ap/{id}/pay")
    @SaCheckPermission(value = "finance:write")
    public Result<?> makePayment(@PathVariable Long id, @RequestParam String amount) {
        service.makePayment(id, new BigDecimal(amount));   // v6.1.4 同上，字符串直转
        return Result.ok("付款成功");
    }

    // ============ 收款单 / 付款单 ============

    @GetMapping("/receipt")
    @SaCheckPermission(value = "finance:read")
    public Result<?> listReceipts() {
        List<PaymentReceipt> list = service.listReceipts();
        if (!FieldFilter.hasAmountPerm("finance-ar")) {
            return Result.ok(FieldFilter.filterListFields(list, FieldFilter.FINANCE_AMOUNT_FIELDS));
        }
        return Result.ok(list);
    }

    @GetMapping("/disbursement")
    @SaCheckPermission(value = "finance:read")
    public Result<?> listDisbursements() {
        List<PaymentDisbursement> list = service.listDisbursements();
        if (!FieldFilter.hasAmountPerm("finance-ar")) {
            return Result.ok(FieldFilter.filterListFields(list, FieldFilter.FINANCE_AMOUNT_FIELDS));
        }
        return Result.ok(list);
    }

    @PostMapping("/receipt")
    @SaCheckPermission(value = "finance:write")
    public Result<PaymentReceipt> createReceipt(@RequestBody PaymentReceipt r) {
        r.operator = userService.currentOperatorName();  // v5.60 经办人（此前列表有列但从不写入）
        return Result.ok(service.createReceipt(r));
    }

    @PostMapping("/disbursement")
    @SaCheckPermission(value = "finance:write")
    public Result<PaymentDisbursement> createDisbursement(@RequestBody PaymentDisbursement d) {
        d.operator = userService.currentOperatorName();  // v5.60 经办人
        return Result.ok(service.createDisbursement(d));
    }

    // ==================== 导出（v5.23，金额列受 finance:amount 权限控制，无权限省略） ====================

    private String arStatus(String s) {
        return switch (s == null ? "" : s) {
            case "UNPAID" -> "未收款";
            case "PARTIAL" -> "部分收款";
            case "PAID" -> "已结清";
            default -> s;
        };
    }

    private String apStatus(String s) {
        return switch (s == null ? "" : s) {
            case "UNPAID" -> "未付款";
            case "PARTIAL" -> "部分付款";
            case "PAID" -> "已结清";
            default -> s;
        };
    }

    private String methodText(String s) {
        return switch (s == null ? "" : s) {
            case "BANK" -> "银行";
            case "CASH" -> "现金";
            case "ACCEPTANCE" -> "承兑";
            default -> s;
        };
    }

    @GetMapping("/ar/export")
    @SaCheckPermission(value = "finance:read")
    public void exportAR(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("finance-ar");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (AccountsReceivable a : service.listAR()) {
            if (amtPerm) {
                java.math.BigDecimal remain = a.amount.subtract(a.receivedAmount == null ? java.math.BigDecimal.ZERO : a.receivedAmount);
                rows.add(new Object[]{
                        a.docNo, a.customerName, a.salesOrderNo, a.contractNo,
                        a.amount, a.receivedAmount, remain,
                        a.dueDate, arStatus(a.status), a.remark
                });
            } else {
                rows.add(new Object[]{
                        a.docNo, a.customerName, a.salesOrderNo, a.contractNo,
                        a.dueDate, arStatus(a.status), a.remark
                });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "应收明细-" + java.time.LocalDate.now(), "应收明细",
                amtPerm
                        ? new String[]{"单号", "客户", "销售订单号", "合同号", "应收金额", "已收金额", "剩余金额", "到期日", "状态", "备注"}
                        : new String[]{"单号", "客户", "销售订单号", "合同号", "到期日", "状态", "备注"},
                rows);
    }

    @GetMapping("/ap/export")
    @SaCheckPermission(value = "finance:read")
    public void exportAP(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("finance-ar");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (AccountsPayable a : service.listAP()) {
            String type = switch (a.payableType == null ? "" : a.payableType) {
                case "PURCHASE" -> "采购";
                case "OUTSOURCE" -> "委外加工";
                case "OTHER" -> "其他";
                default -> a.payableType;
            };
            if (amtPerm) {
                java.math.BigDecimal remain = a.amount.subtract(a.paidAmount == null ? java.math.BigDecimal.ZERO : a.paidAmount);
                rows.add(new Object[]{
                        a.docNo, a.supplierName, a.purchaseOrderNo, a.outsourceOrderNo, type,
                        a.amount, a.paidAmount, remain,
                        a.dueDate, apStatus(a.status), a.remark
                });
            } else {
                rows.add(new Object[]{
                        a.docNo, a.supplierName, a.purchaseOrderNo, a.outsourceOrderNo, type,
                        a.dueDate, apStatus(a.status), a.remark
                });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "应付明细-" + java.time.LocalDate.now(), "应付明细",
                amtPerm
                        ? new String[]{"单号", "供应商", "采购单号", "委外单号", "类型", "应付金额", "已付金额", "剩余金额", "到期日", "状态", "备注"}
                        : new String[]{"单号", "供应商", "采购单号", "委外单号", "类型", "到期日", "状态", "备注"},
                rows);
    }

    @GetMapping("/receipt/export")
    @SaCheckPermission(value = "finance:read")
    public void exportReceipt(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("finance-ar");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (PaymentReceipt r : service.listReceipts()) {
            if (amtPerm) {
                rows.add(new Object[]{
                        r.docNo, r.arDocNo, r.customerName, r.amount,
                        methodText(r.method), r.bankAccount, r.receiptDate, r.operator, r.remark
                });
            } else {
                rows.add(new Object[]{
                        r.docNo, r.arDocNo, r.customerName,
                        methodText(r.method), r.bankAccount, r.receiptDate, r.operator, r.remark
                });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "收款单-" + java.time.LocalDate.now(), "收款单",
                amtPerm
                        ? new String[]{"单号", "关联应收单", "客户", "收款金额", "收款方式", "银行账户", "收款日期", "经办人", "备注"}
                        : new String[]{"单号", "关联应收单", "客户", "收款方式", "银行账户", "收款日期", "经办人", "备注"},
                rows);
    }

    @GetMapping("/disbursement/export")
    @SaCheckPermission(value = "finance:read")
    public void exportDisbursement(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("finance-ar");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (PaymentDisbursement d : service.listDisbursements()) {
            if (amtPerm) {
                rows.add(new Object[]{
                        d.docNo, d.apDocNo, d.supplierName, d.amount,
                        methodText(d.method), d.bankAccount, d.payDate, d.operator, d.remark
                });
            } else {
                rows.add(new Object[]{
                        d.docNo, d.apDocNo, d.supplierName,
                        methodText(d.method), d.bankAccount, d.payDate, d.operator, d.remark
                });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "付款单-" + java.time.LocalDate.now(), "付款单",
                amtPerm
                        ? new String[]{"单号", "关联应付单", "供应商", "付款金额", "付款方式", "银行账户", "付款日期", "经办人", "备注"}
                        : new String[]{"单号", "关联应付单", "供应商", "付款方式", "银行账户", "付款日期", "经办人", "备注"},
                rows);
    }
}
