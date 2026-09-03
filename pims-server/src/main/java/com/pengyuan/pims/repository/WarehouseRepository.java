package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);
    List<Warehouse> findByEnabledTrue();
    List<Warehouse> findByProcessorId(String processorId);

    /** v5.43.2 工作台统计口径：只数启用中的（排除禁用测试残留） */
    long countByEnabledTrue();
}
