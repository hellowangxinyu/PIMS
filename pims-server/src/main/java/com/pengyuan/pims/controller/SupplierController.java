package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Supplier;
import com.pengyuan.pims.service.SupplierService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商管理接口
 * 支持按类型（MATERIAL/FINISHED）过滤
 */
@RestController
@RequestMapping("/api/supplier")
public class SupplierController {

    private final SupplierService service;
    private final com.pengyuan.pims.service.ExcelImportService excelImportService;
    private final com.pengyuan.pims.service.UserService userService;
    private final com.pengyuan.pims.repository.AccountsPayableRepository apRepo;
    private final com.pengyuan.pims.repository.PaymentDisbursementRepository disbursementRepo;
    private final com.pengyuan.pims.repository.InvoiceRepository invoiceRepo;
    private final com.pengyuan.pims.repository.PurchaseOrderRepository purchaseOrderRepo;
    private final com.pengyuan.pims.repository.ReturnOrderRepository returnRepo;

    public SupplierController(SupplierService service,
                              com.pengyuan.pims.service.ExcelImportService excelImportService,
                              com.pengyuan.pims.service.UserService userService,
                              com.pengyuan.pims.repository.AccountsPayableRepository apRepo,
                              com.pengyuan.pims.repository.PaymentDisbursementRepository disbursementRepo,
                              com.pengyuan.pims.repository.InvoiceRepository invoiceRepo,
                              com.pengyuan.pims.repository.PurchaseOrderRepository purchaseOrderRepo,
                              com.pengyuan.pims.repository.ReturnOrderRepository returnRepo) {
        this.service = service;
        this.excelImportService = excelImportService;
        this.userService = userService;
        this.apRepo = apRepo;
        this.disbursementRepo = disbursementRepo;
        this.invoiceRepo = invoiceRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.returnRepo = returnRepo;
    }

    /** v5.56 供应商 Excel 导入模板 */
    @GetMapping("/import/template")
    @SaCheckPermission(value = "supplier:write")
    public void importTemplate(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        excelImportService.downloadSupplierTemplate(response);
    }

    /** v5.56 供应商 Excel 导入（全量校验，任一错整体拒绝；通过则单事务落库） */
    @org.springframework.web.bind.annotation.PostMapping("/import")
    @SaCheckPermission(value = "supplier:write")
    public Result<?> importExcel(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            int count = excelImportService.importSuppliers(file, userService.currentOperatorName());
            return Result.ok(java.util.Map.of("count", count));
        } catch (com.pengyuan.pims.common.ExcelImportException e) {
            return new Result<>(400, e.getMessage(), e.getErrors());
        }
    }

    /** v5.49 供应商 360°：基础信息 + 应付/付款/进项发票汇总 + 最近采购订单/付款/退货 */
    @GetMapping("/{id}/profile")
    @SaCheckPermission(value = "supplier:read")
    public Result<java.util.Map<String, Object>> profile(@PathVariable Long id) {
        var c = service.getById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("supplier", c);

        java.math.BigDecimal apTotal = java.math.BigDecimal.ZERO, paid = java.math.BigDecimal.ZERO;
        var aps = apRepo.findBySupplierId(id);
        int unpaidCount = 0;
        for (var ap : aps) {
            apTotal = apTotal.add(ap.amount == null ? java.math.BigDecimal.ZERO : ap.amount);
            paid = paid.add(ap.paidAmount == null ? java.math.BigDecimal.ZERO : ap.paidAmount);
            if (!"PAID".equals(ap.status)) unpaidCount++;
        }
        m.put("apTotal", apTotal);
        m.put("apPaid", paid);
        m.put("apBalance", apTotal.subtract(paid));
        m.put("apUnpaidCount", unpaidCount);

        var invs = invoiceRepo.findByPartnerIdAndDirectionOrderByCreateTimeAsc(id, "INPUT");
        java.math.BigDecimal invAmount = java.math.BigDecimal.ZERO;
        for (var i : invs) invAmount = invAmount.add(i.totalAmount == null ? java.math.BigDecimal.ZERO : i.totalAmount);
        m.put("invoiceNetTotal", invAmount);

        var pays = disbursementRepo.findBySupplierIdOrderByCreateTimeDesc(id);
        java.math.BigDecimal payTotal = java.math.BigDecimal.ZERO;
        for (var r : pays) payTotal = payTotal.add(r.amount == null ? java.math.BigDecimal.ZERO : r.amount);
        m.put("payTotal", payTotal);

        var orders = purchaseOrderRepo.findBySupplierIdOrderByCreateTimeDesc(id);
        m.put("orderCount", orders.size());
        m.put("recentOrders", orders.stream().limit(8).toList());
        m.put("recentPayments", pays.stream().limit(8).toList());
        m.put("recentReturns", returnRepo.findBySupplierIdOrderByCreateTimeDesc(id).stream().limit(5).toList());
        return Result.ok(m);
    }

    /**
     * 供应商列表
     * @param keyword 名称搜索关键词
     * @param type 供应商类型过滤（MATERIAL/FINISHED/PROCESSOR），不传则返回全部
     * @param enabled true=仅启用（下拉选择用，拉黑/删除的自动不可见）；不传=管理列表显示全部（含拉黑，便于解除）
     */
    @GetMapping
    @SaCheckPermission(value = "supplier:read")
    public Result<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean enabled) {
        List<Supplier> result;
        boolean hasType = type != null && !type.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasType && hasKeyword) {
            result = Boolean.TRUE.equals(enabled) ? service.searchByTypeEnabled(type, keyword) : service.searchByType(type, keyword);
        } else if (hasType) {
            result = Boolean.TRUE.equals(enabled) ? service.listByType(type) : service.listByTypeAll(type);
        } else if (hasKeyword) {
            result = Boolean.TRUE.equals(enabled) ? service.searchEnabled(keyword) : service.search(keyword);
        } else {
            result = Boolean.TRUE.equals(enabled) ? service.listEnabled() : service.listAll();
        }
        if (!FieldFilter.hasPerm("supplier:payment")) {
            return Result.ok(FieldFilter.filterListFields(result, FieldFilter.SUPPLIER_PAYMENT_FIELDS));
        }
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "supplier:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getById(id).map(item -> {
            if (!FieldFilter.hasPerm("supplier:payment")) {
                return Result.ok(FieldFilter.filterFields(item, FieldFilter.SUPPLIER_PAYMENT_FIELDS));
            }
            return Result.ok(item);
        }).orElse(Result.fail(500, "供应商不存在"));
    }

    @PostMapping
    @SaCheckPermission(value = "supplier:write")
    public Result<Supplier> create(@RequestBody Supplier s) {
        return Result.ok(service.create(s));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "supplier:write")
    public Result<Supplier> update(@PathVariable Long id, @RequestBody Supplier s) {
        return Result.ok(service.update(id, s));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "supplier:delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * v5.27：拉黑 / 解除拉黑
     * @param body {blacklisted: true=拉黑, false=解除}，拉黑即禁用（采购/委外选择时不可见）
     */
    @PostMapping("/{id}/blacklist")
    @SaCheckPermission(value = "supplier:write")
    public Result<Supplier> blacklist(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        boolean blacklisted = body.get("blacklisted") != null && Boolean.parseBoolean(body.get("blacklisted").toString());
        return Result.ok(service.setBlacklisted(id, blacklisted));
    }
}
