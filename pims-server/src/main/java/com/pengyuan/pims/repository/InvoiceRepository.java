package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);


    List<Invoice> findAllByOrderByCreateTimeDescIdDesc();

    List<Invoice> findByPartnerIdAndDirectionOrderByCreateTimeAsc(Long partnerId, String direction);

    /** 取指定前缀最大单号序号（防并发撞号，删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM invoice WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
