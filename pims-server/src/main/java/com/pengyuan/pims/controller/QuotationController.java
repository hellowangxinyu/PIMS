package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Quotation;
import com.pengyuan.pims.entity.QuotationItem;
import com.pengyuan.pims.service.QuotationService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v5.52 报价单管理。权限沿用 sales:read/write（销售岗自然可见，无需新权限码）。
 */
@RestController
@RequestMapping("/api/quotation")
public class QuotationController {

    private final QuotationService service;
    private final UserService userService;

    public QuotationController(QuotationService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission(value = "sales:read")
    public Result<List<Quotation>> list(@RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) String status) {
        return Result.ok(service.list(customerId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "sales:read")
    public Result<Quotation> detail(@PathVariable Long id) { return Result.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    @SaCheckPermission(value = "sales:read")
    public Result<List<QuotationItem>> items(@PathVariable Long id) { return Result.ok(service.getItems(id)); }

    @PostMapping
    @SaCheckPermission(value = "sales:write")
    public Result<Quotation> create(@RequestBody Map<String, Object> body) {
        Quotation q = parse(body);
        q.createdBy = userService.currentOperatorName();  // v5.60 制单人（服务端注入，不信任前端）
        return Result.ok(service.create(q, parseItems(body)));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "sales:write")
    public Result<Quotation> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(service.update(id, parse(body), parseItems(body)));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "sales:write")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }

    /** 提交报价（DRAFT → QUOTED，开始计算有效期） */
    @PostMapping("/{id}/submit")
    @SaCheckPermission(value = "sales:write")
    public Result<Quotation> submit(@PathVariable Long id) { return Result.ok(service.submit(id)); }

    /** 标记客户未接受（QUOTED → REJECTED） */
    @PostMapping("/{id}/reject")
    @SaCheckPermission(value = "sales:write")
    public Result<Quotation> reject(@PathVariable Long id) { return Result.ok(service.reject(id)); }

    /** 转销售订单（QUOTED 且未过期，生成 DRAFT 订单） */
    @PostMapping("/{id}/to-order")
    @SaCheckPermission(value = "sales:write")
    public Result<Quotation> toOrder(@PathVariable Long id) { return Result.ok(service.toOrder(id)); }

    private Quotation parse(Map<String, Object> body) {
        Quotation q = new Quotation();
        if (body.get("customerId") == null) throw new IllegalArgumentException("请选择客户");
        q.customerId = ((Number) body.get("customerId")).longValue();
        q.customerName = (String) body.get("customerName");
        if (body.get("validUntil") != null && !body.get("validUntil").toString().isBlank())
            q.validUntil = LocalDate.parse(body.get("validUntil").toString().substring(0, 10));
        q.remark = (String) body.get("remark");
        q.createdBy = (String) body.get("createdBy");
        return q;
    }

    private List<QuotationItem> parseItems(Map<String, Object> body) {
        List<QuotationItem> items = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                QuotationItem item = new QuotationItem();
                item.materialCode = (String) m.get("materialCode");
                item.materialName = (String) m.get("materialName");
                if (item.materialCode == null || item.materialCode.isBlank()) continue;
                item.qty = m.get("qty") != null ? BigDecimal.valueOf(((Number) m.get("qty")).doubleValue()) : null;
                item.unit = (String) m.get("unit");
                item.unitPrice = m.get("unitPrice") != null ? BigDecimal.valueOf(((Number) m.get("unitPrice")).doubleValue()) : null;
                item.remark = (String) m.get("remark");
                items.add(item);
            }
        }
        return items;
    }
}
