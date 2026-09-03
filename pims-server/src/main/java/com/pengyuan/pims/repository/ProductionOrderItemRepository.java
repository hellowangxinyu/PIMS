package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProductionOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductionOrderItemRepository extends JpaRepository<ProductionOrderItem, Long> {
    List<ProductionOrderItem> findByOrderId(Long orderId);
    void deleteByOrderId(Long orderId);
}
