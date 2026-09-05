package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);

    List<PaymentReceipt> findByOrderByCreateTimeDesc();
    List<PaymentReceipt> findByCustomerIdOrderByCreateTimeDesc(Long customerId);

    /** 月度收款合计（近N个月） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(amount), 0) " +
            "FROM payment_receipt WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyAmountSince(String sinceDate);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM payment_receipt WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    /** v7.6 应收周转：某日零点前累计收款（按客户），与累计立账相减得期初/期末应收余额 */
    @Query(value = "SELECT customer_id, COALESCE(SUM(amount),0) FROM payment_receipt " +
            "WHERE customer_id IS NOT NULL AND create_time < 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY customer_id", nativeQuery = true)
    List<Object[]> cumReceivedByCustomer(String dateExclusive);

}
