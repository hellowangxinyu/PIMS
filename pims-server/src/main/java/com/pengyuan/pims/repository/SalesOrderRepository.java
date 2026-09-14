package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SalesOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCustomerIdOrderByCreateTimeDesc(Long customerId);
    /** 客户最近 N 条订单 + 客户订单总数（客户360°有界查询，不拉全量） */
    List<SalesOrder> findByCustomerIdOrderByCreateTimeDesc(Long customerId, Pageable pageable);
    long countByCustomerId(Long customerId);
    List<SalesOrder> findByStatusOrderByCreateTimeDesc(String status);
    List<SalesOrder> findAllByOrderByCreateTimeDesc(Pageable pageable);
    /** 按订单号精确查询（退货关联用） */
    Optional<SalesOrder> findByOrderNo(String orderNo);

    /** 销售订单状态分布（数量+金额，近N个月，按订单日期） */
    @Query(value = "SELECT status, COUNT(*), COALESCE(SUM(total_amount), 0) FROM sales_order " +
            "WHERE order_date >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) GROUP BY status", nativeQuery = true)
    List<Object[]> statusAmountSince(String sinceDate);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(order_no, -4) AS INTEGER)) FROM sales_order WHERE order_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    /** v5.27：取指定前缀最大合同号序号（合同号 = HT+客户简称+日期-序号，序号 2 位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(contract_no, INSTR(contract_no,'-')+1) AS INTEGER)) FROM sales_order WHERE contract_no LIKE ?1", nativeQuery = true)
    Integer findMaxContractSeq(String prefix);

    /** v5.27：全部订单号+状态（生产/委外列表推导"已发货"用） */
    @Query("SELECT s.orderNo, s.status FROM SalesOrder s")
    List<Object[]> orderNosAndStatus();

    /**
     * v9.5（ATP 轻量版）：各物料"已订未发"=已确认销售订单明细量 − 该单已确认出库量（物料级聚合，只剩正数）。
     * 短量关闭(CLOSED)订单不占需求（余量属有意不再发货）。
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT i.material_code, " +
            "SUM(i.qty) - COALESCE((SELECT SUM(so.qty) FROM sales_outbound so " +
            "WHERE so.sales_order_no = o.order_no AND so.material_code = i.material_code AND so.status = 'CONFIRMED'), 0) " +
            "FROM sales_order_item i JOIN sales_order o ON i.order_id = o.id " +
            "WHERE o.status = 'CONFIRMED' AND i.qty IS NOT NULL " +
            "GROUP BY i.material_code HAVING SUM(i.qty) - COALESCE((SELECT SUM(so.qty) FROM sales_outbound so " +
            "WHERE so.sales_order_no = o.order_no AND so.material_code = i.material_code AND so.status = 'CONFIRMED'), 0) > 0",
            nativeQuery = true)
    java.util.List<Object[]> openDemandByMaterial();
}
