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
 * 退货单服务
 * 采购退货流程（v5.5 修正）：QC 来料质检判定"退货" → 自动生成退货单(DRAFT) → 采购员审核(APPROVED/REJECTED)
 *          → 质检判定不合格的货物从未入库（REJECT 不触发入库），审核通过即退货完成(DONE)，
 *            货物直接退回供应商（无出库操作）；**不生成应付、不冲减应付**（收货才立应付，退货无财务动作）
 * 销售退货流程：手工创建退货单(DRAFT) → 销售员审核(APPROVED/REJECTED)
 *          → 仓管参照退货单做退货入库(DONE，由 SalesReturnService 处理)
 * type 区分：PURCHASE_RETURN / SALES_RETURN
 */
@Service
public class ReturnOrderService {

    private static final Logger log = LoggerFactory.getLogger(ReturnOrderService.class);

    private final ReturnOrderRepository returnOrderRepo;
    private final PurchaseArrivalRepository arrivalRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseOrderItemRepository purchaseItemRepo;
    private final QualityInspectionRepository qcRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final RawMaterialPurchaseRepository rawRepo;
    private final FinishedProductPurchaseRepository finishedRepo;
    private final FinanceService financeService;
    // v5.24：全局写锁（单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;

    public ReturnOrderService(ReturnOrderRepository returnOrderRepo,
                              PurchaseArrivalRepository arrivalRepo,
                              PurchaseOrderRepository purchaseOrderRepo,
                              PurchaseOrderItemRepository purchaseItemRepo,
                              QualityInspectionRepository qcRepo,
                              InventoryLedgerRepository ledgerRepo,
                              RawMaterialPurchaseRepository rawRepo,
                              FinishedProductPurchaseRepository finishedRepo,
                              FinanceService financeService,
                              WriteQueue writeQueue) {
        this.returnOrderRepo = returnOrderRepo;
        this.arrivalRepo = arrivalRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseItemRepo = purchaseItemRepo;
        this.qcRepo = qcRepo;
        this.ledgerRepo = ledgerRepo;
        this.rawRepo = rawRepo;
        this.finishedRepo = finishedRepo;
        this.financeService = financeService;
        this.writeQueue = writeQueue;
    }

    /** 查询全部退货单（按类型过滤，v5.4：采购/销售退货分页展示） */
    public List<ReturnOrder> listAll(String type) {
        return returnOrderRepo.findByTypeOrderByCreateTimeDesc(type);
    }

    /** 按状态查询（按类型过滤） */
    public List<ReturnOrder> listByStatus(String type, String status) {
        return returnOrderRepo.findByTypeAndStatusOrderByCreateTimeDesc(type, status);
    }

    public Optional<ReturnOrder> getById(Long id) {
        return returnOrderRepo.findById(id);
    }

    /**
     * 手工创建采购退货单（v5.27：参照到货单退货）
     * 选择已合格入库的到货单 → 带出物料/供应商/采购单号/库存批号 → 按该批号退货
     * 审核通过后需退货出库（按锁定批号扣库存并冲减应付）
     * @return 退货单（DRAFT）
     */
    // v8.6（N3）：去 @Transactional——方法内 executeTx 已锁内包事务，外层注解=旧时序（先开事务后抢锁）
    public ReturnOrder createManual(Long arrivalId, BigDecimal qty, BigDecimal unitPrice,
                                    String remark, String operator) {
        PurchaseArrival arrival = arrivalRepo.findById(arrivalId)
                .orElseThrow(() -> new IllegalArgumentException("到货单不存在"));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("退货数量必须大于 0");

        // 反查库存批号与可退量（到货单 → 质检单 → 合格入库台账）
        java.util.Map<String, Object> resolved = resolveArrivalBatch(arrival);
        String batchNo = (String) resolved.get("batchNo");
        BigDecimal stockQty = (BigDecimal) resolved.get("stockQty");
        if (batchNo == null) {
            throw new IllegalArgumentException("该到货单尚未合格入库，无库存批号可退（到货单#" + arrival.id + "）");
        }
        if (qty.compareTo(stockQty) > 0) {
            throw new IllegalArgumentException("退货数量不能超过批号 " + batchNo + " 的库存 " + stockQty);
        }

        // 单价：传参优先，否则按采购明细单价带出
        BigDecimal finalUnitPrice = unitPrice != null ? unitPrice
                : findPurchaseUnitPrice(arrival.refOrderNo, arrival.materialCode);

        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // v8.3（A7）：锁内二次校验库存可退量（锁外校验 TOCTOU）
            var arrivalNow = arrivalRepo.findById(arrivalId).orElse(null);
            if (arrivalNow == null) throw new IllegalArgumentException("到货单不存在");
            var resolvedNow = resolveArrivalBatch(arrivalNow);
            BigDecimal stockNow = (BigDecimal) resolvedNow.get("stockQty");
            if (qty.compareTo(stockNow) > 0) {
                throw new IllegalArgumentException("退货数量不能超过批号 " + resolvedNow.get("batchNo") + " 的库存 " + stockNow);
            }
            Integer maxSeq = returnOrderRepo.findMaxSeq("RO-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("RO-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            ReturnOrder ro = new ReturnOrder();
            ro.docNo = docNo;
            ro.type = "PURCHASE_RETURN";
            ro.refArrivalId = arrival.id;
            ro.purchaseOrderNo = arrival.refOrderNo;
            ro.supplierId = arrival.supplierId;
            ro.materialCode = arrival.materialCode;
            ro.materialName = arrival.materialName;
            ro.batchNo = batchNo;
            ro.unit = arrival.unit;
            ro.qty = qty;
            ro.unitPrice = finalUnitPrice;
            ro.returnAmount = ro.qty.multiply(finalUnitPrice).setScale(2, RoundingMode.HALF_UP);
            ro.status = "DRAFT";
            ro.createdBy = operator;
            ro.remark = remark;
            ro.createTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            log.info("手工采购退货单创建(参照到货单#{}): {} 物料={} 批号={} 数量={} 金额={}",
                    arrival.id, docNo, arrival.materialCode, batchNo, qty, ro.returnAmount);
            return ro;
        });
    }

    /** 按采购单号+物料取采购单价（原料/成品表） */
    private BigDecimal findPurchaseUnitPrice(String orderNo, String materialCode) {
        if (orderNo == null || materialCode == null) return BigDecimal.ZERO;
        for (RawMaterialPurchase r : rawRepo.findAllByOrderNo(orderNo)) {
            if (materialCode.equals(r.materialCode) && r.unitPrice != null) return r.unitPrice;
        }
        for (FinishedProductPurchase f : finishedRepo.findAllByOrderNo(orderNo)) {
            if (materialCode.equals(f.materialCode) && f.unitPrice != null) return f.unitPrice;
        }
        return BigDecimal.ZERO;
    }

    /** 批量版：从预取的 采购单号→明细 映射取价（语义与逐单版一致） */
    private BigDecimal findPurchaseUnitPrice(String orderNo, String materialCode,
                                             java.util.Map<String, List<RawMaterialPurchase>> rawByOrderNo,
                                             java.util.Map<String, List<FinishedProductPurchase>> finByOrderNo) {
        if (orderNo == null || materialCode == null) return BigDecimal.ZERO;
        for (RawMaterialPurchase r : rawByOrderNo.getOrDefault(orderNo, List.of())) {
            if (materialCode.equals(r.materialCode) && r.unitPrice != null) return r.unitPrice;
        }
        for (FinishedProductPurchase f : finByOrderNo.getOrDefault(orderNo, List.of())) {
            if (materialCode.equals(f.materialCode) && f.unitPrice != null) return f.unitPrice;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 可参照退货的到货单列表（v5.27）：只有合格入库且批号仍有库存的到货单才能被参照
     * @return 每项含 arrivalId/refOrderNo/supplierName/materialCode/materialName/batchNo/stockQty/unitPrice/arrivalQty
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> listReturnableArrivals() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        List<PurchaseArrival> arrivals = arrivalRepo.findAll();
        // v5.55：批量预取质检/台账/采购价（替代逐单 4 次反查的 N+1）
        java.util.Map<String, List<QualityInspection>> qcByRefDoc = new java.util.HashMap<>();
        for (QualityInspection q : qcRepo.findByType("INCOMING")) {
            if (q.refDocNo != null) qcByRefDoc.computeIfAbsent(q.refDocNo, k -> new java.util.ArrayList<>()).add(q);
        }
        java.util.Map<String, List<com.pengyuan.pims.entity.InventoryLedger>> ledgerByQcNo = new java.util.HashMap<>();
        for (var l : ledgerRepo.findByQcInspectionNoIsNotNull()) {
            ledgerByQcNo.computeIfAbsent(l.qcInspectionNo, k -> new java.util.ArrayList<>()).add(l);
        }
        java.util.Set<String> refNos = new java.util.HashSet<>();
        for (PurchaseArrival a : arrivals) {
            if (a.refOrderNo != null) refNos.add(a.refOrderNo);
        }
        java.util.Map<String, List<RawMaterialPurchase>> rawByOrderNo = new java.util.HashMap<>();
        if (!refNos.isEmpty()) {
            for (RawMaterialPurchase r : rawRepo.findAllByOrderNoIn(refNos)) {
                rawByOrderNo.computeIfAbsent(r.orderNo, k -> new java.util.ArrayList<>()).add(r);
            }
        }
        java.util.Map<String, List<FinishedProductPurchase>> finByOrderNo = new java.util.HashMap<>();
        if (!refNos.isEmpty()) {
            for (FinishedProductPurchase f : finishedRepo.findAllByOrderNoIn(refNos)) {
                finByOrderNo.computeIfAbsent(f.orderNo, k -> new java.util.ArrayList<>()).add(f);
            }
        }
        for (PurchaseArrival a : arrivals) {
            java.util.Map<String, Object> resolved = resolveArrivalBatch(a,
                    qcByRefDoc.getOrDefault(a.refOrderNo, List.of()),
                    q -> ledgerByQcNo.getOrDefault(q, List.of()));
            String batchNo = (String) resolved.get("batchNo");
            BigDecimal stockQty = (BigDecimal) resolved.get("stockQty");
            if (batchNo == null || stockQty == null || stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("arrivalId", a.id);
            row.put("arrivalDate", a.arrivalDate);
            row.put("refOrderNo", a.refOrderNo);
            row.put("supplierId", a.supplierId);
            row.put("supplierName", a.supplierName);
            row.put("materialCode", a.materialCode);
            row.put("materialName", a.materialName);
            row.put("arrivalQty", a.qty);
            row.put("batchNo", batchNo);
            row.put("stockQty", stockQty);
            row.put("warehouseId", resolved.get("warehouseId")); // v5.27：批号所在仓库，出库直接带出
            row.put("unitPrice", findPurchaseUnitPrice(a.refOrderNo, a.materialCode, rawByOrderNo, finByOrderNo));
            result.add(row);
        }
        // 按批号库存降序，便于优先退库存多的
        result.sort((x, y) -> ((BigDecimal) y.get("stockQty")).compareTo((BigDecimal) x.get("stockQty")));
        return result;
    }

    /**
     * 到货单退货预览（v5.27）：反查该到货单合格入库后的库存批号、可退量、采购单价
     * @return batchNo/stockQty/unitPrice/supplierName（batchNo 为空表示未合格入库不可退）
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> arrivalPreview(Long arrivalId) {
        PurchaseArrival arrival = arrivalRepo.findById(arrivalId)
                .orElseThrow(() -> new IllegalArgumentException("到货单不存在"));
        java.util.Map<String, Object> resolved = resolveArrivalBatch(arrival);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("arrivalId", arrival.id);
        result.put("arrivalDate", arrival.arrivalDate);
        result.put("refOrderNo", arrival.refOrderNo);
        result.put("supplierId", arrival.supplierId);
        result.put("supplierName", arrival.supplierName);
        result.put("materialCode", arrival.materialCode);
        result.put("materialName", arrival.materialName);
        result.put("arrivalQty", arrival.qty);
        result.put("batchNo", resolved.get("batchNo"));
        result.put("stockQty", resolved.get("stockQty"));
        result.put("warehouseId", resolved.get("warehouseId")); // v5.27：批号所在仓库，出库自动带出
        result.put("unitPrice", findPurchaseUnitPrice(arrival.refOrderNo, arrival.materialCode));
        return result;
    }

    /** 反查到货单合格入库后的库存批号与可退量（到货单 → 质检单同单号同物料同数量 → 台账 qc_inspection_no） */
    private java.util.Map<String, Object> resolveArrivalBatch(PurchaseArrival arrival) {
        return resolveArrivalBatch(arrival, qcRepo.findByRefDocNoAndType(arrival.refOrderNo, "INCOMING"),
                q -> ledgerRepo.findByQcInspectionNo(q));
    }

    /** 批量版：qcs / ledgerByQcNo 为预取数据，语义与逐单版一致 */
    private java.util.Map<String, Object> resolveArrivalBatch(PurchaseArrival arrival,
                                                              List<QualityInspection> qcs,
                                                              java.util.function.Function<String, List<com.pengyuan.pims.entity.InventoryLedger>> ledgerByQcNo) {
        String batchNo = null;
        BigDecimal stockQty = BigDecimal.ZERO;
        String warehouseId = null; // v5.27：批号所在仓库（库存最大的仓，出库自动带出）
        for (QualityInspection q : qcs) {
            if (q.materialCode == null || !q.materialCode.equals(arrival.materialCode)) continue;
            if (q.qty == null || q.qty.compareTo(arrival.qty) != 0) continue;
            var ledgers = ledgerByQcNo.apply(q.inspectionNo);
            java.util.Map<String, BigDecimal> whQty = new java.util.HashMap<>();
            for (var l : ledgers) {
                if (l.batchNo != null && !l.batchNo.isBlank()) {
                    batchNo = l.batchNo;
                    stockQty = stockQty.add(l.qty != null ? l.qty : BigDecimal.ZERO);
                    if (l.warehouseId != null) {
                        whQty.merge(l.warehouseId, l.qty != null ? l.qty : BigDecimal.ZERO, BigDecimal::add);
                    }
                }
            }
            // 取库存最大的仓
            if (!whQty.isEmpty()) {
                warehouseId = whQty.entrySet().stream()
                        .max(java.util.Map.Entry.comparingByValue())
                        .map(java.util.Map.Entry::getKey)
                        .orElse(null);
            }
            if (batchNo != null) break;
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("batchNo", batchNo);
        result.put("stockQty", stockQty);
        result.put("warehouseId", warehouseId);
        return result;
    }

    /**
     * QC 判定退货时自动生成采购退货单（DRAFT 状态）
     * 幂等：同一质检单号仅生成一次
     * @param qcId 质检单 ID
     */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public ReturnOrder createFromQcReject(Long qcId) {
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            QualityInspection qc = qcRepo.findById(qcId)
                    .orElseThrow(() -> new IllegalArgumentException("质检单不存在"));
            // 幂等校验
            if (qc.refDocNo != null && returnOrderRepo.findByQcInspectionNo(qc.inspectionNo).isPresent()) {
                log.info("质检单 {} 已生成退货单，跳过", qc.inspectionNo);
                return returnOrderRepo.findByQcInspectionNo(qc.inspectionNo).get();
            }
            // 反查采购到货单与采购订单明细，取单价
            PurchaseArrival arrival = null;
            PurchaseOrder purchaseOrder = null;
            BigDecimal unitPrice = BigDecimal.ZERO;
            Long supplierId = null;
            if ("PURCHASE".equals(qc.refDocType) && qc.refDocNo != null) {
                // refDocNo 存的是采购单号
                purchaseOrder = purchaseOrderRepo.findByOrderNo(qc.refDocNo).orElse(null);
                if (purchaseOrder != null) {
                    for (PurchaseOrderItem it : purchaseItemRepo.findByOrderId(purchaseOrder.id)) {
                        if (qc.materialCode != null && qc.materialCode.equals(it.materialCode)) {
                            unitPrice = it.unitPrice != null ? it.unitPrice : BigDecimal.ZERO;
                            break;
                        }
                    }
                }
                // v5.5：purchase_order 旧表已废弃（0 行），实际采购单在 raw/finished 表，反查单价与供应商
                if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                    for (RawMaterialPurchase rp : rawRepo.findAllByOrderNo(qc.refDocNo)) {
                        if (qc.materialCode != null && qc.materialCode.equals(rp.materialCode)) {
                            unitPrice = rp.unitPrice != null ? rp.unitPrice : BigDecimal.ZERO;
                            if (rp.supplierId != null) supplierId = rp.supplierId;
                            break;
                        }
                    }
                    if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                        for (FinishedProductPurchase fp : finishedRepo.findAllByOrderNo(qc.refDocNo)) {
                            if (qc.materialCode != null && qc.materialCode.equals(fp.materialCode)) {
                                unitPrice = fp.unitPrice != null ? fp.unitPrice : BigDecimal.ZERO;
                                if (fp.supplierId != null) supplierId = fp.supplierId;
                                break;
                            }
                        }
                    }
                }
                // 反查到货单（v4.8：按单号直查；v5.27：精确匹配同物料+同数量的到货单——多批到货各批独立立账/冲减）
                var arrivals = arrivalRepo.findByRefOrderNo(qc.refDocNo);
                if (!arrivals.isEmpty()) {
                    arrival = arrivals.stream()
                            .filter(a -> qc.materialCode != null && qc.materialCode.equals(a.materialCode))
                            .filter(a -> a.qty != null && a.qty.compareTo(qc.qty) == 0)
                            .max(java.util.Comparator.comparing(a -> a.id))
                            .orElse(arrivals.get(0));
                }
            }
            if (supplierId == null) {
                supplierId = purchaseOrder != null ? purchaseOrder.supplierId
                        : (arrival != null ? arrival.supplierId : null);
            }
            BigDecimal returnAmount = qc.qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = returnOrderRepo.findMaxSeq("RO-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("RO-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            ReturnOrder ro = new ReturnOrder();
            ro.docNo = docNo;
            ro.type = "PURCHASE_RETURN";
            ro.refArrivalId = arrival != null ? arrival.id : null;
            ro.purchaseOrderNo = qc.refDocNo; // 采购单号，冲减应付用
            ro.qcInspectionNo = qc.inspectionNo;
            ro.materialCode = qc.materialCode;
            ro.materialName = qc.materialName;
            ro.unit = qc.unit;
            ro.qty = qc.qty;
            ro.unitPrice = unitPrice;
            ro.returnAmount = returnAmount;
            ro.supplierId = supplierId;
            ro.status = "DRAFT";
            ro.createdBy = qc.inspector != null ? qc.inspector : "QC";
            ro.remark = "QC 判定退货：" + (qc.resultRemark != null ? qc.resultRemark : "");
            ro.createTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            log.info("采购退货单自动生成: {} 质检单={} 采购单={} 退货金额={}", docNo, qc.inspectionNo, qc.refDocNo, returnAmount);
            return ro;
        });
    }

    /**
     * 审核退货单（DRAFT → 审核）
     * v5.5/v5.27 按类型分流：
     * - PURCHASE_RETURN（采购退货）：
     *   质检退货（有质检单号）→ 货物从未入库，但到货审核已立应付 → 审核即完成（DONE），
     *   并按到货单冲减应付（applyPurchaseReturn，v6.1）
     *   手工退货（无质检单号）→ 选择库存退货（已入库货物）→ 审核后（APPROVED）等待退货出库，出库时扣库存并冲减应付
     * - SALES_RETURN（销售退货）：审核通过（APPROVED），等待仓管退货入库（入库时冲减应收）
     */
    @Transactional
    public ReturnOrder approve(Long id, String approver, String remark) {
        ReturnOrder ro = returnOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("退货单不存在"));
        if (!"DRAFT".equals(ro.status)) {
            throw new IllegalArgumentException("仅待审核状态的退货单可审核");
        }
        ro.approvedBy = approver;
        ro.approveTime = LocalDateTime.now();
        if (remark != null && !remark.isBlank()) {
            ro.remark = (ro.remark == null ? "" : ro.remark + " | ") + "审核：" + remark;
        }
        if ("PURCHASE_RETURN".equals(ro.type)) {
            if (ro.qcInspectionNo != null && !ro.qcInspectionNo.isBlank()) {
                // v6.1 修复（v5.80 审查高#6）：v5.27 起到货审核即立 AP（货到即负债），
                // 质检退货审核必须冲减该到货立的 AP，否则应付永久虚挂。冲减金额=退货数量×采购单价（与立账口径一致）。
                try {
                    java.math.BigDecimal returnAmt = ro.unitPrice != null ? ro.unitPrice.multiply(ro.qty) : null;
                    financeService.applyPurchaseReturn(ro.purchaseOrderNo, returnAmt, ro.docNo, null);
                    log.info("质检退货冲减应付: 退货单={} 订单={} 金额={}", ro.docNo, ro.purchaseOrderNo, returnAmt);
                } catch (IllegalArgumentException e) {
                    throw e;   // 冲减失败（如无 AP 可冲）向上抛，阻止无账务的完成
                }
                ro.status = "DONE";
                ro.updateTime = LocalDateTime.now();
                log.info("采购退货完成(质检退货,无库存无应付): {} 审核人={}", ro.docNo, approver);
            } else {
                // 手工退货：选择库存退货（已入库货物）→ 审核后等待退货出库，出库时扣库存并冲减应付
                ro.status = "APPROVED";
                ro.updateTime = LocalDateTime.now();
                log.info("采购退货审核通过(库存退货,待出库): {} 审核人={}", ro.docNo, approver);
            }
        } else {
            ro.status = "APPROVED"; // 销售退货：等待仓管退货入库
            ro.updateTime = LocalDateTime.now();
            log.info("销售退货审核通过: {} 审核人={}，等待退货入库", ro.docNo, approver);
        }
        returnOrderRepo.save(ro);
        return ro;
    }

    /**
     * v5.5：迁移旧流程遗留的 APPROVED 采购退货单（旧逻辑审核后停在 APPROVED 等待"退货出库"，现已无出库步骤）。
     * → 按新采购单表反查补单价/金额（仅展示，旧逻辑用废弃的 purchase_order 表反查恒为 0）→ 置 DONE。
     * **不冲减应付**：退货物料从未生成应付（收货才立应付），冲减会误伤同单其他合格物料的应付。
     * 启动幂等执行；新流程产生的单据不会进入 APPROVED，此方法仅兜底迁移。
     */
    @Transactional
    public int migrateApprovedPurchaseReturns() {
        int n = 0;
        for (ReturnOrder ro : returnOrderRepo.findByTypeAndStatusOrderByCreateTimeDesc("PURCHASE_RETURN", "APPROVED")) {
            if (ro.returnAmount == null || ro.returnAmount.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal unitPrice = lookupUnitPrice(ro.purchaseOrderNo, ro.materialCode);
                if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    ro.unitPrice = unitPrice;
                    ro.returnAmount = ro.qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                }
            }
            ro.status = "DONE";
            ro.updateTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            n++;
            log.info("v5.5 迁移: 采购退货单 {} 审核即完成 → DONE（无应付冲减）", ro.docNo);
        }
        return n;
    }

    /** 从新采购单表（raw/finished）反查物料单价 */
    private BigDecimal lookupUnitPrice(String orderNo, String materialCode) {
        if (orderNo == null || materialCode == null) return null;
        for (RawMaterialPurchase rp : rawRepo.findAllByOrderNo(orderNo)) {
            if (materialCode.equals(rp.materialCode)) return rp.unitPrice;
        }
        for (FinishedProductPurchase fp : finishedRepo.findAllByOrderNo(orderNo)) {
            if (materialCode.equals(fp.materialCode)) return fp.unitPrice;
        }
        return null;
    }

    /**
     * 采购员驳回退货单（DRAFT → REJECTED）
     */
    @Transactional
    public ReturnOrder reject(Long id, String approver, String reason) {
        ReturnOrder ro = returnOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("退货单不存在"));
        if (!"DRAFT".equals(ro.status)) {
            throw new IllegalArgumentException("仅待审核状态的退货单可驳回");
        }
        ro.status = "REJECTED";
        ro.approvedBy = approver;
        ro.approveTime = LocalDateTime.now();
        ro.remark = (ro.remark == null ? "" : ro.remark + " | ") + "驳回：" + (reason != null ? reason : "");
        ro.updateTime = LocalDateTime.now();
        returnOrderRepo.save(ro);
        log.info("退货单驳回: {} 审核人={} 原因={}", ro.docNo, approver, reason);
        return ro;
    }
}
