package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OutsourceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutsourceOrderItemRepository extends JpaRepository<OutsourceOrderItem, Long> {
    List<OutsourceOrderItem> findByOrderId(Long orderId);
    void deleteByOrderId(Long orderId);
}
