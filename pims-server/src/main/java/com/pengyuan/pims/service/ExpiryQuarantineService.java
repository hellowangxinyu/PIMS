package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.InventoryLedger;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.WarehouseLocation;
import com.pengyuan.pims.entity.WarehouseZone;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import com.pengyuan.pims.repository.MaterialRepository;
import com.pengyuan.pims.repository.WarehouseZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 过期批次自动隔离（v5.37 起，v5.38.2 按用户口径调整）：
 *
 * 原材料（A/P/F/R/S）过期 → qcStatus='EXPIRED' 且**自动移入该仓的不合格品库分库**
 * （不合格品库是每个一级仓的必备分库，原材料不合格品的主要来源就是过期）；
 * 半成品/成品过期 → 原地标记 EXPIRED（不入不合格品库，等待复检或报废）。
 *
 * 复检放行由 QualityInspectionService（judge 的 REINSPECTION 分支）负责：
 * 合格时恢复状态、更新 expiryDate 并移回普通库位。
 */
@Service
public class ExpiryQuarantineService {

    private static final Logger log = LoggerFactory.getLogger(ExpiryQuarantineService.class);

    /** 原材料大类（v5.38.3：过期自动入「原材料不合格品库」） */
    private static final Set<String> RAW_CATEGORIES = Set.of("A", "P", "F", "R", "S");

    /** 物料大类 → 不合格品分库类型（原材料/半成品/成品三库，v5.38.3 用户口径） */
    private static String unqualifiedZoneType(String category) {
        if (category != null && RAW_CATEGORIES.contains(category)) return "UNQUALIFIED_RAW";
        if ("B".equals(category)) return "UNQUALIFIED_SEMI";
        if ("C".equals(category)) return "UNQUALIFIED_FIN";
        return "UNQUALIFIED_RAW";  // 无档案兜底原材料库
    }

    private final InventoryLedgerRepository ledgerRepo;
    private final MaterialRepository materialRepo;
    private final WarehouseZoneRepository zoneRepo;
    private final WarehouseLocationService locationService;
    private final WriteQueue writeQueue;
    private final com.pengyuan.pims.repository.InventoryMovementRepository movementRepo;
    // v5.39：隔离即自动触发复检评估单（@Lazy 避免循环依赖——QIS 判定回写走 EQS）
    private final QualityInspectionService qcService;
    private final com.pengyuan.pims.repository.QualityInspectionRepository qcRepo;

    public ExpiryQuarantineService(InventoryLedgerRepository ledgerRepo,
                                   MaterialRepository materialRepo,
                                   WarehouseZoneRepository zoneRepo,
                                   WarehouseLocationService locationService,
                                   WriteQueue writeQueue,
                                   com.pengyuan.pims.repository.QualityInspectionRepository qcRepo,
                                   @org.springframework.context.annotation.Lazy QualityInspectionService qcService,
                                   com.pengyuan.pims.repository.InventoryMovementRepository movementRepo) {
        this.ledgerRepo = ledgerRepo;
        this.materialRepo = materialRepo;
        this.zoneRepo = zoneRepo;
        this.locationService = locationService;
        this.writeQueue = writeQueue;
        this.movementRepo = movementRepo;
        this.qcRepo = qcRepo;
        this.qcService = qcService;
    }

    /**
     * v8.9：启动扫描合并到 ApplicationReadyEvent——原 @PostConstruct 在表结构就绪前查库，
     * 测试环境（ddl-auto=create-drop）下 bean 创建期 inventory_ledger 尚不存在直接炸上下文；
     * Ready 时机更晚且语义相同（仅扫描提前几百毫秒之差）。之后每日 04:15 扫描。
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        quarantineOnly();              // v5.39 原启动扫描
        quarantineExpiredBatches();    // 隔离幂等（已处理直接跳过），补触发复检评估单
    }

    @Scheduled(cron = "0 15 4 * * ?")
    public void scheduledQuarantine() {
        quarantineExpiredBatches();
    }

    /** 只做隔离（不改状态之外的动作），启动早期调用 */
    public int quarantineOnly() {
        return doQuarantine(false);
    }

    /** 隔离 + 自动触发复检评估单（定时任务与应用就绪后调用） */
    public int quarantineExpiredBatches() {
        return doQuarantine(true);
    }

    /**
     * 扫描全部在库台账（内存过滤，几百行量级，绕开 SQLite 日期格式坑）：
     * qty>0 且 expiryDate 已过 且 qcStatus 属于可用态（null/PASS/CONCESSION）：
     * 按物料大类 → EXPIRED + 移入该仓对应不合格品库分库。
     * @param triggerReinspection true 时对无复检单的过期批次自动生成待判定复检评估单
     * @return 本轮隔离行数
     */
    private int doQuarantine(boolean triggerReinspection) {
        return writeQueue.executeTx(() -> {
            LocalDate today = LocalDate.now();
            List<InventoryLedger> all = ledgerRepo.findAll();
            // 物料大类一次性建映射（逐行 findByCode 是 N+1）
            Map<String, String> categoryByCode = new HashMap<>();
            for (Material m : materialRepo.findAll()) categoryByCode.put(m.code, m.category);
            int count = 0, moved = 0;
            for (InventoryLedger l : all) {
                if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (l.expiryDate == null || !l.expiryDate.isBefore(today)) continue;
                // v5.38.3：过期统一搬入按物料大类对应的不合格品库（原材料/半成品/成品三库）；
                // 可用态先标记 EXPIRED，已 EXPIRED 但未搬位的补搬（幂等，覆盖历史隔离行）
                String category = categoryByCode.get(l.materialCode);
                String zoneType = unqualifiedZoneType(category);
                if (isQuarantinable(l.qcStatus) || "EXPIRED".equals(l.qcStatus)) {
                    boolean newly = isQuarantinable(l.qcStatus);
                    l.qcStatus = "EXPIRED";
                    if (moveToUnqualifiedZone(l, zoneType)) {
                        moved++;
                        ledgerRepo.save(l);
                        if (newly) count++;
                        log.info("过期入{}: {} 批次 {} 于 {} 过期，数量 {} {} → {}",
                                "UNQUALIFIED_RAW".equals(zoneType) ? "原材料不合格品库"
                                        : "UNQUALIFIED_SEMI".equals(zoneType) ? "半成品不合格品库" : "成品不合格品库",
                                l.materialCode, l.batchNo, l.expiryDate, l.qty, l.unit, l.locationName);
                    } else if (newly) {
                        ledgerRepo.save(l);
                        count++;
                    }
                    continue;
                }
            }
            if (count + moved > 0) log.info("过期批次隔离完成: 本轮新标记 {} 行，移入不合格品库 {} 行", count, moved);

            // v5.39：隔离即触发复检评估（仅 bean 就绪后）——每个在库过期批次（EXPIRED 且有库存）若无任何复检单（含已判过的），
            // 自动生成 PENDING 复检评估单，质检员到「质检管理」直接判定（判过的批次不再自动建，需人工到过期预警页再发起）
            Set<String> reinspectTargets = new java.util.LinkedHashSet<>();
            for (InventoryLedger l : all) {
                if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (!"EXPIRED".equals(l.qcStatus)) continue;
                if (l.expiryDate == null || !l.expiryDate.isBefore(today)) continue;
                reinspectTargets.add(l.materialCode + "|" + (l.batchNo == null ? "" : l.batchNo));
            }
            int triggered = 0;
        int failed = 0;   // v8.4（A5）
            for (String key : triggerReinspection ? reinspectTargets : java.util.Set.<String>of()) {
                String[] parts = key.split("[|]", 2);
                String mc = parts[0], bn = parts.length > 1 ? parts[1] : "";
                boolean hasDoc = !qcRepo.findByMaterialCodeAndBatchNoAndRefDocTypeOrderByCreateTimeDesc(mc, bn, "REINSPECTION").isEmpty();
                if (hasDoc) continue;
                try {
                    qcService.createReinspection(mc, bn, "系统·过期自动触发");
                    triggered++;
                } catch (Exception e) {
                    // v8.4（A5）：升级 error 并计失败数（原 warn 后静默，部分批次隔离失败无感知）
                    log.error("复检评估单自动生成失败: {} 批次 {} 原因={}", mc, bn, e.getMessage(), e);
                    failed++;
                }
            }
            if (triggered > 0) log.info("复检评估自动触发: 本轮生成 {} 张待判定复检单", triggered);
            if (failed > 0) log.error("复检评估自动触发: {} 张生成失败（见上方堆栈，请核查隔离分库配置）", failed);
            return count;
        });
    }

    private static boolean isQuarantinable(String qcStatus) {
        // null=历史存量未质检，PASS/CONCESSION=可用态；REJECT/TAILING/TAILING_FC/EXPIRED 已隔离
        return qcStatus == null || "PASS".equals(qcStatus) || "CONCESSION".equals(qcStatus);
    }

    /**
     * 把台账行移入其所在仓的指定类型不合格品分库（默认库位）。
     * 撞键（目标键已有行）或该仓无对应分库时返回 false（调用方保持原地标记）。
     */
    private boolean moveToUnqualifiedZone(InventoryLedger l, String zoneType) {
        if (l.warehouseId == null || l.warehouseId.isBlank()) return false;
        try {
            Long whId = Long.valueOf(l.warehouseId);
            WarehouseZone zone = zoneRepo
                    .findFirstByWarehouseIdAndZoneTypeAndEnabledTrueOrderBySortOrderAsc(whId, zoneType)
                    .orElse(null);
            if (zone == null) return false;
            List<WarehouseLocation> locs = locationService.listByZone(zone.id);
            if (locs.isEmpty()) return false;
            WarehouseLocation target = locs.get(0);
            String targetLocId = String.valueOf(target.id);
            if (targetLocId.equals(l.locationId)) return true;  // 已在不合格品库
            // 撞键保护：同物料同批号已占该库位时不移动（避免唯一键冲突）
            if (ledgerRepo.countKeyConflict(l.materialCode, l.warehouseId, targetLocId, l.id, l.batchNo) > 0) return false;
            l.locationId = targetLocId;
            l.locationName = target.name;
            l.zoneName = zone.name;
            // v6.1.6：隔离移动补异动流水（原改库位不留痕，异动轨迹断链）
            movementRepo.save(movementOf(l, zoneType));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 复检合格后移回普通库位（该仓第一个普通分库的第一个启用库位；无则无库位）。返回最终库位描述。 */
    public Map<String, String> restoreToNormalLocation(InventoryLedger l) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        try {
            if (l.warehouseId == null || l.warehouseId.isBlank()) return result;
            Long whId = Long.valueOf(l.warehouseId);
            List<WarehouseZone> normalZones = zoneRepo.findByWarehouseIdAndEnabledTrueOrderBySortOrderAsc(whId)
                    .stream().filter(z -> z.zoneType == null || z.zoneType.isBlank()).toList();
            if (!normalZones.isEmpty()) {
                List<WarehouseLocation> locs = locationService.listByZone(normalZones.get(0).id);
                if (!locs.isEmpty()) {
                    WarehouseLocation target = locs.get(0);
                    String targetLocId = String.valueOf(target.id);
                    if (ledgerRepo.countKeyConflict(l.materialCode, l.warehouseId, targetLocId, l.id, l.batchNo) == 0) {
                        l.locationId = targetLocId;
                        l.locationName = target.name;
                        l.zoneName = normalZones.get(0).name;
                        result.put("location", target.name);
                        result.put("zone", normalZones.get(0).name);
                        return result;
                    }
                }
            }
            // 兜底：移为无库位（普通无库位行合并进 getOrCreate 语义，无唯一键风险——无库位行本身是单行）
            l.locationId = null;
            l.locationName = null;
            l.zoneName = null;
            result.put("location", "（无库位）");
        } catch (NumberFormatException ignored) { }
        return result;
    }
    /** v6.1.6：隔离移动异动快照（同库位平移，量不变） */
    private com.pengyuan.pims.entity.InventoryMovement movementOf(InventoryLedger l, String zoneType) {
        com.pengyuan.pims.entity.InventoryMovement m = new com.pengyuan.pims.entity.InventoryMovement();
        m.docType = "EXPIRED".equals(zoneType) ? "EXPIRED_QUARANTINE" : "REJECT_QUARANTINE";
        // v6.1.7：docNo 加随机尾防同毫秒撞号；operator 记 system（定时隔离任务无登录人）
        m.docNo = m.docType + "-" + System.currentTimeMillis() + "-" + Integer.toHexString(java.util.Objects.hashCode(l));
        m.operator = "system";
        m.materialCode = l.materialCode;
        m.batchNo = l.batchNo;
        m.warehouseId = l.warehouseId;
        m.locationId = l.locationId;
        m.direction = "IN";
        m.qty = l.qty;
        m.qtyBefore = l.qty;
        m.qtyAfter = l.qty;
        m.ownershipType = l.ownershipType;
        m.remark = "隔离移动至 " + l.zoneName;
        return m;
    }

}
