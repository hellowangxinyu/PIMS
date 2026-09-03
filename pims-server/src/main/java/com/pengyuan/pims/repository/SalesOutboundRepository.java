package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SalesOutbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SalesOutboundRepository extends JpaRepository<SalesOutbound, Long> {
    List<SalesOutbound> findByOrderByCreateTimeDesc();

    /** v5.27：按单号查（销售退货参照出库单） */
    Optional<SalesOutbound> findByDocNo(String docNo);

    /**
     * 月度销售统计（出库口径）：收入 = 实发数量 × 订单明细单价，成本 = 出库单成本 + 当月公司承担运费。
     * v5.66 运费归集：两层聚合——出库按月 SUM 后 LEFT JOIN 按月的 Σ公司承担运费（ship_date 归月）加进 cost；
     * 不做行级 JOIN（一单多行出库会把运费加倍）。
     * create_time 存毫秒时间戳，需转东八区日期再比较/格式化
     */
    @Query(value = "SELECT t.period, t.income, t.cost + COALESCE(f.freight, 0) AS cost, t.qty FROM (" +
            "SELECT strftime('%Y-%m', so.create_time/1000, 'unixepoch', '+8 hours') AS period, " +
            "COALESCE(SUM(so.qty * si.unit_price), 0) AS income, COALESCE(SUM(so.cost), 0) AS cost, COALESCE(SUM(so.qty), 0) AS qty " +
            "FROM sales_outbound so " +
            "LEFT JOIN sales_order o ON so.sales_order_no = o.order_no " +
            "LEFT JOIN sales_order_item si ON si.order_id = o.id AND si.material_code = so.material_code " +
            "WHERE so.status = 'CONFIRMED' AND so.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY period) t " +
            "LEFT JOIN (SELECT strftime('%Y-%m', ship_date) AS period, SUM(freight) AS freight " +
            "FROM shipping_log WHERE borne = 'COMPANY' AND ship_date >= ?1 GROUP BY strftime('%Y-%m', ship_date)) f " +
            "ON f.period = t.period ORDER BY t.period", nativeQuery = true)
    List<Object[]> monthlySalesSince(String sinceDate);

    /** 客户销售TOP10（按出库收入，附带成本用于毛利；v5.66 cost 含该客户订单的公司承担运费——两层聚合防重复） */
    @Query(value = "SELECT t.customer_name, t.income, t.cost + COALESCE(f.freight, 0) AS cost FROM (" +
            "SELECT so.customer_name, COALESCE(SUM(so.qty * si.unit_price), 0) AS income, COALESCE(SUM(so.cost), 0) AS cost " +
            "FROM sales_outbound so " +
            "LEFT JOIN sales_order o ON so.sales_order_no = o.order_no " +
            "LEFT JOIN sales_order_item si ON si.order_id = o.id AND si.material_code = so.material_code " +
            "WHERE so.status = 'CONFIRMED' AND so.customer_name IS NOT NULL " +
            "AND so.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY so.customer_name) t " +
            "LEFT JOIN (SELECT o2.customer_name AS customer_name, SUM(l.freight) AS freight " +
            "FROM shipping_log l JOIN sales_order o2 ON l.sales_order_no = o2.order_no " +
            "WHERE l.borne = 'COMPANY' AND l.ship_date >= ?1 GROUP BY o2.customer_name) f " +
            "ON f.customer_name = t.customer_name ORDER BY t.income DESC LIMIT 10", nativeQuery = true)
    List<Object[]> customerRankSince(String sinceDate);

    /** 产品销售TOP10（按出库收入，附带成本用于毛利；运费为订单级费用不按产品摊分，产品成本不含运费——报表注明） */
    @Query(value = "SELECT so.material_name, COALESCE(SUM(so.qty * si.unit_price), 0) AS income, COALESCE(SUM(so.cost), 0) AS cost " +
            "FROM sales_outbound so " +
            "LEFT JOIN sales_order o ON so.sales_order_no = o.order_no " +
            "LEFT JOIN sales_order_item si ON si.order_id = o.id AND si.material_code = so.material_code " +
            "WHERE so.status = 'CONFIRMED' AND so.material_name IS NOT NULL " +
            "AND so.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY so.material_name ORDER BY income DESC LIMIT 10", nativeQuery = true)
    List<Object[]> materialRankSince(String sinceDate);

    /** 制单人销售排行（系统无业务员字段，用制单人近似） */
    @Query(value = "SELECT so.created_by, COALESCE(SUM(so.qty * si.unit_price), 0) AS income " +
            "FROM sales_outbound so " +
            "LEFT JOIN sales_order o ON so.sales_order_no = o.order_no " +
            "LEFT JOIN sales_order_item si ON si.order_id = o.id AND si.material_code = so.material_code " +
            "WHERE so.status = 'CONFIRMED' AND so.created_by IS NOT NULL " +
            "AND so.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY so.created_by ORDER BY income DESC LIMIT 10", nativeQuery = true)
    List<Object[]> salesmanRankSince(String sinceDate);

    /** v5.9：关键字分页搜索（docNo/materialCode/materialName/batchNo 模糊匹配） */
    @Query("SELECT o FROM SalesOutbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.materialCode LIKE %:kw% OR o.materialName LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<SalesOutbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM sales_outbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
