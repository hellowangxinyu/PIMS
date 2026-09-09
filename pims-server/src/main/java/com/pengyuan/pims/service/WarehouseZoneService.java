package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.WarehouseZone;
import com.pengyuan.pims.repository.WarehouseZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WarehouseZoneService {

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final WarehouseZoneRepository repo;
    public WarehouseZoneService(WarehouseZoneRepository repo, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue; this.repo = repo; }

    public List<WarehouseZone> listByWarehouse(Long warehouseId) {
        return repo.findByWarehouseIdAndEnabledTrueOrderBySortOrderAsc(warehouseId);
    }

    public List<WarehouseZone> listAllByWarehouse(Long warehouseId) {
        return repo.findByWarehouseIdOrderBySortOrderAsc(warehouseId);
    }

    /** v5.38：全部分库（隔离分库查找用） */
    public List<WarehouseZone> listAll() { return repo.findAll(); }

    public Optional<WarehouseZone> getById(Long id) { return repo.findById(id); }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public WarehouseZone create(WarehouseZone zone) {
        return writeQueue.executeTx(() -> {
            long seq = repo.countByWarehouseId(zone.warehouseId) + 1;
            zone.code = String.format("Z%03d", seq);
            return repo.save(zone);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public WarehouseZone update(Long id, WarehouseZone zone) {
        return writeQueue.executeTx(() -> {
            WarehouseZone exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("分库不存在"));
            exist.name = zone.name;
            exist.remark = zone.remark;
            exist.sortOrder = zone.sortOrder;
            exist.updateTime = java.time.LocalDateTime.now();
            return repo.save(exist);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void delete(Long id) {
        writeQueue.executeTx(() -> {
            WarehouseZone zone = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("分库不存在"));
            // v5.38：隔离分库（隔离区/油尾区）受保护不可删除——隔离货的唯一存放地
            // v5.58：类型中文名统一走 IsolatedZoneService.zoneTypeLabel（与库位删除拦截共用）
            if (zone.zoneType != null && !zone.zoneType.isBlank()) {
                throw new IllegalArgumentException("该分库为系统隔离分库（" + IsolatedZoneService.zoneTypeLabel(zone.zoneType) + "），不可删除");
            }
            zone.enabled = false;
            repo.save(zone);
    
        });
    }
}
