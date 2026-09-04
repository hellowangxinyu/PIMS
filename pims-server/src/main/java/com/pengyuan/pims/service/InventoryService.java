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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 库存管理核心服务
 * 所有权与物理位置分离建模
 * 所有库存变更由单据驱动，记录审计日志
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryLedgerRepository ledgerRepo;
    private final InventoryMovementRepository movementRepo;
    private final WarehouseLocationRepository locationRepo;
    private final WarehouseZoneRepository zoneRepo;
    private final WarehouseRepository warehouseRepo;
    private final ProductionOutboundRepository prodOutRepo;
    private final OtherOutboundRepository otherOutRepo;
    private final MaterialRepository materialRepo;
    private final WriteQueue writeQueue;

    /** 批号日期内序号（同一天内递增） */
    private final AtomicInteger batchSeq = new AtomicInteger(0);
    private volatile String lastBatchDate = "";

    public InventoryService(InventoryLedgerRepository ledgerRepo,
                            InventoryMovementRepository movementRepo,
                            WarehouseLocationRepository locationRepo,
                            WarehouseZoneRepository zoneRepo,
                            WarehouseRepository warehouseRepo,
                            ProductionOutboundRepository prodOutRepo,
                            OtherOutboundRepository otherOutRepo,
                            MaterialRepository materialRepo,
                            WriteQueue writeQueue) {
        this.ledgerRepo = ledgerRepo;
        this.movementRepo = movementRepo;
        this.locationRepo = locationRepo;
        this.zoneRepo = zoneRepo;
        this.warehouseRepo = warehouseRepo;
        this.prodOutRepo = prodOutRepo;
        this.otherOutRepo = otherOutRepo;
        this.materialRepo = materialRepo;
        this.writeQueue = writeQueue;
    }

    // ==================== 查询 ====================

    /** v6.3 第四批：删除台账全表直出（无调用方；前端走分页 search，防大表全量加载） */

    /** 按物料查所有仓库库存 */
    public List<InventoryLedger> queryByMaterial(String materialCode) {
        return ledgerRepo.findByMaterialCode(materialCode);
    }

    /** 按仓库查所有物料库存 */
    public List<InventoryLedger> queryByWarehouse(String warehouseId) {
        return ledgerRepo.findByWarehouseId(warehouseId);
    }

    /** v5.9：台账关键字分页搜索（品名/编码/批号 + 仓库过滤） */
    public org.springframework.data.domain.Page<InventoryLedger> searchLedger(String keyword, String warehouseId, org.springframework.data.domain.Pageable pageable) {
        return ledgerRepo.searchByKeyword(keyword == null ? "" : keyword.trim(),
                warehouseId == null ? "" : warehouseId.trim(), pageable);
    }

    /**
     * 库存批次选项（v5.4，退货出库等选批号用）
     * 按物料（+仓库）过滤有库存的台账行，按批号聚合可用量
     */
    public List<java.util.Map<String, Object>> queryBatchOptions(String materialCode, String warehouseId) {
        List<InventoryLedger> ledgers = (warehouseId == null || warehouseId.isBlank())
                ? ledgerRepo.findByMaterialCode(materialCode)
                : ledgerRepo.findByMaterialCodeAndWarehouseId(materialCode, warehouseId);
        java.util.Map<String, BigDecimal> qtyByBatch = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> availByBatch = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> earliestByBatch = new java.util.LinkedHashMap<>();   // v5.63 FIFO 引导
        for (InventoryLedger l : ledgers) {
            if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue; // 无库存批次不展示
            String batch = l.batchNo != null ? l.batchNo : "(无批次)";
            qtyByBatch.merge(batch, l.qty, BigDecimal::add);
            availByBatch.merge(batch, l.availableQty != null ? l.availableQty : BigDecimal.ZERO, BigDecimal::add);
            String date = l.inboundDate == null ? "9999-12-31" : l.inboundDate.toString();
            earliestByBatch.merge(batch, date, (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        qtyByBatch.forEach((batch, qty) -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("batchNo", batch);
            m.put("qty", qty);
            m.put("availableQty", availByBatch.getOrDefault(batch, BigDecimal.ZERO));
            m.put("earliestInbound", earliestByBatch.get(batch));   // v5.63 最早入库日期（FIFO 默认选最早批次）
            result.add(m);
        });
        result.sort(java.util.Comparator.comparing(o -> String.valueOf(o.get("earliestInbound"))));   // FIFO 友好：最早批次排最前
        return result;
    }

    /** 查芃远所有权总库存 */
    public BigDecimal queryTotalOwned(String materialCode) {
        return ledgerRepo.sumQtyByMaterialCode(materialCode);
    }

    /** 查物料的库存异动日志（v5.26：主表+归档表合并，归档后历史异动仍可查看） */
    public List<InventoryMovement> queryMovements(String materialCode) {
        return movementRepo.findByMaterialCodeIncludingArchive(materialCode);
    }

    /** 批次追溯（v4.8：主表+归档表合并，已归档历史批次仍可追溯） */
    public List<InventoryMovement> queryBatchTrace(String materialCode, String batchNo) {
        return movementRepo.findByMaterialCodeAndBatchNoIncludingArchive(materialCode, batchNo);
    }

    /** 低库存预警 */
    public List<InventoryLedger> queryLowStock() {
        return ledgerRepo.findLowStock();
    }

    // ==================== 写操作（全部排队执行） ====================

    /**
     * 采购入库（或直送委外仓入库）
     * @param docType PURCHASE_IN
     * @param docNo 采购单号
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param batchNo 批次
     * @param warehouseId 目标仓库
     * @param locationId 库位ID
     * @param qty 入库数量
     * @param unitPrice 单价
     * @param operator 操作人
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void purchaseInbound(String docType, String docNo, String materialCode,
                                String materialName, String batchNo, String warehouseId,
                                String locationId, BigDecimal qty,
                                BigDecimal unitPrice, String operator) {
        // 所有入库一律自动生成批号；v5.4：销售退货入库传入显式批号（与退货入库单一致），null 时自动生成
        // v5.7：显式批号必须全局唯一（手工录入批号不允许与既有批次重复）
        if (batchNo != null && !batchNo.isBlank() && ledgerRepo.existsByBatchNo(batchNo)) {
            throw new IllegalArgumentException("批号 " + batchNo + " 已存在，批次号不能重复");
        }
        String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : generateBatchNo();
        writeQueue.executeTx(() -> {
            // v6.1.6：显式批号锁内复查（锁外查重后放锁前他人可插入同批号，TOCTOU）
            if (batchNo != null && !batchNo.isBlank() && ledgerRepo.existsByBatchNo(finalBatchNo)) {
                throw new IllegalArgumentException("批号 " + finalBatchNo + " 已存在，批次号不能重复");
            }
            InventoryLedger ledger = getOrCreateLedger(materialCode, finalBatchNo, warehouseId, locationId);

            BigDecimal qtyBefore = ledger.qty;
            BigDecimal amountBefore = ledger.amount;

            ledger.qty = ledger.qty.add(qty);
            ledger.availableQty = ledger.availableQty.add(qty);
            if (materialName != null) ledger.materialName = materialName;
            if (locationId != null) {
                ledger.locationId = locationId;
                locationRepo.findById(Long.valueOf(locationId)).ifPresent(loc -> {
                    ledger.locationName = loc.name;
                    zoneRepo.findById(loc.zoneId).ifPresent(zone -> ledger.zoneName = zone.name);
                });
            }
            if (unitPrice != null) {
                // v6.1（中#13）：同批二次到货按加权平均并入（新额=旧额+量×新价），不再整行按新价重估
                if (qtyBefore.compareTo(BigDecimal.ZERO) > 0 && amountBefore != null
                        && amountBefore.compareTo(BigDecimal.ZERO) > 0) {
                    ledger.amount = amountBefore.add(qty.multiply(unitPrice)).setScale(2, java.math.RoundingMode.HALF_UP);
                    ledger.unitPrice = ledger.amount.divide(ledger.qty, 2, java.math.RoundingMode.HALF_UP);
                } else {
                    ledger.unitPrice = unitPrice;
                    ledger.amount = ledger.qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
                }
            }
            // v6.1（中#13）：入库日期仅首次落，二次到货不回写 now——否则老批次被排成 FIFO 最新，先入先出失真
            if (ledger.inboundDate == null) {
                ledger.inboundDate = LocalDate.now();
                materialRepo.findByCode(materialCode).ifPresent(mat -> {
                    if (mat.shelfLifeDays != null && mat.shelfLifeDays > 0 && ledger.expiryDate == null) {
                        ledger.expiryDate = ledger.inboundDate.plusDays(mat.shelfLifeDays);
                    }
                });
            }
            ledger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(ledger);

            // 写异动日志
            saveMovement(docType, docNo, materialCode, finalBatchNo, warehouseId, ledger.locationId,
                    "IN", qty, qtyBefore, ledger.qty, ledger.ownershipType, operator, null);

            log.info("采购入库: {} {} 批次{} +{} -> 仓{} 库位{} 余额{}", docNo, materialCode, finalBatchNo, qty, warehouseId, locationId, ledger.qty);
        });
    }

    /**
     * 采购入库（带生产日期/过期日期/质检信息，质检合格后调用）
     * @param produceDate 生产日期（来自质检单，可空）
     * @param expiryDate  过期日期（来自质检单计算，可空；为空时按物料质保期推算）
     * @param qcInfo      质检信息（status/inspectionNo/result/inspector/date），可空表示无质检
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void purchaseInboundWithDate(String docType, String docNo, String materialCode,
                                        String materialName, String batchNo, String warehouseId,
                                        String locationId, BigDecimal qty,
                                        BigDecimal unitPrice, LocalDate produceDate,
                                        LocalDate expiryDate, String operator,
                                        QcInfo qcInfo) {
        purchaseInboundWithDate(docType, docNo, materialCode, materialName, batchNo, warehouseId,
                locationId, qty, unitPrice, produceDate, expiryDate, operator, qcInfo, false);
    }

    /**
     * v5.35：skipBatchUniqueCheck=true 时跳过批号全局查重（油尾退回带原批号重新入库）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void purchaseInboundWithDate(String docType, String docNo, String materialCode,
                                        String materialName, String batchNo, String warehouseId,
                                        String locationId, BigDecimal qty,
                                        BigDecimal unitPrice, LocalDate produceDate,
                                        LocalDate expiryDate, String operator,
                                        QcInfo qcInfo, boolean skipBatchUniqueCheck) {
        // v6.1 防呆：入库数量必须为正（负数入库=直接减库存；所有入库入口的最终实现在此收口）
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }
        // 入库批号：v5.7 支持显式批号（单据自动生成后传入，保证单据与台账一致）；null 时自动生成
        // v5.7：显式批号必须全局唯一；v5.35 油尾退回带原批号时可跳过
        if (!skipBatchUniqueCheck && batchNo != null && !batchNo.isBlank() && ledgerRepo.existsByBatchNo(batchNo)) {
            throw new IllegalArgumentException("批号 " + batchNo + " 已存在，批次号不能重复");
        }
        // v5.38 入库防线（隔离仓分库化后的后端收口）：隔离分库的库位只接受特定通道写入——
        // 隔离区仅限质检不合格入库（qcStatus=REJECT），油尾区仅限油尾退回入库（docType=TAILING_IN）；
        // 其余任何正常入库（含前端漏过滤误选）一律拒绝，从此不依赖前端下拉过滤
        if (locationId != null && !locationId.isBlank()) {
            boolean unq = isUnqualifiedLocation(locationId);
            boolean tail = isTailingLocation(locationId);
            if (unq && !(qcInfo != null && "REJECT".equals(qcInfo.status))) {
                throw new IllegalArgumentException("不合格品库不接受正常入库，只有质检判定不合格或过期自动隔离的货物才会进入");
            }
            if (tail && !"TAILING_IN".equals(docType)) {
                throw new IllegalArgumentException("油尾库不接受正常入库，只有油尾退回单确认后才会进入");
            }
        }
        String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : generateBatchNo();
        writeQueue.executeTx(() -> {
            // v6.1.6：显式批号锁内复查（同 purchaseInbound）
            if (!skipBatchUniqueCheck && batchNo != null && !batchNo.isBlank() && ledgerRepo.existsByBatchNo(finalBatchNo)) {
                throw new IllegalArgumentException("批号 " + finalBatchNo + " 已存在，批次号不能重复");
            }
            InventoryLedger ledger = getOrCreateLedger(materialCode, finalBatchNo, warehouseId, locationId);

            BigDecimal qtyBefore = ledger.qty;

            ledger.qty = ledger.qty.add(qty);
            ledger.availableQty = ledger.availableQty.add(qty);
            if (materialName != null) ledger.materialName = materialName;
            if (locationId != null) {
                ledger.locationId = locationId;
                locationRepo.findById(Long.valueOf(locationId)).ifPresent(loc -> {
                    ledger.locationName = loc.name;
                    zoneRepo.findById(loc.zoneId).ifPresent(zone -> ledger.zoneName = zone.name);
                });
            }
            if (unitPrice != null) {
                // v6.1（中#13）：同批二次到货按加权平均并入（新额=旧额+量×新价），不再整行按新价重估
                if (qtyBefore.compareTo(BigDecimal.ZERO) > 0 && ledger.amount != null
                        && ledger.amount.compareTo(BigDecimal.ZERO) > 0) {
                    ledger.amount = ledger.amount.add(qty.multiply(unitPrice)).setScale(2, java.math.RoundingMode.HALF_UP);
                    ledger.unitPrice = ledger.amount.divide(ledger.qty, 2, java.math.RoundingMode.HALF_UP);
                } else {
                    ledger.unitPrice = unitPrice;
                    ledger.amount = ledger.qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
                }
            }
            // v6.1（中#13）：入库日期仅首次落，二次到货不回写 now——否则老批次被排成 FIFO 最新，先入先出失真
            if (ledger.inboundDate == null) ledger.inboundDate = LocalDate.now();
            // 生产日期：优先用入参（来自质检单），覆盖历史 null
            if (produceDate != null) {
                ledger.produceDate = produceDate;
            }
            // 过期日期：优先用入参（质检单基于生产日期算出）；无入参时仅在行尚无过期日时按质保期推算（不刷新老批次的到期日）
            if (expiryDate != null) {
                ledger.expiryDate = expiryDate;
            } else if (ledger.expiryDate == null) {
                materialRepo.findByCode(materialCode).ifPresent(mat -> {
                    if (mat.shelfLifeDays != null && mat.shelfLifeDays > 0) {
                        ledger.expiryDate = ledger.inboundDate.plusDays(mat.shelfLifeDays);
                    }
                });
            }
            // 质检信息落库（入库即合格）
            if (qcInfo != null) {
                ledger.qcStatus = qcInfo.status;
                ledger.qcInspectionNo = qcInfo.inspectionNo;
                ledger.qcResult = qcInfo.result;
                ledger.qcInspector = qcInfo.inspector;
                ledger.qcDate = qcInfo.date;
            }
            ledger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(ledger);

            saveMovement(docType, docNo, materialCode, finalBatchNo, warehouseId, ledger.locationId,
                    "IN", qty, qtyBefore, ledger.qty, ledger.ownershipType, operator, null);

            log.info("入库(质检合格): {} {} 批次{} +{} -> 仓{} 库位{} 余额{} 质检={}", docNo, materialCode, finalBatchNo, qty, warehouseId, locationId, ledger.qty, qcInfo != null ? qcInfo.status : "无");
        });
    }

    /**
     * 销售出库 / 委外耗用出库
     * @param docType SALES_OUT / OUTSOURCE_CONSUME
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void outbound(String docType, String docNo, String materialCode,
                         String batchNo, String warehouseId, BigDecimal qty,
                         String operator) {
        outbound(docType, docNo, materialCode, batchNo, warehouseId, null, qty, operator);
    }

    /** 无库位出库 + 过期放行标记（v5.23） */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void outbound(String docType, String docNo, String materialCode,
                         String batchNo, String warehouseId, BigDecimal qty,
                         String operator, boolean allowExpired) {
        outbound(docType, docNo, materialCode, batchNo, warehouseId, null, qty, operator, allowExpired);
    }

    /**
     * 出库（指定库位，库位为空时匹配无库位的台账行）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void outbound(String docType, String docNo, String materialCode,
                         String batchNo, String warehouseId, String locationId, BigDecimal qty,
                         String operator) {
        outbound(docType, docNo, materialCode, batchNo, warehouseId, locationId, qty, operator, false);
    }

    /**
     * 出库（v5.23：allowExpired 控制过期批次是否放行）
     * 正常业务出库（销售/生产领料/委外发料等）默认禁止过期批次出库；
     * 仅其他出库 报废(SCRAP)/样品(SAMPLE)/退货(RETURN) 处理通道传 true 放行，
     * 否则过期物料永远无法清出库存。
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void outbound(String docType, String docNo, String materialCode,
                         String batchNo, String warehouseId, String locationId, BigDecimal qty,
                         String operator, boolean allowExpired) {
        // v6.1 防呆：出库数量必须为正（负数出库=库存反增；所有出库入口的最终实现在此收口）
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("出库数量必须大于 0");
        }
        // v5.30/5.35（v5.38 改库位级）：隔离分库出库拦截——正常业务出库一律禁止，
        // 仅「其他出库」（报废/退货处理通道）放行不合格区；油尾区另放行生产领料（制漆配方消化）。
        // 拦截锚点为台账行库位（隔离货必有库位；无库位匹配路径不可能是隔离行，FIFO 路径由过滤兜底）
        if (batchNo == null || batchNo.isBlank()) {
            throw new IllegalArgumentException("出库必须指定批号（物料操作精确到批次铁律）: " + materialCode);
        }
        // v6.8：返工领料 REWORK_OUT 同步放行不合格库（与 zoneAllowedByDocType 同口径）
        boolean unqBlocked = isUnqualifiedLocation(locationId) && !"OTHER_OUT".equals(docType) && !"REWORK_OUT".equals(docType);
        boolean tailBlocked = isTailingLocation(locationId)
                && !"PRODUCTION_OUT".equals(docType) && !"OTHER_OUT".equals(docType);
        if (unqBlocked) {
            throw new IllegalArgumentException("不合格品库的物料不可用于正常业务出库，请走「其他出库-报废/退货」处理");
        }
        if (tailBlocked) {
            throw new IllegalArgumentException("油尾库的物料已加稀料，不可再销售，仅可生产领料（制漆配方消化）或「其他出库-报废」处理");
        }
        writeQueue.executeTx(() -> {
            if (locationId != null) {
                // 指定库位：精确匹配单行台账（原逻辑）
                InventoryLedger ledger = getOrCreateLedger(materialCode, batchNo, warehouseId, locationId);
                checkExpired(ledger, allowExpired);
                if (ledger.qty.compareTo(qty) < 0) {
                    throw new IllegalArgumentException(String.format(
                            "库存不足: %s 批次%s 仓%s 库位%s 库存%.3f 需要%.3f",
                            materialCode, batchNo, warehouseId, locationId, ledger.qty, qty));
                }
                deductSingle(ledger, qty, docType, docNo, warehouseId, operator);
                return;
            }
            // 未指定库位：按物料+批次+仓库查所有库位行，按入库时间升序 FIFO 扣减
            List<InventoryLedger> rows = ledgerRepo
                    .findByMaterialCodeAndBatchNoAndWarehouseIdOrderByInboundDateAscIdAsc(
                            materialCode, batchNo, warehouseId);
            // 仅保留有库存的行；v6.1（中#10）：同 outboundFifo 口径排除隔离分库行——
            // 此前该分支未过滤 zoneType，销售出库等正常业务可把不合格区/油尾区的货扣走
            java.util.Map<Long, String> locZone = locationZoneTypes();
            List<InventoryLedger> available = rows.stream()
                    .filter(l -> l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0)
                    .filter(l -> zoneAllowedByDocType(zoneTypeOf(locZone, l.locationId), docType))
                    .collect(Collectors.toList());
            // v5.23：扣减前逐行校验过期（任一库位行过期即拦截，避免过期料混入正常出库）
            for (InventoryLedger ledger : available) {
                checkExpired(ledger, allowExpired);
            }
            BigDecimal total = available.stream()
                    .map(l -> l.qty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(qty) < 0) {
                throw new IllegalArgumentException(String.format(
                        "库存不足: %s 批次%s 仓%s 库存%.3f 需要%.3f",
                        materialCode, batchNo, warehouseId, total, qty));
            }
            BigDecimal remaining = qty;
            for (InventoryLedger ledger : available) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal take = ledger.qty.compareTo(remaining) <= 0 ? ledger.qty : remaining;
                deductSingle(ledger, take, docType, docNo, warehouseId, operator);
                remaining = remaining.subtract(take);
            }
        });
    }

    /**
     * 全仓先进先出扣减（生产订单自动出库用）：
     * 不限仓库/库位，按该物料该批号所有台账行按入库日期升序 FIFO 扣减。
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void outboundFifo(String docType, String docNo, String materialCode,
                             String batchNo, BigDecimal qty, String operator, boolean allowExpired) {
        writeQueue.executeTx(() -> {
            List<InventoryLedger> rows = ledgerRepo.findByMaterialCodeAndBatchNo(materialCode, batchNo);
            java.util.Map<Long, String> locZone = locationZoneTypes();
            List<InventoryLedger> available = rows.stream()
                    .filter(l -> l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0)
                    // v5.30/5.35（v5.38 改库位级）：隔离分库不参与正常业务出库——
                    // 隔离区仅其他出库（报废/退货通道）可扣；油尾区另放行生产领料（制漆配方消化），与 outbound 拦截同口径
                    .filter(l -> zoneAllowedByDocType(zoneTypeOf(locZone, l.locationId), docType))
                    .sorted(java.util.Comparator
                            .comparing((InventoryLedger l) -> l.inboundDate == null ? LocalDate.EPOCH : l.inboundDate)
                            .thenComparing(l -> l.id == null ? 0L : l.id))
                    .collect(Collectors.toList());
            // 扣减前逐行校验过期（任一库位行过期即拦截，避免过期料混入正常出库）
            for (InventoryLedger ledger : available) {
                checkExpired(ledger, allowExpired);
            }
            BigDecimal total = available.stream()
                    .map(l -> l.qty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(qty) < 0) {
                throw new IllegalArgumentException(String.format(
                        "库存不足: %s 批次%s 全部仓 库存%.3f 需要%.3f",
                        materialCode, batchNo, total, qty));
            }
            BigDecimal remaining = qty;
            for (InventoryLedger ledger : available) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal take = ledger.qty.compareTo(remaining) <= 0 ? ledger.qty : remaining;
                deductSingle(ledger, take, docType, docNo, ledger.warehouseId, operator);
                remaining = remaining.subtract(take);
            }
        });
    }

    /** 库位扣减明细（生产订单自动出库按库位拆行记录用） */
    public static class LocationDeduct {
        public String warehouseId;
        public String locationId;
        public String zoneName;
        public String locationName;
        public BigDecimal qty;
        public BigDecimal price;
    }

    /**
     * 全仓先进先出扣减并返回每个库位的扣减明细（生产订单自动出库按库位拆行记录用）：
     * 不限仓库/库位，按该物料该批号所有台账行按入库日期升序 FIFO 扣减，返回每个库位实际扣了多少。
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<LocationDeduct> outboundFifoWithDetail(String docType, String docNo, String materialCode,
                                                       String batchNo, BigDecimal qty, String operator, boolean allowExpired) {
        return outboundFifoWithDetail(docType, docNo, materialCode, batchNo, qty, operator, allowExpired, null);
    }

    /**
     * 带仓库白名单的全仓先进先出扣减（生产订单只从自有仓、委外只从对应代工厂仓）：
     * allowedWarehouseIds 为 null/空时不限仓库；非空时只扣减这些仓库的台账行。
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public List<LocationDeduct> outboundFifoWithDetail(String docType, String docNo, String materialCode,
                                                       String batchNo, BigDecimal qty, String operator, boolean allowExpired,
                                                       java.util.Set<String> allowedWarehouseIds) {
        return writeQueue.executeTx(() -> {
            List<InventoryLedger> rows = ledgerRepo.findByMaterialCodeAndBatchNo(materialCode, batchNo);
            java.util.Map<Long, String> locZone = locationZoneTypes();
            List<InventoryLedger> available = rows.stream()
                    .filter(l -> l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0)
                    .filter(l -> allowedWarehouseIds == null || allowedWarehouseIds.isEmpty()
                            || (l.warehouseId != null && allowedWarehouseIds.contains(l.warehouseId)))
                    // v5.30/5.35（v5.38 改库位级）：隔离分库不参与正常业务出库（与白名单双保险）——
                    // 油尾区放行生产领料（制漆配方消化），与 outbound 拦截同口径
                    .filter(l -> zoneAllowedByDocType(zoneTypeOf(locZone, l.locationId), docType))
                    .sorted(java.util.Comparator
                            .comparing((InventoryLedger l) -> l.inboundDate == null ? LocalDate.EPOCH : l.inboundDate)
                            .thenComparing(l -> l.id == null ? 0L : l.id))
                    .collect(Collectors.toList());
            for (InventoryLedger ledger : available) {
                checkExpired(ledger, allowExpired);
            }
            BigDecimal total = available.stream().map(l -> l.qty).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(qty) < 0) {
                throw new IllegalArgumentException(String.format(
                        "库存不足: %s 批次%s 库存%.3f 需要%.3f%s",
                        materialCode, batchNo, total, qty,
                        allowedWarehouseIds != null && !allowedWarehouseIds.isEmpty() ? "（仅限定仓库内）" : ""));
            }
            List<LocationDeduct> details = new java.util.ArrayList<>();
            BigDecimal remaining = qty;
            for (InventoryLedger ledger : available) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal take = ledger.qty.compareTo(remaining) <= 0 ? ledger.qty : remaining;
                deductSingle(ledger, take, docType, docNo, ledger.warehouseId, operator);
                LocationDeduct d = new LocationDeduct();
                d.warehouseId = ledger.warehouseId;
                d.locationId = ledger.locationId;
                d.zoneName = ledger.zoneName;
                d.locationName = ledger.locationName;
                d.qty = take;
                d.price = ledger.unitPrice;
                details.add(d);
                remaining = remaining.subtract(take);
            }
            return details;
        });
    }
    /**
     * v5.23 过期批次出库拦截：过期批次禁止正常业务出库（allowExpired=true 时放行，如报废/退货处理通道）
     */
    private void checkExpired(InventoryLedger ledger, boolean allowExpired) {
        if (allowExpired) return;
        if (ledger.expiryDate != null && ledger.expiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(String.format(
                    "批次 %s 已于 %s 过期，禁止出库（过期物料请走「其他出库-报废」处理）",
                    ledger.batchNo, ledger.expiryDate));
        }
    }

    /**
     * 从单条台账行扣减指定数量，并写入异动日志
     */
    private void deductSingle(InventoryLedger ledger, BigDecimal qty, String docType, String docNo,
                              String warehouseId, String operator) {
        BigDecimal qtyBefore = ledger.qty;
        ledger.qty = ledger.qty.subtract(qty);
        ledger.availableQty = (ledger.availableQty != null ? ledger.availableQty : BigDecimal.ZERO).subtract(qty);
        ledger.amount = ledger.qty.multiply(
                ledger.unitPrice != null ? ledger.unitPrice : BigDecimal.ZERO);
        ledger.lastUpdateTime = LocalDateTime.now();
        ledgerRepo.save(ledger);

        saveMovement(docType, docNo, ledger.materialCode, ledger.batchNo, warehouseId, ledger.locationId,
                "OUT", qty, qtyBefore, ledger.qty, ledger.ownershipType, operator,
                ledger.locationId != null ? "库位:" + ledger.locationId : null);

        log.info("库存出库: {} {} -{} {} -> 仓{} 库位{} 余额{}",
                docNo, ledger.materialCode, qty, ledger.unit, warehouseId, ledger.locationId, ledger.qty);
    }

    /**
     * 生产领料出库，创建独立单据并扣减库存
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param batchNo 批次（可为null）
     * @param warehouseId 出库仓库
     * @param qty 出库数量
     * @param operator 操作人
     * @return 生产出库单据
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public ProductionOutbound productionOutbound(String materialCode, String materialName,
                                                  String batchNo, String warehouseId,
                                                  BigDecimal qty, String operator) {
        // v5.24：单号生成+单据保存+库存扣减整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = prodOutRepo.findMaxSeq("PROD-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("PROD-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""),
                    (maxSeq == null ? 0 : maxSeq) + 1);

            // 执行库存扣减（WriteQueue 可重入，不会死锁）
            outbound("PRODUCTION_OUT", docNo, materialCode, batchNo, warehouseId, qty, operator);

            // 创建独立单据
            ProductionOutbound doc = new ProductionOutbound();
            doc.docNo = docNo;
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.qty = qty;
            doc.status = "CONFIRMED";
            doc.createdBy = operator;
            doc.createTime = LocalDateTime.now();
            prodOutRepo.save(doc);

            log.info("生产出库单据: {} 物料{} 数量{} 仓{}", docNo, materialCode, qty, warehouseId);
            return doc;
        });
    }

    /** 查询生产出库单据列表 */
    public List<ProductionOutbound> queryProductionOutbounds() {
        return prodOutRepo.findByOrderByCreateTimeDesc();
    }

    /**
     * 其他出库，创建独立单据并扣减库存
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param batchNo 批次（可为null）
     * @param warehouseId 出库仓库
     * @param qty 出库数量
     * @param reason 出库原因
     * @param remark 备注
     * @param operator 操作人
     * @return 其他出库单据
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public OtherOutbound otherOutbound(String materialCode, String materialName,
                                       String batchNo, String warehouseId,
                                       BigDecimal qty, String reason,
                                       String remark, String operator) {
        // v5.24：单号生成+单据保存+库存扣减整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = otherOutRepo.findMaxSeq("OTHER-OUT-" + LocalDate.now().toString().replace("-", "") + "-%");
            String docNo = String.format("OTHER-OUT-%s-%04d", LocalDate.now().toString().replace("-", ""),
                    (maxSeq == null ? 0 : maxSeq) + 1);

            // 执行库存扣减（v5.23：报废/样品/退货放行过期批次，其他原因禁止）
            boolean allowExpired = reason != null
                    && (reason.equals("SCRAP") || reason.equals("SAMPLE") || reason.equals("RETURN"));
            outbound("OTHER_OUT", docNo, materialCode, batchNo, warehouseId, qty, operator, allowExpired);

            // 创建独立单据
            OtherOutbound doc = new OtherOutbound();
            doc.docNo = docNo;
            doc.materialCode = materialCode;
            doc.materialName = materialName;
            doc.batchNo = batchNo;
            doc.warehouseId = warehouseId;
            doc.qty = qty;
            doc.reason = reason;
            doc.status = "CONFIRMED";
            doc.createdBy = operator;
            doc.remark = remark;
            doc.createTime = LocalDateTime.now();
            otherOutRepo.save(doc);

            log.info("其他出库单据: {} 物料{} 数量{} 仓{} 原因{}", docNo, materialCode, qty, warehouseId, reason);
            return doc;
        });
    }

    /** 查询其他出库单据列表 */
    public List<OtherOutbound> queryOtherOutbounds() {
        return otherOutRepo.findByOrderByCreateTimeDesc();
    }

    /**
     * 委外材料出库（仅变更物理仓，所有权总库存不减少）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void outsourceMaterialTransfer(String docNo, String materialCode,
                                          String batchNo, String fromWarehouseId,
                                          String toWarehouseId, BigDecimal qty,
                                          String operator) {
        writeQueue.executeTx(() -> {
            // 1. 调出仓减少
            InventoryLedger fromLedger = getOrCreateLedger(materialCode, batchNo, fromWarehouseId, null);
            // v5.23：委外调拨属正常业务，禁止调拨过期批次
            checkExpired(fromLedger, false);
            // 校验改用 qty（实物库存），避免 availableQty 历史脏数据导致无法调拨
            if (fromLedger.qty.compareTo(qty) < 0) {
                throw new IllegalArgumentException(String.format(
                        "调出仓库存不足: %s 仓%s 库存%.3f 需要%.3f",
                        materialCode, fromWarehouseId, fromLedger.qty, qty));
            }

            BigDecimal fromBefore = fromLedger.qty;
            fromLedger.qty = fromLedger.qty.subtract(qty);
            fromLedger.availableQty = fromLedger.availableQty.subtract(qty);
            fromLedger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(fromLedger);

            saveMovement("OUTSOURCE_OUT", docNo, materialCode, batchNo, fromWarehouseId, fromLedger.locationId,
                    "OUT", qty, fromBefore, fromLedger.qty, fromLedger.ownershipType, operator,
                    "调往 " + toWarehouseId);

            // 2. 调入仓增加（所有权不变，仍为 PENGYUAN）
            InventoryLedger toLedger = getOrCreateLedger(materialCode, batchNo, toWarehouseId, defaultLocationOf(toWarehouseId));
            // 确保调入仓的所有权标记正确
            toLedger.ownershipType = "PENGYUAN";

            BigDecimal toBefore = toLedger.qty;
            toLedger.qty = toLedger.qty.add(qty);
            toLedger.availableQty = toLedger.availableQty.add(qty);
            // v6.1.3：金额随量结转（量×调出方行单价），目标行不再被源行价格整行重估；
            // 目标行原价保留加权并入，调出方等额扣减
            java.math.BigDecimal trAmt = fromLedger.unitPrice != null && fromLedger.unitPrice.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? qty.multiply(fromLedger.unitPrice).setScale(2, java.math.RoundingMode.HALF_UP) : null;
            if (trAmt != null) {
                if (fromLedger.amount != null) {
                    fromLedger.amount = fromLedger.amount.subtract(trAmt);
                    if (fromLedger.amount.compareTo(java.math.BigDecimal.ZERO) < 0) fromLedger.amount = java.math.BigDecimal.ZERO;
                }
                toLedger.amount = (toLedger.amount == null ? java.math.BigDecimal.ZERO : toLedger.amount).add(trAmt);
                if (toLedger.qty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    toLedger.unitPrice = toLedger.amount.divide(toLedger.qty, 2, java.math.RoundingMode.HALF_UP);
                }
            }
            toLedger.unit = fromLedger.unit;
            toLedger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(fromLedger);
            ledgerRepo.save(toLedger);

            saveMovement("OUTSOURCE_OUT", docNo, materialCode, batchNo, toWarehouseId, toLedger.locationId,
                    "IN", qty, toBefore, toLedger.qty, toLedger.ownershipType, operator,
                    "来自 " + fromWarehouseId);

            log.info("委外材料调拨: {} {} {} -> {} 数量{} 芃远总库存不变",
                    docNo, materialCode, fromWarehouseId, toWarehouseId, qty);
        });
    }

    /**
     * 跨仓调拨（通用）
     * 委外仓之间调拨本期 P1，自有仓↔委外仓 P0
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void transfer(String docNo, String materialCode, String batchNo,
                         String fromWarehouseId, String toWarehouseId,
                         BigDecimal qty, String operator) {
        // v5.38：隔离仓改分库后删除原仓库级调拨拦截——
        // 调拨是仓库级操作，只匹配「无库位」台账行（getOrCreateLedger(..., null)），
        // 隔离货始终带隔离库位（由质检不合格入库/油尾退回单写入），不会被调拨触及，天然安全。
        writeQueue.executeTx(() -> {
            // 调出
            InventoryLedger fromLedger = getOrCreateLedger(materialCode, batchNo, fromWarehouseId, defaultLocationOf(fromWarehouseId));
            // v5.23：跨仓调拨属正常业务，禁止调拨过期批次
            checkExpired(fromLedger, false);
            // 校验改用 qty（实物库存），避免 availableQty 历史脏数据导致无法调拨
            if (fromLedger.qty.compareTo(qty) < 0) {
                throw new IllegalArgumentException("调出仓库存不足");
            }
            BigDecimal fromBefore = fromLedger.qty;
            fromLedger.qty = fromLedger.qty.subtract(qty);
            fromLedger.availableQty = fromLedger.availableQty.subtract(qty);
            fromLedger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(fromLedger);

            saveMovement("TRANSFER", docNo, materialCode, batchNo, fromWarehouseId, fromLedger.locationId,
                    "OUT", qty, fromBefore, fromLedger.qty, fromLedger.ownershipType, operator,
                    "调往 " + toWarehouseId);

            // 调入
            InventoryLedger toLedger = getOrCreateLedger(materialCode, batchNo, toWarehouseId, defaultLocationOf(toWarehouseId));
            BigDecimal toBefore = toLedger.qty;
            toLedger.qty = toLedger.qty.add(qty);
            toLedger.availableQty = toLedger.availableQty.add(qty);
            toLedger.ownershipType = fromLedger.ownershipType;
            toLedger.unit = fromLedger.unit;
            // v6.1.3：金额随量结转（量×调出方行单价），目标行加权并入，不再被源行价格整行重估
            java.math.BigDecimal mvAmt = fromLedger.unitPrice != null && fromLedger.unitPrice.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? qty.multiply(fromLedger.unitPrice).setScale(2, java.math.RoundingMode.HALF_UP) : null;
            if (mvAmt != null) {
                if (fromLedger.amount != null) {
                    fromLedger.amount = fromLedger.amount.subtract(mvAmt);
                    if (fromLedger.amount.compareTo(java.math.BigDecimal.ZERO) < 0) fromLedger.amount = java.math.BigDecimal.ZERO;
                }
                toLedger.amount = (toLedger.amount == null ? java.math.BigDecimal.ZERO : toLedger.amount).add(mvAmt);
                if (toLedger.qty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    toLedger.unitPrice = toLedger.amount.divide(toLedger.qty, 2, java.math.RoundingMode.HALF_UP);
                }
            } else if (toLedger.unitPrice == null) {
                toLedger.unitPrice = fromLedger.unitPrice;
            }
            toLedger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(fromLedger);
            ledgerRepo.save(toLedger);

            saveMovement("TRANSFER", docNo, materialCode, batchNo, toWarehouseId, toLedger.locationId,
                    "IN", qty, toBefore, toLedger.qty, toLedger.ownershipType, operator,
                    "来自 " + fromWarehouseId);

            log.info("跨仓调拨: {} {} {} -> {} 数量{}", docNo, materialCode, fromWarehouseId, toWarehouseId, qty);
        });
    }

    // ==================== 内部方法 ====================

    /** v5.46：仓库默认库位（首个普通分库的首个启用库位）——期初导入/预留调拨等无显式库位通道的兜底落位 */
    private String defaultLocationOf(String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank()) return null;
        try {
            Long whId = Long.valueOf(warehouseId);
            return zoneRepo.findByWarehouseIdAndEnabledTrueOrderBySortOrderAsc(whId).stream()
                    .filter(z -> z.zoneType == null || z.zoneType.isBlank())
                    .findFirst()
                    .flatMap(z -> locationRepo.findByZoneIdAndEnabledTrueOrderBySortOrderAsc(z.id).stream().findFirst())
                    .map(l -> String.valueOf(l.id))
                    .orElseThrow(() -> new IllegalArgumentException("仓库 " + warehouseId + " 无可用普通库位，请先在仓库管理中建立分库和库位"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** v5.38.3：判断某库位是否属于不合格品分库（原材料/半成品/成品三类不合格品库，按物料大类分库） */
    private boolean isUnqualifiedLocation(String locationId) {
        return isSpecialLocation(locationId, "UNQUALIFIED_RAW")
                || isSpecialLocation(locationId, "UNQUALIFIED_SEMI")
                || isSpecialLocation(locationId, "UNQUALIFIED_FIN");
    }

    /** v5.38：判断某库位是否属于油尾库分库（聚酯 TAILING / 氟碳 TAILING_FC，v5.38.2 按体系分库） */
    private boolean isTailingLocation(String locationId) {
        return isSpecialLocation(locationId, "TAILING") || isSpecialLocation(locationId, "TAILING_FC");
    }

    /** 隔离判断锚点：库位 → 分库 → zone_type（无库位的台账行不属于任何分库，恒为普通） */
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

    /** v5.55：库位 → 分库类型 映射一次加载（主数据量小；替代 FIFO 逐行 2×findById×2 的 N+1） */
    private java.util.Map<Long, String> locationZoneTypes() {
        java.util.Map<Long, Long> zoneIdByLoc = new java.util.HashMap<>();
        for (var l : locationRepo.findAll()) zoneIdByLoc.put(l.id, l.zoneId);
        java.util.Map<Long, String> zoneTypeByZone = new java.util.HashMap<>();
        for (var z : zoneRepo.findAll()) zoneTypeByZone.put(z.id, z.zoneType);
        java.util.Map<Long, String> m = new java.util.HashMap<>();
        zoneIdByLoc.forEach((locId, zoneId) -> {
            if (zoneId != null) m.put(locId, zoneTypeByZone.get(zoneId));
        });
        return m;
    }

    private static String zoneTypeOf(java.util.Map<Long, String> locZone, String locationId) {
        if (locationId == null || locationId.isBlank()) return null;
        try {
            return locZone.get(Long.valueOf(locationId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 隔离/油尾分库在当前单据类型下是否放行（锚点=分库类型；与 isUnqualified/isTailingLocation 同口径） */
    private static boolean zoneAllowedByDocType(String zoneType, String docType) {
        if (zoneType == null) return true;
        switch (zoneType) {
            case "UNQUALIFIED_RAW":
            case "UNQUALIFIED_SEMI":
            case "UNQUALIFIED_FIN":
            // v6.8：REWORK_OUT 返工领料——不合格品领出重新加工（其余仍只能报废/退货走 OTHER_OUT）
                return "OTHER_OUT".equals(docType) || "REWORK_OUT".equals(docType);   // v6.8 返工领料放行
            case "TAILING":
            case "TAILING_FC":
                return "PRODUCTION_OUT".equals(docType) || "OTHER_OUT".equals(docType);
            default:
                return true;
        }
    }

    /**
     * v5.46 铁律（用户口径）：物料操作必须精确到批号和库位——batchNo / locationId 为空直接拒绝，
     * 从源头杜绝无批次、无库位台账行的产生。历史遗留通道（期初导入、预留调拨）由调用方先落默认库位。
     */
    private InventoryLedger getOrCreateLedger(String materialCode, String batchNo, String warehouseId, String locationId) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new IllegalArgumentException("物料 " + materialCode + " 操作必须指定批号（精确到批次铁律）");
        }
        if (locationId == null || locationId.isBlank()) {
            throw new IllegalArgumentException("物料 " + materialCode + " 批次 " + batchNo + " 操作必须指定库位（精确到库位铁律）");
        }
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
                    // 防御性修复：DB中可能存在NULL字段
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
                    l.inboundDate = LocalDate.now();
                    return l;
                });
    }

    /**
     * 自动生成批号：B + yyyyMMdd + "-" + 3位序号
     * 例如: B20260801-001
     * v5.7 批号全局防重：
     * ① 跨日/服务重启后从库存台账+异动表恢复当日最大序号（重启不再从头计数，避免重复）；
     * ② 生成后查重兜底（existsByBatchNo），极端情况下继续递增，保证任何情况都不重复。
     */
    private synchronized String generateBatchNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        if (!today.equals(lastBatchDate)) {
            // 从数据库恢复当日已用最大序号（台账 + 异动合并，含重启前产生的批号）
            lastBatchDate = today;
            String prefix = "B" + today + "-%";
            Integer ledgerMax = ledgerRepo.findMaxBatchSeq(prefix);
            Integer moveMax = movementRepo.findMaxBatchSeq(prefix);
            batchSeq.set(Math.max(ledgerMax == null ? 0 : ledgerMax, moveMax == null ? 0 : moveMax));
        }
        int seq = batchSeq.incrementAndGet();
        String batch = String.format("B%s-%03d", today, seq);
        // 兜底防重：即使极端情况下序号冲突（手工数据/多实例），继续递增直到唯一
        while (ledgerRepo.existsByBatchNo(batch)) {
            batch = String.format("B%s-%03d", today, ++seq);
        }
        batchSeq.set(seq);
        return batch;
    }

    /** v5.4：对外暴露批号生成器（销售退货入库需在台账与退货入库单上记录同一批号） */
    public synchronized String nextBatchNo() {
        return generateBatchNo();
    }

    /**
     * 期初导入入库（每行一个批次）：写台账 + OPENING 异动流水。
     * 与采购入库的差异：qcStatus 直接记 PASS（期初即合格）、无质检单、过期日期可显式指定。
     * v6.1.5：本方法 executeTx（锁内包事务）；被 importOpening 的整批 executeTx 调用时锁重入+事务加入，
     * 整批要么全进要么全不进的设计不变。
     */
    public void openingInbound(String docNo, String materialCode, String materialName,
                               String warehouseId, String batchNo, BigDecimal qty,
                               BigDecimal unitPrice, LocalDate produceDate, LocalDate expiryDate,
                               String operator) {
        openingInbound(docNo, materialCode, materialName, warehouseId, batchNo, qty,
                unitPrice, produceDate, expiryDate, operator, null, null);
    }

    /**
     * 期初导入入库（批量导入版）：material / defaultLocationId 为调用方预加载的数据
     * （批量导入场景按物料/仓库缓存一次，替代逐行回查物料档案与仓库默认库位），null 时内部回查，行为与单行版一致。
     */
    public void openingInbound(String docNo, String materialCode, String materialName,
                               String warehouseId, String batchNo, BigDecimal qty,
                               BigDecimal unitPrice, LocalDate produceDate, LocalDate expiryDate,
                               String operator, Material material, String defaultLocationId) {
        // 显式批号全局防重（含文件内前面行刚插入的批次——同事务内可见）
        if (batchNo != null && !batchNo.isBlank() && ledgerRepo.existsByBatchNo(batchNo)) {
            throw new IllegalArgumentException("批号 " + batchNo + " 已存在，批次号不能重复");
        }
        String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : generateBatchNo();
        writeQueue.executeTx(() -> {
            InventoryLedger ledger = getOrCreateLedger(materialCode, finalBatchNo, warehouseId,
                    defaultLocationId != null ? defaultLocationId : defaultLocationOf(warehouseId));
            BigDecimal qtyBefore = ledger.qty;

            ledger.qty = ledger.qty.add(qty);
            ledger.availableQty = ledger.availableQty.add(qty);
            if (materialName != null) ledger.materialName = materialName;
            // 注：台账 unit 列与现有所有入库流程一致不赋值（全库台账 unit 均为空，展示按物料档案）
            if (unitPrice != null) {
                // v6.1.3：期初导入同批合并行按加权平均并入（与主入库路径同口径），不再整行覆盖重估
                if (qtyBefore.compareTo(java.math.BigDecimal.ZERO) > 0 && ledger.amount != null
                        && ledger.amount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    ledger.amount = ledger.amount.add(qty.multiply(unitPrice)).setScale(2, java.math.RoundingMode.HALF_UP);
                    ledger.unitPrice = ledger.amount.divide(ledger.qty, 2, java.math.RoundingMode.HALF_UP);
                } else {
                    ledger.unitPrice = unitPrice;
                    ledger.amount = ledger.qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
                }
            }
            // v6.1.3：入库日期仅首次落，重复导入不回写 now（防 FIFO 排序失真）
            if (ledger.inboundDate == null) ledger.inboundDate = LocalDate.now();
            if (produceDate != null) ledger.produceDate = produceDate;
            if (expiryDate != null) {
                ledger.expiryDate = expiryDate;
            } else {
                Material mat = material != null ? material
                        : materialRepo.findByCode(materialCode).orElse(null);
                if (mat != null && mat.shelfLifeDays != null && mat.shelfLifeDays > 0) {
                    ledger.expiryDate = ledger.inboundDate.plusDays(mat.shelfLifeDays);
                }
            }
            ledger.qcStatus = "PASS";
            ledger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(ledger);

            saveMovement("OPENING", docNo, materialCode, finalBatchNo, warehouseId, ledger.locationId,
                    "IN", qty, qtyBefore, ledger.qty, ledger.ownershipType, operator, "期初导入");
            log.info("期初导入: {} {} 批次{} +{} -> 仓{} 余额{}", docNo, materialCode, finalBatchNo, qty, warehouseId, ledger.qty);
        });
    }

    /** 仓库默认库位（公开给批量导入方按仓缓存复用，语义同 defaultLocationOf） */
    public String resolveDefaultLocation(String warehouseId) {
        return defaultLocationOf(warehouseId);
    }

    /**
     * 冲正入库（反审核到货时用）
     */
    // v6.1.4（大件迁移）：executeTx 锁内包事务，提交后放锁（原 @Transactional+execute 锁先放、提交在后，并发窗口读旧快照/丢更新）
    public void reverseInbound(String docType, String docNo, String materialCode,
                                String batchNo, String warehouseId, BigDecimal qty,
                                String operator) {
        writeQueue.executeTx(() -> {
            InventoryLedger ledger = getOrCreateLedger(materialCode, batchNo, warehouseId, null);

            if (ledger.qty.compareTo(qty) < 0) {
                throw new IllegalArgumentException(String.format(
                        "库存不足无法冲正: %s 批次%s 仓%s 当前%.3f 需要冲回%.3f",
                        materialCode, batchNo, warehouseId, ledger.qty, qty));
            }

            BigDecimal qtyBefore = ledger.qty;
            ledger.qty = ledger.qty.subtract(qty);
            ledger.availableQty = ledger.availableQty.subtract(qty);
            if (ledger.availableQty.compareTo(BigDecimal.ZERO) < 0) ledger.availableQty = BigDecimal.ZERO;
            ledger.amount = ledger.qty.multiply(
                    ledger.unitPrice != null ? ledger.unitPrice : BigDecimal.ZERO);
            ledger.lastUpdateTime = java.time.LocalDateTime.now();
            ledgerRepo.save(ledger);

            saveMovement(docType, docNo, materialCode, batchNo, warehouseId, ledger.locationId,
                    "OUT", qty.negate(), qtyBefore, ledger.qty, ledger.ownershipType, operator,
                    "反审核冲正");
        });
    }

    private void saveMovement(String docType, String docNo, String materialCode,
                              String batchNo, String warehouseId, String direction,
                              BigDecimal qty, BigDecimal qtyBefore, BigDecimal qtyAfter,
                              String ownershipType, String operator, String remark) {
        saveMovement(docType, docNo, materialCode, batchNo, warehouseId, null, direction,
                qty, qtyBefore, qtyAfter, ownershipType, operator, remark);
    }

    /** v5.46：流水精确到库位（locationId 新参数版，扣减/入库调用点必传） */
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

    /**
     * 质检信息载体（入库时透传到库存台账）
     */
    public static class QcInfo {
        public final String status;        // PASS / CONCESSION
        public final String inspectionNo;  // 质检单号
        public final String result;        // 检测结果
        public final String inspector;     // 检验员
        public final LocalDate date;       // 检验日期

        public QcInfo(String status, String inspectionNo, String result, String inspector, LocalDate date) {
            this.status = status;
            this.inspectionNo = inspectionNo;
            this.result = result;
            this.inspector = inspector;
            this.date = date;
        }
    }
}
