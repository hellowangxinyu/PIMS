package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.OutsourceOrder;
import com.pengyuan.pims.entity.OutsourceOrderItem;
import com.pengyuan.pims.entity.OutsourceMaterialOutbound;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.repository.MaterialRepository;
import com.pengyuan.pims.repository.OutsourceMaterialOutboundRepository;
import com.pengyuan.pims.repository.InventoryMovementRepository;
import com.pengyuan.pims.service.OutboundService;
import com.pengyuan.pims.service.OutsourceOrderService;
import com.pengyuan.pims.service.UserService;
import com.pengyuan.pims.service.RecipeService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 委外订单（配方表）接口
 * 权限校验与生产订单保持一致：read/write 分离
 */
@RestController
@RequestMapping("/api/outsource-order")
public class OutsourceOrderController {

    private final OutsourceOrderService service;
    private final UserService userService;
    private final RecipeService recipeService;
    private final OutboundService outboundService;
    private final OutsourceMaterialOutboundRepository outboundRepo;
    private final InventoryMovementRepository movementRepo;
    private final MaterialRepository materialRepo;

    public OutsourceOrderController(OutsourceOrderService service, RecipeService recipeService,
                                     OutboundService outboundService,
                                     OutsourceMaterialOutboundRepository outboundRepo,
                                     InventoryMovementRepository movementRepo,
                                     MaterialRepository materialRepo, UserService userService) {
        this.service = service;
        this.recipeService = recipeService;
        this.outboundService = outboundService;
        this.outboundRepo = outboundRepo;
        this.movementRepo = movementRepo;
        this.userService = userService;
        this.materialRepo = materialRepo;
    }

    @GetMapping
    @SaCheckPermission(value = "outsource:read")
    public Result list(@RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return Result.ok(service.listByStatus(status));
        }
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "outsource:read")
    public Result detail(@PathVariable Long id) {
        OutsourceOrder order = service.getById(id);
        List<OutsourceOrderItem> items = service.getItems(id);
        return Result.ok(Map.of("order", order, "items", items));
    }

    @GetMapping("/{id}/items")
    @SaCheckPermission(value = "outsource:read")
    public Result items(@PathVariable Long id) {
        return Result.ok(service.getItems(id));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    @SaCheckPermission(value = "outsource:write")
    public Result create(@RequestBody Map<String, Object> body) {
        OutsourceOrder order = new OutsourceOrder();
        order.productName = (String) body.get("productName");
        order.productCode = (String) body.get("productCode");
        // v5.13：产品编码必填（入库台账/质检以编码建档，空编码导致半成品成品无编码可追溯）
        if (order.productCode == null || order.productCode.isBlank()) {
            throw new IllegalArgumentException("请填写产品编码（选择成品/半成品物料或输入编码）");
        }
        order.batchQty = new BigDecimal(body.get("batchQty").toString());
        order.unit = "kg"; // v4.9：所有物料单位统一为公斤
        order.processor = (String) body.get("processor");
        // v5.27：委外必须指定代工厂（供应商管理中类型=代工厂，创建委外单时必选）
        if (order.processor == null || order.processor.isBlank()) {
            throw new IllegalArgumentException("请选择代工厂（委外订单必须指定代加工供应商）");
        }
        // v5.9 修复：委外加工费与代工供应商未解析，导致委外入库后加工费 AP 永远无法生成
        // v5.27：加工费必填（委外入库后按加工费立应付，漏填将无应付可立）
        if (body.get("processingFee") == null || new BigDecimal(body.get("processingFee").toString()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("请填写委外加工费（委外入库后按此金额立应付）");
        }
        order.processingFee = new BigDecimal(body.get("processingFee").toString());
        if (body.get("supplierId") != null) {
            order.supplierId = Long.valueOf(body.get("supplierId").toString());
        }
        // v5.27：来源销售订单（销售订单一键转委外时自动带入）
        if (body.get("salesOrderNo") != null) {
            order.salesOrderNo = body.get("salesOrderNo").toString();
        }
        order.remark = (String) body.get("remark");
        order.createdBy = userService.currentOperatorName();

        // 参照配方版本（v5.6：半成品保留为一行，不展开原料——半成品为常备库存，直接领用半成品）
        List<OutsourceOrderItem> items;
        if (body.get("recipeVersionId") != null) {
            Long recipeVersionId = Long.valueOf(body.get("recipeVersionId").toString());
            order.recipeVersionId = recipeVersionId;
            List<Map<String, Object>> expanded = recipeService.expandForOrder(recipeVersionId, order.batchQty);
            items = new ArrayList<>();
            for (Map<String, Object> row : expanded) {
                OutsourceOrderItem item = new OutsourceOrderItem();
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
    @SaCheckPermission(value = "outsource:write")
    public Result update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceOrder order = new OutsourceOrder();
        order.productName = (String) body.get("productName");
        order.productCode = (String) body.get("productCode");
        // v5.13：产品编码必填（入库台账/质检以编码建档，空编码导致半成品成品无编码可追溯）
        if (order.productCode == null || order.productCode.isBlank()) {
            throw new IllegalArgumentException("请填写产品编码（选择成品/半成品物料或输入编码）");
        }
        order.batchQty = new BigDecimal(body.get("batchQty").toString());
        order.unit = "kg"; // v4.9：所有物料单位统一为公斤
        order.processor = (String) body.get("processor");
        // v5.27：委外必须指定代工厂
        if (order.processor == null || order.processor.isBlank()) {
            throw new IllegalArgumentException("请选择代工厂（委外订单必须指定代加工供应商）");
        }
        if (body.get("processingFee") != null) {
            order.processingFee = new BigDecimal(body.get("processingFee").toString());
        }
        if (body.get("supplierId") != null) {
            order.supplierId = Long.valueOf(body.get("supplierId").toString());
        }
        order.remark = (String) body.get("remark");

        List<OutsourceOrderItem> items = parseItems(body);
        return Result.ok(service.update(id, order, items));
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission(value = "outsource:write")
    public Result confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id));
    }

    /** 委外订单自动出库（打印即出库，先进先出，只从该代工厂对应的仓库扣） */
    @PostMapping("/{id}/auto-outbound")
    @SaCheckPermission(value = "outsource:write")
    public Result autoOutbound(@PathVariable Long id) {
        String operator = userService.currentOperatorName();
        return Result.ok(outboundService.autoFifoOutsourceOutbound(id, operator));
    }

    /** 委外订单出库前库存预检（只算不扣，代工厂仓） */
    @GetMapping("/{id}/stock-check")
    @SaCheckPermission(value = "outsource:read")
    public Result stockCheck(@PathVariable Long id) {
        return Result.ok(outboundService.stockCheckForOutsource(id));
    }

    /** 委外订单的出库记录明细 */
    @GetMapping("/{id}/outbounds")
    @SaCheckPermission(value = "outsource:read")
    public Result outbounds(@PathVariable Long id) {
        return Result.ok(outboundService.listOutsourceOutboundsByOrderId(id));
    }

    @PostMapping("/{id}/complete")
    @SaCheckPermission(value = "outsource:write")
    public Result complete(@PathVariable Long id) {
        return Result.ok(service.complete(id));
    }

    /** v5.27：排产（已确认 → 已委外） */
    @PostMapping("/{id}/schedule")
    @SaCheckPermission(value = "outsource:write")
    public Result schedule(@PathVariable Long id) {
        return Result.ok(service.schedule(id));
    }

    /** v5.27：取消排产（已委外 → 已确认） */
    @PostMapping("/{id}/unschedule")
    @SaCheckPermission(value = "outsource:write")
    public Result unschedule(@PathVariable Long id) {
        return Result.ok(service.unschedule(id));
    }

    /** v5.27：调整排产顺序（direction=1 下移往后排，-1 上移往前排） */
    @PostMapping("/{id}/move-schedule")
    @SaCheckPermission(value = "outsource:write")
    public Result moveSchedule(@PathVariable Long id, @RequestParam int direction) {
        service.moveSchedule(id, direction);
        return Result.ok(null);
    }

    /** v5.27：拖拽排序后批量重排（body: {ids: [按新顺序排列的订单ID]}） */
    @PostMapping("/reorder-schedule")
    @SaCheckPermission(value = "outsource:write")
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
    @SaCheckPermission(value = "outsource:write")
    public Result delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok(null);
    }

    /**
     * 溯源：根据委外订单追溯完整配方谱系 + 实际出库批次 + 供应商
     * 逻辑与生产订单溯源保持一致
     */
    @GetMapping("/{id}/trace")
    @SaCheckPermission(value = "outsource:read")
    public Result trace(@PathVariable Long id) {
        OutsourceOrder order = service.getById(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderNo", order.orderNo);
        result.put("productName", order.productName);
        result.put("processor", order.processor);

        // 配方谱系
        if (order.recipeVersionId != null) {
            var traceData = recipeService.traceRecipe(order.recipeVersionId, order.batchQty);
            result.put("hasRecipe", true);
            result.putAll(traceData);
        } else {
            result.put("hasRecipe", false);
        }

        // 实际出库批次记录（已确认的）
        List<OutsourceMaterialOutbound> outbounds = outboundRepo.findByOutsourceOrderNo(order.orderNo);
        List<OutsourceMaterialOutbound> confirmed = outbounds.stream()
                .filter(o -> "CONFIRMED".equals(o.status))
                .toList();
        List<Map<String, Object>> batchRecords = new ArrayList<>();
        for (OutsourceMaterialOutbound ob : confirmed) {
            Map<String, Object> rec = new java.util.LinkedHashMap<>();
            rec.put("materialCode", ob.materialCode);
            // 委外出库单未冗余品名，反查物料表补充（与生产溯源保持一致）
            if (ob.materialCode != null) {
                materialRepo.findByCode(ob.materialCode).ifPresent(m -> rec.put("materialName", m.name));
            }
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
    private List<OutsourceOrderItem> parseItems(Map<String, Object> body) {
        List<OutsourceOrderItem> items = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    OutsourceOrderItem item = new OutsourceOrderItem();
                    item.materialCode = (String) m.get("materialCode");
                    item.materialName = (String) m.get("materialName");
                    item.spec = (String) m.get("spec");
                    item.unit = (String) m.get("unit");
                    item.qty = new BigDecimal(m.get("qty").toString());
                    item.remark = (String) m.get("remark");
                    items.add(item);
                }
            }
        }
        return items;
    }
}
