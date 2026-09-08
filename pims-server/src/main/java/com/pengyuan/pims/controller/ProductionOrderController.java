package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.ProductionOrder;
import com.pengyuan.pims.entity.ProductionOrderItem;
import com.pengyuan.pims.entity.ProductionOutbound;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.repository.ProductionOutboundRepository;
import com.pengyuan.pims.repository.InventoryMovementRepository;
import com.pengyuan.pims.service.OutboundService;
import com.pengyuan.pims.service.ProductionOrderService;
import com.pengyuan.pims.service.UserService;
import com.pengyuan.pims.service.RecipeService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 生产订单（配方表）接口
 */
@RestController
@RequestMapping("/api/production-order")
public class ProductionOrderController {

    private final ProductionOrderService service;
    private final UserService userService;
    private final RecipeService recipeService;
    private final OutboundService outboundService;
    private final ProductionOutboundRepository outboundRepo;
    private final InventoryMovementRepository movementRepo;

    public ProductionOrderController(ProductionOrderService service, RecipeService recipeService,
                                     OutboundService outboundService,
                                     ProductionOutboundRepository outboundRepo,
                                     InventoryMovementRepository movementRepo, UserService userService) {
        this.service = service;
        this.recipeService = recipeService;
        this.outboundService = outboundService;
        this.outboundRepo = outboundRepo;
        this.movementRepo = movementRepo;
        this.userService = userService;
    }

    @GetMapping
    @SaCheckPermission(value = "production:read")
    public Result list(@RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return Result.ok(service.listByStatus(status));
        }
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "production:read")
    public Result detail(@PathVariable Long id) {
        ProductionOrder order = service.getById(id);
        List<ProductionOrderItem> items = service.getItems(id);
        return Result.ok(Map.of("order", order, "items", items));
    }

    @GetMapping("/{id}/items")
    @SaCheckPermission(value = "production:read")
    public Result items(@PathVariable Long id) {
        return Result.ok(service.getItems(id));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    @SaCheckPermission(value = "production:write")
    public Result create(@RequestBody Map<String, Object> body) {
        ProductionOrder order = new ProductionOrder();
        order.productName = (String) body.get("productName");
        order.productCode = (String) body.get("productCode");
        // v5.13：产品编码必填（入库台账/质检以编码建档，空编码导致半成品成品无编码可追溯）
        if (order.productCode == null || order.productCode.isBlank()) {
            throw new IllegalArgumentException("请填写产品编码（选择成品/半成品物料或输入编码）");
        }
        order.batchQty = new BigDecimal(body.get("batchQty").toString());
        order.unit = "kg"; // v4.9：所有物料单位统一为公斤
        order.remark = (String) body.get("remark");
        // v5.27：生产来源必选——销售订单 或 备料生产（备料生产=自产，不挂销售单）
        String sourceType = body.get("sourceType") != null ? body.get("sourceType").toString() : "";
        if (sourceType.isBlank()) {
            throw new IllegalArgumentException("请选择生产来源：销售订单 或 备料生产");
        }
        if ("SALES".equals(sourceType)) {
            if (body.get("salesOrderNo") == null || body.get("salesOrderNo").toString().isBlank()) {
                throw new IllegalArgumentException("生产来源为销售订单时，必须选择来源销售订单");
            }
            order.salesOrderNo = body.get("salesOrderNo").toString();
        } else if ("STOCK".equals(sourceType)) {
            order.salesOrderNo = null;
        } else {
            throw new IllegalArgumentException("生产来源不合法：" + sourceType);
        }
        order.createdBy = userService.currentOperatorName();

        // 参照配方版本（v5.6：半成品保留为一行，不展开原料——半成品为常备库存，直接领用半成品）
        List<ProductionOrderItem> items;
        if (body.get("recipeVersionId") != null) {
            Long recipeVersionId = Long.valueOf(body.get("recipeVersionId").toString());
            order.recipeVersionId = recipeVersionId;
            List<Map<String, Object>> expanded = recipeService.expandForOrder(recipeVersionId, order.batchQty);
            items = new ArrayList<>();
            for (Map<String, Object> row : expanded) {
                ProductionOrderItem item = new ProductionOrderItem();
                item.materialCode = (String) row.get("materialCode");
                item.materialName = (String) row.get("materialName");
                item.spec = (String) row.get("spec");
                item.unit = (String) row.get("unit");
                item.qty = (BigDecimal) row.get("qty");
                item.remark = (String) row.get("remark");
                item.nodeType = (String) row.get("nodeType");
                item.refRecipeId = row.get("refRecipeId") != null
                        ? Long.valueOf(row.get("refRecipeId").toString()) : null;
                // 半成品为常备库存：需绑定物料编码才能领料出库，未绑定给出明确提示
                if ("SUB_RECIPE".equals(item.nodeType)
                        && (item.materialCode == null || item.materialCode.isBlank())) {
                    throw new IllegalArgumentException("配方中的半成品「" + item.materialName
                            + "」未绑定物料编码，请先在配方编辑中为该半成品选择物料");
                }
                items.add(item);
            }
        } else {
            items = parseItems(body);
        }
        return Result.ok(service.create(order, items));
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{id}")
    @SaCheckPermission(value = "production:write")
    public Result update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ProductionOrder order = new ProductionOrder();
        order.productName = (String) body.get("productName");
        order.productCode = (String) body.get("productCode");
        // v5.13：产品编码必填（入库台账/质检以编码建档，空编码导致半成品成品无编码可追溯）
        if (order.productCode == null || order.productCode.isBlank()) {
            throw new IllegalArgumentException("请填写产品编码（选择成品/半成品物料或输入编码）");
        }
        order.batchQty = new BigDecimal(body.get("batchQty").toString());
        order.unit = "kg"; // v4.9：所有物料单位统一为公斤
        order.remark = (String) body.get("remark");

        List<ProductionOrderItem> items = parseItems(body);
        return Result.ok(service.update(id, order, items));
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission(value = "production:write")
    public Result confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id));
    }

    @PostMapping("/{id}/complete")
    @SaCheckPermission(value = "production:write")
    public Result complete(@PathVariable Long id,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String reason) {
        return Result.ok(service.complete(id, reason));
    }

    /** 生产订单自动出库（打印即出库，按先进先出自动分配批次，不足自动补下一批次） */
    @PostMapping("/{id}/auto-outbound")
    @SaCheckPermission(value = "production:write")
    public Result autoOutbound(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(outboundService.autoFifoOutbound(id, operator));
    }

    /** 生产订单出库前库存预检（只算不扣，自有仓） */
    @GetMapping("/{id}/stock-check")
    @SaCheckPermission(value = "production:read")
    public Result stockCheck(@PathVariable Long id) {
        return Result.ok(outboundService.stockCheckForProduction(id));
    }

    /** 生产订单的出库记录明细（生产出库作为订单的记录明细） */
    @GetMapping("/{id}/outbounds")
    @SaCheckPermission(value = "production:read")
    public Result outbounds(@PathVariable Long id) {
        // v8.3（C2）：领料单价/成本后端脱敏（此前仅前端隐藏，直接调接口可见）
        return Result.ok(com.pengyuan.pims.common.FieldFilter.filterListFields(
                outboundService.listOutboundsByOrderId(id), "unitPrice", "cost"));
    }

    /** v5.27：排产（已确认 → 已排产） */
    @PostMapping("/{id}/schedule")
    @SaCheckPermission(value = "production:write")
    public Result schedule(@PathVariable Long id) {
        return Result.ok(service.schedule(id));
    }

    /** v5.27：取消排产（已排产 → 已确认） */
    @PostMapping("/{id}/unschedule")
    @SaCheckPermission(value = "production:write")
    public Result unschedule(@PathVariable Long id) {
        return Result.ok(service.unschedule(id));
    }

    /** v5.27：调整排产顺序（direction=1 下移往后排，-1 上移往前排） */
    @PostMapping("/{id}/move-schedule")
    @SaCheckPermission(value = "production:write")
    public Result moveSchedule(@PathVariable Long id, @RequestParam int direction) {
        service.moveSchedule(id, direction);
        return Result.ok(null);
    }

    /** v5.27：拖拽排序后批量重排（body: {ids: [按新顺序排列的订单ID]}） */
    @PostMapping("/reorder-schedule")
    @SaCheckPermission(value = "production:write")
    public Result reorderSchedule(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) ids.add(Long.valueOf(o.toString()));
        }
        service.reorderSchedule(ids);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "production:write")
    public Result delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 溯源：根据生产订单追溯完整配方谱系 + 实际出库批次 + 供应商 */
    @GetMapping("/{id}/trace")
    @SaCheckPermission(value = "production:read")
    public Result trace(@PathVariable Long id) {
        ProductionOrder order = service.getById(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderNo", order.orderNo);
        result.put("productName", order.productName);

        // 配方谱系
        if (order.recipeVersionId != null) {
            var traceData = recipeService.traceRecipe(order.recipeVersionId, order.batchQty);
            result.put("hasRecipe", true);
            result.putAll(traceData);
        } else {
            result.put("hasRecipe", false);
        }

        // 实际出库批次记录（已确认的）
        List<ProductionOutbound> outbounds = outboundRepo.findByProductionOrderNoAndStatus(order.orderNo, "CONFIRMED");
        List<Map<String, Object>> batchRecords = new ArrayList<>();
        for (ProductionOutbound ob : outbounds) {
            Map<String, Object> rec = new java.util.LinkedHashMap<>();
            rec.put("materialCode", ob.materialCode);
            rec.put("materialName", ob.materialName);
            rec.put("batchNo", ob.batchNo);
            rec.put("qty", ob.qty);
            rec.put("unit", ob.unit);
            // 追溯该批次的入库来源（找到最早的PURCHASE_IN记录；v4.8 兼容已归档的历史批次）
            if (ob.batchNo != null && !ob.batchNo.isBlank()) {
                List<InventoryMovement> movements = movementRepo.findByMaterialCodeAndBatchNoIncludingArchive(
                        ob.materialCode, ob.batchNo);
                movements.stream()
                        .filter(m -> "IN".equals(m.direction) && "PURCHASE_IN".equals(m.docType))
                        .findFirst()
                        .ifPresent(m -> rec.put("sourceDocNo", m.docNo));
            }
            batchRecords.add(rec);
        }
        result.put("outboundBatches", batchRecords);
        return Result.ok(result);
    }

    @SuppressWarnings("unchecked")
    private List<ProductionOrderItem> parseItems(Map<String, Object> body) {
        List<ProductionOrderItem> items = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    ProductionOrderItem item = new ProductionOrderItem();
                    item.materialCode = (String) m.get("materialCode");
                    item.materialName = (String) m.get("materialName");
                    item.spec = (String) m.get("spec");
                    item.unit = (String) m.get("unit");
                    item.qty = new BigDecimal(m.get("qty").toString());
                    item.nodeType = (String) m.get("nodeType");   // v5.72：油尾行 OIL_TAIL（仅成品漆订单）
                    item.remark = (String) m.get("remark");
                    items.add(item);
                }
            }
        }
        return items;
    }
}
