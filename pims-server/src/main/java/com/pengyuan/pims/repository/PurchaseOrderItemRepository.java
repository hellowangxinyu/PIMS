package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByOrderId(Long orderId);
}
