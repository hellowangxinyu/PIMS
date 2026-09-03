package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ShippingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ShippingLogRepository extends JpaRepository<ShippingLog, Long> {

    List<ShippingLog> findAllByOrderByCreateTimeDescIdDesc();

    /** 发货单是否已登记过运费（一单一次物流，防重复参照） */
    boolean existsByOutboundDocNo(String outboundDocNo);

    Optional<ShippingLog> findByOutboundDocNo(String outboundDocNo);

    /** 取指定前缀最大单号序号（SHIP-YYYY-NNNN） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM shipping_log WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
