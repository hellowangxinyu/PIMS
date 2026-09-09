package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.WarehouseLocation;
import com.pengyuan.pims.entity.WarehouseZone;
import com.pengyuan.pims.repository.WarehouseLocationRepository;
import com.pengyuan.pims.repository.WarehouseZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WarehouseLocationService {

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final WarehouseLocationRepository repo;
    private final WarehouseZoneRepository zoneRepo;
    public WarehouseLocationService(WarehouseLocationRepository repo, WarehouseZoneRepository zoneRepo, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.repo = repo;
        this.zoneRepo = zoneRepo;
    }

    /**
     * 获取某仓库下所有启用库位（跨分库）。
     * v5.38：默认排除隔离分库（隔离区/油尾区）的库位——正常业务页面的库位下拉永不见隔离库位，
     * 前端不再需要各自记得过滤（散弹收口）；需要隔离库位的场景（质检判定选位/油尾入库）走 listByZone 或含隔离重载。
     */
    public List<WarehouseLocation> listByWarehouse(Long warehouseId) {
        return listByWarehouse(warehouseId, false);
    }

    /** includeIsolated=true 时包含隔离分库库位（隔离货管理场景专用） */
    public List<WarehouseLocation> listByWarehouse(Long warehouseId, boolean includeIsolated) {
        List<Long> zoneIds = zoneRepo.findByWarehouseIdAndEnabledTrueOrderBySortOrderAsc(warehouseId)
                .stream()
                .filter(z -> includeIsolated || z.zoneType == null || z.zoneType.isBlank())
                .map(z -> z.id).collect(Collectors.toList());
        if (zoneIds.isEmpty()) return Collections.emptyList();
        return repo.findByZoneIdInAndEnabledTrueOrderBySortOrderAsc(zoneIds);
    }

    public List<WarehouseLocation> listByZone(Long zoneId) {
        return repo.findByZoneIdAndEnabledTrueOrderBySortOrderAsc(zoneId);
    }

    public List<WarehouseLocation> listAllByZone(Long zoneId) {
        return repo.findByZoneIdOrderBySortOrderAsc(zoneId);
    }

    public Optional<WarehouseLocation> getById(Long id) { return repo.findById(id); }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public WarehouseLocation create(WarehouseLocation loc) {
        return writeQueue.executeTx(() -> {
            long seq = repo.countByZoneId(loc.zoneId) + 1;
            loc.code = String.format("L%03d", seq);
            return repo.save(loc);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public WarehouseLocation update(Long id, WarehouseLocation loc) {
        return writeQueue.executeTx(() -> {
            WarehouseLocation exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("库位不存在"));
            exist.name = loc.name;
            exist.remark = loc.remark;
            exist.sortOrder = loc.sortOrder;
            exist.updateTime = java.time.LocalDateTime.now();
            return repo.save(exist);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void delete(Long id) {
        writeQueue.executeTx(() -> {
            WarehouseLocation loc = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("库位不存在"));
            // v5.58：隔离分库下的库位受保护——隔离货的唯一落位点，删掉后过期/质检不合格/油尾流程会静默失败
            WarehouseZone zone = zoneRepo.findById(loc.zoneId).orElse(null);
            if (zone != null && zone.zoneType != null && !zone.zoneType.isBlank()) {
                throw new IllegalArgumentException("该库位属于系统隔离分库（" + IsolatedZoneService.zoneTypeLabel(zone.zoneType) + "），不可删除");
            }
            loc.enabled = false;
            repo.save(loc);
    
        });
    }
}
