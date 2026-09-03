package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.CostService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成本核算（v5.36）：生产/委外订单成本归集 + 配方理论成本对比 + 人工制费补录
 * 与财务报表（利润试算/对账单）共用 finance 权限域。
 */
@RestController
@RequestMapping("/api/cost")
public class CostController {

    private static final String[] COST_FIELDS = {"materialCost", "outsourceFee", "laborFee", "overheadFee",
            "totalCost", "unitCost", "theoreticalCost", "costDiff"};

    private final CostService service;
    public CostController(CostService service) { this.service = service; }

    /** 订单成本列表，type=PRODUCTION(默认)/OUTSOURCE */
    @GetMapping("/order")
    @SaCheckPermission("finance:read")
    public Result<?> listOrder(@RequestParam(defaultValue = "PRODUCTION") String type) {
        List<Map<String, Object>> list = service.listOrderCost(type);
        if (!FieldFilter.hasAmountPerm("cost")) {
            return Result.ok(FieldFilter.filterListFields(list, COST_FIELDS));
        }
        return Result.ok(list);
    }

    /** 生产订单人工/制费补录 */
    @PutMapping("/order/{orderNo}/fees")
    @SaCheckPermission("finance:write")
    public Result<?> updateFees(@PathVariable String orderNo,
                                @RequestParam BigDecimal laborFee,
                                @RequestParam BigDecimal overheadFee) {
        service.updateFees(orderNo, laborFee, overheadFee);
        return Result.ok("保存成功");
    }
}
