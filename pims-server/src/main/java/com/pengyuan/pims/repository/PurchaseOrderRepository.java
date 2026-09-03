package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PurchaseOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findBySupplierIdOrderByCreateTimeDesc(Long supplierId);
    List<PurchaseOrder> findByStatusOrderByCreateTimeDesc(String status);
    List<PurchaseOrder> findAllByOrderByCreateTimeDesc(Pageable pageable);
    /** 按订单号精确查询（退货关联用） */
    Optional<PurchaseOrder> findByOrderNo(String orderNo);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(order_no, -4) AS INTEGER)) FROM purchase_order WHERE order_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
