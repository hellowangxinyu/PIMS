package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProductionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {
    List<ProductionOrder> findByOrderByCreateTimeDesc();
    List<ProductionOrder> findByStatusOrderByCreateTimeDesc(String status);
    Optional<ProductionOrder> findByOrderNo(String orderNo);

    /** v5.7：取指定年份最大单号序号（删除记录后 count() 会错位导致单号重复，改按最大序号+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(order_no, -4) AS INTEGER)) FROM production_order WHERE order_no LIKE ?1", nativeQuery = true)
    Integer findMaxOrderSeq(String prefix);

    /** v5.27：销售订单转生产防重复（同一销售订单+同一产品只生成一次） */
    boolean existsBySalesOrderNoAndProductCode(String salesOrderNo, String productCode);

    /** v5.27：已排产订单按排产顺序号升序（排产中心队列） */
    List<ProductionOrder> findByStatusOrderByScheduleSeqAsc(String status);

    /** v5.27：当前最大排产顺序号 */
    @Query("SELECT MAX(o.scheduleSeq) FROM ProductionOrder o")
    Integer findMaxScheduleSeq();

    /** 按月统计生产订单数（按状态分组） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, status, COUNT(*) AS cnt " +
            "FROM production_order WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period, status ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyCountByStatusSince(String sinceDate);

    /** 按月统计生产总量（batch_qty汇总） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(batch_qty),0) AS qty " +
            "FROM production_order WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) AND status != 'DRAFT' GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyQtySince(String sinceDate);
}
