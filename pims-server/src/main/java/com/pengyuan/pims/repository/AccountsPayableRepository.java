package com.pengyuan.pims.repository;
import com.pengyuan.pims.entity.AccountsPayable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface AccountsPayableRepository extends JpaRepository<AccountsPayable, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);

    List<AccountsPayable> findBySupplierId(Long supplierId);

    /** 按供应商聚合应付（总表直出，替代 findAll+内存分组）：supplier_id, 供应商名, Σamount, Σpaid, 总单数, 未付/部分/已结单数, MIN(id)（稳定排序） */
    @Query(value = "SELECT a.supplier_id, MAX(s.name), COALESCE(SUM(a.amount),0), COALESCE(SUM(a.paid_amount),0), COUNT(*), " +
            "SUM(CASE WHEN a.status='UNPAID' THEN 1 ELSE 0 END), SUM(CASE WHEN a.status='PARTIAL' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.status='PAID' THEN 1 ELSE 0 END), MIN(a.id) " +
            "FROM accounts_payable a LEFT JOIN supplier s ON a.supplier_id = s.id " +
            "WHERE a.supplier_id IS NOT NULL GROUP BY a.supplier_id ORDER BY MIN(a.id)", nativeQuery = true)
    List<Object[]> totalBySupplier();

    /** v11.3 应付总表：超期应付（已过账期未付余额）按供应商聚合 */
    @org.springframework.data.jpa.repository.Query(
            "SELECT a.supplierId, COALESCE(SUM(a.amount - COALESCE(a.paidAmount, 0)), 0) " +
            "FROM AccountsPayable a WHERE a.status <> 'PAID' AND a.dueDate < :today GROUP BY a.supplierId")
    java.util.List<Object[]> overdueBySupplier(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    /** 按供应商查询未结清应付单（按创建时间升序，用于付款 FIFO 冲减） */
    List<AccountsPayable> findBySupplierIdAndStatusNotOrderByCreateTimeAsc(Long supplierId, String status);

    /** 按采购订单号查询应付单（按创建时间升序，便于退货按 FIFO 冲减） */
    List<AccountsPayable> findByPurchaseOrderNoOrderByCreateTimeAsc(String purchaseOrderNo);

    /** v5.27：按到货单查询应付单（采购应付按到货单立账的幂等键） */
    List<AccountsPayable> findByArrivalId(Long arrivalId);

    /** 按月统计应付金额（按创建时间分组）；v5.55 裸列毫秒比较走索引（参数 yyyy-MM-dd 折算东八区当日零点） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(amount),0), COALESCE(SUM(paid_amount),0) " +
            "FROM accounts_payable WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyApSince(String sinceDate);

    /** 账龄明细（未结清，v5.9 替代 findAll+内存过滤）：join 供应商档案取名称 */
    @Query(value = "SELECT a.doc_no, a.supplier_id, s.name, a.purchase_order_no, a.outsource_order_no, a.amount, a.paid_amount, a.due_date, a.status " +
            "FROM accounts_payable a LEFT JOIN supplier s ON a.supplier_id = s.id " +
            "WHERE a.status != 'PAID' AND a.amount > COALESCE(a.paid_amount, 0)", nativeQuery = true)
    List<Object[]> agingList();
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM accounts_payable WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    // v7.6 应付周转：期间立账 / 累计立账（参数 yyyy-MM-dd，SQL 端折东八区零点毫秒，同 monthlyApSince 口径）
    @Query(value = "SELECT supplier_id, COALESCE(SUM(amount),0) FROM accounts_payable " +
            "WHERE supplier_id IS NOT NULL AND create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "AND create_time < 1000 * (CAST(strftime('%s', ?2) AS INTEGER) - 28800) GROUP BY supplier_id", nativeQuery = true)
    List<Object[]> billedBySupplier(String startDate, String endDateExclusive);

    @Query(value = "SELECT supplier_id, COALESCE(SUM(amount),0) FROM accounts_payable " +
            "WHERE supplier_id IS NOT NULL AND create_time < 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY supplier_id", nativeQuery = true)
    List<Object[]> cumBilledBySupplier(String dateExclusive);

}
