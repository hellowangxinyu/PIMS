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

/**
 * 出库单据服务
 * 统一管理四种出库：生产出库、销售出库、委外出库、其他出库
 * 流程：创建单据(DRAFT) → 确认(CONFIRMED)触发库存变动
 */
@Service
public class OutboundService {

    private static final Logger log = LoggerFactory.getLogger(OutboundService.class);

    private final ProductionOutboundRepository prodRepo;
    private final SalesOutboundRepository salesRepo;
    private final OutsourceMaterialOutboundRepository outsourceRepo;
    private final OtherOutboundRepository otherRepo;
    private final InventoryService inventoryService;
    private final ProductionOrderRepository orderRepo;
    private final ProductionOrderItemRepository orderItemRepo;
    private final ProductionInboundRepository prodInRepo;
    private final OutsourceOrderRepository outsourceOrderRepo;
    private final OutsourceOrderItemRepository outsourceOrderItemRepo;
    private final OutsourceFinishInboundRepository outsourceInRepo;
    private final OtherInboundRepository otherInRepo;
    private final QualityInspectionService qcService;
    private final InventoryLedgerRepository ledgerRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final SalesOrderItemRepository salesItemRepo;
    private final FinanceService financeService;
    private final CustomerRepository customerRepo;
    // 退货关联用：退货单 / 采购到货 / 采购订单 / 采购订单明细
    private final ReturnOrderRepository returnOrderRepo;
    private final PurchaseArrivalRepository purchaseArrivalRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseOrderItemRepository purchaseItemRepo;
    // 分库/库位名称查询（用于入库单冗余字段填充）
    private final WarehouseZoneRepository zoneRepo;
    private final WarehouseLocationRepository locationRepo;
    private final com.pengyuan.pims.repository.WarehouseRepository warehouseRepo;
    // v5.24：全局写锁（单号生成+单据保存需与库存扣减共用同一把锁，防并发撞号）
    private final WriteQueue writeQueue;
    // v5.63：计价收口（出库成本按计价方式分流）
    private final CostingService costingService;

    public OutboundService(ProductionOutboundRepository prodRepo,
                           SalesOutboundRepository salesRepo,
                           OutsourceMaterialOutboundRepository outsourceRepo,
                           OtherOutboundRepository otherRepo,
                           InventoryService inventoryService,
                           ProductionOrderRepository orderRepo,
                           ProductionOrderItemRepository orderItemRepo,
                           ProductionInboundRepository prodInRepo,
                           OutsourceOrderRepository outsourceOrderRepo,
                           OutsourceOrderItemRepository outsourceOrderItemRepo,
                           OutsourceFinishInboundRepository outsourceInRepo,
                           OtherInboundRepository otherInRepo,
                           QualityInspectionService qcService,
                           InventoryLedgerRepository ledgerRepo,
                           SalesOrderRepository salesOrderRepo,
                           SalesOrderItemRepository salesItemRepo,
                           FinanceService financeService,
                           CustomerRepository customerRepo,
                           ReturnOrderRepository returnOrderRepo,
                           PurchaseArrivalRepository purchaseArrivalRepo,
                           PurchaseOrderRepository purchaseOrderRepo,
                           PurchaseOrderItemRepository purchaseItemRepo,
                           WarehouseZoneRepository zoneRepo,
                           WarehouseLocationRepository locationRepo,
                           com.pengyuan.pims.repository.WarehouseRepository warehouseRepo,
                           WriteQueue writeQueue,
                           CostingService costingService) {
        this.prodRepo = prodRepo;
        this.salesRepo = salesRepo;
        this.outsourceRepo = outsourceRepo;
        this.otherRepo = otherRepo;
        this.inventoryService = inventoryService;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.prodInRepo = prodInRepo;
        this.outsourceOrderRepo = outsourceOrderRepo;
        this.outsourceOrderItemRepo = outsourceOrderItemRepo;
        this.outsourceInRepo = outsourceInRepo;
        this.otherInRepo = otherInRepo;
        this.qcService = qcService;
        this.ledgerRepo = ledgerRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.salesItemRepo = salesItemRepo;
        this.financeService = financeService;
        this.customerRepo = customerRepo;
        this.returnOrderRepo = returnOrderRepo;
        this.purchaseArrivalRepo = purchaseArrivalRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseItemRepo = purchaseItemRepo;
        this.zoneRepo = zoneRepo;
        this.locationRepo = locationRepo;
        this.warehouseRepo = warehouseRepo;
        this.writeQueue = writeQueue;
        this.costingService = costingService;
    }

    /** 仓库ID → 仓库名称（用于出库明细冗余展示） */
    private String warehouseName(String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank()) return null;
        return warehouseRepo.findById(Long.valueOf(warehouseId)).map(w -> w.name).orElse(null);
    }

    /**
     * 根据库位ID查询分库名称和库位名称（用于入库单冗余字段填充）
     * @param locationId 库位ID（字符串形式）
     * @return [zoneName, locationName]，查不到返回 [null, null]
     */
    private String[] resolveZoneAndLocationName(String locationId) {
        if (locationId == null || locationId.isBlank()) return new String[]{null, null};
        try {
            Long locId = Long.valueOf(locationId);
            return locationRepo.findById(locId)
                    .map(loc -> {
                        String locName = loc.name;
                        String zoneName = zoneRepo.findById(loc.zoneId)
                                .map(z -> z.name)
                                .orElse(null);
                        return new String[]{zoneName, locName};
                    })
                    .orElse(new String[]{null, null});
        } catch (NumberFormatException e) {
            return new String[]{null, null};
        }
    }

    /**
     * 按客户收款条件计算应收到期日（账期缓冲）：
     * 款到发货/货到付款=当天；账期N天=发货日+N天；月结=次月1号；两月结=+2月1号；三月结=+3月1号
     */
    private java.time.LocalDate calcArDueDate(Customer customer, java.time.LocalDate fallback) {
        if (customer == null || customer.paymentTerms == null || customer.paymentTerms.isBlank()) return fallback;
        java.time.LocalDate today = java.time.LocalDate.now();
        switch (customer.paymentTerms) {
            case "PREPAID": return today;
            case "COD": return today; // v5.6：货到付款，货到即付
            case "CREDIT_30": return today.plusDays(30);
            case "CREDIT_60": return today.plusDays(60);
            case "MONTHLY": return today.plusMonths(1).withDayOfMonth(1);
            case "TWO_MONTH": return today.plusMonths(2).withDayOfMonth(1);
            case "THREE_MONTH": return today.plusMonths(3).withDayOfMonth(1);
            default:
                // 自定义账期，如「账期45天」
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(customer.paymentTerms);
                if (customer.paymentTerms.contains("天") && m.find()) {
                    return today.plusDays(Long.parseLong(m.group(1)));
                }
                return fallback;
        }
    }

    /** 得率 = 实际产出 / 理论产出 × 100（理论量为空或0时返回 null） */
    private BigDecimal calcYieldRate(BigDecimal actual, BigDecimal theoretical) {
        if (actual == null || theoretical == null || theoretical.compareTo(BigDecimal.ZERO) <= 0) return null;
        return actual.multiply(BigDecimal.valueOf(100)).divide(theoretical, 2, RoundingMode.HALF_UP);
    }

    /** 出库必须选择批号（实际成本按批号直取） */
    private void requireBatchNo(String batchNo, String materialCode) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new IllegalArgumentException("物料 " + materialCode + " 出库必须选择批号");
        }
    }

    /** v5.27：生产出库前置校验——只有已排产的订单才允许领料出库（独立单据不校验） */
    private void requireScheduled(String productionOrderNo) {
        if (productionOrderNo == null || productionOrderNo.isBlank()) return;
        orderRepo.findByOrderNo(productionOrderNo).ifPresent(o -> {
            if (!"SCHEDULED".equals(o.status)) {
                throw new IllegalArgumentException("该订单未排产，不能领料出库（请先在排产中心排产）");
            }
        });
    }

    /** v5.27：委外发料前置校验——只有已委外（已排产）的订单才允许发料出库 */
    private void requireOutsourced(String outsourceOrderNo) {
        if (outsourceOrderNo == null || outsourceOrderNo.isBlank()) return;
        outsourceOrderRepo.findByOrderNo(outsourceOrderNo).ifPresent(o -> {
            if (!"OUTSOURCED".equals(o.status)) {
                throw new IllegalArgumentException("该订单未排产（委外），不能发料出库（请先在排产中心排产）");
            }
        });
    }

    /** v5.63 出库取价收口：委托 CostingService 按计价方式分流（个别计价=批次价现状/移动加权/全月平均暂估） */
    private BigDecimal batchUnitPrice(String materialCode, String batchNo, String warehouseId) {
        if (materialCode == null || batchNo == null) return null;
        return costingService.unitPrice(materialCode, batchNo, warehouseId, null);
    }

    /** v5.55：批量预取多物料的台账行（一次往返），供批量出库/预检循环复用，消除逐物料 N+1 */
    private java.util.Map<String, List<InventoryLedger>> ledgersByCode(java.util.Collection<String> codes) {
        java.util.Map<String, List<InventoryLedger>> m = new java.util.HashMap<>();
        for (String c : codes) m.put(c, new java.util.ArrayList<>());
        for (InventoryLedger l : ledgerRepo.findByMaterialCodeIn(codes)) {
            m.computeIfAbsent(l.materialCode, k -> new java.util.ArrayList<>()).add(l);
        }
        return m;
    }

    /** 回填出库单据的单价与实际成本（v5.63 经 CostingService 按计价方式分流；preloaded 为批量预取台账行） */
    private void fillBatchCost(BigDecimal qty, String materialCode, String batchNo,
                               String warehouseId, List<InventoryLedger> preloaded,
                               java.util.function.Consumer<BigDecimal[]> setter) {
        if (materialCode == null || batchNo == null) {
            setter.accept(new BigDecimal[]{null, null});
            return;
        }
        BigDecimal price = costingService.unitPrice(materialCode, batchNo, warehouseId, preloaded);
        setter.accept(new BigDecimal[]{price,
                price != null ? qty.multiply(price).setScale(2, RoundingMode.HALF_UP) : null});
    }

    /** 回填出库单据的批号单价与实际成本 */
    private void fillBatchCost(BigDecimal qty, String materialCode, String batchNo,
                               String warehouseId, java.util.function.Consumer<BigDecimal[]> setter) {
        BigDecimal price = batchUnitPrice(materialCode, batchNo, warehouseId);
        setter.accept(new BigDecimal[]{price,
                price != null ? qty.multiply(price).setScale(2, RoundingMode.HALF_UP) : null});
    }


    // ==================== v5.9 关键字分页搜索 ====================
    public org.springframework.data.domain.Page<ProductionOutbound> searchProduction(String kw, org.springframework.data.domain.Pageable pageable) {
        return prodRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    public org.springframework.data.domain.Page<ProductionInbound> searchProductionInbound(String kw, org.springframework.data.domain.Pageable pageable) {
        return prodInRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    public org.springframework.data.domain.Page<SalesOutbound> searchSales(String kw, org.springframework.data.domain.Pageable pageable) {
        return salesRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    public org.springframework.data.domain.Page<OutsourceMaterialOutbound> searchOutsource(String kw, org.springframework.data.domain.Pageable pageable) {
        return outsourceRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    public org.springframework.data.domain.Page<OutsourceFinishInbound> searchOutsourceInbound(String kw, org.springframework.data.domain.Pageable pageable) {
        return outsourceInRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    public org.springframework.data.domain.Page<OtherOutbound> searchOther(String kw, org.springframework.data.domain.Pageable pageable) {
        return otherRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    public org.springframework.data.domain.Page<OtherInbound> searchOtherInbound(String kw, org.springframework.data.domain.Pageable pageable) {
        return otherInRepo.searchByKeyword(kw == null ? "" : kw.trim(), pageable);
    }
    // ==================== 生产出库（参照生产订单/配方表） ====================

    public List<ProductionOutbound> listProduction() {
        return prodRepo.findByOrderByCreateTimeDesc();
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<ProductionOutbound> createFromOrder(Long productionOrderId, String warehouseId,
                                                    String operator, String remark,
                                                    java.util.Map<String, java.util.Map<String, Object>> overrides) {
        return createFromOrder(productionOrderId, warehouseId, operator, remark, overrides, false, null);
    }

    /**
     * 参照生产订单创建出库单据（按配方明细逐项生成）
     * supplement=true 为补领：允许已领料订单追加领料（跳过一单一出校验），
     * 未填追加用量（qty 为空或 ≤0）的明细行不生成出库单
     * @param productionOrderId 生产订单ID
     * @param warehouseId 出库仓库
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<ProductionOutbound> createFromOrder(Long productionOrderId, String warehouseId,
                                                    String operator, String remark,
                                                    java.util.Map<String, java.util.Map<String, Object>> overrides,
                                                    boolean supplement, String supplementType) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
        ProductionOrder order = orderRepo.findById(productionOrderId)
                .orElseThrow(() -> new IllegalArgumentException("生产订单不存在"));
        // v5.27：只有已排产的订单才允许领料出库
        if (!"SCHEDULED".equals(order.status))
            throw new IllegalArgumentException("该订单未排产，不能领料出库（请先在排产中心排产，并确保原材料库存充足）");

            List<ProductionOrderItem> items = orderItemRepo.findByOrderId(productionOrderId);
            if (items.isEmpty())
                throw new IllegalArgumentException("生产订单无配方明细");

            // v5.70 P0 防呆：领料总量不允许超过订单配方需求量的 150%
        {
            java.math.BigDecimal totalNeed = java.math.BigDecimal.ZERO;
            for (ProductionOrderItem item : items) totalNeed = totalNeed.add(item.qty);
            java.math.BigDecimal maxAllowed = totalNeed.multiply(new java.math.BigDecimal("1.5"));
            if (!supplement) {
                // 首次领料：校验各行用量
                if (overrides != null) {
                    java.math.BigDecimal totalIssue = java.math.BigDecimal.ZERO;
                    for (ProductionOrderItem item : items) {
                        java.util.Map<String, Object> ov = overrides.get(item.materialCode);
                        if (ov != null && ov.get("qty") != null) {
                            totalIssue = totalIssue.add(java.math.BigDecimal.valueOf(((Number) ov.get("qty")).doubleValue()));
                        }
                    }
                    if (totalIssue.compareTo(maxAllowed) > 0) {
                        throw new IllegalArgumentException(String.format(
                            "领料总量 %.2f 超过配方需求量 %.2f 的 1.5 倍上限（如确需超领请走补领流程）",
                            totalIssue, totalNeed));
                    }
                }
            }
        }
        // 重复参照校验：同一生产订单只允许领料出库一次（补领除外——补领就是给已领料订单追加）
            if (!supplement) {
                List<ProductionOutbound> existOuts = prodRepo.findByProductionOrderNoAndStatus(order.orderNo, "CONFIRMED");
                if (!existOuts.isEmpty()) {
                    throw new IllegalArgumentException("该生产订单已领料出库，不允许重复参照（如需追加请用「补领」）");
                }
            }

            // 入库完成校验：已存在 DONE 状态入库单的订单不允许再出库
            List<ProductionInbound> existIns = prodInRepo.findByProductionOrderNo(order.orderNo);
            for (ProductionInbound exist : existIns) {
                if ("DONE".equals(exist.status)) {
                    throw new IllegalArgumentException("该生产订单已完成入库，不允许再出库");
                }
            }

            List<ProductionOutbound> docs = new java.util.ArrayList<>();
            // v5.55：批量预取明细物料的台账（循环内 fillBatchCost 不再逐项回表）
            java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                    items.stream().map(i -> i.materialCode).toList());
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer prodOutMaxSeq = prodRepo.findMaxSeq("PROD-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
            long seq = (prodOutMaxSeq == null ? 0 : prodOutMaxSeq);
            for (ProductionOrderItem item : items) {
                // 优先使用前端选择的批次和用量，批号必填
                java.util.Map<String, Object> ov = overrides != null ? overrides.get(item.materialCode) : null;
                // 补领：只生成填写了追加用量的行（用量空/0 视为本次不补该料）
                if (supplement && (ov == null || ov.get("qty") == null
                        || java.math.BigDecimal.valueOf(((Number) ov.get("qty")).doubleValue()).compareTo(java.math.BigDecimal.ZERO) <= 0)) {
                    continue;
                }
                seq++;
                String docNo = String.format("PROD-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""), seq);
                ProductionOutbound doc = new ProductionOutbound();
                doc.docNo = docNo;
                doc.productionOrderNo = order.orderNo;
                doc.productName = order.productName;
                doc.materialCode = item.materialCode;
                doc.materialName = item.materialName;
                doc.batchNo = ov != null && ov.get("batchNo") != null ? ov.get("batchNo").toString() : null;
                requireBatchNo(doc.batchNo, item.materialCode);
                doc.qty = ov != null && ov.get("qty") != null
                        ? java.math.BigDecimal.valueOf(((Number) ov.get("qty")).doubleValue()) : item.qty;
                // v5.7：跨库领料——每行可指定独立出库仓库（半成品/原料分仓存放），未指定回退全局仓库
                doc.warehouseId = ov != null && ov.get("warehouseId") != null
                        ? ov.get("warehouseId").toString() : warehouseId;
                // v4.5：读取前端按库位分行选择批次时传入的 locationId，精确扣减对应库位库存
                doc.locationId = ov != null && ov.get("locationId") != null ? ov.get("locationId").toString() : null;
                String[] zoneLoc = resolveZoneAndLocationName(doc.locationId);
                doc.zoneName = zoneLoc[0];
                doc.locationName = zoneLoc[1];
                doc.unit = item.unit;
                // 实际成本按批号直取：批号库存单价 × 用量（v5.7：按行级仓库取批次成本）
                fillBatchCost(doc.qty, doc.materialCode, doc.batchNo, doc.warehouseId,
                        ledgerByCode.get(doc.materialCode), pc -> {
                            doc.unitPrice = pc[0];
                            doc.cost = pc[1];
                        });
                doc.status = "CONFIRMED";
                if (supplement && supplementType != null && !supplementType.isBlank()) doc.supplementType = supplementType;   // v6.8 补领原因
            doc.createdBy = operator;
                doc.remark = remark;
                doc.createTime = LocalDateTime.now();
                doc.updateTime = LocalDateTime.now();
                prodRepo.save(doc);
                // 创建即确认，直接扣减库存（传入 locationId 精确扣减指定库位）
                inventoryService.outbound("PRODUCTION_OUT", doc.docNo, doc.materialCode,
                        doc.batchNo, doc.warehouseId, doc.locationId, doc.qty, operator);
                docs.add(doc);
            }
            // 补领：一行追加用量都没填时不产生空单据
            if (docs.isEmpty())
                throw new IllegalArgumentException("没有需要领料的明细行（补领请填写本次追加的用量）");
            log.info("生产出库单据批量创建并确认: 订单={} 共{}项{}", order.orderNo, docs.size(),
                    supplement ? "（补领）" : "");
            return docs;
        });
    }

    // ==================== 生产退料（负数行，成本 SUM 口径自动净额） ====================

    /**
     * 某生产订单的已领明细（按 物料+批次+仓库+库位 聚合），供退料弹窗选行：
     * 每行含 已领量 issuedQty / 已退量 returnedQty / 可退量 returnableQty / 批次单价 unitPrice
     */
    public List<java.util.Map<String, Object>> listIssuedLines(String productionOrderNo) {
        List<ProductionOutbound> history = prodRepo.findByProductionOrderNoAndStatus(productionOrderNo, "CONFIRMED");
        java.util.Map<String, java.util.Map<String, Object>> grouped = new java.util.LinkedHashMap<>();
        for (ProductionOutbound doc : history) {
            String key = issuedLineKey(doc.materialCode, doc.batchNo, doc.warehouseId, doc.locationId);
            java.util.Map<String, Object> g = grouped.computeIfAbsent(key, k -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("materialCode", doc.materialCode);
                m.put("materialName", doc.materialName);
                m.put("batchNo", doc.batchNo);
                m.put("warehouseId", doc.warehouseId);
                m.put("locationId", doc.locationId);
                m.put("zoneName", doc.zoneName);
                m.put("locationName", doc.locationName);
                m.put("unit", doc.unit);
                m.put("unitPrice", doc.unitPrice);
                m.put("issuedQty", BigDecimal.ZERO);
                m.put("returnedQty", BigDecimal.ZERO);
                return m;
            });
            if ("RETURN".equals(doc.docType)) {
                g.put("returnedQty", ((BigDecimal) g.get("returnedQty")).add(doc.qty.negate()));
            } else {
                g.put("issuedQty", ((BigDecimal) g.get("issuedQty")).add(doc.qty));
                if (doc.unitPrice != null) g.put("unitPrice", doc.unitPrice);   // 单价按原领料行直取
            }
        }
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> g : grouped.values()) {
            BigDecimal returnable = ((BigDecimal) g.get("issuedQty")).subtract((BigDecimal) g.get("returnedQty"));
            g.put("returnableQty", returnable);
            result.add(g);
        }
        return result;
    }

    /** 已领明细聚合键：物料+批次+仓库+库位（库位空归一为 ""，与退料校验同口径） */
    private String issuedLineKey(String materialCode, String batchNo, String warehouseId, String locationId) {
        return materialCode + "|" + (batchNo == null ? "" : batchNo) + "|"
                + (warehouseId == null ? "" : warehouseId) + "|" + (locationId == null ? "" : locationId);
    }

    /**
     * 生产退料：每行按 物料+批次+仓库+库位 校验 已领−已退 ≥ 本次退料数量；
     * 库存按原批次原库位加回（带原批号跳过全局查重，历史无库位行兜底仓库默认库位）；
     * production_outbound 写 docType=RETURN 的负数行（qty/cost 为负、status=CONFIRMED、单价按原领料行直取）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<ProductionOutbound> returnMaterial(String productionOrderNo,
                                                   List<java.util.Map<String, Object>> lines,
                                                   String operator, String remark) {
        // 单号生成+单据保存+库存加回整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (productionOrderNo == null || productionOrderNo.isBlank())
                throw new IllegalArgumentException("生产订单号不能为空");
            if (lines == null || lines.isEmpty())
                throw new IllegalArgumentException("退料明细不能为空");
            ProductionOrder order = orderRepo.findByOrderNo(productionOrderNo).orElse(null);

            // 可退量聚合：已领合计 − 已退合计（按 物料+批次+仓库+库位）
            java.util.Map<String, java.util.Map<String, Object>> avail = new java.util.HashMap<>();
            for (java.util.Map<String, Object> g : listIssuedLines(productionOrderNo)) {
                avail.put(issuedLineKey((String) g.get("materialCode"), (String) g.get("batchNo"),
                        (String) g.get("warehouseId"), (String) g.get("locationId")), g);
            }
            if (avail.isEmpty())
                throw new IllegalArgumentException("该生产订单没有已确认的领料记录，无法退料");

            Integer maxSeq = prodRepo.findMaxSeq("PROD-RET-" + LocalDate.now().toString().replace("-", "") + "-%");
            long seq = (maxSeq == null ? 0 : maxSeq);
            List<ProductionOutbound> docs = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> line : lines) {
                String materialCode = line.get("materialCode") != null ? line.get("materialCode").toString() : null;
                String batchNo = line.get("batchNo") != null ? line.get("batchNo").toString() : null;
                String warehouseId = line.get("warehouseId") != null ? line.get("warehouseId").toString() : null;
                String locationId = line.get("locationId") != null ? line.get("locationId").toString() : null;
                BigDecimal qty = line.get("qty") != null
                        ? BigDecimal.valueOf(((Number) line.get("qty")).doubleValue()) : null;
                if (materialCode == null || materialCode.isBlank())
                    throw new IllegalArgumentException("退料行缺少物料编码");
                if (batchNo == null || batchNo.isBlank())
                    throw new IllegalArgumentException("退料必须指定批号（物料操作精确到批次铁律）: " + materialCode);
                if (warehouseId == null || warehouseId.isBlank())
                    throw new IllegalArgumentException("退料必须指定退回仓库: " + materialCode);
                if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
                    throw new IllegalArgumentException("退料数量必须大于 0: " + materialCode);

                java.util.Map<String, Object> g = avail.get(issuedLineKey(materialCode, batchNo, warehouseId, locationId));
                if (g == null)
                    throw new IllegalArgumentException(String.format(
                            "该订单无此领料记录: %s 批次%s 仓%s", materialCode, batchNo, warehouseId));
                BigDecimal returnable = (BigDecimal) g.get("returnableQty");
                if (qty.compareTo(returnable) > 0) {
                    throw new IllegalArgumentException(String.format(
                            "退料数量超出可退量: %s 批次%s 已领%.3f 已退%.3f 可退%.3f 本次退%.3f",
                            materialCode, batchNo, g.get("issuedQty"), g.get("returnedQty"), returnable, qty));
                }

                seq++;
                ProductionOutbound doc = new ProductionOutbound();
                doc.docNo = String.format("PROD-RET-%s-%04d", LocalDate.now().toString().replace("-", ""), seq);
                doc.docType = "RETURN";
                doc.productionOrderNo = productionOrderNo;
                doc.productName = order != null ? order.productName : null;
                doc.materialCode = materialCode;
                doc.materialName = (String) g.get("materialName");
                doc.batchNo = batchNo;
                doc.warehouseId = warehouseId;
                // 历史领料行可能无库位（跨库位 FIFO 扣减），退回时兜底仓库默认库位（精确到库位铁律）
                doc.locationId = (locationId != null && !locationId.isBlank())
                        ? locationId : inventoryService.resolveDefaultLocation(warehouseId);
                String[] zoneLoc = resolveZoneAndLocationName(doc.locationId);
                doc.zoneName = zoneLoc[0];
                doc.locationName = zoneLoc[1];
                doc.qty = qty.negate();
                doc.unit = (String) g.get("unit");
                doc.unitPrice = (BigDecimal) g.get("unitPrice");
                doc.cost = doc.unitPrice != null
                        ? doc.qty.multiply(doc.unitPrice).setScale(2, RoundingMode.HALF_UP) : null;
                doc.status = "CONFIRMED";
                doc.createdBy = operator;
                doc.remark = remark;
                doc.createTime = LocalDateTime.now();
                doc.updateTime = LocalDateTime.now();
                prodRepo.save(doc);
                // 库存加回：原批次原库位，沿用批次现有台账的生产/过期日期（不重置质保期）
                java.time.LocalDate produceDate = null;
                java.time.LocalDate expiryDate = null;
                List<InventoryLedger> batchLedgers = ledgerRepo
                        .findByMaterialCodeAndBatchNoAndWarehouseIdOrderByInboundDateAscIdAsc(materialCode, batchNo, warehouseId);
                if (!batchLedgers.isEmpty()) {
                    produceDate = batchLedgers.get(0).produceDate;
                    expiryDate = batchLedgers.get(0).expiryDate;
                }
                inventoryService.purchaseInboundWithDate("PROD_RETURN", doc.docNo, doc.materialCode, doc.materialName,
                        doc.batchNo, doc.warehouseId, doc.locationId, qty, doc.unitPrice,
                        produceDate, expiryDate, operator, null, true);
                docs.add(doc);
            }
            log.info("生产退料: 订单={} 共{}行", productionOrderNo, docs.size());
            return docs;
        });
    }

    /**
     * v5.64 领料单作废：整行冲回（复用退料闭环）+ 原行标记 CANCELLED。
     * 生成一张 PROD-RET 负数行（qty/cost 取负、库存按原批次原库位加回），原 ISSUE 行 status=CANCELLED——
     * 下游聚合（成本核算/投出比/可退明细）全部按 CONFIRMED 过滤，作废行自动剔除，无需改任何口径。
     * 拦截：非 ISSUE 行、非 CONFIRMED 行、订单已入库（DONE）、该行已被部分退料。
     */
    // v6.1.5：去残留 @Transactional——方法内调 returnMaterial(executeTx)，外层事务先开等价旧时序（PROD-RET 取号撞号窗口）
    public java.util.Map<String, Object> voidProductionOutbound(Long id, String operator) {
        // v8.4（A4）：整体包 executeTx——原"先回冲退料、再两次 save 作废"各自独立提交，中途失败留半截
        return writeQueue.executeTx(() -> {
        return voidProductionOutboundTx(id, operator);
        });
    }

    private java.util.Map<String, Object> voidProductionOutboundTx(Long id, String operator) {
        ProductionOutbound doc = prodRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("领料单不存在"));
        if ("CANCELLED".equals(doc.status)) throw new IllegalArgumentException("该领料行已作废");
        if (!"CONFIRMED".equals(doc.status)) throw new IllegalArgumentException("只有已确认的领料行可以作废");
        if (!"ISSUE".equals(doc.docType)) throw new IllegalArgumentException("退料冲减行不能作废");
        if (doc.productionOrderNo == null || doc.productionOrderNo.isBlank())
            throw new IllegalArgumentException("该出库行未关联生产订单，请走其他出库的对应流程");
        // 订单已入库拦截：成品已产出再作废领料会破坏投出比分母
        for (ProductionInbound in : prodInRepo.findByProductionOrderNo(doc.productionOrderNo)) {
            if ("DONE".equals(in.status)) {
                throw new IllegalArgumentException("该生产订单已完成入库，不能作废领料（如需调整成本请走生产退料）");
            }
        }
        // 部分退料拦截：剩余可退量不足以整行冲回
        String key = issuedLineKey(doc.materialCode, doc.batchNo, doc.warehouseId, doc.locationId);
        BigDecimal returnable = listIssuedLines(doc.productionOrderNo).stream()
                .filter(g -> key.equals(issuedLineKey((String) g.get("materialCode"), (String) g.get("batchNo"),
                        (String) g.get("warehouseId"), (String) g.get("locationId"))))
                .map(g -> (BigDecimal) g.get("returnableQty")).findFirst().orElse(BigDecimal.ZERO);
        if (returnable.compareTo(doc.qty) < 0) {
            throw new IllegalArgumentException(String.format(
                    "该领料行已被退料过（剩余可退 %.3f 不足整行 %.3f），请走生产退料按剩余量处理",
                    returnable, doc.qty));
        }

        // 冲减行：复用退料闭环（校验/负数行/库存加回全部走同一套逻辑）
        java.util.Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("materialCode", doc.materialCode);
        line.put("batchNo", doc.batchNo);
        line.put("warehouseId", doc.warehouseId);
        line.put("locationId", doc.locationId);
        line.put("qty", doc.qty);
        List<ProductionOutbound> rets = returnMaterial(doc.productionOrderNo, List.of(line), operator,
                "作废冲减 " + doc.docNo);
        // 作废口径：冲减行同样置 CANCELLED——成本 SUM(CONFIRMED) 与原行双双清零（=0 而非 -cost），
        // 可退明细两行双双消失；库存加回已落 inventory_movement 流水，与单据状态无关
        for (ProductionOutbound ret : rets) {
            ret.status = "CANCELLED";
            ret.updateTime = LocalDateTime.now();
            prodRepo.save(ret);
        }

        doc.status = "CANCELLED";
        doc.updateTime = LocalDateTime.now();
        prodRepo.save(doc);
        log.info("领料单作废: {} 订单={} 操作={}，冲减单={}", doc.docNo, doc.productionOrderNo, operator,
                rets.isEmpty() ? "-" : rets.get(0).docNo);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("cancelledDocNo", doc.docNo);
        result.put("returnDocNo", rets.isEmpty() ? null : rets.get(0).docNo);
        return result;
    }

    /** 某订单的出库记录明细（生产出库作为订单的记录明细） */
    public List<ProductionOutbound> listOutboundsByOrderId(Long orderId) {
        return orderRepo.findById(orderId)
                .map(o -> prodRepo.findByProductionOrderNoAndStatus(o.orderNo, "CONFIRMED"))
                .orElse(List.of());
    }

    /** 批次可用聚合（自动先进先出排序用） */
    private static class BatchAvail {
        String batchNo;
        BigDecimal qty = BigDecimal.ZERO;
        java.time.LocalDate earliestInbound;   // 最早入库日期（FIFO 依据）
        BigDecimal price;                       // 最早入库行单价
    }

    /**
     * 生产订单自动出库（合并流程：打印即出库）：
     * 按先进先出自动分配批次——全部仓库的批次按入库日期最早优先，
     * 某批次不足自动补下一个批次（每个批次生成一行出库记录）；跳过过期批次；全部不足报错整体回滚。
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<ProductionOutbound> autoFifoOutbound(Long productionOrderId, String operator) {
        return writeQueue.executeTx(() -> {
            ProductionOrder order = orderRepo.findById(productionOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("生产订单不存在"));
            if (!"CONFIRMED".equals(order.status) && !"SCHEDULED".equals(order.status)) {
                throw new IllegalArgumentException("只有已确认的生产订单可自动出库");
            }
            // 一单一出：同一订单只允许出库一次
            List<ProductionOutbound> existOuts = prodRepo.findByProductionOrderNoAndStatus(order.orderNo, "CONFIRMED");
            if (!existOuts.isEmpty()) throw new IllegalArgumentException("该生产订单已出库，不允许重复出库");
            // 已完成入库的订单禁止再出库
            List<ProductionInbound> existIns = prodInRepo.findByProductionOrderNo(order.orderNo);
            for (ProductionInbound in : existIns) {
                if ("DONE".equals(in.status)) throw new IllegalArgumentException("该生产订单已完成入库，不允许再出库");
            }
            List<ProductionOrderItem> items = orderItemRepo.findByOrderId(productionOrderId);
            if (items.isEmpty()) throw new IllegalArgumentException("生产订单无配方明细");

            List<ProductionOutbound> docs = new java.util.ArrayList<>();
            Integer prodOutMaxSeq = prodRepo.findMaxSeq("PROD-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
            long seq = (prodOutMaxSeq == null ? 0 : prodOutMaxSeq);
            // v6.1.3：FIFO 单号 PROD-OUT-{年}-F{序号} 与天流水主单格式不同，须按年前缀独立取号——
            // 此前共用按天计数器（年-F 单号 LIKE 天前缀永不命中），每天从 F0001 重起，同年跨日撞号
            Integer fifoMax = prodRepo.findMaxSeq("PROD-OUT-" + LocalDateTime.now().getYear() + "-F%");
            long fifoSeq = (fifoMax == null ? 0 : fifoMax);
            // 生产订单只从自有仓出库（processor_id 为空的仓库）；v5.30 排除不合格品库，v5.35 排除油尾库（消化走人工领料）
            java.util.Set<String> ownWarehouseIds = warehouseRepo.findAll().stream()
                    .filter(w -> w.processorId == null || w.processorId.isBlank())
                    .filter(w -> !"OWN_UNQUALIFIED".equals(w.warehouseType))
                    .filter(w -> !"OWN_TAILING".equals(w.warehouseType))
                    .map(w -> String.valueOf(w.id))
                    .collect(java.util.stream.Collectors.toSet());
            // v5.55：明细物料台账一次预取（原逐物料 findByMaterialCode 的 N+1）
            java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                    items.stream().map(i -> i.materialCode).toList());
            for (ProductionOrderItem item : items) {
                BigDecimal need = item.qty == null ? BigDecimal.ZERO : item.qty;
                if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
                // 自有仓内该物料的台账行 → 按批号聚合可用量 + 最早入库日期（跳过过期批次）
                List<InventoryLedger> rows = ledgerByCode.getOrDefault(item.materialCode, List.of());
                java.util.Map<String, BatchAvail> batchMap = new java.util.LinkedHashMap<>();
                for (InventoryLedger l : rows) {
                    if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (l.batchNo == null || l.batchNo.isBlank()) continue;
                    if (l.warehouseId == null || !ownWarehouseIds.contains(l.warehouseId)) continue;  // 仅自有仓
                    if (l.expiryDate != null && l.expiryDate.isBefore(java.time.LocalDate.now())) continue;
                    BatchAvail ba = batchMap.computeIfAbsent(l.batchNo, k -> { BatchAvail b = new BatchAvail(); b.batchNo = k; return b; });
                    ba.qty = ba.qty.add(l.qty);
                    if (ba.earliestInbound == null || (l.inboundDate != null && l.inboundDate.isBefore(ba.earliestInbound))) {
                        ba.earliestInbound = l.inboundDate;
                        ba.price = l.unitPrice;   // 取最早入库行的单价
                    }
                }
                List<BatchAvail> sorted = new java.util.ArrayList<>(batchMap.values());
                sorted.sort(java.util.Comparator.comparing(
                        (BatchAvail b) -> b.earliestInbound == null ? java.time.LocalDate.MAX : b.earliestInbound));
                BigDecimal total = sorted.stream().map(b -> b.qty).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (total.compareTo(need) < 0) {
                    throw new IllegalArgumentException(String.format(
                            "库存不足: %s(%s) 可用%.3f 需要%.3f，请先补货或调整批量",
                            item.materialCode, item.materialName, total, need));
                }
                BigDecimal remaining = need;
                for (BatchAvail ba : sorted) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal take = ba.qty.compareTo(remaining) <= 0 ? ba.qty : remaining;
                    // 全仓 FIFO 扣减该批次，并拿到每个库位的扣减明细（按库位拆行记录，精确到出库自哪个库位）
                    String fifoDocNo = String.format("PROD-OUT-%d-F%04d", LocalDateTime.now().getYear(), fifoSeq + 1);
                    List<InventoryService.LocationDeduct> locDetails =
                            inventoryService.outboundFifoWithDetail("PRODUCTION_OUT", fifoDocNo, item.materialCode, ba.batchNo, take, operator, false, ownWarehouseIds);
                    // 每个库位生成一行出库明细记录
                    for (InventoryService.LocationDeduct ld : locDetails) {
                        seq++;
                        String docNo = String.format("PROD-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""), seq);
                        ProductionOutbound doc = new ProductionOutbound();
                        doc.docNo = docNo;
                        doc.productionOrderNo = order.orderNo;
                        doc.productName = order.productName;
                        doc.materialCode = item.materialCode;
                        doc.materialName = item.materialName;
                        doc.batchNo = ba.batchNo;
                        doc.qty = ld.qty;
                        doc.unit = item.unit;
                        doc.warehouseId = ld.warehouseId;
                        doc.locationId = ld.locationId;
                        doc.zoneName = ld.zoneName != null ? ld.zoneName : (ld.locationId != null ? resolveZoneAndLocationName(ld.locationId)[0] : null);
                        doc.locationName = ld.locationName != null ? ld.locationName : (ld.locationId != null ? resolveZoneAndLocationName(ld.locationId)[1] : null);
                        doc.unitPrice = costingService.autoPrice(item.materialCode, ba.batchNo, ld.price);   // v5.63 计价分流
                        doc.cost = doc.unitPrice != null ? ld.qty.multiply(doc.unitPrice).setScale(2, RoundingMode.HALF_UP) : null;
                        doc.status = "CONFIRMED";
                        doc.createdBy = operator;
                        doc.remark = "自动出库(先进先出)" + (warehouseName(ld.warehouseId) != null ? "·" + warehouseName(ld.warehouseId) : "");
                        doc.createTime = LocalDateTime.now();
                        doc.updateTime = LocalDateTime.now();
                        prodRepo.save(doc);
                        docs.add(doc);
                    }
                    remaining = remaining.subtract(take);
                }
            }
            log.info("生产订单自动出库(先进先出): 订单={} 共{}行", order.orderNo, docs.size());
            return docs;
        });
    }

    /**
     * 生产订单出库前库存预检（只算不扣）：返回每项物料的需求量 vs 自有仓可用库存。
     */
    public java.util.List<java.util.Map<String, Object>> stockCheckForProduction(Long orderId) {
        ProductionOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("生产订单不存在"));
        java.util.Set<String> ownWarehouseIds = warehouseRepo.findAll().stream()
                .filter(w -> w.processorId == null || w.processorId.isBlank())
                .map(w -> String.valueOf(w.id))
                .collect(java.util.stream.Collectors.toSet());
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        var prodItems = orderItemRepo.findByOrderId(orderId);
        java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                prodItems.stream().map(i -> i.materialCode).toList());
        for (ProductionOrderItem item : prodItems) {
            BigDecimal need = item.qty == null ? BigDecimal.ZERO : item.qty;
            BigDecimal available = BigDecimal.ZERO;
            for (InventoryLedger l : ledgerByCode.getOrDefault(item.materialCode, List.of())) {
                if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (l.warehouseId == null || !ownWarehouseIds.contains(l.warehouseId)) continue;
                if (l.expiryDate != null && l.expiryDate.isBefore(java.time.LocalDate.now())) continue;
                available = available.add(l.qty);
            }
            BigDecimal shortage = need.subtract(available).max(BigDecimal.ZERO);
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("materialCode", item.materialCode);
            m.put("materialName", item.materialName);
            m.put("need", need);
            m.put("available", available);
            m.put("shortage", shortage);
            m.put("enough", shortage.compareTo(BigDecimal.ZERO) <= 0);
            result.add(m);
        }
        return result;
    }

    /**
     * 委外订单出库前库存预检（只算不扣）：返回每项物料的需求量 vs 代工厂仓可用库存。
     */
    public java.util.List<java.util.Map<String, Object>> stockCheckForOutsource(Long orderId) {
        OutsourceOrder order = outsourceOrderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("委外订单不存在"));
        java.util.Set<String> factoryWarehouseIds = warehouseRepo.findAll().stream()
                .filter(w -> order.processor != null && order.processor.equals(w.processorName))
                .map(w -> String.valueOf(w.id))
                .collect(java.util.stream.Collectors.toSet());
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        var osItems = outsourceOrderItemRepo.findByOrderId(orderId);
        java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                osItems.stream().map(i -> i.materialCode).toList());
        for (OutsourceOrderItem item : osItems) {
            BigDecimal need = item.qty == null ? BigDecimal.ZERO : item.qty;
            BigDecimal available = BigDecimal.ZERO;
            for (InventoryLedger l : ledgerByCode.getOrDefault(item.materialCode, List.of())) {
                if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (l.warehouseId == null || !factoryWarehouseIds.contains(l.warehouseId)) continue;
                if (l.expiryDate != null && l.expiryDate.isBefore(java.time.LocalDate.now())) continue;
                available = available.add(l.qty);
            }
            BigDecimal shortage = need.subtract(available).max(BigDecimal.ZERO);
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("materialCode", item.materialCode);
            m.put("materialName", item.materialName);
            m.put("need", need);
            m.put("available", available);
            m.put("shortage", shortage);
            m.put("enough", shortage.compareTo(BigDecimal.ZERO) <= 0);
            result.add(m);
        }
        return result;
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public ProductionOutbound confirmProduction(Long id, String operator) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            ProductionOutbound doc = prodRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("单据不存在"));
            if (!"DRAFT".equals(doc.status))
                throw new IllegalArgumentException("只有草稿状态的单据可确认");

            // v5.27：只有已排产的订单才允许出库
            requireScheduled(doc.productionOrderNo);
            requireBatchNo(doc.batchNo, doc.materialCode);
            // 确认时按批号回填成本（创建时未记录的情况）
            if (doc.cost == null) {
                fillBatchCost(doc.qty, doc.materialCode, doc.batchNo, doc.warehouseId, pc -> {
                    doc.unitPrice = pc[0];
                    doc.cost = pc[1];
                });
            }
            // v4.5：传入 locationId 精确扣减指定库位库存
            inventoryService.outbound("PRODUCTION_OUT", doc.docNo, doc.materialCode,
                    doc.batchNo, doc.warehouseId, doc.locationId, doc.qty, operator);
    
            doc.status = "CONFIRMED";
            doc.updateTime = LocalDateTime.now();
            prodRepo.save(doc);
            log.info("生产出库确认: {}", doc.docNo);
            return doc;
        });
    }

    /**
     * 批量确认同一生产订单下的所有出库单据
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<ProductionOutbound> confirmByOrder(String productionOrderNo, String operator) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            List<ProductionOutbound> docs = prodRepo.findByProductionOrderNoAndStatus(productionOrderNo, "DRAFT");
            if (docs.isEmpty())
                throw new IllegalArgumentException("无待确认的出库单据");
            for (ProductionOutbound doc : docs) {
                // v5.27：只有已排产的订单才允许出库
                requireScheduled(doc.productionOrderNo);
                requireBatchNo(doc.batchNo, doc.materialCode);
                if (doc.cost == null) {
                    fillBatchCost(doc.qty, doc.materialCode, doc.batchNo, doc.warehouseId, pc -> {
                        doc.unitPrice = pc[0];
                        doc.cost = pc[1];
                    });
                }
                inventoryService.outbound("PRODUCTION_OUT", doc.docNo, doc.materialCode,
                        doc.batchNo, doc.warehouseId, doc.locationId, doc.qty, operator);
                doc.status = "CONFIRMED";
                doc.updateTime = LocalDateTime.now();
                prodRepo.save(doc);
            }
            log.info("生产出库批量确认: 订单={} 共{}项", productionOrderNo, docs.size());
            return docs;
        });
    }

    // ==================== 可参照订单列表（过滤已完工/已出库/已入库的订单） ====================

    /**
     * 可参照出库的生产订单列表
     * 排除：已完成入库（DONE 入库单）的订单；默认另排除已领料出库（CONFIRMED 出库单）的订单
     * mode=supplement（补领）时不排除已领料订单，允许追加领料
     */
    public List<ProductionOrder> listReferenceableProductionOrdersForOutbound() {
        return listReferenceableProductionOrdersForOutbound(null);
    }

    public List<ProductionOrder> listReferenceableProductionOrdersForOutbound(String mode) {
        boolean supplement = "supplement".equals(mode);
        // v6.1：参照列表统一取 CONFIRMED+SCHEDULED（排产即 SCHEDULED，补领对象也在其中；
        // v6.1.1 修复：原 supplement 分支二次 addAll SCHEDULED 导致列表重复项）
        List<ProductionOrder> orders = new java.util.ArrayList<>(orderRepo.findByStatusOrderByCreateTimeDesc("CONFIRMED"));
        orders.addAll(orderRepo.findByStatusOrderByCreateTimeDesc("SCHEDULED"));
        // 批量取集合后过滤（此前逐单 2 次查询的 N+1）
        java.util.Set<String> fedOrders = new java.util.HashSet<>(prodRepo.findConfirmedOrderNos());
        java.util.Set<String> doneInbound = new java.util.HashSet<>();
        for (Object[] row : prodInRepo.sumDoneQtyGroupByOrderNo()) doneInbound.add((String) row[0]);
        return orders.stream()
                .filter(o -> (supplement || !fedOrders.contains(o.orderNo)) && !doneInbound.contains(o.orderNo))
                .toList();
    }

    /**
     * 可参照入库的生产订单列表
     * 要求：必须已领料出库；排除：已入库（DONE/CONFIRMED 入库单）的订单
     */
    public List<ProductionOrder> listReferenceableProductionOrdersForInbound() {
        // v6.1.1：与入库创建同口径纳入 SCHEDULED（排产领料后即可入库；此前创建放行但参照列表看不到）
        List<ProductionOrder> orders = new java.util.ArrayList<>(orderRepo.findByStatusOrderByCreateTimeDesc("CONFIRMED"));
        orders.addAll(orderRepo.findByStatusOrderByCreateTimeDesc("SCHEDULED"));
        java.util.Set<String> fedOrders = new java.util.HashSet<>(prodRepo.findConfirmedOrderNos());
        java.util.Set<String> inbounded = new java.util.HashSet<>(prodInRepo.findDoneOrConfirmedOrderNos());
        return orders.stream()
                .filter(o -> fedOrders.contains(o.orderNo) && !inbounded.contains(o.orderNo))
                .toList();
    }

    // ==================== 生产入库（参照生产订单） ====================

    public List<ProductionInbound> listProductionInbound() {
        return prodInRepo.findByOrderByCreateTimeDesc();
    }

    /**
     * 参照生产订单创建入库单据
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public ProductionInbound createProductionInbound(Long productionOrderId, String warehouseId,
                                                     String locationId, BigDecimal qty,
                                                     String batchNo, String operator, String remark) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            ProductionOrder order = orderRepo.findById(productionOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("生产订单不存在"));
            // v6.1 修复（高#8）：排产领料后订单停在 SCHEDULED，原来只认 CONFIRMED 流程卡死；放行 CONFIRMED|SCHEDULED（与委外同口径）
            if (!"CONFIRMED".equals(order.status) && !"SCHEDULED".equals(order.status))
                throw new IllegalArgumentException("只有已确认或已排产的订单可入库");
    
            // 校验：必须先完成生产出库（领料），才允许参照生成生产入库单
            List<ProductionOutbound> outs = prodRepo.findByProductionOrderNoAndStatus(order.orderNo, "CONFIRMED");
            if (outs.isEmpty()) {
                throw new IllegalArgumentException("该生产订单尚未进行生产出库，不允许参照生成生产入库单");
            }
    
            // 校验：禁止重复参照入库（同一订单已存在已入库/待质检的入库单时拦截）
            List<ProductionInbound> existInbounds = prodInRepo.findByProductionOrderNo(order.orderNo);
            for (ProductionInbound exist : existInbounds) {
                if ("DONE".equals(exist.status)) {
                    throw new IllegalArgumentException("该生产订单已完成入库，不允许重复参照");
                }
                if ("CONFIRMED".equals(exist.status)) {
                    throw new IllegalArgumentException("该生产订单已有待质检的入库单，请先完成质检");
                }
            }
    
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer prodInMaxSeq = prodInRepo.findMaxSeq("PROD-IN-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("PROD-IN-%s-%04d", LocalDate.now().toString().replace("-", ""), (prodInMaxSeq == null ? 0 : prodInMaxSeq) + 1);
            // v5.7：批次号自动生成且必填（未传入时由系统生成，保证单据/质检/台账批号一致）
            String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : inventoryService.nextBatchNo();
            ProductionInbound doc = new ProductionInbound();
            doc.docNo = docNo;
            doc.productionOrderNo = order.orderNo;
            doc.productName = order.productName;
            doc.productCode = order.productCode;
            doc.batchNo = finalBatchNo;
            doc.warehouseId = warehouseId;
            doc.locationId = locationId;
            // 冗余填充分库/库位名称，便于列表展示
            String[] zl = resolveZoneAndLocationName(locationId);
            doc.zoneName = zl[0];
            doc.locationName = zl[1];
            doc.qty = qty != null ? qty : order.batchQty;
            doc.unit = order.unit;
            // 得率：实际产出 vs 理论产出（配方批量）
            doc.theoreticalQty = order.batchQty;
            doc.yieldRate = calcYieldRate(doc.qty, order.batchQty);
            doc.status = "DRAFT";
            doc.createdBy = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            prodInRepo.save(doc);
            log.info("生产入库单据创建(草稿): {} 产品={}", docNo, order.productName);
            return doc;
        });
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public ProductionInbound confirmProductionInbound(Long id, String operator) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            ProductionInbound doc = prodInRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("单据不存在"));
            if (!"DRAFT".equals(doc.status))
                throw new IllegalArgumentException("只有草稿状态的单据可确认");

            // 双保险：确认时再校验订单必须已完成出库（未出库绝对不允许入库）
            List<ProductionOutbound> outs = prodRepo.findByProductionOrderNoAndStatus(doc.productionOrderNo, "CONFIRMED");
            if (outs.isEmpty()) {
                throw new IllegalArgumentException("该生产订单尚未进行生产出库，不允许确认入库");
            }
    
            // 创建入库前质检单（待检状态，QC合格后才触发入库）
            qcService.createForInbound("PRODUCTION_INBOUND", doc.docNo,
                    (doc.productCode != null && !doc.productCode.isBlank()) ? doc.productCode : doc.productName,
                    doc.productName, doc.batchNo, doc.qty, doc.unit,
                    doc.warehouseId, doc.locationId,
                    java.time.LocalDate.now(),   // v5.71.8 生产日期=确认入库日（自产产品），质检报告展示+保质期计算
                    operator);
    
            doc.status = "CONFIRMED";
            doc.updateTime = LocalDateTime.now();
            prodInRepo.save(doc);
            log.info("生产入库确认(待检): {} 产品={} +{}", doc.docNo, doc.productName, doc.qty);
            return doc;
        });
    }

    // ==================== 销售出库 ====================

    /**
     * 参照销售订单生成发货出库单（草稿）：批号必选，仅创建 DRAFT 单据，不扣库存、不生成应收
     * 由仓管在销售出库列表审核确认后，才扣减库存并生成应收账款
     * overrides: materialCode -> {batchNo, qty(可选，默认未发量)}
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<SalesOutbound> shipFromOrder(Long salesOrderId, String warehouseId, String operator,
                                             String remark, java.util.Map<String, java.util.Map<String, Object>> overrides) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            SalesOrder order = salesOrderRepo.findById(salesOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
            if (!"CONFIRMED".equals(order.status))
                throw new IllegalArgumentException("只有已确认的销售订单可发货");
            List<SalesOrderItem> items = salesItemRepo.findByOrderId(salesOrderId);
            if (items.isEmpty()) throw new IllegalArgumentException("订单无明细，无法发货");
    
            List<SalesOutbound> docs = new java.util.ArrayList<>();
            // v5.55：明细物料台账一次预取（批次定位库位 + 成本回填共用，原逐项 2 次查询的 N+1）
            java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                    items.stream().map(i -> i.materialCode).toList());
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer salesMaxSeq = salesRepo.findMaxSeq("SALES-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
            long salesSeq = (salesMaxSeq == null ? 0 : salesMaxSeq);
            for (SalesOrderItem item : items) {
                salesSeq++;
                java.util.Map<String, Object> ov = overrides != null ? overrides.get(item.materialCode) : null;
                String batchNo = ov != null && ov.get("batchNo") != null ? ov.get("batchNo").toString() : null;
                requireBatchNo(batchNo, item.materialCode);
                BigDecimal shipped = item.shippedQty == null ? BigDecimal.ZERO : item.shippedQty;
                BigDecimal remain = item.qty.subtract(shipped);
                BigDecimal shipQty = ov != null && ov.get("qty") != null
                        ? BigDecimal.valueOf(((Number) ov.get("qty")).doubleValue()) : remain;
                if (shipQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (shipQty.compareTo(remain) > 0)
                    throw new IllegalArgumentException("物料 " + item.materialCode + " 发货数量超过未发量 " + remain);

                String docNo = String.format("SALES-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""), salesSeq);
                // 定位批号所在库位（取该仓内库存量最大的台账行），保证后续审核扣减命中
                String locationId = ledgerByCode.getOrDefault(item.materialCode, List.of()).stream()
                        .filter(l -> batchNo.equals(l.batchNo))
                        .filter(l -> warehouseId.equals(l.warehouseId))
                        .max(java.util.Comparator.comparing(l -> l.qty == null ? BigDecimal.ZERO : l.qty))
                        .map(l -> l.locationId).orElse(null);
                SalesOutbound doc = new SalesOutbound();
                doc.docNo = docNo;
                doc.salesOrderNo = order.orderNo;
                doc.customerName = order.customerName;
                doc.materialCode = item.materialCode;
                doc.materialName = item.materialName;
                doc.batchNo = batchNo;
                doc.warehouseId = warehouseId;
                doc.locationId = locationId;
                doc.qty = shipQty;
                doc.unit = item.unit;
                doc.status = "DRAFT";
                doc.createdBy = operator;
                doc.remark = remark;
                doc.createTime = LocalDateTime.now();
                // 实际成本按批号直取（预填，审核时可重算）
                fillBatchCost(shipQty, item.materialCode, batchNo, warehouseId,
                        ledgerByCode.get(item.materialCode),
                        p -> { doc.unitPrice = p[0]; doc.cost = p[1]; });
                salesRepo.save(doc);
                docs.add(doc);
            }
            if (docs.isEmpty()) throw new IllegalArgumentException("无可发货明细（已全部发完）");
    
            // 仅记录发货单已生成，不扣库存、不生成应收、不修改订单状态
            // 仓管审核确认出库单时，由 confirmSales() 统一处理库存扣减、应收生成、订单状态流转
            order.updateTime = LocalDateTime.now();
            salesOrderRepo.save(order);
            log.info("销售发货单已生成(待仓管审核): 订单={} 单据{}张", order.orderNo, docs.size());
            return docs;
        });
    }

    public List<SalesOutbound> listSales() {
        return salesRepo.findByOrderByCreateTimeDesc();
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public SalesOutbound createSales(String salesOrderNo,
                                     String materialCode, String materialName,
                                     String batchNo, String warehouseId,
                                     BigDecimal qty, String operator, String remark) {
        // v5.27：销售出库必须参照销售订单，不允许无来源出库
        if (salesOrderNo == null || salesOrderNo.isBlank()) {
            throw new IllegalArgumentException("销售出库必须参照销售订单");
        }
        var so = salesOrderRepo.findByOrderNo(salesOrderNo)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在：" + salesOrderNo));
        // v5.27：物料必须来自销售订单明细（销售出库只能发订单内的物料），且不超过剩余可发量
        var soItem = salesItemRepo.findByOrderId(so.id).stream()
                .filter(it -> materialCode.equals(it.materialCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "物料「" + materialCode + "」不在销售订单 " + salesOrderNo + " 明细中，销售出库只能发订单内的物料"));
        final var soItemRef = soItem;
        // v5.27：客户由销售订单带出，不允许修改
        final String finalCustomerName = so.customerName;
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        // v8.3（A6）：可发量校验挪进锁内——原在锁外查（TOCTOU），两张草稿并发建均可过校验，确认时超发
        return writeQueue.executeTx(() -> {
        {
            var item = salesItemRepo.findByOrderId(so.id).stream()
                    .filter(it -> materialCode.equals(it.materialCode)).findFirst().orElse(null);
            if (item == null) throw new IllegalArgumentException("物料「" + materialCode + "」不在销售订单明细中");
            BigDecimal remaining = item.qty.subtract(item.shippedQty == null ? BigDecimal.ZERO : item.shippedQty);
            if (qty.compareTo(remaining) > 0) {
                throw new IllegalArgumentException("出库数量不能超过销售订单剩余可发量 " + remaining
                        + (item.unit == null ? "" : item.unit));
            }
        }
        // v5.24：按最大序号+1（count 会删除错位且并发撞号）
        Integer salesMaxSeq = salesRepo.findMaxSeq("SALES-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
        String docNo = String.format("SALES-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""), (salesMaxSeq == null ? 0 : salesMaxSeq) + 1);
        SalesOutbound doc = new SalesOutbound();
            doc.docNo = docNo;
            doc.salesOrderNo = salesOrderNo;
            doc.customerName = finalCustomerName;
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.qty = qty;
            doc.status = "DRAFT";
            doc.createdBy = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            salesRepo.save(doc);
            log.info("销售出库单据创建(草稿): {}", docNo);
            return doc;
        });
    }

    /**
     * 销售出库单审核确认：扣减库存、生成应收账款、回写销售订单明细已发量、订单全部发完置为已发货
     * @param id          出库单ID
     * @param operator    操作人
     * @param qtyOverride 仓管修改后的实际出库数量（可空=不修改，按单据原数量出库）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public SalesOutbound confirmSales(Long id, String operator, BigDecimal qtyOverride) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            SalesOutbound doc = salesRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("单据不存在"));
            if (!"DRAFT".equals(doc.status))
                throw new IllegalArgumentException("只有草稿状态的单据可确认");
    
            // 仓管可调整实际出库数量（不超过单据原数量）
            BigDecimal outQty;
            if (qtyOverride != null && qtyOverride.compareTo(BigDecimal.ZERO) > 0) {
                if (qtyOverride.compareTo(doc.qty) > 0)
                    throw new IllegalArgumentException("实际出库数量不能超过单据数量 " + doc.qty);
                doc.qty = qtyOverride;
                outQty = qtyOverride;
            } else {
                outQty = doc.qty;
            }
            final BigDecimal finalOutQty = outQty;
    
            // 入库时已质检合格，库存即合格，销售出库无需再次质检校验
            inventoryService.outbound("SALES_OUT", doc.docNo, doc.materialCode,
                    doc.batchNo, doc.warehouseId, doc.locationId, outQty, operator);
    
            // 实际成本按批号直取（审核时重算，确保与扣减数量一致）
            fillBatchCost(outQty, doc.materialCode, doc.batchNo, doc.warehouseId,
                    p -> { doc.unitPrice = p[0]; doc.cost = p[1]; });
    
            doc.status = "CONFIRMED";
            doc.updateTime = LocalDateTime.now();
            salesRepo.save(doc);
    
            // 回写销售订单明细已发量 & 生成应收账款 & 订单全部发完置为已发货
            if (doc.salesOrderNo != null && !doc.salesOrderNo.isBlank()) {
                // v4.8：按单号直查（原 findAll 全表扫描后过滤，订单量大时性能劣化）
                salesOrderRepo.findByOrderNo(doc.salesOrderNo)
                        .ifPresent(order -> {
                            // 累加该出库单对应明细的已发量
                            salesItemRepo.findByOrderId(order.id).stream()
                                    .filter(it -> doc.materialCode.equals(it.materialCode))
                                    .findFirst()
                                    .ifPresent(item -> {
                                        BigDecimal shipped = item.shippedQty == null ? BigDecimal.ZERO : item.shippedQty;
                                        // v8.3（A6）：确认时复核剩余可发量——草稿建立到确认期间可能又有他单发货
                                        if (finalOutQty.compareTo(item.qty.subtract(shipped)) > 0) {
                                            throw new IllegalArgumentException(String.format(
                                                    "确认失败：物料 %s 本单 %s 超过订单剩余可发量 %s（并发发货，请调整数量）",
                                                    item.materialCode, finalOutQty, item.qty.subtract(shipped)));
                                        }
                                        item.shippedQty = shipped.add(finalOutQty);
                                        salesItemRepo.save(item);

                                        // v5.27：销售出库必须立应收——明细未定价（单价为空或≤0）时禁止审核确认
                                        if (item.unitPrice == null || item.unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                                            throw new IllegalArgumentException("销售订单明细「" + item.materialCode + "」未填写销售单价，无法立应收，请先在销售订单中补充单价");
                                        }
                                        // 应收金额 = 实际出库数量 × 销售单价
                                        BigDecimal arAmount = finalOutQty.multiply(item.unitPrice)
                                                .setScale(2, RoundingMode.HALF_UP);
                                        AccountsReceivable ar = new AccountsReceivable();
                                        ar.customerId = order.customerId;
                                        ar.salesOrderNo = order.orderNo;
                                        ar.contractNo = order.contractNo;
                                        ar.amount = arAmount;
                                        Customer customer = customerRepo.findById(order.customerId).orElse(null);
                                        ar.dueDate = calcArDueDate(customer, order.expectedShipDate);
                                        ar.status = "UNPAID";
                                        ar.remark = "销售出库审核确认 " + doc.docNo;
                                        financeService.createAR(ar);
                                    });
    
                            // 全部明细发完则订单置为已发货
                            boolean allShipped = salesItemRepo.findByOrderId(order.id).stream()
                                    .allMatch(i -> i.shippedQty != null && i.shippedQty.compareTo(i.qty) >= 0);
                            if (allShipped) order.status = "SHIPPED";
                            order.updateTime = LocalDateTime.now();
                            salesOrderRepo.save(order);
                        });
            }
    
            log.info("销售出库审核确认: {} 实出={}", doc.docNo, outQty);
            return doc;
        });
    }

    // ==================== 委外出库（参照委外订单/配方表） ====================

    public List<OutsourceMaterialOutbound> listOutsource() {
        return outsourceRepo.findByOrderByCreateTimeDesc();
    }

    /**
     * 参照委外订单创建出库单据（按配方明细逐项生成）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<OutsourceMaterialOutbound> createOutsourceFromOrder(Long outsourceOrderId, String fromWarehouseId,
                                                                     String operator, String remark,
                                                                     java.util.Map<String, java.util.Map<String, Object>> overrides) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
        OutsourceOrder order = outsourceOrderRepo.findById(outsourceOrderId)
                .orElseThrow(() -> new IllegalArgumentException("委外订单不存在"));
        // v5.27：只有已委外（已排产）的订单才允许发料出库
        if (!"OUTSOURCED".equals(order.status))
            throw new IllegalArgumentException("该订单未排产（委外），不能发料出库（请先在排产中心排产）");
    
            List<OutsourceOrderItem> items = outsourceOrderItemRepo.findByOrderId(outsourceOrderId);
            if (items.isEmpty())
                throw new IllegalArgumentException("委外订单无配方明细");
    
            // 重复参照校验：同一委外订单只允许发料出库一次
            List<OutsourceMaterialOutbound> existOuts = outsourceRepo.findByOutsourceOrderNo(order.orderNo);
            for (OutsourceMaterialOutbound exist : existOuts) {
                if ("CONFIRMED".equals(exist.status)) {
                    throw new IllegalArgumentException("该委外订单已发料出库，不允许重复参照");
                }
            }
    
            // 入库完成校验：已存在 DONE 状态入库单的订单不允许再出库
            List<OutsourceFinishInbound> existIns = outsourceInRepo.findByOutsourceOrderNo(order.orderNo);
            for (OutsourceFinishInbound exist : existIns) {
                if ("DONE".equals(exist.status)) {
                    throw new IllegalArgumentException("该委外订单已完成入库，不允许再出库");
                }
            }
    
            List<OutsourceMaterialOutbound> docs = new java.util.ArrayList<>();
            // v5.55：明细物料台账一次预取（循环内 fillBatchCost 不再逐项回表）
            java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                    items.stream().map(i -> i.materialCode).toList());
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer outsourceMaxSeq = outsourceRepo.findMaxSeq("OUT-IO-" + LocalDate.now().toString().replace("-", "") + "-%");
            long seq = (outsourceMaxSeq == null ? 0 : outsourceMaxSeq);
            for (OutsourceOrderItem item : items) {
                seq++;
                String docNo = String.format("OUT-IO-%s-%04d", LocalDate.now().toString().replace("-", ""), seq);
                OutsourceMaterialOutbound doc = new OutsourceMaterialOutbound();
                doc.docNo = docNo;
                doc.outsourceOrderNo = order.orderNo;
                doc.processorId = order.processor != null ? order.processor : "";
                doc.processorName = order.processor;  // v4.5：冗余存储代工厂名称
                // 优先使用前端选择的批次和用量，批号必填
                java.util.Map<String, Object> ov = overrides != null ? overrides.get(item.materialCode) : null;
                // v5.7：跨库发料——每行可指定独立调出仓库（半成品/原料分仓存放），未指定回退全局
                doc.fromWarehouseId = ov != null && ov.get("warehouseId") != null
                        ? ov.get("warehouseId").toString() : fromWarehouseId;
                // v4.5：不再使用调入仓，置空
                doc.toWarehouseId = null;
                doc.materialCode = item.materialCode;
                doc.batchNo = ov != null && ov.get("batchNo") != null ? ov.get("batchNo").toString() : null;
                requireBatchNo(doc.batchNo, item.materialCode);
                doc.qty = ov != null && ov.get("qty") != null
                        ? java.math.BigDecimal.valueOf(((Number) ov.get("qty")).doubleValue()) : item.qty;
                // v4.5：读取前端按库位分行选择批次时传入的 locationId，精确扣减对应库位库存
                doc.locationId = ov != null && ov.get("locationId") != null ? ov.get("locationId").toString() : null;
                String[] zoneLoc = resolveZoneAndLocationName(doc.locationId);
                doc.zoneName = zoneLoc[0];
                doc.locationName = zoneLoc[1];
                doc.unit = item.unit;
                // 实际成本按调出仓批号直取（v5.7：按行级仓库）
                fillBatchCost(doc.qty, doc.materialCode, doc.batchNo, doc.fromWarehouseId,
                        ledgerByCode.get(doc.materialCode), pc -> {
                            doc.unitPrice = pc[0];
                            doc.cost = pc[1];
                        });
                doc.status = "CONFIRMED";
                doc.createdBy = operator;
                doc.remark = remark;
                doc.createTime = LocalDateTime.now();
                doc.updateTime = LocalDateTime.now();
                outsourceRepo.save(doc);
                // v4.5：直接扣减调出仓库存（材料发给委外工厂，所有权随之转移），传入 locationId 精确扣减指定库位
                inventoryService.outbound("OUTSOURCE_OUT", doc.docNo, doc.materialCode,
                        doc.batchNo, doc.fromWarehouseId, doc.locationId, doc.qty, operator);
                docs.add(doc);
            }
            log.info("委外出库单据批量创建并确认: 订单={} 代工厂={} 共{}项", order.orderNo, order.processor, docs.size());
            return docs;
        });
    }

    /** 某委外订单的出库记录明细 */
    public List<OutsourceMaterialOutbound> listOutsourceOutboundsByOrderId(Long orderId) {
        return outsourceOrderRepo.findById(orderId)
                .map(o -> outsourceRepo.findByOutsourceOrderNo(o.orderNo).stream()
                        .filter(d -> "CONFIRMED".equals(d.status)).collect(java.util.stream.Collectors.toList()))
                .orElse(List.of());
    }

    /**
     * 委外订单自动出库（打印即出库，先进先出，只从该代工厂对应的仓库扣减）：
     * 按订单 processor 匹配 warehouse.processor_name，只在那些仓里按批号 FIFO。
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<OutsourceMaterialOutbound> autoFifoOutsourceOutbound(Long outsourceOrderId, String operator) {
        return writeQueue.executeTx(() -> {
            OutsourceOrder order = outsourceOrderRepo.findById(outsourceOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("委外订单不存在"));
            // 与生产订单逻辑一致：确认（CONFIRMED）即可自动出库；老数据 OUTSOURCED 兼容
            if (!"CONFIRMED".equals(order.status) && !"OUTSOURCED".equals(order.status)) {
                throw new IllegalArgumentException("只有已确认的委外订单可自动出库");
            }
            // 该代工厂对应的仓库集合（按 processor_name 匹配）
            java.util.Set<String> factoryWarehouseIds = warehouseRepo.findAll().stream()
                    .filter(w -> order.processor != null && order.processor.equals(w.processorName))
                    .map(w -> String.valueOf(w.id))
                    .collect(java.util.stream.Collectors.toSet());
            if (factoryWarehouseIds.isEmpty()) {
                throw new IllegalArgumentException("未找到代工厂「" + order.processor + "」对应的仓库，请在仓库管理里配置该代工厂的所属仓库");
            }
            // 重复出库校验
            for (OutsourceMaterialOutbound ex : outsourceRepo.findByOutsourceOrderNo(order.orderNo)) {
                if ("CONFIRMED".equals(ex.status)) throw new IllegalArgumentException("该委外订单已出库，不允许重复出库");
            }
            // 已入库校验
            for (OutsourceFinishInbound in : outsourceInRepo.findByOutsourceOrderNo(order.orderNo)) {
                if ("DONE".equals(in.status)) throw new IllegalArgumentException("该委外订单已完成入库，不允许再出库");
            }
            List<OutsourceOrderItem> items = outsourceOrderItemRepo.findByOrderId(outsourceOrderId);
            if (items.isEmpty()) throw new IllegalArgumentException("委外订单无配方明细");

            List<OutsourceMaterialOutbound> docs = new java.util.ArrayList<>();
            Integer maxSeq = outsourceRepo.findMaxSeq("OUT-IO-" + LocalDate.now().toString().replace("-", "") + "-%");
            long seq = (maxSeq == null ? 0 : maxSeq);
            // v6.1.3：FIFO 单号按年独立计数（同生产侧修复，防同年跨日撞号）
            Integer fifoMax = outsourceRepo.findMaxSeq("OUT-IO-" + LocalDateTime.now().getYear() + "-F%");
            long fifoSeq = (fifoMax == null ? 0 : fifoMax);
            // v5.55：明细物料台账一次预取（原逐物料 findByMaterialCode 的 N+1）
            java.util.Map<String, List<InventoryLedger>> ledgerByCode = ledgersByCode(
                    items.stream().map(i -> i.materialCode).toList());
            for (OutsourceOrderItem item : items) {
                BigDecimal need = item.qty == null ? BigDecimal.ZERO : item.qty;
                if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
                // 代工厂仓内该物料台账 → 按批号聚合（跳过过期）
                java.util.Map<String, BatchAvail> batchMap = new java.util.LinkedHashMap<>();
                for (InventoryLedger l : ledgerByCode.getOrDefault(item.materialCode, List.of())) {
                    if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (l.batchNo == null || l.batchNo.isBlank()) continue;
                    if (l.warehouseId == null || !factoryWarehouseIds.contains(l.warehouseId)) continue;
                    if (l.expiryDate != null && l.expiryDate.isBefore(java.time.LocalDate.now())) continue;
                    BatchAvail ba = batchMap.computeIfAbsent(l.batchNo, k -> { BatchAvail b = new BatchAvail(); b.batchNo = k; return b; });
                    ba.qty = ba.qty.add(l.qty);
                    if (ba.earliestInbound == null || (l.inboundDate != null && l.inboundDate.isBefore(ba.earliestInbound))) {
                        ba.earliestInbound = l.inboundDate;
                        ba.price = l.unitPrice;
                    }
                }
                List<BatchAvail> sorted = new java.util.ArrayList<>(batchMap.values());
                sorted.sort(java.util.Comparator.comparing(
                        (BatchAvail b) -> b.earliestInbound == null ? java.time.LocalDate.MAX : b.earliestInbound));
                BigDecimal total = sorted.stream().map(b -> b.qty).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (total.compareTo(need) < 0) {
                    throw new IllegalArgumentException(String.format(
                            "代工厂仓库存不足: %s(%s) 可用%.3f 需要%.3f，请先向该代工厂仓调拨物料",
                            item.materialCode, item.materialName, total, need));
                }
                BigDecimal remaining = need;
                for (BatchAvail ba : sorted) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal take = ba.qty.compareTo(remaining) <= 0 ? ba.qty : remaining;
                    String fifoDocNo = String.format("OUT-IO-%d-F%04d", LocalDateTime.now().getYear(), fifoSeq + 1);
                    List<InventoryService.LocationDeduct> locDetails = inventoryService.outboundFifoWithDetail(
                            "OUTSOURCE_OUT", fifoDocNo, item.materialCode, ba.batchNo, take, operator, false, factoryWarehouseIds);
                    for (InventoryService.LocationDeduct ld : locDetails) {
                        seq++;
                        String docNo = String.format("OUT-IO-%s-%04d", LocalDate.now().toString().replace("-", ""), seq);
                        OutsourceMaterialOutbound doc = new OutsourceMaterialOutbound();
                        doc.docNo = docNo;
                        doc.outsourceOrderNo = order.orderNo;
                        doc.processorId = order.processor != null ? order.processor : "";
                        doc.processorName = order.processor;
                        doc.fromWarehouseId = ld.warehouseId;
                        doc.toWarehouseId = null;
                        doc.materialCode = item.materialCode;
                        doc.batchNo = ba.batchNo;
                        doc.qty = ld.qty;
                        doc.locationId = ld.locationId;
                        doc.zoneName = ld.zoneName != null ? ld.zoneName : (ld.locationId != null ? resolveZoneAndLocationName(ld.locationId)[0] : null);
                        doc.locationName = ld.locationName != null ? ld.locationName : (ld.locationId != null ? resolveZoneAndLocationName(ld.locationId)[1] : null);
                        doc.unit = item.unit;
                        doc.unitPrice = costingService.autoPrice(item.materialCode, ba.batchNo, ld.price);   // v5.63 计价分流
                        doc.cost = doc.unitPrice != null ? ld.qty.multiply(doc.unitPrice).setScale(2, RoundingMode.HALF_UP) : null;
                        doc.status = "CONFIRMED";
                        doc.createdBy = operator;
                        doc.remark = "自动出库(先进先出·" + order.processor + ")";
                        doc.createTime = LocalDateTime.now();
                        doc.updateTime = LocalDateTime.now();
                        outsourceRepo.save(doc);
                        docs.add(doc);
                    }
                    remaining = remaining.subtract(take);
                }
            }
            log.info("委外订单自动出库(先进先出): 订单={} 代工厂={} 共{}行", order.orderNo, order.processor, docs.size());
            return docs;
        });
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OutsourceMaterialOutbound confirmOutsource(Long id, String operator) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            OutsourceMaterialOutbound doc = outsourceRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("单据不存在"));
            if (!"DRAFT".equals(doc.status))
                throw new IllegalArgumentException("只有草稿状态的单据可确认");

            // v5.27：只有已委外（已排产）的订单才允许发料出库
            requireOutsourced(doc.outsourceOrderNo);
            requireBatchNo(doc.batchNo, doc.materialCode);
            if (doc.cost == null) {
                fillBatchCost(doc.qty, doc.materialCode, doc.batchNo, doc.fromWarehouseId, pc -> {
                    doc.unitPrice = pc[0];
                    doc.cost = pc[1];
                });
            }
            // v4.5：直接扣减调出仓库存（不再调拨到委外仓），传入 locationId 精确扣减指定库位
            inventoryService.outbound("OUTSOURCE_OUT", doc.docNo, doc.materialCode,
                    doc.batchNo, doc.fromWarehouseId, doc.locationId, doc.qty, operator);
    
            doc.status = "CONFIRMED";
            doc.updateTime = LocalDateTime.now();
            outsourceRepo.save(doc);
            log.info("委外出库确认: {}", doc.docNo);
            return doc;
        });
    }

    /**
     * 可参照出库的委外订单列表
     * 排除：已发料出库（CONFIRMED 出库单）、已完成入库（DONE 入库单）的订单
     */
    public List<OutsourceOrder> listReferenceableOutsourceOrdersForOutbound() {
        List<OutsourceOrder> orders = outsourceOrderRepo.findByStatusOrderByCreateTimeDesc("CONFIRMED");
        // 批量取集合后过滤（此前逐单 2 次查询的 N+1）
        java.util.Set<String> fedOrders = new java.util.HashSet<>(outsourceRepo.findConfirmedOrderNos());
        java.util.Set<String> doneInbound = new java.util.HashSet<>();
        for (Object[] row : outsourceInRepo.sumDoneQtyGroupByOrderNo()) doneInbound.add((String) row[0]);
        return orders.stream()
                .filter(o -> !fedOrders.contains(o.orderNo) && !doneInbound.contains(o.orderNo))
                .toList();
    }

    /**
     * 可参照入库的委外订单列表
     * 要求：必须已发料出库；排除：已入库（DONE/CONFIRMED 入库单）的订单
     */
    public List<OutsourceOrder> listReferenceableOutsourceOrdersForInbound() {
        List<OutsourceOrder> orders = outsourceOrderRepo.findByStatusOrderByCreateTimeDesc("CONFIRMED");
        java.util.Set<String> fedOrders = new java.util.HashSet<>(outsourceRepo.findConfirmedOrderNos());
        java.util.Set<String> inbounded = new java.util.HashSet<>(outsourceInRepo.findDoneOrConfirmedOrderNos());
        return orders.stream()
                .filter(o -> fedOrders.contains(o.orderNo) && !inbounded.contains(o.orderNo))
                .toList();
    }

    // ==================== 委外入库（参照委外订单） ====================

    public List<OutsourceFinishInbound> listOutsourceInbound() {
        return outsourceInRepo.findByOrderByCreateTimeDesc();
    }

    /**
     * 参照委外订单创建入库单据（加工完成产品回库）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OutsourceFinishInbound createOutsourceInbound(Long outsourceOrderId, String warehouseId,
                                                          String locationId, BigDecimal qty,
                                                          String batchNo, String operator, String remark) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            OutsourceOrder order = outsourceOrderRepo.findById(outsourceOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("委外订单不存在"));
            // v5.70.2 修复：委外排产后状态为 OUTSOURCED，发料要求 OUTSOURCED，此处只认 CONFIRMED 导致发料后永远无法入库
            if (!"CONFIRMED".equals(order.status) && !"OUTSOURCED".equals(order.status))
                throw new IllegalArgumentException("只有已确认或已委外的订单可入库");
    
            // v4.5：前置校验——必须存在已确认的委外出库单（领料），否则不允许参照生成委外入库单
            List<OutsourceMaterialOutbound> outbounds = outsourceRepo.findByOutsourceOrderNo(order.orderNo);
            boolean hasConfirmedOutbound = outbounds.stream().anyMatch(o -> "CONFIRMED".equals(o.status));
            if (!hasConfirmedOutbound) {
                throw new IllegalArgumentException("该委外订单尚未进行委外出库，不允许参照生成委外入库单");
            }
    
            // 校验：禁止重复参照入库（同一订单已存在已入库/待质检的入库单时拦截）
            List<OutsourceFinishInbound> existInbounds = outsourceInRepo.findByOutsourceOrderNo(order.orderNo);
            for (OutsourceFinishInbound exist : existInbounds) {
                if ("DONE".equals(exist.status)) {
                    throw new IllegalArgumentException("该委外订单已完成入库，不允许重复参照");
                }
                if ("CONFIRMED".equals(exist.status)) {
                    throw new IllegalArgumentException("该委外订单已有待质检的入库单，请先完成质检");
                }
            }
    
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer outInMaxSeq = outsourceInRepo.findMaxSeq("OUT-FG-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("OUT-FG-%s-%04d", LocalDate.now().toString().replace("-", ""), (outInMaxSeq == null ? 0 : outInMaxSeq) + 1);
            // v5.7：批次号自动生成且必填（未传入时由系统生成，保证单据/质检/台账批号一致）
            String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : inventoryService.nextBatchNo();
            OutsourceFinishInbound doc = new OutsourceFinishInbound();
            doc.docNo = docNo;
            doc.outsourceOrderNo = order.orderNo;
            doc.productName = order.productName;
            doc.productCode = order.productCode;
            doc.batchNo = finalBatchNo;
            doc.warehouseId = warehouseId;
            doc.locationId = locationId;
            // 冗余填充分库/库位名称，便于列表展示（与生产入库保持一致）
            String[] zl = resolveZoneAndLocationName(locationId);
            doc.zoneName = zl[0];
            doc.locationName = zl[1];
            doc.qty = qty != null ? qty : order.batchQty;
            doc.unit = order.unit;
            // 得率：实际产出 vs 理论产出（配方批量）
            doc.theoreticalQty = order.batchQty;
            doc.yieldRate = calcYieldRate(doc.qty, order.batchQty);
            doc.status = "DRAFT";
            doc.createdBy = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            outsourceInRepo.save(doc);
            log.info("委外入库单据创建(草稿): {} 产品={}", docNo, order.productName);
            return doc;
        });
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OutsourceFinishInbound confirmOutsourceInbound(Long id, String operator) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            OutsourceFinishInbound doc = outsourceInRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("单据不存在"));
            if (!"DRAFT".equals(doc.status))
                throw new IllegalArgumentException("只有草稿状态的单据可确认");

            // 双保险：确认时再校验订单必须已完成出库（未出库绝对不允许入库）
            List<OutsourceMaterialOutbound> outbounds = outsourceRepo.findByOutsourceOrderNo(doc.outsourceOrderNo);
            boolean hasConfirmedOutbound = outbounds.stream().anyMatch(o -> "CONFIRMED".equals(o.status));
            if (!hasConfirmedOutbound) {
                throw new IllegalArgumentException("该委外订单尚未进行委外出库，不允许确认入库");
            }
    
            // 创建入库前质检单（待检状态，QC合格后才触发入库）
            qcService.createForInbound("OUTSOURCE_INBOUND", doc.docNo,
                    (doc.productCode != null && !doc.productCode.isBlank()) ? doc.productCode : doc.productName,
                    doc.productName, doc.batchNo, doc.qty, doc.unit,
                    doc.warehouseId, doc.locationId,
                    java.time.LocalDate.now(),   // v5.71.8 委外生产日期=确认入库日
                    operator);
    
            doc.status = "CONFIRMED";
            doc.updateTime = LocalDateTime.now();
            outsourceInRepo.save(doc);
            log.info("委外入库确认(待检): {} 产品={} +{}", doc.docNo, doc.productName, doc.qty);
            return doc;
        });
    }

    // ==================== 其他出库 ====================

    public List<OtherOutbound> listOther() {
        return otherRepo.findByOrderByCreateTimeDesc();
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OtherOutbound createOther(String materialCode, String materialName,
                                     String batchNo, String warehouseId,
                                     BigDecimal qty, String reason,
                                     String operator, String remark,
                                     Boolean genFinance, BigDecimal financeAmount,
                                     Long financePartnerId, String financePartnerName) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // 其他出库同样必须选择批号
            requireBatchNo(batchNo, materialCode);
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
        Integer otherOutMaxSeq = otherRepo.findMaxSeq("OTHER-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
        String docNo = String.format("OTHER-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""), (otherOutMaxSeq == null ? 0 : otherOutMaxSeq) + 1);
            OtherOutbound doc = new OtherOutbound();
            doc.docNo = docNo;
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.qty = qty;
            doc.reason = reason;
            // 实际成本按批号直取
            fillBatchCost(qty, materialCode, batchNo, warehouseId, pc -> {
                doc.unitPrice = pc[0];
                doc.cost = pc[1];
            });
            // 财务字段（v6.8：返工强制不立应收）
            final boolean rework = "REWORK".equals(reason);
            doc.genFinance = genFinance != null && genFinance && !rework;
            doc.financeAmount = financeAmount;
            doc.financePartnerId = financePartnerId;
            doc.financePartnerName = financePartnerName;
            // 库存内均为已质检合格品，出库无需再质检，创建即扣减库存
            // v5.23：报废/样品/退货为过期批次处理通道，允许出库；其余原因禁止过期批次出库
            boolean allowExpired = reason != null
                    && (reason.equals("SCRAP") || reason.equals("SAMPLE") || reason.equals("RETURN") || reason.equals("REWORK"));
            if (rework && Boolean.TRUE.equals(genFinance)) {
                log.warn("返工领料单 {} 忽略前端立应收请求（返工不产生收入）", docNo);
            }
            inventoryService.outbound(rework ? "REWORK_OUT" : "OTHER_OUT", docNo, materialCode,
                    batchNo, warehouseId, qty, operator, allowExpired);
            doc.status = "CONFIRMED";
            doc.createdBy = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            otherRepo.save(doc);
            // 生成应收账款（AR）
            if (doc.genFinance && financeAmount != null && financeAmount.compareTo(BigDecimal.ZERO) > 0 && financePartnerId != null) {
                AccountsReceivable ar = new AccountsReceivable();
                ar.customerId = financePartnerId;
                ar.amount = financeAmount.setScale(2, java.math.RoundingMode.HALF_UP);
                ar.dueDate = java.time.LocalDate.now().plusDays(30);
                ar.status = "UNPAID";
                ar.remark = "其他出库自动生成 " + docNo;
                AccountsReceivable saved = financeService.createAR(ar);
                doc.financeDocNo = saved.docNo;
                otherRepo.save(doc);
                log.info("其他出库生成AR: 单据={} 金额={}", docNo, financeAmount);
            }
            log.info("其他出库单据创建并扣减库存: {}", docNo);
            return doc;
        });
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OtherOutbound confirmOther(Long id, String operator) {
        // 其他出库已改为"创建即提交质检"，QC判定合格后自动扣减库存，无需独立确认
        throw new IllegalArgumentException("其他出库由质检判定驱动扣减库存，不支持手动确认");   // v6.1.7：业务拒绝用 IllegalArgument 透出
    }

    // ==================== 其他入库 ====================

    public List<OtherInbound> listOtherInbound() {
        return otherInRepo.findByOrderByCreateTimeDesc();
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OtherInbound createOtherInbound(String materialCode, String materialName,
                                           String batchNo, String warehouseId,
                                           String locationId, BigDecimal qty, BigDecimal price,
                                           BigDecimal taxRate,
                                           String unit, String reason, String operator, String remark,
                                           Boolean genFinance, BigDecimal financeAmount,
                                           Long financePartnerId, String financePartnerName) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
        Integer otherInMaxSeq = otherInRepo.findMaxSeq("OTHER-IN-" + LocalDate.now().toString().replace("-", "") + "-%");
        String docNo = String.format("OTHER-IN-%s-%04d", LocalDate.now().toString().replace("-", ""), (otherInMaxSeq == null ? 0 : otherInMaxSeq) + 1);
            // v5.7：批号一律自动生成（全局唯一），避免手动输入造成重复；单据/质检/台账批号一致
            String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : inventoryService.nextBatchNo();
            OtherInbound doc = new OtherInbound();
            doc.docNo = docNo;
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = finalBatchNo;
            doc.warehouseId = warehouseId;
            doc.locationId = locationId;
            doc.qty = qty;
            doc.price = price;
            doc.taxRate = taxRate != null ? taxRate : java.math.BigDecimal.valueOf(13);   // v5.76
            doc.unit = unit;
            doc.reason = reason;
            // 财务字段（质检合格入库后生成AP）
            doc.genFinance = genFinance != null && genFinance;
            doc.financeAmount = financeAmount;
            doc.financePartnerId = financePartnerId;
            doc.financePartnerName = financePartnerName;
            doc.status = "PENDING_QC";
            doc.createdBy = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            otherInRepo.save(doc);
            // 创建即提交质检，QC判定合格后自动入库（批号随单据，保证一致性）
            qcService.createForOther("INCOMING", docNo, "OTHER_INBOUND",
                    materialCode, materialName, finalBatchNo, qty, unit,
                    warehouseId, locationId, price, operator);
            log.info("其他入库单据创建并提交质检: {}", docNo);
            return doc;
        });
    }

    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OtherInbound confirmOtherInbound(Long id, String operator) {
        // 其他入库已改为"创建即提交质检"，QC判定合格后自动入库，无需独立确认
        throw new IllegalArgumentException("其他入库由质检判定驱动入库，不支持手动确认");   // v6.1.7：同上
    }

    // ==================== 参照退货单出库（采购退货出库）====================

    /**
     * 参照已审核的采购退货单创建其他出库单（reason=RETURN）。
     * 流程：QC 判定退货 → 生成退货单 → 采购审核 → 仓管参照退货单出库。
     * 出库确认时同步冲减应付账款。
     * @param returnOrderId 退货单 ID（须为 APPROVED 状态）
     * @param batchNo       实际出库批号（仓管选择）
     * @param warehouseId   出库仓库
     * @param locationId    出库库位
     * @param operator      操作人
     * @deprecated v5.5 业务修正：质检判定不合格的货物从未入库（REJECT 不触发入库），
     *             采购退货不再需要出库；审核通过即退货完成（ReturnOrderService.approve 按类型分流）。
     *             本方法保留仅为兼容旧入口，采购退货审核后直接置 DONE，不会再进入 APPROVED 状态。
     */
    // v6.1.5：去残留 @Transactional（内部已是 executeTx，旧注解使其退化为旧锁时序）
    @Deprecated
    public OtherOutbound createOutboundFromReturnOrder(Long returnOrderId, String batchNo,
                                                       String warehouseId, String locationId,
                                                       String operator) {
        // v5.24：单号生成+单据保存+库存变动整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            ReturnOrder ro = returnOrderRepo.findById(returnOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("退货单不存在"));
            if (!"APPROVED".equals(ro.status)) {
                throw new IllegalArgumentException("退货单未审核，不可出库");
            }
            if (!"PURCHASE_RETURN".equals(ro.type)) {
                throw new IllegalArgumentException("仅采购退货单可做出库");
            }
            // 校验是否已出库（防止重复出库）
            if (otherRepo.findByReturnOrderId(returnOrderId).isPresent()) {
                throw new IllegalArgumentException("该退货单已生成出库单，不可重复出库");
            }
            // v5.27：创建退货单时已锁定批号，出库必须按单据批号退（传入批号不一致则拒绝）
            if (ro.batchNo != null && !ro.batchNo.isBlank() && !ro.batchNo.equals(batchNo)) {
                throw new IllegalArgumentException("退货单已锁定批号 " + ro.batchNo + "，退货出库必须按该批号");
            }
            // v5.27：仓库自动带出——未传仓库时按锁定批号定位库存最大的仓（批号确定仓库即确定）
            String finalWarehouseId = warehouseId;
            if (finalWarehouseId == null || finalWarehouseId.isBlank()) {
                finalWarehouseId = ledgerRepo.findByMaterialCode(ro.materialCode).stream()
                        .filter(l -> batchNo.equals(l.batchNo) && l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0)
                        .max(java.util.Comparator.comparing(l -> l.qty))
                        .map(l -> l.warehouseId)
                        .orElse(null);
                if (finalWarehouseId == null) {
                    throw new IllegalArgumentException("批号 " + batchNo + " 无库存，无法退货出库");
                }
            }
            // 先生成单据号，用于库存异动留痕
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
        Integer otherOutMaxSeq = otherRepo.findMaxSeq("OTHER-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
        String docNo = String.format("OTHER-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""), (otherOutMaxSeq == null ? 0 : otherOutMaxSeq) + 1);
            // 扣减库存（按批号，直接走 outbound；v5.23：采购退货属处理通道，过期批次放行）
            inventoryService.outbound("OTHER_OUT", docNo, ro.materialCode, batchNo, finalWarehouseId, locationId, ro.qty, operator, true);
            // 从台账反查实际成本单价
            BigDecimal unitPrice = ro.unitPrice != null ? ro.unitPrice : BigDecimal.ZERO;
            BigDecimal cost = ro.qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            // 创建其他出库单
            OtherOutbound out = new OtherOutbound();
            out.docNo = docNo;
            out.materialCode = ro.materialCode;
            out.materialName = ro.materialName;
            out.batchNo = batchNo;
            out.warehouseId = finalWarehouseId;
            out.locationId = locationId;
            out.qty = ro.qty;
            out.unit = ro.unit;
            out.unitPrice = unitPrice;
            out.cost = cost;
            out.reason = "RETURN";
            out.returnOrderId = ro.id;
            out.returnRefDocNo = ro.docNo;
            out.status = "CONFIRMED"; // 参照退货单出库自动确认
            out.createdBy = operator;
            out.remark = "参照退货单 " + ro.docNo + " 出库";
            out.createTime = LocalDateTime.now();
            otherRepo.save(out);
            // 退货单状态置为 DONE，回写出库单号
            ro.status = "DONE";
            ro.outboundDocNo = docNo;
            ro.updateTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            // 冲减应付：v5.27 参照到货单的退货单按到货单精准冲（只冲该批次 AP）；无到货单关联按采购单号冲；手工单未关联采购单按供应商 FIFO 冲
            BigDecimal applied = BigDecimal.ZERO;
            if (ro.returnAmount != null && ro.returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                if (ro.refArrivalId != null) {
                    applied = financeService.applyPurchaseReturn(ro.purchaseOrderNo, ro.returnAmount, docNo, ro.refArrivalId);
                } else if (ro.purchaseOrderNo != null && !ro.purchaseOrderNo.isBlank()) {
                    applied = financeService.applyPurchaseReturn(ro.purchaseOrderNo, ro.returnAmount, docNo, null);
                } else if (ro.supplierId != null) {
                    applied = financeService.applyPurchaseReturnBySupplier(ro.supplierId, ro.returnAmount, docNo);
                }
            }
            log.info("采购退货出库完成: 退货单={} 出库单={} 冲减应付={}", ro.docNo, docNo, applied);
            return out;
        });
    }

    /** 按物料编码从库存台账反查品名/单位（避免注入 MaterialService） */
    private InventoryLedger findLedgerByMaterialCode(String code) {
        if (code == null) return null;
        var ledgers = ledgerRepo.findByMaterialCode(code);
        return ledgers.isEmpty() ? null : ledgers.get(0);
    }

    // ==================== 实际材料成本（按订单汇总） ====================

    /**
     * 按生产订单汇总实际材料成本（已确认生产出库单据的批号成本之和）
     * 用于半成品/成品入库单直出实际成本
     * v4.8：改为数据库 GROUP BY 聚合（原 findAll 全表拉取后内存累加，单据量大时劣化）
     */
    public List<java.util.Map<String, Object>> productionActualCosts() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object[] row : prodRepo.sumCostGroupByProductionOrderNo()) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("orderNo", row[0]);
            item.put("totalCost", ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }

    /** 按委外订单汇总实际材料成本（已确认委外出库单据的批号成本之和） */
    public List<java.util.Map<String, Object>> outsourceActualCosts() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object[] row : outsourceRepo.sumCostGroupByOutsourceOrderNo()) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("orderNo", row[0]);
            item.put("totalCost", ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }
}
