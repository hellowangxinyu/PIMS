package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OutsourceOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OutsourceOrderRepository extends JpaRepository<OutsourceOrder, Long> {
    List<OutsourceOrder> findByOrderByCreateTimeDesc();
    Page<OutsourceOrder> findByOrderByCreateTimeDesc(Pageable pageable);
    List<OutsourceOrder> findByStatusOrderByCreateTimeDesc(String status);
    Optional<OutsourceOrder> findByOrderNo(String orderNo);

    /** v5.7：取指定年份最大单号序号（删除记录后 count() 会错位导致单号重复，改按最大序号+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(order_no, -4) AS INTEGER)) FROM outsource_order WHERE order_no LIKE ?1", nativeQuery = true)
    Integer findMaxOrderSeq(String prefix);

    /** v5.27：销售订单转委外防重复（同一销售订单+同一产品只生成一次） */
    boolean existsBySalesOrderNoAndProductCode(String salesOrderNo, String productCode);

    /** v5.27：已委外订单按排产顺序号升序（排产中心队列） */
    List<OutsourceOrder> findByStatusOrderByScheduleSeqAsc(String status);

    /** v5.27：当前最大排产顺序号 */
    @Query("SELECT MAX(o.scheduleSeq) FROM OutsourceOrder o")
    Integer findMaxScheduleSeq();

    /** 按月统计委外订单数（按状态分组） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, status, COUNT(*) AS cnt " +
            "FROM outsource_order WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period, status ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyCountByStatusSince(String sinceDate);

    /** 按月统计委外总量（batch_qty汇总） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(batch_qty),0) AS qty " +
            "FROM outsource_order WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) AND status != 'DRAFT' GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyQtySince(String sinceDate);

    /** 月度委外加工费（加工费单价 × 订单批量，近N个月） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, " +
            "COALESCE(SUM(processing_fee * batch_qty), 0) AS fee " +
            "FROM outsource_order WHERE status != 'DRAFT' " +
            "AND create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyFeeSince(String sinceDate);

    /** 代工厂加工费排行（近N个月） */
    @Query(value = "SELECT processor, COALESCE(SUM(processing_fee * batch_qty), 0) AS fee " +
            "FROM outsource_order WHERE status != 'DRAFT' AND processor IS NOT NULL AND trim(processor) != '' " +
            "AND create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY processor ORDER BY fee DESC LIMIT 10", nativeQuery = true)
    List<Object[]> processorFeeSince(String sinceDate);
}
