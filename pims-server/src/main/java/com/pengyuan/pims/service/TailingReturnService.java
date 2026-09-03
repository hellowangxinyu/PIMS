package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * v5.35：油尾退回服务
 * 业务：芃远卖给客户的涂料，客户生产完毕未用完的油漆（油尾，已加稀料）退回芃远。
 *  - 必须参照销售出库单（带出客户/物料/原批号/原销售价），原批号带回
 *  - 结算两种方式：DISCOUNT_RETURN 折价退回（退款冲客户应收）/ PAID_RECYCLE 付费回收（客户付钱给芃远，生成应收）
 *  - 确认即入库「油尾库」（qcStatus=TAILING，不走质检，精确到库位），此后不可再销售，仅制漆配方领料消化或报废处置
 * 复用 return_order 表，type=TAILING_RETURN，单号 YW-YYYY-NNNN
 */
@Service
public class TailingReturnService {

    private static final Logger log = LoggerFactory.getLogger(TailingReturnService.class);

    private final ReturnOrderRepository returnOrderRepo;
    private final WarehouseRepository warehouseRepo;
    private final WarehouseLocationService locationService;
    private final InventoryService inventoryService;
    private final FinanceService financeService;
    private final SalesReturnService salesReturnService;
    private final SalesOutboundRepository salesOutRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final WriteQueue writeQueue;
    // v5.38：油尾区改为宿主仓下的分库
    private final com.pengyuan.pims.repository.WarehouseZoneRepository zoneRepo;
    // v5.38.2：按主材体系路由聚酯/氟碳油尾库
    private final com.pengyuan.pims.repository.MaterialRepository materialRepo;

    public TailingReturnService(ReturnOrderRepository returnOrderRepo,
                                WarehouseRepository warehouseRepo,
                                WarehouseLocationService locationService,
                                InventoryService inventoryService,
                                FinanceService financeService,
                                SalesReturnService salesReturnService,
                                SalesOutboundRepository salesOutRepo,
                                SalesOrderRepository salesOrderRepo,
                                WriteQueue writeQueue,
                                com.pengyuan.pims.repository.WarehouseZoneRepository zoneRepo,
                                com.pengyuan.pims.repository.MaterialRepository materialRepo) {
        this.returnOrderRepo = returnOrderRepo;
        this.warehouseRepo = warehouseRepo;
        this.locationService = locationService;
        this.inventoryService = inventoryService;
        this.financeService = financeService;
        this.salesReturnService = salesReturnService;
        this.salesOutRepo = salesOutRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.writeQueue = writeQueue;
        this.zoneRepo = zoneRepo;
        this.materialRepo = materialRepo;
    }

    /** 查询油尾退回单 */
    public List<ReturnOrder> list(String status) {
        return status != null && !status.isBlank()
                ? returnOrderRepo.findByTypeAndStatusOrderByCreateTimeDesc("TAILING_RETURN", status)
                : returnOrderRepo.findByTypeOrderByCreateTimeDesc("TAILING_RETURN");
    }

    public Optional<ReturnOrder> getById(Long id) {
        return returnOrderRepo.findById(id);
    }

    /** 可参照的销售出库单（复用销售退货口径：已确认且剩余可退量>0） */
    public List<java.util.Map<String, Object>> listReturnableOutbounds() {
        return salesReturnService.listReturnableOutbounds();
    }

    /**
     * 油尾区信息（v5.38.1 每个一级仓都有自己的油尾区）：
     * 不带参数返回所有仓的油尾信息列表（前端选仓）；带 warehouseId 返回该仓油尾分库+库位（兼容旧单值结构）。
     */
    public java.util.Map<String, Object> tailingWarehouseInfo(Long warehouseId) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        com.pengyuan.pims.entity.WarehouseZone zone = null;
        if (warehouseId != null) {
            zone = zoneRepo.findFirstByWarehouseIdAndZoneTypeAndEnabledTrueOrderBySortOrderAsc(warehouseId, "TAILING").orElse(null);
        }
        if (zone == null) {
            zone = zoneRepo.findFirstByZoneTypeAndEnabledTrueOrderBySortOrderAsc("TAILING").orElse(null);
        }
        if (zone != null) {
            Warehouse host = warehouseRepo.findById(zone.warehouseId).orElse(null);
            m.put("warehouseId", String.valueOf(zone.warehouseId));
            m.put("warehouseName", host != null ? host.name : "油尾区");
            m.put("zoneId", zone.id);
            m.put("locations", locationService.listByZone(zone.id));
        } else {
            m.put("warehouseId", null);
            m.put("locations", List.of());
        }
        return m;
    }

    /** 全部启用仓的油尾库信息（v5.38.2 每仓聚酯+氟碳两个油尾库；前端创建油尾退回单只选仓，库位体系自动路由） */
    public List<java.util.Map<String, Object>> tailingWarehouseOptions() {
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (Warehouse wh : warehouseRepo.findAll()) {
            if (!Boolean.TRUE.equals(wh.enabled)) continue;
            List<java.util.Map<String, Object>> zones = new java.util.ArrayList<>();
            for (com.pengyuan.pims.entity.WarehouseZone z : zoneRepo.findByWarehouseIdAndEnabledTrueOrderBySortOrderAsc(wh.id)) {
                if (!"TAILING".equals(z.zoneType) && !"TAILING_FC".equals(z.zoneType)) continue;
                java.util.Map<String, Object> zm = new java.util.LinkedHashMap<>();
                zm.put("zoneId", z.id);
                zm.put("zoneType", z.zoneType);
                zm.put("name", z.name);
                zones.add(zm);
            }
            if (zones.isEmpty()) continue;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("warehouseId", String.valueOf(wh.id));
            m.put("warehouseName", wh.name);
            m.put("zones", zones);
            list.add(m);
        }
        return list;
    }

    /**
     * 创建油尾退回单（DRAFT）
     * @param settleType DISCOUNT_RETURN(折价退回，单价>0) / PAID_RECYCLE(付费回收，单价<0)
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public ReturnOrder create(String customerName, String refSalesOutboundNo, String materialCode,
                              String materialName, String batchNo, String unit,
                              BigDecimal qty, BigDecimal unitPrice, String settleType,
                              String warehouseId, String locationId, String locationName,
                              String remark, String operator) {
        if (customerName == null || customerName.isBlank()) throw new IllegalArgumentException("请选择客户");
        if (materialCode == null || materialCode.isBlank()) throw new IllegalArgumentException("物料编码不能为空");
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("退回数量必须大于 0");
        if (settleType == null || (!"DISCOUNT_RETURN".equals(settleType) && !"PAID_RECYCLE".equals(settleType))) {
            throw new IllegalArgumentException("请选择结算方式");
        }
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        if ("DISCOUNT_RETURN".equals(settleType) && price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("折价退回的单价必须大于 0");
        }
        if ("PAID_RECYCLE".equals(settleType) && price.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalArgumentException("付费回收的单价必须小于 0（客户付钱给芃远）");
        }
        if (refSalesOutboundNo == null || refSalesOutboundNo.isBlank()) {
            throw new IllegalArgumentException("油尾退回必须参照原销售出库单");
        }

        return writeQueue.executeTx(() -> {
            // 参照出库单：带出销售订单号与客户 ID（折价退回冲应收、付费回收生成应收用）
            SalesOutbound ob = salesOutRepo.findByDocNo(refSalesOutboundNo)
                    .orElseThrow(() -> new IllegalArgumentException("销售出库单不存在：" + refSalesOutboundNo));
            if (!"CONFIRMED".equals(ob.status)) {
                throw new IllegalArgumentException("销售出库单未确认，不可参照退回");
            }
            if (ob.batchNo != null && !ob.batchNo.isBlank() && batchNo != null && !batchNo.isBlank()
                    && !ob.batchNo.equals(batchNo)) {
                throw new IllegalArgumentException("退回批号与出库单批号不一致（原批号带回，应锁定 " + ob.batchNo + "）");
            }
            // v6.1（中#11）：同一出库单累计退回不可超过原出库量（含草稿在途），防多次退回虚增油尾库存
            BigDecimal obQty = ob.qty == null ? BigDecimal.ZERO : ob.qty;
            BigDecimal returnedSoFar = BigDecimal.ZERO;
            for (ReturnOrder r : returnOrderRepo.findByTypeAndRefSalesOutboundNo("TAILING_RETURN", refSalesOutboundNo)) {
                if (r.qty != null && !"CANCELLED".equals(r.status)) returnedSoFar = returnedSoFar.add(r.qty);
            }
            if (returnedSoFar.add(qty).compareTo(obQty) > 0) {
                throw new IllegalArgumentException(String.format(
                        "累计退回 %.3f + 本次 %.3f 超过原出库量 %.3f，不可退回", returnedSoFar, qty, obQty));
            }
            Integer maxSeq = returnOrderRepo.findMaxSeq("YW-" + LocalDate.now().toString().replace("-", "") + "-%");
            ReturnOrder ro = new ReturnOrder();
            ro.docNo = String.format("YW-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            ro.type = "TAILING_RETURN";
            ro.customerId = null; // SalesOutbound 无 customerId，付费回收生成应收时按名称挂账
            ro.customerName = customerName;
            ro.salesOrderNo = ob.salesOrderNo;
            ro.refSalesOutboundNo = refSalesOutboundNo;
            ro.materialCode = materialCode;
            ro.materialName = materialName;
            ro.batchNo = ob.batchNo != null && !ob.batchNo.isBlank() ? ob.batchNo : batchNo; // 原批号带回
            ro.unit = unit;
            ro.qty = qty;
            ro.unitPrice = price;
            ro.returnAmount = qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP);
            ro.settleType = settleType;
            ro.warehouseId = warehouseId;  // v5.38.2 入库目标仓（确认时按体系路由该仓的聚酯/氟碳油尾库）
            ro.locationId = locationId;
            ro.locationName = locationName;
            ro.status = "DRAFT";
            ro.createdBy = operator;
            ro.remark = remark;
            ro.createTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            log.info("油尾退回单创建: {} 客户={} 物料={} 批号={} 数量={} 结算={} 单价={}",
                    ro.docNo, customerName, materialCode, ro.batchNo, qty, settleType, price);
            return ro;
        });
    }

    /** 确认：入库油尾库（原批号）+ 财务结算 */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public ReturnOrder confirm(Long id, String operator) {
        return writeQueue.executeTx(() -> {
            ReturnOrder ro = returnOrderRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("油尾退回单不存在"));
            if (!"DRAFT".equals(ro.status)) throw new IllegalArgumentException("只有草稿状态的油尾退回单可确认");

            // v5.38.2：按产品主材体系路由油尾库——氟碳进氟碳油尾库，其余（聚酯/环氧/丙烯酸）进聚酯油尾库；
            // 每个一级仓都有自己的聚酯/氟碳油尾库，优先该退回单参照出库的发货仓，未指定兜底第一个有油尾库的仓
            String tailZoneType = "TAILING";
            String mainMat = materialRepo.findByCode(ro.materialCode).map(m -> m.mainMaterial).orElse(null);
            if (mainMat != null && mainMat.contains("氟碳")) tailZoneType = "TAILING_FC";
            com.pengyuan.pims.entity.WarehouseZone tailingZone = null;
            Long srcWhId = null;
            if (ro.warehouseId != null && !ro.warehouseId.isBlank()) {
                try { srcWhId = Long.valueOf(ro.warehouseId.trim()); } catch (NumberFormatException ignored) { }
            }
            if (srcWhId != null) {
                tailingZone = zoneRepo.findFirstByWarehouseIdAndZoneTypeAndEnabledTrueOrderBySortOrderAsc(srcWhId, tailZoneType).orElse(null);
            }
            if (tailingZone == null) {
                tailingZone = zoneRepo.findFirstByZoneTypeAndEnabledTrueOrderBySortOrderAsc(tailZoneType)
                        .orElseThrow(() -> new IllegalArgumentException("油尾分库不存在，请先初始化"));
            }
            // 入库库位：一律取该油尾库默认库位（体系自动路由，人工不选库位）
            String locId = null;
            String locName = null;
            Optional<WarehouseLocation> defLoc = locationService.listByZone(tailingZone.id).stream().findFirst();
            if (defLoc.isPresent()) {
                locId = String.valueOf(defLoc.get().id);
                locName = defLoc.get().name;
            }
            // v5.35：油尾入库带原批号（绕过全局批号查重），qcStatus=TAILING，单位成本 0，不走质检
            InventoryService.QcInfo qcInfo = new InventoryService.QcInfo(
                    "TAILING", "YW-" + ro.docNo, "油尾退回", operator, java.time.LocalDate.now());
            inventoryService.purchaseInboundWithDate(
                    "TAILING_IN", ro.docNo, ro.materialCode, ro.materialName,
                    ro.batchNo, String.valueOf(tailingZone.warehouseId), locId, ro.qty,
                    BigDecimal.ZERO, null, null, operator, qcInfo, true);
            // 回写单据：实际入库仓库与库位（体系自动路由结果，列表可见）
            ro.warehouseId = String.valueOf(tailingZone.warehouseId);
            ro.locationId = locId;
            ro.locationName = (tailingZone.name != null ? tailingZone.name : "油尾库") + (locName != null ? "·" + locName : "");

            // 财务结算（v5.35 两方式）
            BigDecimal amount = ro.returnAmount != null ? ro.returnAmount : BigDecimal.ZERO;
            if ("DISCOUNT_RETURN".equals(ro.settleType) && amount.compareTo(BigDecimal.ZERO) > 0) {
                // 折价退回：冲减该销售订单应收
                if (ro.salesOrderNo != null && !ro.salesOrderNo.isBlank()) {
                    financeService.applySalesReturn(ro.salesOrderNo, amount, ro.docNo);
                } else {
                    log.warn("油尾退回单 {} 无关联销售订单，折价退回未冲减应收", ro.docNo);
                }
            } else if ("PAID_RECYCLE".equals(ro.settleType) && amount.compareTo(BigDecimal.ZERO) < 0) {
                // 付费回收：客户付钱给芃远 → 生成客户应收
                Long customerId = null;
                if (ro.salesOrderNo != null && !ro.salesOrderNo.isBlank()) {
                    customerId = salesOrderRepo.findByOrderNo(ro.salesOrderNo)
                            .map(o -> o.customerId).orElse(null);
                }
                if (customerId == null) {
                    throw new IllegalArgumentException("付费回收需要关联销售订单以确定客户，请重新创建");
                }
                AccountsReceivable ar = new AccountsReceivable();
                ar.customerId = customerId;
                ar.customerName = ro.customerName;
                ar.amount = amount.abs().setScale(2, java.math.RoundingMode.HALF_UP);
                ar.dueDate = java.time.LocalDate.now().plusDays(30);
                ar.status = "UNPAID";
                ar.remark = "油尾回收 " + ro.docNo;
                financeService.createAR(ar);
            }

            ro.status = "CONFIRMED";
            ro.approvedBy = operator;
            ro.approveTime = LocalDateTime.now();
            ro.updateTime = LocalDateTime.now();
            returnOrderRepo.save(ro);
            log.info("油尾退回确认入库: {} 入库油尾库 库位={} 结算={} 金额={}", ro.docNo, locName, ro.settleType, amount);
            return ro;
        });
    }

    /** 驳回 */
    @Transactional
    public ReturnOrder reject(Long id, String operator) {
        ReturnOrder ro = returnOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("油尾退回单不存在"));
        if (!"DRAFT".equals(ro.status)) throw new IllegalArgumentException("只有草稿状态可驳回");
        ro.status = "REJECTED";
        ro.approvedBy = operator;
        ro.approveTime = LocalDateTime.now();
        ro.updateTime = LocalDateTime.now();
        returnOrderRepo.save(ro);
        return ro;
    }
}
