package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {
    List<SalesOrderItem> findByOrderId(Long orderId);

    /** 按订单集合批量查明细（列表聚合用，替代全表明细加载后内存分组） */
    List<SalesOrderItem> findByOrderIdIn(Iterable<Long> orderIds);

    /** v5.4：按销售订单 + 物料编码查询（销售退货入库回写 return_qty 用） */
    List<SalesOrderItem> findByOrderIdAndMaterialCode(Long orderId, String materialCode);

    /** v5.52 客户+物料最近成交价（非草稿订单，取最新一笔）：order_no, order_date, unit_price */
    @Query(value = "SELECT o.order_no, o.order_date, si.unit_price FROM sales_order_item si " +
            "JOIN sales_order o ON si.order_id = o.id " +
            "WHERE o.customer_id = ?1 AND si.material_code = ?2 AND o.status != 'DRAFT' " +
            "ORDER BY o.create_time DESC LIMIT 1", nativeQuery = true)
    List<Object[]> findLatestPrice(Long customerId, String materialCode);

    /** v5.52 客户各物料最新成交价（窗口函数去重）：material_code, material_name, unit_price, order_no, order_date */
    @Query(value = "SELECT material_code, material_name, unit_price, order_no, order_date FROM (" +
            "SELECT si.material_code AS material_code, si.material_name AS material_name, si.unit_price AS unit_price, " +
            "o.order_no AS order_no, o.order_date AS order_date, " +
            "ROW_NUMBER() OVER (PARTITION BY si.material_code ORDER BY o.create_time DESC) rn " +
            "FROM sales_order_item si JOIN sales_order o ON si.order_id = o.id " +
            "WHERE o.customer_id = ?1 AND o.status != 'DRAFT') WHERE rn = 1 ORDER BY material_code", nativeQuery = true)
    List<Object[]> latestPriceList(Long customerId);

    /** 销售订单执行清单：订单+明细+发货/退货数量（完成率在前端计算；日期列返回毫秒，由 Java 转字符串——SQLite date() 结果列会触发 JDBC 类型推断错误） */
    @Query(value = "SELECT o.order_no, o.customer_name, o.status, " +
            "o.order_date, o.expected_ship_date, " +
            "si.material_code, si.material_name, si.qty, si.shipped_qty, si.return_qty " +
            "FROM sales_order_item si JOIN sales_order o ON si.order_id = o.id " +
            "WHERE o.status != 'DRAFT' ORDER BY o.order_no DESC, si.id", nativeQuery = true)
    List<Object[]> orderExecList();
}
