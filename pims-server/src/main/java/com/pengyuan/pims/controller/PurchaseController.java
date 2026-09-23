package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.service.PurchaseService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 采购入口：原料/成品采购 CRUD 与审核/反审核、批量建单、到货（审核触发质检+立应付）、旧版请购单。
 * 防呆：审核前校验收货仓库与地址齐备（v5.71.6）；到货禁选隔离分库（v5.71.7）。
 */
@RestController
@RequestMapping("/api")
public class PurchaseController {

    private final PurchaseService service;
    private final UserService userService;

    public PurchaseController(PurchaseService service, UserService userService) { this.service = service; this.userService = userService; }

    // ==================== 原料采购 ====================

    @GetMapping("/raw-material-purchase")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> listRaw() {
        List<RawMaterialPurchase> list = service.listRaw();
        if (!FieldFilter.hasPerm("purchase:price")) {
            return Result.ok(FieldFilter.filterListFields(list, FieldFilter.PURCHASE_PRICE_FIELDS));
        }
        return Result.ok(list);
    }

    /**
     * 原料采购分页查询（支持状态过滤 + 多条件）
     * 默认每页 20 条，按采购日期倒序
     */
    @GetMapping("/raw-material-purchase/search")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> searchRaw(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String orderNo,
                               @RequestParam(required = false) String supplierName,
                               @RequestParam(required = false) String materialCode,
                               @RequestParam(required = false) String materialName,
                               @RequestParam(required = false) LocalDate startDate,
                               @RequestParam(required = false) LocalDate endDate,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        var result = service.searchRaw(status, orderNo, supplierName, materialCode, materialName,
                startDate, endDate, page, size);
        if (!FieldFilter.hasPerm("purchase:price")) {
            return Result.ok(Map.of(
                    "content", FieldFilter.filterListFields(result.getContent(), FieldFilter.PURCHASE_PRICE_FIELDS),
                    "totalElements", result.getTotalElements(),
                    "totalPages", result.getTotalPages(),
                    "number", result.getNumber(),
                    "size", result.getSize()
            ));
        }
        return Result.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        ));
    }

    @GetMapping("/raw-material-purchase/{id}")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> getRaw(@PathVariable Long id) {
        return service.getRaw(id).map(item -> {
            if (!FieldFilter.hasPerm("purchase:price")) {
                return Result.ok(FieldFilter.filterFields(item, FieldFilter.PURCHASE_PRICE_FIELDS));
            }
            return Result.ok(item);
        }).orElse(Result.fail(500, "不存在"));
    }

    /** 查上次采购记录（品名输入后自动回填） */
    @GetMapping("/raw-material-purchase/last/{materialName}")
    @SaCheckPermission(value = "purchase:read")
    public Result<RawMaterialPurchase> getLast(@PathVariable String materialName) {
        return Result.ok(service.getLastPurchase(materialName));
    }

    @PostMapping("/raw-material-purchase")
    @SaCheckPermission(value = "purchase:write")
    public Result<RawMaterialPurchase> createRaw(@RequestBody RawMaterialPurchase rp) {
        return Result.ok(service.createRaw(rp));
    }

    /** v5.0：原料采购批量创建（一张单据一个供应商多个物料，共享合同号） */
    @PostMapping("/raw-material-purchase/batch")
    @SaCheckPermission(value = "purchase:write")
    public Result<List<RawMaterialPurchase>> batchCreateRaw(@RequestBody Map<String, Object> body) {
        return Result.ok(service.batchCreateRaw(body));
    }

    @PutMapping("/raw-material-purchase/{id}")
    @SaCheckPermission(value = "purchase:write")
    public Result<RawMaterialPurchase> updateRaw(@PathVariable Long id, @RequestBody RawMaterialPurchase rp) {
        return Result.ok(service.updateRaw(id, rp));
    }

    /** 上传合同PDF */
    @PostMapping("/raw-material-purchase/{id}/upload")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> uploadContract(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        service.uploadContract(id, file);
        return Result.ok("上传成功");
    }

    // ==================== 成品采购 ====================

    @GetMapping("/finished-product-purchase")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> listFinished() {
        List<FinishedProductPurchase> list = service.listFinished();
        if (!FieldFilter.hasPerm("purchase:price")) {
            return Result.ok(FieldFilter.filterListFields(list, FieldFilter.PURCHASE_PRICE_FIELDS));
        }
        return Result.ok(list);
    }

    /**
     * 成品采购分页查询（支持状态过滤 + 多条件）
     */
    @GetMapping("/finished-product-purchase/search")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> searchFinished(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String orderNo,
                                     @RequestParam(required = false) String supplierName,
                                     @RequestParam(required = false) String materialCode,
                                     @RequestParam(required = false) String materialName,
                                     @RequestParam(required = false) LocalDate startDate,
                                     @RequestParam(required = false) LocalDate endDate,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        var result = service.searchFinished(status, orderNo, supplierName, materialCode, materialName,
                startDate, endDate, page, size);
        if (!FieldFilter.hasPerm("purchase:price")) {
            return Result.ok(Map.of(
                    "content", FieldFilter.filterListFields(result.getContent(), FieldFilter.PURCHASE_PRICE_FIELDS),
                    "totalElements", result.getTotalElements(),
                    "totalPages", result.getTotalPages(),
                    "number", result.getNumber(),
                    "size", result.getSize()
            ));
        }
        return Result.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        ));
    }

    @GetMapping("/finished-product-purchase/{id}")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> getFinished(@PathVariable Long id) {
        return service.getFinished(id).map(item -> {
            if (!FieldFilter.hasPerm("purchase:price")) {
                return Result.ok(FieldFilter.filterFields(item, FieldFilter.PURCHASE_PRICE_FIELDS));
            }
            return Result.ok(item);
        }).orElse(Result.fail(500, "不存在"));
    }

    @PostMapping("/finished-product-purchase")
    @SaCheckPermission(value = "purchase:write")
    public Result<FinishedProductPurchase> createFinished(@RequestBody FinishedProductPurchase fp) {
        return Result.ok(service.createFinished(fp));
    }

    /** v5.0：成品采购批量创建（一张单据一个供应商多个成品，共享合同号） */
    @PostMapping("/finished-product-purchase/batch")
    @SaCheckPermission(value = "purchase:write")
    public Result<List<FinishedProductPurchase>> batchCreateFinished(@RequestBody Map<String, Object> body) {
        return Result.ok(service.batchCreateFinished(body));
    }

    @PutMapping("/finished-product-purchase/{id}")
    @SaCheckPermission(value = "purchase:write")
    public Result<FinishedProductPurchase> updateFinished(@PathVariable Long id, @RequestBody FinishedProductPurchase fp) {
        return Result.ok(service.updateFinished(id, fp));
    }

    // ==================== 审核/反审核 ====================

    @PostMapping("/raw-material-purchase/{id}/audit")
    @SaCheckPermission(value = {"raw-material-purchase:audit", "purchase:audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<RawMaterialPurchase> auditRaw(@PathVariable Long id) {
        return Result.ok(service.auditRaw(id));
    }

    @PostMapping("/raw-material-purchase/{id}/reverse-audit")
    @SaCheckPermission(value = {"raw-material-purchase:reverse-audit", "purchase:reverse-audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<RawMaterialPurchase> reverseAuditRaw(@PathVariable Long id) {
        return Result.ok(service.reverseAuditRaw(id));
    }

    @PostMapping("/finished-product-purchase/{id}/audit")
    @SaCheckPermission(value = {"finished-purchase:audit", "purchase:audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<FinishedProductPurchase> auditFinished(@PathVariable Long id) {
        return Result.ok(service.auditFinished(id));
    }

    @PostMapping("/finished-product-purchase/{id}/reverse-audit")
    @SaCheckPermission(value = {"finished-purchase:reverse-audit", "purchase:reverse-audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<FinishedProductPurchase> reverseAuditFinished(@PathVariable Long id) {
        return Result.ok(service.reverseAuditFinished(id));
    }

    // ==================== 到货管理 ====================

    /** v9.6 导出：到货单（价格列按 purchase:price 权限脱敏，与列表同口径） */
    @GetMapping("/purchase-arrival/export")
    @SaCheckPermission(value = "purchase:read")
    public void exportArrivals(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String type,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean price = com.pengyuan.pims.common.FieldFilter.hasPerm("purchase:price");
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var a : service.searchArrivals(keyword == null ? "" : keyword, type,
                org.springframework.data.domain.PageRequest.of(0, 100000)).getContent()) {
            rows.add(new Object[]{
                    a.docNo, "FINISHED".equals(a.type) ? "成品" : "原料", a.refOrderNo, a.supplierName,
                    a.materialCode, a.materialName, a.qty, a.unit,
                    price ? a.unitPrice : "", a.taxRate, price ? a.unitPrice : "",
                    a.arrivalDate, a.warehouseId, a.zoneName, a.locationName, a.batchNo,
                    com.pengyuan.pims.common.ExcelUtil.statusCn(a.status), a.operator, a.remark
            });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "采购到货-" + java.time.LocalDate.now(), "采购到货",
                new String[]{"单号", "类型", "关联采购单", "供应商", "物料编码", "品名", "数量", "单位",
                        "单价", "税率%", "金额", "到货日期", "仓库", "分库", "库位", "批号", "状态", "经办人", "备注"}, rows);
    }

    @GetMapping("/purchase-arrival")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> listArrivals(@RequestParam(required = false) String type,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "25") int pageSize,
                                  @RequestParam(defaultValue = "") String keyword) {
        var rows = service.searchArrivals(keyword, type, org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        return Result.ok(java.util.Map.of("rows", rows.getContent(), "total", rows.getTotalElements()));
    }

    @PostMapping("/purchase-arrival")
    @SaCheckPermission(value = "purchase:write")
    public Result<PurchaseArrival> createArrival(@RequestBody PurchaseArrival pa) {
        pa.operator = userService.currentOperatorName();
        return Result.ok(service.createArrival(pa));
    }

    @PostMapping("/purchase-arrival/{id}/audit")
    @SaCheckPermission(value = {"purchase-arrival:audit", "purchase:audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<PurchaseArrival> auditArrival(@PathVariable Long id) {
        return Result.ok(service.auditArrival(id));
    }

    @PostMapping("/purchase-arrival/{id}/reverse-audit")
    @SaCheckPermission(value = {"purchase-arrival:reverse-audit", "purchase:reverse-audit"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<PurchaseArrival> reverseAuditArrival(@PathVariable Long id) {
        return Result.ok(service.reverseAuditArrival(id));
    }

    /** v11.0 到货后续调价：改含税单价并重算应付（已付款禁止，需填原因，锁期/质检/台账三联动） */
    @PutMapping("/purchase-arrival/{id}/price")
    @SaCheckPermission(value = "purchase:write")
    public Result<PurchaseArrival> adjustArrivalPrice(@PathVariable Long id,
                                                      @RequestBody java.util.Map<String, Object> body) {
        java.math.BigDecimal price = new java.math.BigDecimal(String.valueOf(body.get("price")));
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : "";
        return Result.ok(service.adjustArrivalPrice(id, price, reason,
                userService.currentOperatorName()));
    }

    // ==================== 到货录入（新） ====================

    /** 获取未完全到货的订单列表 */
    @GetMapping("/purchase/incomplete")
    @SaCheckPermission(value = "purchase:read")
    public Result<?> getIncompleteOrders(@RequestParam String type) {
        return Result.ok(service.getIncompleteOrders(type));
    }

    /** 录入到货（v5.35：批号自动生成，不允许手工干预——质检单与入库台账沿用同一批号） */
    @PostMapping("/purchase/arrival/record")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> recordArrival(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String type = params.get("type").toString();
        java.math.BigDecimal arrivalQty = new java.math.BigDecimal(params.get("arrivalQty").toString());
        String warehouseId = params.get("warehouseId").toString();
        String locationId = params.get("locationId") != null ? params.get("locationId").toString() : null;
        String batchNo = params.get("batchNo") != null ? params.get("batchNo").toString().trim() : null;
        String operator = userService.currentOperatorName();
        service.recordArrival(id, type, arrivalQty, warehouseId, locationId, batchNo, operator);
        return Result.ok("到货录入成功");
    }

    /** 手动结束采购订单 */
    @PostMapping("/purchase/close/{id}")
    @SaCheckPermission(value = "purchase:write")
    public Result<?> closeOrder(@PathVariable Long id, @RequestParam String type,
                                @RequestParam(required = false) String reason) {
        service.closeOrder(id, type, reason);
        return Result.ok("订单已关闭");
    }

    // ==================== 导出（v5.23） ====================

    /** 采购状态中文（导出用） */
    private String purchaseStatus(String s) {
        return switch (s == null ? "" : s) {
            case "DRAFT" -> "草稿";
            case "APPROVED" -> "已审核";
            case "CLOSED" -> "已关闭";
            default -> s;
        };
    }

    /** 原料采购导出：当前筛选条件全量（价格列受 purchase:price 权限控制，无权限省略） */
    @GetMapping("/raw-material-purchase/export")
    @SaCheckPermission(value = "purchase:read")
    public void exportRaw(@RequestParam(required = false) String status,
                          @RequestParam(required = false) String orderNo,
                          @RequestParam(required = false) String supplierName,
                          @RequestParam(required = false) String materialCode,
                          @RequestParam(required = false) String materialName,
                          @RequestParam(required = false) LocalDate startDate,
                          @RequestParam(required = false) LocalDate endDate,
                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var result = service.searchRaw(status, orderNo, supplierName, materialCode, materialName,
                startDate, endDate, 0, 100000);
        boolean pricePerm = FieldFilter.hasPerm("purchase:price");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (RawMaterialPurchase r : result.getContent()) {
            if (pricePerm) {
                rows.add(new Object[]{
                        r.orderNo, r.purchaseDate, r.supplierName, r.materialCode, r.materialName, r.brand,
                        r.qty, r.unitPrice, r.totalAmount,
                        purchaseStatus(r.status), r.receivedQty,
                        Boolean.TRUE.equals(r.isFree) ? "赠送" : "", r.createdBy, r.remark
                });
            } else {
                rows.add(new Object[]{
                        r.orderNo, r.purchaseDate, r.supplierName, r.materialCode, r.materialName, r.brand,
                        r.qty, purchaseStatus(r.status), r.receivedQty,
                        Boolean.TRUE.equals(r.isFree) ? "赠送" : "", r.createdBy, r.remark
                });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "原料采购-" + LocalDate.now(), "原料采购",
                pricePerm
                        ? new String[]{"合同号", "采购日期", "供应商", "物料编码", "品名", "牌号", "数量", "采购单价", "采购金额", "状态", "已到货数量", "赠送", "制单人", "备注"}
                        : new String[]{"合同号", "采购日期", "供应商", "物料编码", "品名", "牌号", "数量", "状态", "已到货数量", "赠送", "制单人", "备注"},
                rows);
    }

    /** 成品采购导出：当前筛选条件全量（价格列受 purchase:price 权限控制，无权限省略） */
    @GetMapping("/finished-product-purchase/export")
    @SaCheckPermission(value = "purchase:read")
    public void exportFinished(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String orderNo,
                               @RequestParam(required = false) String supplierName,
                               @RequestParam(required = false) String materialCode,
                               @RequestParam(required = false) String materialName,
                               @RequestParam(required = false) LocalDate startDate,
                               @RequestParam(required = false) LocalDate endDate,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var result = service.searchFinished(status, orderNo, supplierName, materialCode, materialName,
                startDate, endDate, 0, 100000);
        boolean pricePerm = FieldFilter.hasPerm("purchase:price");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (FinishedProductPurchase r : result.getContent()) {
            if (pricePerm) {
                rows.add(new Object[]{
                        r.orderNo, r.purchaseDate, r.supplierName, r.materialCode, r.materialName, r.brand,
                        r.qty, r.unitPrice, r.totalAmount,
                        purchaseStatus(r.status), r.receivedQty,
                        Boolean.TRUE.equals(r.isFree) ? "赠送" : "", r.createdBy, r.remark
                });
            } else {
                rows.add(new Object[]{
                        r.orderNo, r.purchaseDate, r.supplierName, r.materialCode, r.materialName, r.brand,
                        r.qty, purchaseStatus(r.status), r.receivedQty,
                        Boolean.TRUE.equals(r.isFree) ? "赠送" : "", r.createdBy, r.remark
                });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "成品采购-" + LocalDate.now(), "成品采购",
                pricePerm
                        ? new String[]{"合同号", "采购日期", "供应商", "物料编码", "品名", "牌号", "数量", "采购单价", "采购金额", "状态", "已到货数量", "赠送", "制单人", "备注"}
                        : new String[]{"合同号", "采购日期", "供应商", "物料编码", "品名", "牌号", "数量", "状态", "已到货数量", "赠送", "制单人", "备注"},
                rows);
    }
}
