package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PaymentDisbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PaymentDisbursementRepository extends JpaRepository<PaymentDisbursement, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);

    List<PaymentDisbursement> findByOrderByCreateTimeDesc();
    List<PaymentDisbursement> findBySupplierIdOrderByCreateTimeDesc(Long supplierId);

    /** 月度付款合计（近N个月） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(amount), 0) " +
            "FROM payment_disbursement WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyAmountSince(String sinceDate);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM payment_disbursement WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
