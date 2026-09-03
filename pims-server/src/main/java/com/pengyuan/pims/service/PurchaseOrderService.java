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

    public PurchaseOrderService(PurchaseOrderRepository orderRepo,
                                PurchaseOrderItemRepository itemRepo,
                                InventoryService inventoryService,
                                WriteQueue writeQueue) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.inventoryService = inventoryService;
        this.writeQueue = writeQueue;
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
