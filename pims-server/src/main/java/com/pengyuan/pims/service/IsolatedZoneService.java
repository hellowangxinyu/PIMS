package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.Warehouse;
import com.pengyuan.pims.entity.WarehouseLocation;
import com.pengyuan.pims.entity.WarehouseZone;
import com.pengyuan.pims.repository.WarehouseLocationRepository;
import com.pengyuan.pims.repository.WarehouseRepository;
import com.pengyuan.pims.repository.WarehouseZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * v5.58：隔离分库维护（从 DataInitializer 抽出，单一逻辑源）。
 * 启动巡检（ensureAll）与新增/启用仓库的即时联动（ensureForWarehouse）共用同一套幂等补建逻辑，
 * 保证每个启用的一级仓随时具备 5 个系统隔离分库（3 不合格品库按物料大类 + 2 油尾库按体系）——
 * 隔离流程按 仓+zone_type 路由，缺分库会导致过期隔离静默降级、质检不合格跨仓兜底、油尾退回报错。
 */
@Service
public class IsolatedZoneService {

    private static final Logger log = LoggerFactory.getLogger(IsolatedZoneService.class);

    private final WarehouseRepository warehouseRepo;
    private final WarehouseZoneRepository zoneRepo;
    private final WarehouseLocationRepository locationRepo;

    public IsolatedZoneService(WarehouseRepository warehouseRepo, WarehouseZoneRepository zoneRepo,
                               WarehouseLocationRepository locationRepo) {
        this.warehouseRepo = warehouseRepo;
        this.zoneRepo = zoneRepo;
        this.locationRepo = locationRepo;
    }

    /** 5 类系统隔离分库定义：类型 / 标准名 / 默认库位名 / 备注 / 历史类型 / 历史名（存量迁移参数，幂等） */
    private record ZoneSpec(String zoneType, String zoneName, String locName, String remark,
                            String legacyZoneType, String legacyName) {}

    private static final List<ZoneSpec> SPECS = List.of(
            new ZoneSpec("UNQUALIFIED_RAW", "原材料不合格品库", "不合格品位",
                    "原材料不合格品分库（过期自动进入、来料质检不合格转入，仅可报废/退货出库）",
                    "UNQUALIFIED", "不合格品库"),
            new ZoneSpec("UNQUALIFIED_SEMI", "半成品不合格品库", "不合格品位",
                    "半成品（浆）不合格品分库（质检判定不合格自动转入，仅可报废/退货出库）", null, null),
            new ZoneSpec("UNQUALIFIED_FIN", "成品不合格品库", "不合格品位",
                    "成品（漆）不合格品分库（质检判定不合格自动转入，仅可报废/退货出库）", null, null),
            new ZoneSpec("TAILING", "聚酯油尾库", "油尾位",
                    "聚酯体系油尾分库（客户退回的未用完油漆，不可再销售，仅制漆配方领料消化或报废处置）",
                    "TAILING", "油尾区"),
            new ZoneSpec("TAILING_FC", "氟碳油尾库", "油尾位",
                    "氟碳体系油尾分库（客户退回的未用完油漆，不可再销售，仅制漆配方领料消化或报废处置）", null, null));

    /** 隔离分库类型中文名（分库/库位删除拦截提示共用；未知类型兜底「隔离分库」） */
    public static String zoneTypeLabel(String zoneType) {
        return switch (zoneType == null ? "" : zoneType) {
            case "UNQUALIFIED_RAW" -> "原材料不合格品库";
            case "UNQUALIFIED_SEMI" -> "半成品不合格品库";
            case "UNQUALIFIED_FIN" -> "成品不合格品库";
            case "TAILING" -> "聚酯油尾库";
            case "TAILING_FC" -> "氟碳油尾库";
            default -> "隔离分库";
        };
    }

    /** 启动巡检：所有启用中的一级仓幂等补齐 5 个隔离分库（原 DataInitializer 启动行为，v5.58 抽出） */
    @Transactional
    public void ensureAll() {
        for (Warehouse wh : warehouseRepo.findAll()) {
            ensureForWarehouse(wh);
        }
    }

    /** v5.58：单仓幂等补齐——新增/启用仓库时即时调用，消除「等重启才补建」的空窗。非启用仓跳过（与启动巡检口径一致）。 */
    @Transactional
    public void ensureForWarehouse(Warehouse wh) {
        if (wh == null || wh.id == null || !Boolean.TRUE.equals(wh.enabled)) return;
        for (ZoneSpec spec : SPECS) {
            ensureZone(wh, spec);
        }
    }

    private void ensureZone(Warehouse wh, ZoneSpec spec) {
        WarehouseZone found = zoneRepo
                .findFirstByWarehouseIdAndZoneTypeAndEnabledTrueOrderBySortOrderAsc(wh.id, spec.zoneType()).orElse(null);
        // 历史类型迁移（幂等）：老类型老名的分库改为新类型新名（如 UNQUALIFIED「不合格品库」→ UNQUALIFIED_RAW）
        if (found == null && spec.legacyZoneType() != null) {
            var legacy = zoneRepo.findAll().stream()
                    .filter(z -> wh.id.equals(z.warehouseId) && Boolean.TRUE.equals(z.enabled)
                            && spec.legacyZoneType().equals(z.zoneType))
                    .findFirst();
            if (legacy.isPresent()) {
                WarehouseZone z = legacy.get();
                z.zoneType = spec.zoneType();
                z.name = spec.zoneName();
                z.remark = spec.remark();
                z.updateTime = java.time.LocalDateTime.now();
                zoneRepo.save(z);
                log.info("种子数据：{} 隔离分库迁移 {}「{}」→ {}「{}」",
                        wh.name, spec.legacyZoneType(), spec.legacyName(), spec.zoneType(), spec.zoneName());
                return;
            }
        }
        if (found != null) {
            // 命名统一（幂等）：名称与标准不一致时纠正（用户在界面上改名，此处改回标准名）
            if (!spec.zoneName().equals(found.name)) {
                String oldName = found.name;
                found.name = spec.zoneName();
                found.remark = spec.remark();
                found.updateTime = java.time.LocalDateTime.now();
                zoneRepo.save(found);
                log.info("种子数据：{} 隔离分库改名「{}」→「{}」", wh.name, oldName, spec.zoneName());
            }
            return;
        }
        WarehouseZone zone = new WarehouseZone();
        zone.warehouseId = wh.id;
        zone.code = String.format("Z%03d", zoneRepo.countByWarehouseId(wh.id) + 1);
        zone.name = spec.zoneName();
        zone.zoneType = spec.zoneType();
        zone.remark = spec.remark();
        zone.sortOrder = 99;
        zone.enabled = true;
        zone = zoneRepo.save(zone);
        if (locationRepo.countByZoneId(zone.id) == 0) {
            WarehouseLocation loc = new WarehouseLocation();
            loc.zoneId = zone.id;
            loc.code = "L001";
            loc.name = spec.locName();
            loc.sortOrder = 0;
            loc.enabled = true;
            locationRepo.save(loc);
        }
        log.info("种子数据：{} 已创建隔离分库「{}」", wh.name, spec.zoneName());
    }
}
