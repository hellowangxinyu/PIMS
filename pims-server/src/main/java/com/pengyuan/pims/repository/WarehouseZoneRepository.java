package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.WarehouseZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WarehouseZoneRepository extends JpaRepository<WarehouseZone, Long> {
    List<WarehouseZone> findByWarehouseIdAndEnabledTrueOrderBySortOrderAsc(Long warehouseId);
    List<WarehouseZone> findByWarehouseIdOrderBySortOrderAsc(Long warehouseId);
    long countByWarehouseId(Long warehouseId);

    /** v5.38：按分库类型找隔离分库（UNQUALIFIED=隔离区 / TAILING=油尾区；全局第一个，兜底用） */
    java.util.Optional<WarehouseZone> findFirstByZoneTypeAndEnabledTrueOrderBySortOrderAsc(String zoneType);

    /** v5.38.1：按仓库+类型找隔离分库（每个一级仓都有自己的隔离区/油尾区） */
    java.util.Optional<WarehouseZone> findFirstByWarehouseIdAndZoneTypeAndEnabledTrueOrderBySortOrderAsc(Long warehouseId, String zoneType);
}
