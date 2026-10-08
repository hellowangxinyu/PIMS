package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);

    /** v11.8（FT-05）：发票号业务查重（原仅查系统单号 docNo，同号发票可重复录入） */
    boolean existsByInvoiceNoAndStatus(String invoiceNo, String status);

    boolean existsByInvoiceNoAndStatusAndIdNot(String invoiceNo, String status, Long id);


    List<Invoice> findAllByOrderByCreateTimeDescIdDesc();

    List<Invoice> findByPartnerIdAndDirectionOrderByCreateTimeAsc(Long partnerId, String direction);

    /** 取指定前缀最大单号序号（防并发撞号，删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, LENGTH(doc_no)-3, 4) AS INTEGER)) FROM invoice WHERE LOWER(doc_no) LIKE LOWER(?1)", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
