package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.SalesOrder;
import com.pengyuan.pims.entity.SalesOrderItem;
import com.pengyuan.pims.service.SalesOrderService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单入口：创建（Map 体手工解析，新增实体字段须同步解析，如 taxRate v5.75）、确认、转生产。
 */
@RestController
@RequestMapping("/api/sales-order")
public class SalesOrderController {

    private final SalesOrderService service;
    private final com.pengyuan.pims.repository.WarehouseRepository warehouseRepo;
    private final com.pengyuan.pims.service.UserService userService;
    private final com.pengyuan.pims.repository.SalesOrderItemRepository itemRepo;
    public SalesOrderController(SalesOrderService service,
                                com.pengyuan.pims.repository.WarehouseRepository warehouseRepo,
                                com.pengyuan.pims.service.UserService userService,
                                com.pengyuan.pims.repository.SalesOrderItemRepository itemRepo) {
        this.service = service;
        this.warehouseRepo = warehouseRepo;
        this.userService = userService;
        this.itemRepo = itemRepo;
    }

    /** v5.52 客户+物料最近成交价（下单/报价自动带出，防报错价） */
    @GetMapping("/recent-price")
    @SaCheckPermission(value = "sales:read")
    public Result<Map<String, Object>> recentPrice(@RequestParam Long customerId, @RequestParam String materialCode) {
        Map<String, Object> r = new HashMap<>();
        for (Object[] row : itemRepo.findLatestPrice(customerId, materialCode)) {
            r.put("orderNo", (String) row[0]);
            r.put("orderDate", row[1] == null ? null : row[1].toString());
            r.put("unitPrice", row[2] == null ? null : new BigDecimal(row[2].toString()));
        }
        return Result.ok(r);
    }

    /** v5.52 客户各物料最新成交价列表（客户360°/价格查询） */
    @GetMapping("/recent-prices")
    @SaCheckPermission(value = "sales:read")
    public Result<List<Map<String, Object>>> recentPrices(@RequestParam Long customerId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : itemRepo.latestPriceList(customerId)) {
            Map<String, Object> m = new HashMap<>();
            m.put("materialCode", (String) row[0]);
            m.put("materialName", (String) row[1]);
            m.put("unitPrice", row[2] == null ? null : new BigDecimal(row[2].toString()));
            m.put("orderNo", (String) row[3]);
            m.put("orderDate", row[4] == null ? null : row[4].toString());
            list.add(m);
        }
        return Result.ok(list);
    }

    @GetMapping
    @SaCheckPermission(value = "sales:read")
    public Result<List<SalesOrder>> list(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return Result.ok(service.listByStatus(status));
        }
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "sales:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok).orElse(Result.fail(500, "订单不存在"));
    }

    @GetMapping("/{id}/items")
    @SaCheckPermission(value = "sales:read")
    public Result<List<SalesOrderItem>> items(@PathVariable Long id) {
        return Result.ok(service.getItems(id));
    }

    /** 创建销售订单（含明细）：{customerId, customerName, expectedShipDate, remark, items:[{materialCode, materialName, qty, unit, unitPrice}]}，合同号自动生成 */
    @PostMapping
    @SaCheckPermission(value = "sales:write")
    public Result<SalesOrder> create(@RequestBody Map<String, Object> body) {
        SalesOrder order = new SalesOrder();
        if (body.get("customerId") == null) throw new IllegalArgumentException("请选择客户");
        order.customerId = ((Number) body.get("customerId")).longValue();
        order.customerName = (String) body.get("customerName");
        // v5.27：发货仓库由排产环节确定，下单不选（存空串待排产）
        order.sourceWarehouseId = (String) body.get("sourceWarehouseId");
        if (order.sourceWarehouseId == null || order.sourceWarehouseId.isBlank()) {
            order.sourceWarehouseId = "";
        }
        if (body.get("expectedShipDate") != null && !body.get("expectedShipDate").toString().isBlank())
            order.expectedShipDate = LocalDate.parse(body.get("expectedShipDate").toString().substring(0, 10));
        order.remark = (String) body.get("remark");
        // v5.75 税率%（默认13，单据级可改）
        if (body.get("taxRate") != null && !body.get("taxRate").toString().isBlank()) {
            order.taxRate = new java.math.BigDecimal(body.get("taxRate").toString());
        }
        List<SalesOrderItem> items = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                SalesOrderItem item = new SalesOrderItem();
                item.materialCode = (String) m.get("materialCode");
                item.materialName = (String) m.get("materialName");
                if (item.materialCode == null || item.materialCode.isBlank()) continue;
                item.qty = m.get("qty") != null ? BigDecimal.valueOf(((Number) m.get("qty")).doubleValue()) : null;
                if (item.qty == null || item.qty.compareTo(BigDecimal.ZERO) <= 0)
                    throw new IllegalArgumentException("物料 " + item.materialCode + " 数量必须大于0");
                item.unit = (String) m.get("unit");
                item.unitPrice = m.get("unitPrice") != null ? BigDecimal.valueOf(((Number) m.get("unitPrice")).doubleValue()) : null;
                items.add(item);
            }
        }
        if (items.isEmpty()) throw new IllegalArgumentException("请至少添加一条销售明细");
        order.createdBy = userService.currentOperatorName();  // v5.60 制单人
        return Result.ok(service.create(order, items));
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission(value = "sales:write")
    public Result<SalesOrder> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id));
    }

    /** v5.47 编辑订单（仅 DRAFT）+ 变更留痕 */
    @PutMapping("/{id}")
    @SaCheckPermission(value = "sales:write")
    public Result<SalesOrder> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SalesOrder order = new SalesOrder();
        if (body.get("expectedShipDate") != null && !body.get("expectedShipDate").toString().isBlank())
            order.expectedShipDate = LocalDate.parse(body.get("expectedShipDate").toString().substring(0, 10));
        order.remark = (String) body.get("remark");
        // v5.75 税率%（默认13，单据级可改）
        if (body.get("taxRate") != null && !body.get("taxRate").toString().isBlank()) {
            order.taxRate = new java.math.BigDecimal(body.get("taxRate").toString());
        }
        List<SalesOrderItem> items = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                SalesOrderItem item = new SalesOrderItem();
                item.materialCode = (String) m.get("materialCode");
                item.materialName = (String) m.get("materialName");
                if (item.materialCode == null || item.materialCode.isBlank()) continue;
                item.qty = m.get("qty") != null ? BigDecimal.valueOf(((Number) m.get("qty")).doubleValue()) : null;
                if (item.qty == null || item.qty.compareTo(BigDecimal.ZERO) <= 0)
                    throw new IllegalArgumentException("物料 " + item.materialCode + " 数量必须大于0");
                item.unit = (String) m.get("unit");
                item.unitPrice = m.get("unitPrice") != null ? BigDecimal.valueOf(((Number) m.get("unitPrice")).doubleValue()) : null;
                items.add(item);
            }
        }
        return Result.ok(service.update(id, order, items, userService.currentOperatorName()));
    }

    /** v5.47 订单变更记录 */
    @GetMapping("/{id}/changes")
    @SaCheckPermission(value = "sales:read")
    public Result<java.util.List<com.pengyuan.pims.entity.SalesOrderChangeLog>> changes(@PathVariable Long id) {
        return Result.ok(service.listChanges(id));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "sales:write")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** v6.1.6：/ship 遗留入口下线——无批号出库必撞"精确到批次铁律"且不立应收，前端发货走 /outbound/sales/from-order 出库单流程 */
    @PostMapping("/{id}/ship")
    @SaCheckPermission(value = "sales:write")
    public Result<?> ship(@PathVariable Long id) {
        return Result.fail(410, "该入口已下线，请通过「销售出库单」发货（走批次与应收完整链路）");
    }

    /**
     * v5.27：手工结束订单（已确认/已发货可结束；结束后不可发货、不可转生产/委外）
     */
    @PostMapping("/{id}/close")
    @SaCheckPermission(value = "sales:write")
    public Result<?> close(@PathVariable Long id,
                           @RequestParam(required = false) String reason) {
        return Result.ok(service.closeOrder(id, userService.currentOperatorName(), reason));
    }

    /**
     * v5.27：销售订单一键转生产订单
     * 明细中的半成品/成品各生成一张生产订单（草稿），材料跳过；已生成过的产品自动跳过
     * @return {created: 生成数, skipped: 跳过数}
     */
    @PostMapping("/{id}/create-production-orders")
    @SaCheckPermission(value = "production:write")
    public Result<?> createProductionOrders(@PathVariable Long id) {
        return Result.ok(service.createProductionOrders(id, userService.currentOperatorName()));
    }

    /**
     * v5.27：销售订单一键转委外订单
     * 明细中的半成品/成品各生成一张委外订单（草稿），材料跳过；必须指定代工厂
     * @param body {processorId, processorName, processingFee(可选)}
     * @return {created: 生成数, skipped: 跳过数}
     */
    @PostMapping("/{id}/create-outsource-orders")
    @SaCheckPermission(value = "outsource:write")
    public Result<?> createOutsourceOrders(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long processorId = body.get("processorId") != null
                ? Long.valueOf(body.get("processorId").toString()) : null;
        String processorName = (String) body.get("processorName");
        BigDecimal processingFee = body.get("processingFee") != null
                ? new BigDecimal(body.get("processingFee").toString()) : null;
        return Result.ok(service.createOutsourceOrders(id, processorId, processorName,
                processingFee, userService.currentOperatorName()));
    }

    // ==================== 导出（v5.23） ====================

    /** 销售订单导出：全量（与列表页一致，含品名/数量摘要；金额无权限限制，与页面一致） */
    @GetMapping("/export")
    @SaCheckPermission(value = "sales:read")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Map<String, String> whNames = new HashMap<>();
        for (com.pengyuan.pims.entity.Warehouse w : warehouseRepo.findAll()) whNames.put(String.valueOf(w.id), w.name);
        List<Object[]> rows = new ArrayList<>();
        for (SalesOrder o : service.listAll()) {
            String status = switch (o.status == null ? "" : o.status) {
                case "DRAFT" -> "草稿";
                case "CONFIRMED" -> "已确认";
                case "SHIPPED" -> "已发货";
                default -> o.status;
            };
            rows.add(new Object[]{
                    o.orderNo, o.customerName, o.orderDate, o.contractNo, status, o.totalAmount,
                    whNames.getOrDefault(String.valueOf(o.sourceWarehouseId), o.sourceWarehouseId),
                    o.expectedShipDate, o.materialNames, o.materialQtySummary, o.createdBy, o.remark
            });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "销售订单-" + LocalDate.now(), "销售订单",
                new String[]{"单号", "客户", "订单日期", "合同号", "状态", "总金额", "发货仓库", "期望发货日期", "品名", "数量摘要", "制单人", "备注"},
                rows);
    }
}
