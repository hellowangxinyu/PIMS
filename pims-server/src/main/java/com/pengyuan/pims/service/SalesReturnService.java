package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 销售退货服务
 * 业务流程：客户退货 → 手工创建退货单(DRAFT) → 销售员审核(APPROVED/REJECTED，复用 ReturnOrderService.approve/reject)
 *          → 仓管参照退货单做退货入库(DONE)：库存增加(自动新批号)、落库其他入库单(reason=RETURN)、
 *            冲减应收账款(applySalesReturn)、回写销售订单明细 return_qty
 * 与采购退货共用 return_order 表，type=SALES_RETURN，单号 SR-YYYY-NNNN
 */
@Service
public class SalesReturnService {

    private static final Logger log = LoggerFactory.getLogger(SalesReturnService.class);

    private final ReturnOrderRepository returnOrderRepo;
    private final OtherInboundRepository otherInRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final SalesOrderItemRepository salesItemRepo;
    private final SalesOutboundRepository salesOutRepo;
    private final InventoryService inventoryService;
    private final FinanceService financeService;
    // v6.1：退货按成本价入账，反查库存台账批次成本
    private final InventoryLedgerRepository ledgerRepo;
    // v5.24：全局写锁（单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;

    public SalesReturnService(ReturnOrderRepository returnOrderRepo,
                              OtherInboundRepository otherInRepo,
                              SalesOrderRepository salesOrderRepo,
                              SalesOrderItemRepository salesItemRepo,
                              SalesOutboundRepository salesOutRepo,
                              InventoryService inventoryService,
                              FinanceService financeService,
                              InventoryLedgerRepository ledgerRepo,
                              WriteQueue writeQueue) {
        this.returnOrderRepo = returnOrderRepo;
        this.otherInRepo = otherInRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.salesItemRepo = salesItemRepo;
        this.salesOutRepo = salesOutRepo;
        this.inventoryService = inventoryService;
        this.financeService = financeService;
        this.ledgerRepo = ledgerRepo;
        this.writeQueue = writeQueue;
    }

    /** 查询销售退货单（可选状态过滤） */
    public List<ReturnOrder> list(String status) {
        return status != null && !status.isBlank()
                ? returnOrderRepo.findByTypeAndStatusOrderByCreateTimeDesc("SALES_RETURN", status)
                : returnOrderRepo.findByTypeOrderByCreateTimeDesc("SALES_RETURN");
    }

    public Optional<ReturnOrder> getById(Long id) {
        return returnOrderRepo.findById(id);
    }

    /**
     * 手工创建销售退货单（DRAFT；v5.27 参照销售出库单，锁定批号与剩余可退量）
     * @param customerId        客户 ID（可空：参照销售出库单带出时可能只有客户名称）
     * @param customerName      客户名称（必填）
     * @param salesOrderNo      原销售订单号（可空，冲减应收用；参照出库单时带出）
     * @param refSalesOutboundNo 原销售出库单号（参照出库单时必填）
     * @param batchNo           原出库批号（参照出库单时带出锁定）
     * @param materialCode      物料编码（必填）
     * @param materialName      品名
     * @param unit              单位
     * @param qty               退货数量（必填 > 0，不能超过出库单剩余可退量）
     * @param unitPrice         单价（可空默认 0，参照出库单时带出销售单价）
     * @param remark            备注
     * @param operator          制单人
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public ReturnOrder create(Long customerId, String customerName, String salesOrderNo,
                              String refSalesOutboundNo, String batchNo, String materialCode, String materialName,
                              String unit, BigDecimal qty, BigDecimal unitPrice,
                              String remark, String operator) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("请选择退货客户");
        }
        if (materialCode == null || materialCode.isBlank()) {
            throw new IllegalArgumentException("物料编码不能为空");
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退货数量必须大于 0");
        }
        // v5.27：参照销售出库单 → 校验出库单存在、锁定批号、不超过剩余可退量；销售订单号自动带出（冲减应收用）
        final String finalBatchNo;
        final String finalSalesOrderNo;
        if (refSalesOutboundNo != null && !refSalesOutboundNo.isBlank()) {
            SalesOutbound ob = salesOutRepo.findByDocNo(refSalesOutboundNo)
                    .orElseThrow(() -> new IllegalArgumentException("销售出库单不存在：" + refSalesOutboundNo));
            if (!"CONFIRMED".equals(ob.status)) {
                throw new IllegalArgumentException("销售出库单未确认，不可参照退货");
            }
            if (ob.batchNo != null && !ob.batchNo.isBlank() && batchNo != null
                    && !ob.batchNo.equals(batchNo)) {
                throw new IllegalArgumentException("退货批号与出库单批号不一致（应锁定 " + ob.batchNo + "）");
            }
            finalBatchNo = ob.batchNo; // 锁定出库单批号
            finalSalesOrderNo = (salesOrderNo == null || salesOrderNo.isBlank()) ? ob.salesOrderNo : salesOrderNo;
            BigDecimal remaining = calcRemaining(ob.docNo);
            if (qty.compareTo(remaining) > 0) {
                throw new IllegalArgumentException("退货数量不能超过出库单剩余可退量 " + remaining);
            }
        } else {
            finalBatchNo = batchNo;
            finalSalesOrderNo = salesOrderNo;
        }
        // v8.3（A7）：可退量二次校验留待 executeTx 内（见下 finalRefCheck）——锁外校验存在 TOCTOU
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // v8.3（A7）：锁内二次校验可退量（锁外校验 TOCTOU：两笔退货并发均可过初检，合计超退）
            if (refSalesOutboundNo != null && !refSalesOutboundNo.isBlank()) {
                BigDecimal rem = calcRemaining(refSalesOutboundNo);
                if (qty.compareTo(rem) > 0) {
                    throw new IllegalArgumentException("退货数量不能超过出库单剩余可退量 " + rem);
                }
            }
            // 销售退货单号 SR-YYYY-NNNN（与采购退货 RO- 前缀区分，避免 doc_no 唯一冲突）
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = returnOrderRepo.findMaxSeq("SR-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("SR-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            ReturnOrder ro = new ReturnOrder();
            ro.docNo = docNo;
            ro.type = "SALES_RETURN";
            ro.customerId = customerId;
            ro.customerName = customerName;
            ro.salesOrderNo = finalSalesOrderNo;
            ro.refSalesOutboundNo = refSalesOutboundNo;
            ro.batchNo = finalBatchNo;
            ro.materialCode = materialCode;
            ro.materialName = materialName;
            ro.unit = unit;
            ro.qty = qty;
            ro.unitPrice = price;
            ro.returnAmount = amount;
            ro.status = "DRAFT";
            ro.createdBy = operator;
            ro.remark = remark;
            ro.createTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            log.info("销售退货单创建: {} 客户={} 物料={} 批号={} 数量={} 金额={}",
                    docNo, customerName, materialCode, finalBatchNo, qty, amount);
            return ro;
        });
    }

    /**
     * 可参照退货的销售出库单列表（v5.27）：已确认且剩余可退量 > 0（出库量 - 已参照退货量）
     * @return 每项含 id/docNo/salesOrderNo/customerName/materialCode/materialName/batchNo/unit/qty/remaining/salePrice
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> listReturnableOutbounds() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (SalesOutbound ob : salesOutRepo.findByOrderByCreateTimeDesc()) {
            if (!"CONFIRMED".equals(ob.status)) continue;
            BigDecimal remaining = calcRemaining(ob.docNo);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", ob.id);
            row.put("docNo", ob.docNo);
            row.put("salesOrderNo", ob.salesOrderNo);
            row.put("customerName", ob.customerName);
            row.put("materialCode", ob.materialCode);
            row.put("materialName", ob.materialName);
            row.put("batchNo", ob.batchNo);
            row.put("unit", ob.unit);
            row.put("qty", ob.qty);
            row.put("remaining", remaining);
            // 销售单价（应收口径）：销售订单明细单价，冲减应收按此价
            BigDecimal salePrice = BigDecimal.ZERO;
            if (ob.salesOrderNo != null) {
                var orderOpt = salesOrderRepo.findByOrderNo(ob.salesOrderNo);
                if (orderOpt.isPresent()) {
                    for (var it : salesItemRepo.findByOrderId(orderOpt.get().id)) {
                        if (ob.materialCode.equals(it.materialCode)) {
                            salePrice = it.unitPrice != null ? it.unitPrice : BigDecimal.ZERO;
                            break;
                        }
                    }
                }
            }
            row.put("salePrice", salePrice);
            result.add(row);
        }
        return result;
    }

    /** 出库单剩余可退量 = 出库数量 - 已参照退货量（非驳回状态的销售退货单累计） */
    private BigDecimal calcRemaining(String outboundDocNo) {
        BigDecimal returned = BigDecimal.ZERO;
        for (ReturnOrder ro : returnOrderRepo.findByTypeOrderByCreateTimeDesc("SALES_RETURN")) {
            if ("REJECTED".equals(ro.status)) continue;
            if (outboundDocNo.equals(ro.refSalesOutboundNo) && ro.qty != null) {
                returned = returned.add(ro.qty);
            }
        }
        SalesOutbound ob = salesOutRepo.findByDocNo(outboundDocNo).orElse(null);
        if (ob == null || ob.qty == null) return BigDecimal.ZERO;
        return ob.qty.subtract(returned);
    }

    /**
     * 参照退货单入库（销售退货入库）
     * 校验 APPROVED + SALES_RETURN + 防重复 → 库存增加（自动新批号）→ 落库其他入库单(reason=RETURN)
     * → 退货单置 DONE → 冲减应收 → 回写销售订单明细 return_qty
     * @param returnOrderId 退货单 ID（须为 APPROVED 状态）
     * @param warehouseId   入库仓库
     * @param locationId    入库库位
     * @param operator      操作人
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public OtherInbound inboundFromReturn(Long returnOrderId, String warehouseId,
                                          String locationId, String operator) {
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            ReturnOrder ro = returnOrderRepo.findById(returnOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("退货单不存在"));
            if ("DONE".equals(ro.status)) {
                throw new IllegalArgumentException("该退货单已完成入库，不可重复入库");
            }
            if (!"APPROVED".equals(ro.status)) {
                throw new IllegalArgumentException("退货单未审核，不可入库");
            }
            if (!"SALES_RETURN".equals(ro.type)) {
                throw new IllegalArgumentException("仅销售退货单可做入库");
            }
            // 校验是否已入库（防止重复入库，双保险）
            if (otherInRepo.findByReturnRefId(returnOrderId).isPresent()) {
                throw new IllegalArgumentException("该退货单已生成入库单，不可重复入库");
            }
            // 先生成单据号与批号（批号与库存台账一致，便于追溯）
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = otherInRepo.findMaxSeq("OTHER-IN-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("OTHER-IN-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            String batchNo = inventoryService.nextBatchNo();
            // v6.1：退货入库存账改按成本价——此前用销售单价（ro.unitPrice）入账会虚增存货价值、
            // 压低后续销售毛利；正确口径：原批次台账成本 → 现货加权均价 → 退货单单价兜底（历史数据）
            BigDecimal costPrice = resolveCostPrice(ro);
            // 入库：增加库存（显式批号，v5.4）
            inventoryService.purchaseInbound("OTHER_IN", docNo, ro.materialCode, ro.materialName,
                    batchNo, warehouseId, locationId, ro.qty, costPrice, operator);
            // 创建其他入库单（复用预留的退货关联字段）
            OtherInbound in = new OtherInbound();
            in.docNo = docNo;
            in.materialCode = ro.materialCode;
            in.materialName = ro.materialName;
            in.batchNo = batchNo;
            in.warehouseId = warehouseId;
            in.locationId = locationId;
            in.qty = ro.qty;
            in.price = costPrice;
            in.unit = ro.unit;
            in.reason = "RETURN";
            in.returnRefType = "SALES_RETURN";
            in.returnRefId = ro.id;
            in.returnRefDocNo = ro.docNo;
            in.customerId = ro.customerId;
            in.returnAmount = ro.returnAmount;
            in.returnOffsetStatus = "APPLIED"; // 入库即冲减应收
            in.status = "CONFIRMED"; // 参照退货单入库自动确认
            in.createdBy = operator;
            in.remark = "参照退货单 " + ro.docNo + " 入库";
            in.createTime = LocalDateTime.now();
            otherInRepo.save(in);
            // 退货单状态置为 DONE，回写入库单号
            ro.status = "DONE";
            ro.inboundDocNo = docNo;
            ro.updateTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            // 冲减应收（按原销售订单号 FIFO 红冲）
            BigDecimal applied = BigDecimal.ZERO;
            if (ro.salesOrderNo != null && !ro.salesOrderNo.isBlank()
                    && ro.returnAmount != null && ro.returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                applied = financeService.applySalesReturn(ro.salesOrderNo, ro.returnAmount, docNo);
            }
            // 回写销售订单明细退货数量
            updateReturnQty(ro);
            log.info("销售退货入库完成: 退货单={} 入库单={} 批次={} 冲减应收={}", ro.docNo, docNo, batchNo, applied);
            return in;
        });
    }

    /** 回写 sales_order_item.return_qty（按原销售订单号 + 物料编码累加）
     *  v6.1.1 修复：同物料多行明细时原实现逐行各 +ro.qty（退货量翻倍）——只累加到首行 */
    private void updateReturnQty(ReturnOrder ro) {
        if (ro.salesOrderNo == null || ro.salesOrderNo.isBlank()) return;
        salesOrderRepo.findByOrderNo(ro.salesOrderNo).ifPresent(so -> {
            List<SalesOrderItem> items = salesItemRepo.findByOrderIdAndMaterialCode(so.id, ro.materialCode);
            boolean applied = false;
            for (SalesOrderItem it : items) {
                if (applied) break;
                it.returnQty = (it.returnQty == null ? BigDecimal.ZERO : it.returnQty).add(ro.qty);
                salesItemRepo.save(it);
                applied = true;
            }
        });
    }

    /**
     * v6.1 退货入库成本取价：① 原出库批次的台账行单价（该成本层的实际成本，优先未耗尽行）
     * → ② 现货加权均价（Σ金额/Σ数量，仅正库存行）→ ③ 退货单销售单价兜底（无台账的历史数据）。
     */
    private BigDecimal resolveCostPrice(ReturnOrder ro) {
        List<InventoryLedger> rows = ledgerRepo.findByMaterialCode(ro.materialCode);
        if (ro.batchNo != null && !ro.batchNo.isBlank()) {
            InventoryLedger fallbackRow = null;
            for (InventoryLedger l : rows) {
                if (!ro.batchNo.equals(l.batchNo) || l.unitPrice == null
                        || l.unitPrice.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (l.qty != null && l.qty.compareTo(BigDecimal.ZERO) != 0) return l.unitPrice;
                if (fallbackRow == null) fallbackRow = l;
            }
            if (fallbackRow != null) return fallbackRow.unitPrice;
        }
        BigDecimal qtySum = BigDecimal.ZERO, amtSum = BigDecimal.ZERO;
        for (InventoryLedger l : rows) {
            if (l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0
                    && l.amount != null && l.unitPrice != null && l.unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                qtySum = qtySum.add(l.qty);
                amtSum = amtSum.add(l.amount);
            }
        }
        if (qtySum.compareTo(BigDecimal.ZERO) > 0) {
            return amtSum.divide(qtySum, 2, java.math.RoundingMode.HALF_UP);
        }
        return ro.unitPrice != null ? ro.unitPrice : BigDecimal.ZERO;
    }
}
