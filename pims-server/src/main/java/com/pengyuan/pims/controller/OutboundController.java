package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.service.OutboundService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 出库管理统一接口
 * 生产出库 / 销售出库 / 委外出库 / 其他出库
 */
@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService service;
    private final UserService userService;
    public OutboundController(OutboundService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    // ==================== 生产出库（参照生产订单） ====================

    @GetMapping("/production")
    @SaCheckPermission(value = "production:read")
    public Result<?> listProduction(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "25") int pageSize,
                                    @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchProduction(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    /** 可参照的生产订单列表（已过滤已完工/已出库/已入库的订单，避免选中后才报错；mode=supplement 补领不排除已领料订单） */
    @GetMapping("/referenceable-production-orders")
    @SaCheckPermission(value = "production:read")
    public Result<List<ProductionOrder>> referenceableProductionOrders(@RequestParam(defaultValue = "outbound") String forType,
                                                                       @RequestParam(required = false) String mode) {
        if ("inbound".equals(forType)) {
            return Result.ok(service.listReferenceableProductionOrdersForInbound());
        }
        return Result.ok(service.listReferenceableProductionOrdersForOutbound(mode));
    }

    /** 可参照的委外订单列表（已过滤已完工/已出库/已入库的订单，避免选中后才报错） */
    @GetMapping("/referenceable-outsource-orders")
    @SaCheckPermission(value = "outsource:read")
    public Result<List<OutsourceOrder>> referenceableOutsourceOrders(@RequestParam(defaultValue = "outbound") String forType) {
        if ("inbound".equals(forType)) {
            return Result.ok(service.listReferenceableOutsourceOrdersForInbound());
        }
        return Result.ok(service.listReferenceableOutsourceOrdersForOutbound());
    }

    /** 按生产订单汇总的实际材料成本（半成品/成品实际成本直出） */
    @GetMapping("/production/actual-costs")
    @SaCheckPermission(value = "production:read")
    public Result<List<Map<String, Object>>> productionActualCosts() {
        return Result.ok(service.productionActualCosts());
    }

    /** 参照生产订单创建出库单据（按配方明细批量生成，支持修改用量和批次；v5.7 仓库改为行级，逐行传入；supplement=true 为补领） */
    @PostMapping("/production")
    @SaCheckPermission(value = "production:write")
    @SuppressWarnings("unchecked")
    public Result<List<ProductionOutbound>> createProduction(@RequestParam Long productionOrderId,
                                                             @RequestParam(required = false) String warehouseId,
                                                             @RequestParam(required = false) String remark,
                                                             @RequestParam(required = false, defaultValue = "false") Boolean supplement,
                                                             @RequestParam(required = false) String supplementType,
                                                             @RequestBody(required = false) Map<String, Map<String, Object>> overrides) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createFromOrder(productionOrderId, warehouseId, operator, remark, overrides,
                Boolean.TRUE.equals(supplement), supplementType));
    }

    /** 生产退料：按订单退料（可多行部分退），库存按原批次原库位加回，成本按负数行自动净额 */
    @PostMapping("/production/return")
    @SaCheckPermission(value = "production:write")
    public Result<List<ProductionOutbound>> returnMaterial(@RequestParam String productionOrderNo,
                                                           @RequestParam(required = false) String remark,
                                                           @RequestBody List<Map<String, Object>> lines) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.returnMaterial(productionOrderNo, lines, operator, remark));
    }

    /** 某生产订单的已领明细（含已退/可退量，供退料弹窗选行） */
    @GetMapping("/production/issued-lines")
    @SaCheckPermission(value = "production:read")
    public Result<List<Map<String, Object>>> issuedLines(@RequestParam String orderNo) {
        return Result.ok(service.listIssuedLines(orderNo));
    }

    /** v5.64 领料单作废：整行冲回（生成 PROD-RET 负数行+库存加回）+ 原行标记 CANCELLED，不可逆 */
    @PutMapping("/production/{id}/void")
    @SaCheckPermission(value = "production:write")
    public Result<Map<String, Object>> voidProduction(@PathVariable Long id,
                                                      @RequestBody java.util.Map<String, String> body) {
        // v8.11：作废必填原因（追责依据——操作日志记 who/when，原因记 why）
        return Result.ok(service.voidProductionOutbound(id, userService.currentOperatorName(),
                body != null ? body.get("reason") : null));
    }

    @PostMapping("/production/{id}/confirm")
    @SaCheckPermission(value = "production:write")
    public Result<ProductionOutbound> confirmProduction(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmProduction(id, operator));
    }

    /** 批量确认同一生产订单下的所有出库单 */
    @PostMapping("/production/confirm-by-order")
    @SaCheckPermission(value = "production:write")
    public Result<List<ProductionOutbound>> confirmByOrder(@RequestParam String productionOrderNo) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmByOrder(productionOrderNo, operator));
    }

    // ==================== 生产入库（参照生产订单） ====================

    @GetMapping("/production-inbound")
    @SaCheckPermission(value = "production:read")
    public Result<?> listProductionInbound(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "25") int pageSize,
                              @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchProductionInbound(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    /** 参照生产订单创建入库单据 */
    @PostMapping("/production-inbound")
    @SaCheckPermission(value = "production:write")
    public Result<ProductionInbound> createProductionInbound(@RequestParam Long productionOrderId,
                                                             @RequestParam String warehouseId,
                                                             @RequestParam(required = false) String locationId,
                                                             @RequestParam(required = false) Double qty,
                                                             @RequestParam(required = false) String batchNo,
                                                             @RequestParam(required = false) String remark) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createProductionInbound(productionOrderId, warehouseId,
                locationId, qty != null ? BigDecimal.valueOf(qty) : null, batchNo, operator, remark));
    }

    @PostMapping("/production-inbound/{id}/confirm")
    @SaCheckPermission(value = "production:write")
    public Result<ProductionInbound> confirmProductionInbound(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmProductionInbound(id, operator));
    }

    // ==================== 销售出库 ====================

    // ==================== v9.6 列表导出（与各列表同口径同权限） ====================

    private static final org.springframework.data.domain.Pageable EXPORT_PAGE =
            org.springframework.data.domain.PageRequest.of(0, 100000);

    /** 生产领料导出 */
    @GetMapping("/production/export")
    @SaCheckPermission(value = "production:read")
    public void exportProduction(@RequestParam(defaultValue = "") String keyword,
                                 jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var d : service.searchProduction(keyword, EXPORT_PAGE).getContent()) {
            rows.add(new Object[]{d.docNo, "PROD-RET".startsWith(d.docNo) || "RETURN".equals(d.docType) ? "退料" : "领料",
                    d.productionOrderNo, d.productName, d.materialCode, d.materialName, d.batchNo,
                    d.warehouseId, d.zoneName, d.locationName, d.qty, d.unit, d.unitPrice, d.cost,
                    ExcelUtilCn(d.status), d.createTime == null ? "" : d.createTime.toLocalDate(), d.createdBy});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "生产领料-" + java.time.LocalDate.now(), "生产领料",
                new String[]{"单号", "类型", "生产订单", "产品", "物料编码", "品名", "批号", "仓库", "分库", "库位",
                        "数量", "单位", "单价", "成本", "状态", "日期", "经办人"}, rows);
    }

    /** 委外发料导出 */
    @GetMapping("/outsource/export")
    @SaCheckPermission(value = "outsource:read")
    public void exportOutsource(@RequestParam(defaultValue = "") String keyword,
                                jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var d : service.searchOutsource(keyword, EXPORT_PAGE).getContent()) {
            rows.add(new Object[]{d.docNo, d.outsourceOrderNo, d.processorName, d.materialCode, d.batchNo,
                    d.fromWarehouseId, d.toWarehouseId, d.zoneName, d.locationName, d.qty, d.unit,
                    d.unitPrice, d.cost, ExcelUtilCn(d.status),
                    d.createTime == null ? "" : d.createTime.toLocalDate(), d.createdBy});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "委外发料-" + java.time.LocalDate.now(), "委外发料",
                new String[]{"单号", "委外订单", "代工厂", "物料编码", "批号", "调出仓", "调入仓", "分库", "库位",
                        "数量", "单位", "单价", "成本", "状态", "日期", "经办人"}, rows);
    }

    /** 其他入库导出（含退货入库） */
    @GetMapping("/other-inbound/export")
    @SaCheckPermission(value = "inventory:read")
    public void exportOtherInbound(@RequestParam(defaultValue = "") String keyword,
                                   jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var d : service.searchOtherInbound(keyword, EXPORT_PAGE).getContent()) {
            rows.add(new Object[]{d.docNo, d.materialCode, d.materialName, d.batchNo, d.warehouseId,
                    d.qty, d.unit, d.price, d.qty != null && d.price != null ? d.qty.multiply(d.price) : null,
                    reasonCn(d.reason), d.returnRefDocNo, ExcelUtilCn(d.status),
                    d.createTime == null ? "" : d.createTime.toLocalDate(), d.createdBy});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "其他入库-" + java.time.LocalDate.now(), "其他入库",
                new String[]{"单号", "物料编码", "品名", "批号", "仓库", "数量", "单位", "单价", "金额",
                        "事由", "关联退货单", "状态", "日期", "经办人"}, rows);
    }

    /** 生产入库导出 */
    @GetMapping("/production-inbound/export")
    @SaCheckPermission(value = "production:read")
    public void exportProductionInbound(@RequestParam(defaultValue = "") String keyword,
                                        jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var d : service.searchProductionInbound(keyword, EXPORT_PAGE).getContent()) {
            rows.add(new Object[]{d.docNo, d.productionOrderNo, d.productCode, d.productName, d.batchNo,
                    d.warehouseId, d.zoneName, d.locationName, d.qty, d.theoreticalQty, d.yieldRate,
                    ExcelUtilCn(d.status), d.createTime == null ? "" : d.createTime.toLocalDate(), d.createdBy});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "生产入库-" + java.time.LocalDate.now(), "生产入库",
                new String[]{"单号", "生产订单", "产品编码", "产品名称", "批号", "仓库", "分库", "库位",
                        "实际产出", "理论产出", "收率%", "状态", "日期", "经办人"}, rows);
    }

    /** 委外入库导出 */
    @GetMapping("/outsource-inbound/export")
    @SaCheckPermission(value = "outsource:read")
    public void exportOutsourceInbound(@RequestParam(defaultValue = "") String keyword,
                                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var d : service.searchOutsourceInbound(keyword, EXPORT_PAGE).getContent()) {
            rows.add(new Object[]{d.docNo, d.outsourceOrderNo, d.productCode, d.productName, d.batchNo,
                    d.warehouseId, d.zoneName, d.locationName, d.qty, d.theoreticalQty, d.yieldRate,
                    ExcelUtilCn(d.status), d.createTime == null ? "" : d.createTime.toLocalDate(), d.createdBy});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "委外入库-" + java.time.LocalDate.now(), "委外入库",
                new String[]{"单号", "委外订单", "产品编码", "产品名称", "批号", "仓库", "分库", "库位",
                        "实际产出", "理论产出", "收率%", "状态", "日期", "经办人"}, rows);
    }

    private static String ExcelUtilCn(String s) { return com.pengyuan.pims.common.ExcelUtil.statusCn(s); }

    private static String reasonCn(String r) {
        if (r == null) return "";
        return switch (r) {
            case "RETURN" -> "退货入库"; case "SCRAP" -> "报废"; case "SAMPLE" -> "样品";
            case "ADJUST" -> "调整"; case "OPENING" -> "期初"; default -> r;
        };
    }

    @GetMapping("/sales")
    @SaCheckPermission(value = "sales:read")
    public Result<?> listSales(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "25") int pageSize,
                              @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchSales(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    /** 参照销售订单发货（批号必选，自动确认扣库存并生成应收） */
    @PostMapping("/sales/from-order")
    @SaCheckPermission(value = "sales:write")
    @SuppressWarnings("unchecked")
    public Result<List<SalesOutbound>> shipFromOrder(@RequestParam Long orderId,
                                                     @RequestParam String warehouseId,
                                                     @RequestParam(required = false) String remark,
                                                     @RequestBody(required = false) Map<String, Map<String, Object>> overrides) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.shipFromOrder(orderId, warehouseId, operator, remark, overrides));
    }

    @PostMapping("/sales")
    @SaCheckPermission(value = "sales:write")
    public Result<SalesOutbound> createSales(@RequestParam(required = false) String salesOrderNo,
                                             @RequestParam String materialCode,
                                             @RequestParam(required = false) String materialName,
                                             @RequestParam(required = false) String batchNo,
                                             @RequestParam String warehouseId,
                                             @RequestParam double qty,
                                             @RequestParam(required = false) String remark) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createSales(salesOrderNo, materialCode,
                materialName, batchNo, warehouseId, BigDecimal.valueOf(qty), operator, remark));
    }

    @PostMapping("/sales/{id}/confirm")
    @SaCheckPermission(value = "sales:write")
    public Result<SalesOutbound> confirmSales(@PathVariable Long id,
                                              @RequestParam(required = false) Double qty) {
        String operator = userService.currentOperatorName();
        java.math.BigDecimal qtyOverride = qty != null ? java.math.BigDecimal.valueOf(qty) : null;
        return Result.ok(service.confirmSales(id, operator, qtyOverride));
    }

    // ==================== 委外出库（参照委外订单） ====================

    @GetMapping("/outsource")
    @SaCheckPermission(value = "outsource:read")
    public Result<?> listOutsource(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "25") int pageSize,
                              @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchOutsource(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    /** 按委外订单汇总的实际材料成本（成品实际成本直出） */
    @GetMapping("/outsource/actual-costs")
    @SaCheckPermission(value = "outsource:read")
    public Result<List<Map<String, Object>>> outsourceActualCosts() {
        return Result.ok(service.outsourceActualCosts());
    }

    /** 参照委外订单创建出库单据（按配方明细批量生成，支持批次选择；v5.7 仓库改为行级，逐行传入） */
    @PostMapping("/outsource")
    @SaCheckPermission(value = "outsource:write")
    @SuppressWarnings("unchecked")
    public Result<List<OutsourceMaterialOutbound>> createOutsource(@RequestParam Long outsourceOrderId,
                                                                    @RequestParam(required = false) String fromWarehouseId,
                                                                    @RequestParam(required = false) String remark,
                                                                    @RequestBody(required = false) Map<String, Map<String, Object>> overrides) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createOutsourceFromOrder(outsourceOrderId, fromWarehouseId, operator, remark, overrides));
    }

    @PostMapping("/outsource/{id}/confirm")
    @SaCheckPermission(value = "outsource:write")
    public Result<OutsourceMaterialOutbound> confirmOutsource(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmOutsource(id, operator));
    }

    // ==================== 委外入库（参照委外订单） ====================

    @GetMapping("/outsource-inbound")
    @SaCheckPermission(value = "outsource:read")
    public Result<?> listOutsourceInbound(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "25") int pageSize,
                              @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchOutsourceInbound(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    /** 参照委外订单创建入库单据 */
    @PostMapping("/outsource-inbound")
    @SaCheckPermission(value = "outsource:write")
    public Result<OutsourceFinishInbound> createOutsourceInbound(@RequestParam Long outsourceOrderId,
                                                                  @RequestParam String warehouseId,
                                                                  @RequestParam(required = false) String locationId,
                                                                  @RequestParam(required = false) Double qty,
                                                                  @RequestParam(required = false) String batchNo,
                                                                  @RequestParam(required = false) String remark) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createOutsourceInbound(outsourceOrderId, warehouseId,
                locationId, qty != null ? BigDecimal.valueOf(qty) : null, batchNo, operator, remark));
    }

    @PostMapping("/outsource-inbound/{id}/confirm")
    @SaCheckPermission(value = "outsource:write")
    public Result<OutsourceFinishInbound> confirmOutsourceInbound(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmOutsourceInbound(id, operator));
    }

    // ==================== 其他出库 ====================

    @GetMapping("/other")
    @SaCheckPermission(value = "inventory:read")
    public Result<?> listOther(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "25") int pageSize,
                              @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchOther(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    @PostMapping("/other")
    @SaCheckPermission(value = "inventory:write")
    public Result<OtherOutbound> createOther(@RequestParam String materialCode,
                                             @RequestParam(required = false) String materialName,
                                             @RequestParam(required = false) String batchNo,
                                             @RequestParam String warehouseId,
                                             @RequestParam double qty,
                                             @RequestParam(required = false) String reason,
                                             @RequestParam(required = false) String remark,
                                             @RequestParam(required = false, defaultValue = "false") Boolean genFinance,
                                             @RequestParam(required = false) Double financeAmount,
                                             @RequestParam(required = false) Long financePartnerId,
                                             @RequestParam(required = false) String financePartnerName) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createOther(materialCode, materialName, batchNo,
                warehouseId, BigDecimal.valueOf(qty), reason, operator, remark,
                genFinance, financeAmount != null ? BigDecimal.valueOf(financeAmount) : null,
                financePartnerId, financePartnerName));
    }

    @PostMapping("/other/{id}/confirm")
    @SaCheckPermission(value = "inventory:write")
    public Result<OtherOutbound> confirmOther(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmOther(id, operator));
    }

    // ==================== 其他入库 ====================

    @GetMapping("/other-inbound")
    @SaCheckPermission(value = "inventory:read")
    public Result<?> listOtherInbound(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "25") int pageSize,
                              @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchOtherInbound(keyword, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    @PostMapping("/other-inbound")
    @SaCheckPermission(value = "inventory:write")
    public Result<OtherInbound> createOtherInbound(@RequestParam String materialCode,
                                                   @RequestParam(required = false) String materialName,
                                                   @RequestParam(required = false) String batchNo,
                                                   @RequestParam String warehouseId,
                                                   @RequestParam(required = false) String locationId,
                                                   @RequestParam double qty,
                                                   @RequestParam(required = false) Double price,
                                                   @RequestParam(required = false) Double taxRate,
                                                   @RequestParam(required = false) String unit,
                                                   @RequestParam(required = false) String reason,
                                                   @RequestParam(required = false) String remark,
                                                   @RequestParam(required = false, defaultValue = "false") Boolean genFinance,
                                                   @RequestParam(required = false) Double financeAmount,
                                                   @RequestParam(required = false) Long financePartnerId,
                                                   @RequestParam(required = false) String financePartnerName) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createOtherInbound(materialCode, materialName, batchNo,
                warehouseId, locationId, BigDecimal.valueOf(qty),
                price != null ? BigDecimal.valueOf(price) : null,
                taxRate != null ? BigDecimal.valueOf(taxRate) : null,
                unit, reason, operator, remark,
                genFinance, financeAmount != null ? BigDecimal.valueOf(financeAmount) : null,
                financePartnerId, financePartnerName));
    }

    @PostMapping("/other-inbound/{id}/confirm")
    @SaCheckPermission(value = "inventory:write")
    public Result<OtherInbound> confirmOtherInbound(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.confirmOtherInbound(id, operator));
    }

    // ==================== 参照退货单出库（采购退货出库）====================

    /** 仓管参照已审核的采购退货单做出库（自动确认+冲减应付） */
    @PostMapping("/other-outbound/from-return-order")
    @SaCheckPermission(value = "inventory:write")
    public Result<OtherOutbound> createOutboundFromReturnOrder(@RequestParam Long returnOrderId,
                                                                @RequestParam String batchNo,
                                                                @RequestParam(required = false) String warehouseId,
                                                                @RequestParam(required = false) String locationId) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.createOutboundFromReturnOrder(returnOrderId, batchNo, warehouseId, locationId, operator));
    }
}
