package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.OtherInbound;
import com.pengyuan.pims.entity.ReturnOrder;
import com.pengyuan.pims.service.ReturnOrderService;
import com.pengyuan.pims.service.SalesReturnService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 销售退货管理 Controller
 * 流程：客户退货 → 手工创建退货单(DRAFT) → 销售员审核/驳回 → 仓管参照退货单入库（自动冲减应收）
 * v5.4 新增；与采购退货共用 return_order 表（type=SALES_RETURN，单号 SR-YYYY-NNNN）
 */
@RestController
@RequestMapping("/api/sales-return")
public class SalesReturnController {

    private final SalesReturnService service;
    private final ReturnOrderService returnOrderService; // 复用通用审核/驳回逻辑
    private final UserService userService;

    public SalesReturnController(SalesReturnService service,
                                 ReturnOrderService returnOrderService,
                                 UserService userService) {
        this.service = service;
        this.returnOrderService = returnOrderService;
        this.userService = userService;
    }

    /** 查询销售退货单（可选 status 过滤） */
    @GetMapping
    @SaCheckPermission(value = "sales:read")
    public Result<List<ReturnOrder>> list(@RequestParam(required = false) String status) {
        return Result.ok(service.list(status));
    }

    /** 退货单详情 */
    @GetMapping("/{id}")
    @SaCheckPermission(value = "sales:read")
    public Result<ReturnOrder> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok)
                .orElseThrow(() -> new IllegalArgumentException("退货单不存在"));
    }

    /** v5.27：可参照退货的销售出库单列表（已确认且剩余可退量 > 0） */
    @GetMapping("/returnable-outbounds")
    @SaCheckPermission(value = "sales:read")
    public Result<List<java.util.Map<String, Object>>> returnableOutbounds() {
        return Result.ok(service.listReturnableOutbounds());
    }

    /** 手工创建退货单（客户退货；v5.27 参照销售出库单，锁定批号与剩余可退量） */
    @PostMapping
    @SaCheckPermission(value = "sales:write")
    public Result<ReturnOrder> create(@RequestBody Map<String, Object> params) {
        Long customerId = params.get("customerId") != null ? Long.valueOf(params.get("customerId").toString()) : null;
        String customerName = (String) params.get("customerName");
        String salesOrderNo = (String) params.get("salesOrderNo");
        String refSalesOutboundNo = (String) params.get("refSalesOutboundNo");
        String batchNo = (String) params.get("batchNo");
        String materialCode = (String) params.get("materialCode");
        String materialName = (String) params.get("materialName");
        String unit = (String) params.get("unit");
        BigDecimal qty = params.get("qty") != null ? new BigDecimal(params.get("qty").toString()) : null;
        BigDecimal unitPrice = params.get("unitPrice") != null ? new BigDecimal(params.get("unitPrice").toString()) : null;
        String remark = (String) params.get("remark");
        String operator = userService.currentOperatorName();
        return Result.ok(service.create(customerId, customerName, salesOrderNo, refSalesOutboundNo, batchNo,
                materialCode, materialName, unit, qty, unitPrice, remark, operator));
    }

    /** 审核通过（销售员） */
    @PostMapping("/{id}/approve")
    @SaCheckPermission(value = "sales:write")
    public Result<ReturnOrder> approve(@PathVariable Long id,
                                       @RequestParam(required = false) String remark) {
        String approver = userService.currentOperatorName();
        return Result.ok(returnOrderService.approve(id, approver, remark));
    }

    /** 驳回（销售员） */
    @PostMapping("/{id}/reject")
    @SaCheckPermission(value = "sales:write")
    public Result<ReturnOrder> reject(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        String approver = userService.currentOperatorName();
        return Result.ok(returnOrderService.reject(id, approver, reason));
    }

    /** 退货入库（仓管参照退货单入库） */
    @PostMapping("/{id}/inbound")
    @SaCheckPermission(value = "sales:write")
    public Result<OtherInbound> inbound(@PathVariable Long id,
                                        @RequestParam String warehouseId,
                                        @RequestParam(required = false) String locationId) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.inboundFromReturn(id, warehouseId, locationId, operator));
    }
}
