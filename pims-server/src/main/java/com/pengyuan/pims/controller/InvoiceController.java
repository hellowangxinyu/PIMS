package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.ExcelUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Invoice;
import com.pengyuan.pims.service.InvoiceService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 发票管理（v5.36）：登记/编辑/红冲/客户开票汇总/导出 */
@RestController
@RequestMapping("/api/invoice")
public class InvoiceController {

    private static final String[] AMOUNT_FIELDS = {"amount", "taxAmount", "totalAmount"};

    private final InvoiceService service;
    private final UserService userService;
    public InvoiceController(InvoiceService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<Invoice> list = service.list();
        if (!FieldFilter.hasAmountPerm("invoice")) {
            return Result.ok(FieldFilter.filterListFields(list, AMOUNT_FIELDS));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<Invoice> create(@RequestBody Invoice inv) {
        inv.createdBy = userService.currentOperatorName();  // v5.60 制单人
        return Result.ok(service.create(inv));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<Invoice> update(@PathVariable Long id, @RequestBody Invoice inv) {
        return Result.ok(service.update(id, inv));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = {"invoice:delete", "finance:write"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 红冲：生成负数对冲发票，原单标记已红冲 */
    @PostMapping("/{id}/red-flush")
    @SaCheckPermission("finance:write")
    public Result<Invoice> redFlush(@PathVariable Long id,
                                    @RequestParam(required = false) String redInvoiceNo,
                                    @RequestParam(required = false) String reason) {
        return Result.ok(service.redFlush(id, redInvoiceNo, reason));
    }

    /** 客户开票汇总：开票净额 vs 应收立账 vs 已回款 */
    @GetMapping("/customer-summary")
    @SaCheckPermission("finance:read")
    public Result<List<Map<String, Object>>> customerSummary() {
        if (!FieldFilter.hasAmountPerm("invoice")) return Result.ok(List.of());
        return Result.ok(service.customerSummary());
    }

    @GetMapping("/export")
    @SaCheckPermission("finance:read")
    public void export(jakarta.servlet.http.HttpServletResponse response,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String direction) throws java.io.IOException {
        boolean amtPerm = FieldFilter.hasAmountPerm("invoice");
        // v8.10.1：导出与页面筛选同口径（原无参全量导出）
        final String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        final String dir = direction == null ? "" : direction.trim();
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Invoice i : service.list()) {
            if (!dir.isEmpty() && !dir.equals(i.direction)) continue;
            if (!kw.isEmpty()) {
                String hay = String.valueOf(i.docNo) + "|" + i.invoiceNo + "|" + i.partnerName + "|" + i.partnerTaxNo + "|" + i.refOrderNo;
                if (!hay.toLowerCase().contains(kw)) continue;
            }
            if (amtPerm) {
                rows.add(new Object[]{ i.docNo, i.invoiceNo, "OUTPUT".equals(i.direction) ? "销项" : "进项",
                        i.partnerName, i.partnerTaxNo, i.amount, i.taxRate, i.taxAmount, i.totalAmount,
                        i.invoiceDate, "NORMAL".equals(i.status) ? "正常" : "已红冲", i.refOrderNo, i.remark });
            } else {
                rows.add(new Object[]{ i.docNo, i.invoiceNo, "OUTPUT".equals(i.direction) ? "销项" : "进项",
                        i.partnerName, i.partnerTaxNo, i.invoiceDate,
                        "NORMAL".equals(i.status) ? "正常" : "已红冲", i.refOrderNo, i.remark });
            }
        }
        ExcelUtil.export(response, "发票登记-" + java.time.LocalDate.now(), "发票登记",
                amtPerm ? new String[]{"系统单号","发票号码","方向","往来单位","税号","不含税金额","税率%","税额","价税合计","开票日期","状态","关联订单","备注"}
                        : new String[]{"系统单号","发票号码","方向","往来单位","税号","开票日期","状态","关联订单","备注"},
                rows);
    }
}
