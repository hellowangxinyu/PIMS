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
 * 盘库服务
 * 盘盈：实盘 > 系统，库存增加
 * 盘亏：实盘 < 系统，库存减少
 * 库位调整：同仓库内物料从一个库位移到另一个库位
 */
@Service
public class StockCheckService {

    private static final Logger log = LoggerFactory.getLogger(StockCheckService.class);

    private final StockCheckRepository stockCheckRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final InventoryMovementRepository movementRepo;
    private final WarehouseLocationRepository locationRepo;
    private final WarehouseZoneRepository zoneRepo;
    private final WarehouseRepository warehouseRepo;
    private final WriteQueue writeQueue;

    public StockCheckService(StockCheckRepository stockCheckRepo,
                             InventoryLedgerRepository ledgerRepo,
                             InventoryMovementRepository movementRepo,
                             WarehouseLocationRepository locationRepo,
                             WarehouseZoneRepository zoneRepo,
                             WarehouseRepository warehouseRepo,
                             WriteQueue writeQueue) {
        this.stockCheckRepo = stockCheckRepo;
        this.ledgerRepo = ledgerRepo;
        this.movementRepo = movementRepo;
        this.locationRepo = locationRepo;
        this.zoneRepo = zoneRepo;
        this.warehouseRepo = warehouseRepo;
        this.writeQueue = writeQueue;
    }

    // ==================== 查询 ====================

    public List<StockCheck> queryAll() {
        return stockCheckRepo.findByOrderByCreateTimeDesc();
    }

    public List<StockCheck> queryByType(String docType) {
        return stockCheckRepo.findByDocTypeOrderByCreateTimeDesc(docType);
    }

    // ==================== 盘盈 ====================

    /**
     * 盘盈：实盘数量 > 系统数量，库存增加差异量
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public StockCheck stockGain(String materialCode, String materialName, String batchNo,
                                String warehouseId, String locationId,
                                BigDecimal actualQty,
                                String operator, String remark) {
        // v5.46 铁律：物料操作必须精确到批号和库位
        if (batchNo == null || batchNo.isBlank()) throw new IllegalArgumentException("盘盈必须指定批号（物料操作精确到批次铁律）");
        if (locationId == null || locationId.isBlank()) throw new IllegalArgumentException("盘盈必须指定库位（物料操作精确到库位铁律）");
        // v5.24：单号生成+库存变动+单据保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (actualQty == null) throw new IllegalArgumentException("实盘数量不能为空");
            String docNo = generateDocNo();
            InventoryLedger ledger = getOrCreateLedger(materialCode, batchNo, warehouseId, locationId);
            BigDecimal qtyBefore = ledger.qty;
            // v6.1：差异以台账实际数为准（不信任前端 systemQty）——实盘 − 台账现值 = 盘盈量
            BigDecimal diff = actualQty.subtract(qtyBefore);
            if (diff.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("实盘数量不大于台账当前数量 " + qtyBefore.stripTrailingZeros().toPlainString() + "，无需盘盈");
            }
            // v6.1：金额随量重算（量×单价）；新行无价取该物料现货加权均价，避免 0 价污染计价
            if (ledger.unitPrice == null || ledger.unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                ledger.unitPrice = avgPriceOf(materialCode);
            }
            ledger.qty = ledger.qty.add(diff);
            ledger.availableQty = ledger.availableQty.add(diff);
            // v8.2（P0-3）：盘盈按新增量累加价值（量×单价），不再整行重估——避免吸收历史舍入差
            if (ledger.unitPrice != null && ledger.unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                if (ledger.amount == null) ledger.amount = BigDecimal.ZERO;
                ledger.amount = ledger.amount.add(diff.multiply(ledger.unitPrice)).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            if (materialName != null) ledger.materialName = materialName;
            ledger.locationId = locationId;
            fillLocationInfo(ledger, locationId);
            // v6.1.1：盘盈新行必须有入库日期——null 会让 FIFO 排序在不同口径下时而被当最老、时而最新
            if (ledger.inboundDate == null) ledger.inboundDate = LocalDate.now();
            ledger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(ledger);

            saveMovement("ADJUSTMENT", docNo, materialCode, batchNo, warehouseId, ledger.locationId,
                    "IN", diff, qtyBefore, ledger.qty, ledger.ownershipType, operator, "盘盈");

            StockCheck doc = new StockCheck();
            doc.docNo = docNo;
            doc.docType = "STOCK_GAIN";
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.systemQty = qtyBefore;
            doc.actualQty = actualQty;
            doc.diffQty = diff;
            doc.status = "CONFIRMED";
            doc.operator = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            stockCheckRepo.save(doc);

            log.info("盘盈: {} 物料{} 台账{} 实盘{} 差异+{}", docNo, materialCode, qtyBefore, actualQty, diff);
            return doc;
        });
    }

    // ==================== 盘亏 ====================

    /**
     * 盘亏：实盘数量 < 系统数量，库存减少差异量
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public StockCheck stockLoss(String materialCode, String materialName, String batchNo,
                                String warehouseId, String locationId,
                                BigDecimal actualQty,
                                String operator, String remark) {
        // v5.46 铁律：物料操作必须精确到批号和库位
        if (batchNo == null || batchNo.isBlank()) throw new IllegalArgumentException("盘亏必须指定批号（物料操作精确到批次铁律）");
        if (locationId == null || locationId.isBlank()) throw new IllegalArgumentException("盘亏必须指定库位（物料操作精确到库位铁律）");
        // v5.24：单号生成+库存变动+单据保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (actualQty == null || actualQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("实盘数量不能为负数");
            }
            String docNo = generateDocNo();
            InventoryLedger ledger = getOrCreateLedger(materialCode, batchNo, warehouseId, locationId);
            BigDecimal qtyBefore = ledger.qty;
            // v6.1：差异以台账实际数为准（不信任前端 systemQty）——台账现值 − 实盘 = 盘亏量
            BigDecimal diff = qtyBefore.subtract(actualQty);
            if (diff.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("实盘数量不小于台账当前数量 " + qtyBefore.stripTrailingZeros().toPlainString() + "，无需盘亏");
            }
            ledger.qty = ledger.qty.subtract(diff);
            ledger.availableQty = ledger.availableQty.subtract(diff);
            if (ledger.availableQty.compareTo(BigDecimal.ZERO) < 0) ledger.availableQty = BigDecimal.ZERO;
            // v8.2（P0-3）：盘亏按本次量等额扣减价值，不再整行重估（守恒）
            if (ledger.unitPrice != null && ledger.unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                if (ledger.amount == null) ledger.amount = BigDecimal.ZERO;
                ledger.amount = ledger.amount.subtract(diff.multiply(ledger.unitPrice)).setScale(2, java.math.RoundingMode.HALF_UP);
                if (ledger.amount.compareTo(BigDecimal.ZERO) < 0) ledger.amount = BigDecimal.ZERO;
            }
            ledger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(ledger);

            saveMovement("ADJUSTMENT", docNo, materialCode, batchNo, warehouseId, ledger.locationId,
                    "OUT", diff, qtyBefore, ledger.qty, ledger.ownershipType, operator, "盘亏");

            StockCheck doc = new StockCheck();
            doc.docNo = docNo;
            doc.docType = "STOCK_LOSS";
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.systemQty = qtyBefore;
            doc.actualQty = actualQty;
            doc.diffQty = diff.negate();
            doc.status = "CONFIRMED";
            doc.operator = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            stockCheckRepo.save(doc);

            log.info("盘亏: {} 物料{} 台账{} 实盘{} 差异-{}", docNo, materialCode, qtyBefore, actualQty, diff);
            return doc;
        });
    }

    // ==================== 库位调整 ====================

    /**
     * 库位调整：同仓库内，物料从原库位移到目标库位
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public StockCheck locationAdjust(String materialCode, String materialName, String batchNo,
                                     String warehouseId, String toWarehouseId,
                                     String fromLocationId, String toLocationId,
                                     BigDecimal qty, String operator, String remark) {
        // v5.46 铁律：物料操作必须精确到批号和库位
        if (batchNo == null || batchNo.isBlank()) throw new IllegalArgumentException("库位调整必须指定批号（物料操作精确到批次铁律）");
        if (fromLocationId == null || fromLocationId.isBlank()) throw new IllegalArgumentException("库位调整必须指定原库位（物料操作精确到库位铁律）");
        if (toLocationId == null || toLocationId.isBlank()) throw new IllegalArgumentException("库位调整必须指定目标库位（物料操作精确到库位铁律）");
        // v5.24：单号生成+库存变动+单据保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("调整数量必须大于0");
            }
            if (fromLocationId != null && fromLocationId.equals(toLocationId)
                    && (toWarehouseId == null || toWarehouseId.isBlank() || toWarehouseId.equals(warehouseId))) {
                throw new IllegalArgumentException("原库位与目标库位不能相同");
            }
    
            String docNo = generateDocNo();
            // 目标仓库：为空 = 同仓内调库位
            String finalToWarehouseId = (toWarehouseId == null || toWarehouseId.isBlank()) ? warehouseId : toWarehouseId;

            // v5.30/5.35 隔离校验（v5.38 改库位级：隔离区/油尾区降级为分库，按库位判断）
            // v5.43.3 修复：v5.38.3 拆三库/v5.38.2 拆氟碳时此处漏改（旧单类型值判断恒 false，隔离拦截失效）
            boolean srcUnq = isUnqualifiedLoc(fromLocationId);
            boolean dstUnq = isUnqualifiedLoc(toLocationId);
            boolean srcTail = isTailingLoc(fromLocationId);
            boolean dstTail = isTailingLoc(toLocationId);
            if ((dstUnq || dstTail) && !(srcUnq || srcTail)) {
                throw new IllegalArgumentException("正常库存不可调整进入隔离分库（隔离区/油尾区由质检判定、油尾退回单专用通道转入）");
            }
            if ((srcUnq || srcTail) && !finalToWarehouseId.equals(warehouseId)) {
                throw new IllegalArgumentException("隔离分库的物料不可调整到其他仓库，请走「其他出库-报废/退货」处理");
            }
            if (srcUnq != dstUnq || srcTail != dstTail) {
                throw new IllegalArgumentException("隔离区与油尾区之间不可互调");
            }
    
            // 获取库位名称
            String fromLocName = null;
            String toLocName = null;
            if (fromLocationId != null) {
                Optional<WarehouseLocation> fromLoc = locationRepo.findById(Long.valueOf(fromLocationId));
                if (fromLoc.isPresent()) fromLocName = fromLoc.get().name;
            }
            if (toLocationId != null) {
                Optional<WarehouseLocation> toLoc = locationRepo.findById(Long.valueOf(toLocationId));
                if (toLoc.isPresent()) toLocName = toLoc.get().name;
            }
    
            final String finalFromLocName = fromLocName;
            final String finalToLocName = toLocName;
    
            writeQueue.executeTx(() -> {
                // 1. 原库位扣减（原仓库）
                InventoryLedger fromLedger = getOrCreateLedger(materialCode, batchNo, warehouseId, fromLocationId);
                if (fromLedger.qty.compareTo(qty) < 0) {
                    throw new IllegalArgumentException(String.format(
                            "原库位库存不足: %s 批次%s 库位%s 当前%.3f 需移%.3f",
                            materialCode, batchNo == null ? "-" : batchNo, fromLocationId, fromLedger.qty, qty));
                }
                BigDecimal fromBefore = fromLedger.qty;
                fromLedger.qty = fromLedger.qty.subtract(qty);
                fromLedger.availableQty = fromLedger.availableQty.subtract(qty);
                if (fromLedger.availableQty.compareTo(BigDecimal.ZERO) < 0) fromLedger.availableQty = BigDecimal.ZERO;
                // v6.1：金额随量结转（量×行单价），原行等额扣减——移库不改变库存总价值
                BigDecimal moveAmt = fromLedger.unitPrice != null && fromLedger.unitPrice.compareTo(BigDecimal.ZERO) > 0
                        ? qty.multiply(fromLedger.unitPrice).setScale(2, java.math.RoundingMode.HALF_UP) : null;
                if (moveAmt != null && fromLedger.amount != null) {
                    fromLedger.amount = fromLedger.amount.subtract(moveAmt);
                    if (fromLedger.amount.compareTo(BigDecimal.ZERO) < 0) fromLedger.amount = BigDecimal.ZERO;
                }
                fromLedger.lastUpdateTime = LocalDateTime.now();
                ledgerRepo.save(fromLedger);

                saveMovement("ADJUSTMENT", docNo, materialCode, batchNo, warehouseId, fromLedger.locationId,
                        "OUT", qty, fromBefore, fromLedger.qty, fromLedger.ownershipType, operator,
                        "库位调整: " + finalFromLocName + " → " + finalToLocName);

                // 2. 目标库位增加（跨仓时在目标仓库建立/增加台账行）
                InventoryLedger toLedger = getOrCreateLedger(materialCode, batchNo, finalToWarehouseId, toLocationId);
                BigDecimal toBefore = toLedger.qty;
                toLedger.qty = toLedger.qty.add(qty);
                toLedger.availableQty = toLedger.availableQty.add(qty);
                toLedger.unit = fromLedger.unit;
                toLedger.unitPrice = fromLedger.unitPrice;
                // v6.1：批次属性随行复制——生产日期/保质期/质检信息/入库日期，否则目标行丢失批次追溯与过期判断依据
                toLedger.produceDate = fromLedger.produceDate;
                toLedger.expiryDate = fromLedger.expiryDate;
                toLedger.qcStatus = fromLedger.qcStatus;
                toLedger.qcResult = fromLedger.qcResult;
                toLedger.qcInspectionNo = fromLedger.qcInspectionNo;
                toLedger.qcInspector = fromLedger.qcInspector;
                toLedger.qcDate = fromLedger.qcDate;
                toLedger.inboundDate = fromLedger.inboundDate;
                toLedger.ownershipType = fromLedger.ownershipType;
                if (moveAmt != null) toLedger.amount = toLedger.amount.add(moveAmt);
                if (materialName != null) toLedger.materialName = materialName;
                if (toLocationId != null) {
                    toLedger.locationId = toLocationId;
                    fillLocationInfo(toLedger, toLocationId);
                }
                toLedger.lastUpdateTime = LocalDateTime.now();
                ledgerRepo.save(toLedger);
    
                saveMovement("ADJUSTMENT", docNo, materialCode, batchNo, finalToWarehouseId, toLedger.locationId,
                        "IN", qty, toBefore, toLedger.qty, toLedger.ownershipType, operator,
                        "库位调整: " + finalFromLocName + " → " + finalToLocName);
            });
    
            StockCheck doc = new StockCheck();
            doc.docNo = docNo;
            doc.docType = "LOCATION_ADJUST";
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.fromLocationId = fromLocationId;
            doc.fromLocationName = fromLocName;
            doc.toLocationId = toLocationId;
            doc.toLocationName = toLocName;
            doc.adjustQty = qty;
            doc.status = "CONFIRMED";
            doc.operator = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            stockCheckRepo.save(doc);
    
            log.info("库位调整: {} 物料{} 数量{} {} → {}", docNo, materialCode, qty, fromLocName, toLocName);
            return doc;
        });
    }

    // ==================== 内部方法 ====================

    /** v5.30/5.35：判断某仓库是否为指定类型的隔离仓（不合格品库/油尾库） */
    /** v5.43.3：不合格品库三类（原材料/半成品/成品）任一 */
    private boolean isUnqualifiedLoc(String locationId) {
        return isSpecialLocation(locationId, "UNQUALIFIED_RAW")
                || isSpecialLocation(locationId, "UNQUALIFIED_SEMI")
                || isSpecialLocation(locationId, "UNQUALIFIED_FIN");
    }

    /** v5.43.3：油尾库两类（聚酯/氟碳）任一 */
    private boolean isTailingLoc(String locationId) {
        return isSpecialLocation(locationId, "TAILING") || isSpecialLocation(locationId, "TAILING_FC");
    }

    /** v5.38：判断库位是否属于指定类型的隔离分库（库位→分库→zone_type） */
    private boolean isSpecialLocation(String locationId, String zoneType) {
        if (locationId == null || locationId.isBlank()) return false;
        try {
            return locationRepo.findById(Long.valueOf(locationId))
                    .map(l -> l.zoneId)
                    .flatMap(zoneRepo::findById)
                    .map(z -> zoneType.equals(z.zoneType))
                    .orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String generateDocNo() {
        // v5.24：按最大序号+1（count 会删除错位且并发撞号）
        Integer maxSeq = stockCheckRepo.findMaxSeq("CHK-" + LocalDate.now().toString().replace("-", "") + "-%");
        return String.format("CHK-%s-%04d", LocalDate.now().toString().replace("-", ""),
                (maxSeq == null ? 0 : maxSeq) + 1);
    }

    /** v6.1：该物料现货加权均价（Σ金额/Σ数量，仅正库存行），无价无量为 null——盘盈新行取价用 */
    private BigDecimal avgPriceOf(String materialCode) {
        BigDecimal qtySum = BigDecimal.ZERO, amtSum = BigDecimal.ZERO;
        for (InventoryLedger l : ledgerRepo.findByMaterialCode(materialCode)) {
            if (l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0
                    && l.amount != null && l.unitPrice != null && l.unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                qtySum = qtySum.add(l.qty);
                amtSum = amtSum.add(l.amount);
            }
        }
        return qtySum.compareTo(BigDecimal.ZERO) > 0
                ? amtSum.divide(qtySum, 2, java.math.RoundingMode.HALF_UP) : null;
    }

    private InventoryLedger getOrCreateLedger(String materialCode, String batchNo, String warehouseId, String locationId) {
        Optional<InventoryLedger> found;
        if (batchNo == null && locationId == null) {
            found = ledgerRepo.findByMaterialCodeAndBatchNoIsNullAndWarehouseIdAndLocationIdIsNull(materialCode, warehouseId);
        } else if (batchNo == null) {
            found = ledgerRepo.findByMaterialCodeAndBatchNoIsNullAndWarehouseIdAndLocationId(materialCode, warehouseId, locationId);
        } else if (locationId == null) {
            found = ledgerRepo.findByMaterialCodeAndBatchNoAndWarehouseIdAndLocationIdIsNull(materialCode, batchNo, warehouseId);
        } else {
            found = ledgerRepo.findByMaterialCodeAndBatchNoAndWarehouseIdAndLocationId(materialCode, batchNo, warehouseId, locationId);
        }
        return found.map(l -> {
            if (l.qty == null) l.qty = BigDecimal.ZERO;
            if (l.availableQty == null) l.availableQty = BigDecimal.ZERO;
            if (l.occupiedQty == null) l.occupiedQty = BigDecimal.ZERO;
            if (l.inTransitQty == null) l.inTransitQty = BigDecimal.ZERO;
            if (l.amount == null) l.amount = BigDecimal.ZERO;
            return l;
        }).orElseGet(() -> {
            InventoryLedger l = new InventoryLedger();
            l.materialCode = materialCode;
            l.batchNo = batchNo;
            l.warehouseId = warehouseId;
            l.locationId = locationId;
            l.ownershipType = "PENGYUAN";
            l.qty = BigDecimal.ZERO;
            l.availableQty = BigDecimal.ZERO;
            l.occupiedQty = BigDecimal.ZERO;
            l.inTransitQty = BigDecimal.ZERO;
            l.amount = BigDecimal.ZERO;
            return l;
        });
    }

    private void fillLocationInfo(InventoryLedger ledger, String locationId) {
        locationRepo.findById(Long.valueOf(locationId)).ifPresent(loc -> {
            ledger.locationName = loc.name;
            zoneRepo.findById(loc.zoneId).ifPresent(zone -> ledger.zoneName = zone.name);
        });
    }

    private void saveMovement(String docType, String docNo, String materialCode,
                              String batchNo, String warehouseId, String locationId, String direction,
                              BigDecimal qty, BigDecimal qtyBefore, BigDecimal qtyAfter,
                              String ownershipType, String operator, String remark) {
        InventoryMovement m = new InventoryMovement();
        m.docType = docType;
        m.docNo = docNo;
        m.materialCode = materialCode;
        m.batchNo = batchNo;
        m.warehouseId = warehouseId;
        m.locationId = locationId;
        m.direction = direction;
        m.qty = qty;
        m.qtyBefore = qtyBefore;
        m.qtyAfter = qtyAfter;
        m.ownershipType = ownershipType;
        m.operator = operator;
        m.remark = remark;
        movementRepo.save(m);
    }
}
