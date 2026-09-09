package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepo;
    private final PurchaseOrderItemRepository itemRepo;
    private final InventoryService inventoryService;
    // v5.24：全局写锁（单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    private final PurchaseService purchaseService;
    private final com.pengyuan.pims.repository.MaterialRepository materialRepo;   // v6.5 B1 物料存在性校验   // v6.5 B3 转采购

    public PurchaseOrderService(PurchaseOrderRepository orderRepo,
                                PurchaseOrderItemRepository itemRepo,
                                InventoryService inventoryService,
                                WriteQueue writeQueue,
                                         PurchaseService purchaseService,
                                         com.pengyuan.pims.repository.MaterialRepository materialRepo) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.inventoryService = inventoryService;
        this.writeQueue = writeQueue;
        this.purchaseService = purchaseService;
        this.materialRepo = materialRepo;
    }

    public List<PurchaseOrder> listAll() { return orderRepo.findAll(); }
    public List<PurchaseOrder> listRecent(int limit) { return orderRepo.findAllByOrderByCreateTimeDesc(PageRequest.of(0, limit)); }
    public Optional<PurchaseOrder> getById(Long id) { return orderRepo.findById(id); }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public PurchaseOrder create(PurchaseOrder order, List<PurchaseOrderItem> items) {
        // v5.70.1 防呆：明细不能为空
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("采购订单明细不能为空");
        for (PurchaseOrderItem item : items) {
            if (item.materialCode == null || item.materialCode.isBlank())
                throw new IllegalArgumentException("明细行物料编码不能为空");
            if (item.qty == null || item.qty.doubleValue() <= 0)
                throw new IllegalArgumentException("明细行数量必须大于 0");
            // v6.5 B1：物料存在性校验（拦 "null" 等任意字符串穿透 isBlank 入库成脏数据）
            if (materialRepo.findByCode(item.materialCode).isEmpty()) {
                throw new IllegalArgumentException("明细物料不存在：" + item.materialCode);
            }
        }
        // v5.70 P1 防呆：单价超 10 万拦截
        if (items != null) {
            for (var item : items) {
                if (item.unitPrice != null && item.unitPrice.compareTo(new java.math.BigDecimal(100000)) > 0) {
                    throw new IllegalArgumentException("物料 " + item.materialCode + " 单价异常（超10万），请核对单位");
                }
            }
        }
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号；v6.1 executeTx 锁内包事务
        return writeQueue.executeTx(() -> {
            if (order.orderNo == null || order.orderNo.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                // v6.1 修复：查询/生成统一 PO-YYYYMMDD-NNNN（此前前缀不一致 LIKE 永不命中 → 第二张必撞号）
            String poDay = LocalDate.now().toString().replace("-", "");
            Integer maxSeq = orderRepo.findMaxSeq("PO-" + poDay + "-%");
            order.orderNo = String.format("PO-%s-%04d", poDay, (maxSeq == null ? 0 : maxSeq) + 1);
            }
            order.status = "DRAFT";
            order.orderDate = LocalDate.now();
            BigDecimal total = BigDecimal.ZERO;
            PurchaseOrder saved = orderRepo.save(order);
            for (PurchaseOrderItem item : items) {
                item.orderId = saved.id;
                if (item.amount == null && item.unitPrice != null && item.qty != null) {
                    item.amount = item.unitPrice.multiply(item.qty);
                }
                if (item.amount != null) total = total.add(item.amount);
                itemRepo.save(item);
            }
            saved.totalAmount = total;
            return orderRepo.save(saved);
        });
    }

    public List<PurchaseOrderItem> getItems(Long orderId) { return itemRepo.findByOrderId(orderId); }


    /** v6.5 B3：编辑请购单头（仅 DRAFT；MRP 生成的"供应商待定"单在此补供应商/仓库/交期） */
    public PurchaseOrder updateHeader(Long id, Long supplierId, String targetWarehouseId,
                                      java.time.LocalDate expectedDeliveryDate, String remark) {
        PurchaseOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("请购单不存在"));
        if (!"DRAFT".equals(order.status)) throw new IllegalArgumentException("只有草稿状态的请购单可编辑");
        return writeQueue.executeTx(() -> {
            if (supplierId != null) order.supplierId = supplierId;
            if (targetWarehouseId != null && !targetWarehouseId.isBlank()) order.targetWarehouseId = targetWarehouseId;
            if (expectedDeliveryDate != null) order.expectedDeliveryDate = expectedDeliveryDate;
            if (remark != null) order.remark = remark;
            order.updateTime = java.time.LocalDateTime.now();
            return orderRepo.save(order);
        });
    }

    // v8.6（N1）：DRAFT 态批量改明细单价——P0-9 要求转采购前单价>0，MRP 生成的请购无价，
    // 原来没有任何界面/端点能补价 → MRP→请购→转采购闭环死路
    public java.util.List<com.pengyuan.pims.entity.PurchaseOrderItem> updateItemPrices(Long id, java.util.List<java.util.Map<String, Object>> itemsInput) {
        return writeQueue.executeTx(() -> {
            var order = orderRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("请购单不存在"));
            if (!"DRAFT".equals(order.status) && !"APPROVED".equals(order.status))
                throw new IllegalArgumentException("只有草稿/已审核状态可改明细单价（已转采购或关闭的不可改）");
            var items = itemRepo.findByOrderId(id);
            java.util.Map<String, java.math.BigDecimal> priceByMat = new java.util.HashMap<>();
            for (var m : itemsInput) {
                String code = String.valueOf(m.get("materialCode"));
                Object p = m.get("unitPrice");
                if (p == null || new java.math.BigDecimal(String.valueOf(p)).compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("明细 " + code + " 单价必须大于 0");
                }
                priceByMat.put(code, new java.math.BigDecimal(String.valueOf(p)));
            }
            for (var it : items) {
                java.math.BigDecimal np = priceByMat.get(it.materialCode);
                if (np != null) { it.unitPrice = np; itemRepo.save(it); }
            }
            return itemRepo.findByOrderId(id);
        });
    }

    /** v6.5 B3：审核 DRAFT→APPROVED（须已定供应商且有明细） */
    public PurchaseOrder audit(Long id) {
        PurchaseOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("请购单不存在"));
        if (!"DRAFT".equals(order.status)) throw new IllegalArgumentException("只有草稿状态的请购单可审核");
        if (order.supplierId == null) throw new IllegalArgumentException("请先补充供应商再审核");
        if (itemRepo.findByOrderId(id).isEmpty()) throw new IllegalArgumentException("请购单无明细，不可审核");
        return writeQueue.executeTx(() -> {
            order.status = "APPROVED";
            order.updateTime = java.time.LocalDateTime.now();
            return orderRepo.save(order);
        });
    }

    /** v6.5 B3：转采购——按明细逐物料生成原料/成品采购单（APPROVED 采购单，含数量单价），
     *  请购单置 CLOSED 并在备注记录采购单号；幂等：非 APPROVED 请购单不可转 */
    public java.util.Map<String, Object> toPurchase(Long id, String operator) {
        PurchaseOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("请购单不存在"));
        if (!"APPROVED".equals(order.status)) throw new IllegalArgumentException("只有已审核的请购单可转采购");
        if (order.supplierId == null) throw new IllegalArgumentException("请先补充供应商再转采购");
        if (order.remark != null && order.remark.contains("已转采购：")) {
            throw new IllegalArgumentException("该请购单已转过采购，请勿重复操作");
        }
        var items = itemRepo.findByOrderId(id);
        if (items.isEmpty()) throw new IllegalArgumentException("请购单无明细");
        // v8.1（P0-9）：转采购前校验单价——MRP 生成的请购明细不带价，0 元采购会一路静默到
        // "入库无应付"（AP 金额 0 跳过立账），该批采购在账上永久消失
        for (PurchaseOrderItem it : items) {
            if (it.unitPrice == null || it.unitPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("明细 " + it.materialCode + " 未填单价——请先在请购单上补齐单价再转采购（MRP 生成的请购请手工补价）");
            }
        }
        // v6.6 原子性：逐单生成+请购关闭包同一 executeTx（锁重入、单事务）——
        // 原各 create 内部事务独立，中途失败时已生成的采购单留存 → 重试产生真实重复单据
        java.util.List<String> created = writeQueue.executeTx(() -> {
            var sup = purchaseService.findSupplier(order.supplierId);
            java.util.List<String> nos = new java.util.ArrayList<>();
            for (PurchaseOrderItem it : items) {
                var mat = purchaseService.findMaterialCategory(it.materialCode);
                if (mat.code() == null) throw new IllegalArgumentException("物料不存在：" + it.materialCode);
                nos.add(purchaseService.createPurchaseFromRequisition(sup.id(), sup.name(),
                        it.materialCode, mat.name(), it.qty, it.unitPrice, order.targetWarehouseId, operator));
            }
            order.status = "CLOSED";
            order.remark = (order.remark == null || order.remark.isBlank() ? "" : order.remark + "；")
                    + "已转采购：" + String.join("、", nos);
            order.updateTime = java.time.LocalDateTime.now();
            orderRepo.save(order);
            return nos;
        });
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("purchaseOrders", created);
        return r;
    }

    /** v6.3：删除请购单（仅草稿——MRP 生成的误单可清理；已审核单走业务流不可删） */
    public void delete(Long id) {
        PurchaseOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("请购单不存在"));
        if (!"DRAFT".equals(order.status)) throw new IllegalArgumentException("只有草稿状态的请购单可删除");
        writeQueue.executeTx(() -> {
            itemRepo.deleteAll(itemRepo.findByOrderId(id));
            orderRepo.deleteById(id);
        });
    }
}
