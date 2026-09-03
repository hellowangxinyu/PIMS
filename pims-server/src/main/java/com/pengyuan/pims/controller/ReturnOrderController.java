package com.pengyuan.pims.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.ReturnOrder;
import com.pengyuan.pims.service.ReturnOrderService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购退货单管理 Controller
 * 流程：QC 判定退货自动生成 → 采购员审核/驳回 → 仓管参照退货单出库（出库走 OutboundController）
 * v5.4：随菜单移至采购管理，权限改为 purchase:*；销售退货走 SalesReturnController
 */
@RestController
@RequestMapping("/api/return-order")
public class ReturnOrderController {

    private final ReturnOrderService service;
    private final UserService userService;

    public ReturnOrderController(ReturnOrderService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    /** 查询采购退货单（可选 status 过滤；v5.4 默认仅返回采购退货类型） */
    @GetMapping
    @SaCheckPermission(value = "purchase:read")
    public Result<List<ReturnOrder>> list(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "PURCHASE_RETURN") String type) {
        return Result.ok(status != null && !status.isBlank()
                ? service.listByStatus(type, status) : service.listAll(type));
    }

    /** 退货单详情 */
    @GetMapping("/{id}")
    @SaCheckPermission(value = "purchase:read")
    public Result<ReturnOrder> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok)
                .orElseThrow(() -> new IllegalArgumentException("退货单不存在"));
    }

    /** 待审核列表（采购员工作台） */
    @GetMapping("/pending")
    @SaCheckPermission(value = "purchase:read")
    public Result<List<ReturnOrder>> listPending() {
        return Result.ok(service.listByStatus("PURCHASE_RETURN", "DRAFT"));
    }

    /** v5.27：可参照退货的到货单列表（仅合格入库且批号仍有库存的） */
    @GetMapping("/returnable-arrivals")
    @SaCheckPermission(value = "purchase:read")
    public Result<List<java.util.Map<String, Object>>> returnableArrivals() {
        return Result.ok(service.listReturnableArrivals());
    }

    /** v5.27：到货单退货预览（带出物料/批号/可退量/单价，供新增退货单弹窗展示） */
    @GetMapping("/arrival-preview")
    @SaCheckPermission(value = "purchase:read")
    public Result<java.util.Map<String, Object>> arrivalPreview(@RequestParam Long arrivalId) {
        return Result.ok(service.arrivalPreview(arrivalId));
    }

    /** v5.27：手工创建采购退货单（参照到货单退货：自动带出物料/供应商/采购单/库存批号；审核通过后退货出库并冲减应付） */
    @PostMapping
    @SaCheckPermission(value = "purchase:write")
    public Result<ReturnOrder> create(@RequestParam Long arrivalId,
                                      @RequestParam double qty,
                                      @RequestParam(required = false) Double unitPrice,
                                      @RequestParam(required = false) String remark) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createManual(arrivalId,
                java.math.BigDecimal.valueOf(qty),
                unitPrice != null ? java.math.BigDecimal.valueOf(unitPrice) : null,
                remark, operator));
    }

    /** 审核通过（采购员） */
    @PostMapping("/{id}/approve")
    @SaCheckPermission(value = "purchase:write")
    public Result<ReturnOrder> approve(@PathVariable Long id,
                                       @RequestParam(required = false) String remark) {
        String approver = userService.currentOperatorName();
        return Result.ok(service.approve(id, approver, remark));
    }

    /** 驳回（采购员） */
    @PostMapping("/{id}/reject")
    @SaCheckPermission(value = "purchase:write")
    public Result<ReturnOrder> reject(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        String approver = userService.currentOperatorName();
        return Result.ok(service.reject(id, approver, reason));
    }
}
