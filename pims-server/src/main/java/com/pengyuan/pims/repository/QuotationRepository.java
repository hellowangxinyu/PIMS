package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findAllByOrderByCreateTimeDesc();

    List<Quotation> findByCustomerIdOrderByCreateTimeDesc(Long customerId);

    List<Quotation> findByStatusOrderByCreateTimeDesc(String status);

    List<Quotation> findByCustomerIdAndStatusOrderByCreateTimeDesc(Long customerId, String status);

    /** v5.24 口径：取指定前缀最大单号序号（防并发撞号 + 删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(quote_no, -4) AS INTEGER)) FROM quotation WHERE quote_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
