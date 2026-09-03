package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.PricePolicy;
import com.pengyuan.pims.service.PricePolicyService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 销售价格政策（v6.3 第二批）：按物料/大类维护阶梯价，下单自动带出 */
@RestController
@RequestMapping("/api/price-policy")
public class PricePolicyController {

    private final PricePolicyService service;

    public PricePolicyController(PricePolicyService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "sales:read")
    public Result<List<PricePolicy>> list() {
        return Result.ok(service.list());
    }

    /** 取价（下单带出）：{price, policyId, tier}；无适用政策 price=null（保持手填/最近成交价） */
    @GetMapping("/match")
    @SaCheckPermission(value = "sales:read")
    public Result<Map<String, Object>> match(@RequestParam String materialCode,
                                             @RequestParam(required = false) BigDecimal qty) {
        return Result.ok(service.match(materialCode, qty));
    }

    @PostMapping
    @SaCheckPermission(value = "sales:write")
    public Result<PricePolicy> create(@RequestBody PricePolicy p) {
        return Result.ok(service.create(p));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "sales:write")
    public Result<PricePolicy> update(@PathVariable Long id, @RequestBody PricePolicy p) {
        return Result.ok(service.update(id, p));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "sales:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("已删除");
    }
}
