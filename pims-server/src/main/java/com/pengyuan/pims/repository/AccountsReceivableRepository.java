package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.AccountsReceivable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccountsReceivableRepository extends JpaRepository<AccountsReceivable, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);


        java.util.List<AccountsReceivable> findByStatusNot(String status);
List<AccountsReceivable> findByCustomerId(Long customerId);

    /** 到期未结清应收：到期日及以前、未 PAID 的余额合计（仪表盘待办，替代拉全量内存累加） */
    @Query("select coalesce(sum(coalesce(a.amount,0) - coalesce(a.receivedAmount,0)),0) from AccountsReceivable a where a.status <> 'PAID' and a.dueDate <= :today")
    java.math.BigDecimal sumDueNotSettled(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    long countByStatusNotAndDueDateLessThanEqual(String status, java.time.LocalDate date);

    /** 按客户聚合应收（总表直出，替代 findAll+内存分组）：customer_id, 客户名, Σamount, Σreceived, 总单数, 未收/部分/已结单数, MIN(id)（稳定排序） */
    @Query(value = "SELECT a.customer_id, MAX(c.name), COALESCE(SUM(a.amount),0), COALESCE(SUM(a.received_amount),0), COUNT(*), " +
            "SUM(CASE WHEN a.status='UNPAID' THEN 1 ELSE 0 END), SUM(CASE WHEN a.status='PARTIAL' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.status='PAID' THEN 1 ELSE 0 END), MIN(a.id) " +
            "FROM accounts_receivable a LEFT JOIN customer c ON a.customer_id = c.id " +
            "WHERE a.customer_id IS NOT NULL GROUP BY a.customer_id ORDER BY MIN(a.id)", nativeQuery = true)
    List<Object[]> totalByCustomer();

    /** 按客户查询未结清应收单（按创建时间升序，用于收款 FIFO 冲减） */
    List<AccountsReceivable> findByCustomerIdAndStatusNotOrderByCreateTimeAsc(Long customerId, String status);

    /** 按销售订单号查询应收单（按创建时间升序，便于退货按 FIFO 冲减） */
    List<AccountsReceivable> findBySalesOrderNoOrderByCreateTimeAsc(String salesOrderNo);

    /** 按月统计应收金额（按创建时间分组）；v5.55 时间列已统一毫秒整数，裸列比较走 idx_ar_time_status（参数为 yyyy-MM-dd，折算东八区当日零点） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(amount),0), COALESCE(SUM(received_amount),0) " +
            "FROM accounts_receivable WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyArSince(String sinceDate);

    /** 账龄明细（未结清，v5.9 替代 findAll+内存过滤）：join 客户档案取名称，due_date 毫秒由 Java 转换 */
    @Query(value = "SELECT a.doc_no, a.customer_id, c.name, a.sales_order_no, a.amount, a.received_amount, a.due_date, a.status " +
            "FROM accounts_receivable a LEFT JOIN customer c ON a.customer_id = c.id " +
            "WHERE a.status != 'PAID' AND a.amount > COALESCE(a.received_amount, 0)", nativeQuery = true)
    List<Object[]> agingList();
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM accounts_receivable WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
