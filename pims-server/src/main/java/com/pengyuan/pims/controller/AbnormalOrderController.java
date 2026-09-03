package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.ProductionOrderException;
import com.pengyuan.pims.service.AbnormalOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 异常订单处理 API
 * 投入产出比 < 95% 的已完工订单：列表、处置闭环、导出
 */
@RestController
@RequestMapping("/api/abnormal-order")
public class AbnormalOrderController {

    private final AbnormalOrderService abnormalOrderService;

    public AbnormalOrderController(AbnormalOrderService abnormalOrderService) {
        this.abnormalOrderService = abnormalOrderService;
    }

    /** 异常订单列表（已完工且投出比<95%，附处置状态） */
    @GetMapping
    @SaCheckPermission("production:read")
    public Result<Map<String, Object>> list() {
        return Result.ok(abnormalOrderService.list());
    }

    /** 异常订单处置：填写原因/措施/状态，提交闭环 */
    @PostMapping("/handle")
    @SaCheckPermission("production:write")
    public Result<ProductionOrderException> handle(@RequestBody Map<String, String> body) {
        String operator = body.get("handler");
        if (operator == null || operator.isBlank()) operator = StpUtil.getLoginIdAsString();
        ProductionOrderException ex = abnormalOrderService.handle(
                body.get("orderNo"), body.get("reason"), body.get("measure"),
                body.get("status"), body.get("remark"), operator);
        return Result.ok(ex);
    }

    /** 查询单个订单的处置记录 */
    @GetMapping("/{orderNo}")
    @SaCheckPermission("production:read")
    public Result<ProductionOrderException> detail(@PathVariable String orderNo) {
        return Result.ok(abnormalOrderService.get(orderNo));
    }

    /** 导出异常订单 Excel（与列表同口径） */
    @GetMapping("/export")
    @SaCheckPermission("production:read")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        abnormalOrderService.export(response);
    }
}
