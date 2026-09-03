package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.WarehouseLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {
    List<WarehouseLocation> findByZoneIdAndEnabledTrueOrderBySortOrderAsc(Long zoneId);
    List<WarehouseLocation> findByZoneIdOrderBySortOrderAsc(Long zoneId);
    List<WarehouseLocation> findByZoneIdInAndEnabledTrueOrderBySortOrderAsc(List<Long> zoneIds);
    long countByZoneId(Long zoneId);
}
