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

    // v11.7 清理：listByWarehouse 两个重载已随 /warehouse/{id}/locations 端点下线删除（库位下拉统一走 listByZone）

    public List<WarehouseLocation> listByZone(Long zoneId) {
        return repo.findByZoneIdAndEnabledTrueOrderBySortOrderAsc(zoneId);
    }

    // v11.7 清理：listAllByZone（含停用）已删除，其仓库方法 findByZoneIdOrderBySortOrderAsc 一并删除

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
