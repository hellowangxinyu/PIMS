package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Customer;
import com.pengyuan.pims.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerService service;
    private final com.pengyuan.pims.repository.SalesOrderRepository salesOrderRepo;
    private final com.pengyuan.pims.repository.PaymentReceiptRepository receiptRepo;
    private final com.pengyuan.pims.repository.AccountsReceivableRepository arRepo;
    private final com.pengyuan.pims.repository.InvoiceRepository invoiceRepo;
    private final com.pengyuan.pims.repository.ReturnOrderRepository returnRepo;
    private final com.pengyuan.pims.service.FinanceService financeService;

    public CustomerController(CustomerService service,
                              com.pengyuan.pims.repository.SalesOrderRepository salesOrderRepo,
                              com.pengyuan.pims.repository.PaymentReceiptRepository receiptRepo,
                              com.pengyuan.pims.repository.AccountsReceivableRepository arRepo,
                              com.pengyuan.pims.repository.InvoiceRepository invoiceRepo,
                              com.pengyuan.pims.repository.ReturnOrderRepository returnRepo,
                              com.pengyuan.pims.service.FinanceService financeService) {
        this.service = service;
        this.salesOrderRepo = salesOrderRepo;
        this.receiptRepo = receiptRepo;
        this.arRepo = arRepo;
        this.invoiceRepo = invoiceRepo;
        this.returnRepo = returnRepo;
        this.financeService = financeService;
    }

    /** v5.52 信用检查：当前应收欠款 + 拟下单金额 对比信用额度（空/0=不限额），下单前预警 */
    @GetMapping("/{id}/credit-check")
    @SaCheckPermission(value = "customer:read")
    public Result<java.util.Map<String, Object>> creditCheck(@PathVariable Long id,
                                                             @RequestParam java.math.BigDecimal amount) {
        return Result.ok(financeService.creditCheck(id, amount));
    }

    /** v5.47 客户 360°：基础信息 + 应收/收款/开票汇总 + 最近订单/收款/退货 */
    @GetMapping("/{id}/profile")
    @SaCheckPermission(value = "customer:read")
    public Result<java.util.Map<String, Object>> profile(@PathVariable Long id) {
        var c = service.getById(id).orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("customer", c);

        // 应收汇总
        java.math.BigDecimal arTotal = java.math.BigDecimal.ZERO, received = java.math.BigDecimal.ZERO;
        var ars = arRepo.findByCustomerId(id);
        int unpaidCount = 0;
        for (var ar : ars) {
            arTotal = arTotal.add(ar.amount == null ? java.math.BigDecimal.ZERO : ar.amount);
            received = received.add(ar.receivedAmount == null ? java.math.BigDecimal.ZERO : ar.receivedAmount);
            if (!"PAID".equals(ar.status)) unpaidCount++;
        }
        m.put("arTotal", arTotal);
        m.put("arReceived", received);
        m.put("arBalance", arTotal.subtract(received));
        m.put("arUnpaidCount", unpaidCount);

        // 开票净额（销项，红冲自动抵消）
        var invs = invoiceRepo.findByPartnerIdAndDirectionOrderByCreateTimeAsc(id, "OUTPUT");
        java.math.BigDecimal invAmount = java.math.BigDecimal.ZERO;
        for (var i : invs) invAmount = invAmount.add(i.totalAmount == null ? java.math.BigDecimal.ZERO : i.totalAmount);
        m.put("invoiceNetTotal", invAmount);

        // 收款合计
        var receipts = receiptRepo.findByCustomerIdOrderByCreateTimeDesc(id);
        java.math.BigDecimal rcTotal = java.math.BigDecimal.ZERO;
        for (var r : receipts) rcTotal = rcTotal.add(r.amount == null ? java.math.BigDecimal.ZERO : r.amount);
        m.put("receiptTotal", rcTotal);

        // 最近单据（v5.55 改有界查询：count + topN，不再为取 8 条拉全量）
        m.put("orderCount", salesOrderRepo.countByCustomerId(id));
        m.put("recentOrders", salesOrderRepo.findByCustomerIdOrderByCreateTimeDesc(id,
                org.springframework.data.domain.PageRequest.of(0, 8)));
        m.put("recentReceipts", receipts.stream().limit(8).toList());
        m.put("recentReturns", returnRepo.findByCustomerIdOrderByCreateTimeDesc(id,
                org.springframework.data.domain.PageRequest.of(0, 5)));
        return Result.ok(m);
    }

    /**
     * 客户列表
     * @param keyword 名称搜索
     * @param enabled true=仅启用（销售下单下拉用，拉黑/删除的不可见）；不传=管理列表显示全部（含拉黑，便于解除）
     */
    @GetMapping
    @SaCheckPermission(value = "customer:read")
    public Result<List<Customer>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Boolean enabled) {
        if (keyword != null && !keyword.isBlank()) {
            return Result.ok(Boolean.TRUE.equals(enabled) ? service.searchEnabled(keyword) : service.search(keyword));
        }
        return Result.ok(Boolean.TRUE.equals(enabled) ? service.listEnabled() : service.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "customer:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok).orElse(Result.fail(500, "客户不存在"));
    }

    @PostMapping
    @SaCheckPermission(value = "customer:write")
    public Result<Customer> create(@RequestBody Customer c) { return Result.ok(service.create(c)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "customer:write")
    public Result<Customer> update(@PathVariable Long id, @RequestBody Customer c) {
        return Result.ok(service.update(id, c));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "customer:delete")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }

    /**
     * v5.27：拉黑 / 解除拉黑
     * @param body {blacklisted: true=拉黑, false=解除}，拉黑即禁用（销售下单选择客户时不可见）
     */
    @PostMapping("/{id}/blacklist")
    @SaCheckPermission(value = "customer:write")
    public Result<Customer> blacklist(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        boolean blacklisted = body.get("blacklisted") != null && Boolean.parseBoolean(body.get("blacklisted").toString());
        return Result.ok(service.setBlacklisted(id, blacklisted));
    }
}
