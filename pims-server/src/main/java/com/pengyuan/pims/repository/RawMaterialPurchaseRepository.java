package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.RawMaterialPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface RawMaterialPurchaseRepository extends JpaRepository<RawMaterialPurchase, Long>, JpaSpecificationExecutor<RawMaterialPurchase> {
    List<RawMaterialPurchase> findByOrderNoStartingWithOrderByCreateTimeDesc(String prefix);

    /** v5.11：工作台最新采购订单（按创建时间倒序） */
    List<RawMaterialPurchase> findAllByOrderByCreateTimeDesc();
    Optional<RawMaterialPurchase> findTopByMaterialNameOrderByCreateTimeDesc(String materialName);
    Optional<RawMaterialPurchase> findByOrderNo(String orderNo);

    /** v5.95 多物料批量单：按 单号+物料 精确取行（同单号多行时 findByOrderNo 会 non-unique） */
    Optional<RawMaterialPurchase> findFirstByOrderNoAndMaterialCode(String orderNo, String materialCode);

    /** v5.73 到货带出含税单价：按合同号+物料精确取行 */
    java.util.Optional<RawMaterialPurchase> findFirstByOrderNoAndMaterialCodeAndUnitPriceIsNotNull(String orderNo, String materialCode);
    /** 按订单号查询全部明细（一个采购合同允许多个物料明细） */
    List<RawMaterialPurchase> findAllByOrderNo(String orderNo);

    /** 按订单号集合批量查（退货参照列表预取采购价用） */
    List<RawMaterialPurchase> findAllByOrderNoIn(java.util.Collection<String> orderNos);
    
    /** 查找未完全到货的订单（已审核且未关闭，到货数量为NULL或 < 采购数量） */
    @Query("SELECT r FROM RawMaterialPurchase r WHERE r.status = 'APPROVED' AND (r.receivedQty IS NULL OR r.receivedQty < r.qty)")
    List<RawMaterialPurchase> findIncompleteOrders();

    /** 按月聚合采购金额（最近N个月）；purchase_date 存毫秒时间戳，需转东八区日期再比较/格式化 */
    @Query(value = "SELECT strftime('%Y-%m', purchase_date/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(total_amount),0) AS amount " +
            "FROM raw_material_purchase WHERE purchase_date >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "AND status != 'DRAFT' GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyAmountSince(String sinceDate);

    /** 供应商采购金额TOP10（最近N个月） */
    @Query(value = "SELECT supplier_name, COALESCE(SUM(total_amount),0) AS amount " +
            "FROM raw_material_purchase WHERE purchase_date >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) AND supplier_name IS NOT NULL " +
            "GROUP BY supplier_name ORDER BY amount DESC LIMIT 10", nativeQuery = true)
    List<Object[]> topSuppliersSince(String sinceDate);

    /** 按物料大类聚合采购金额（最近N个月） */
    @Query(value = "SELECT category, COALESCE(SUM(total_amount),0) AS amount " +
            "FROM raw_material_purchase WHERE purchase_date >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) AND category IS NOT NULL " +
            "GROUP BY category ORDER BY amount DESC", nativeQuery = true)
    List<Object[]> categoryAmountSince(String sinceDate);

    /** 按物料编码查询采购价格历史（按日期升序，用于价格走势图） */
    List<RawMaterialPurchase> findByMaterialCodeAndUnitPriceIsNotNullOrderByPurchaseDateAsc(String materialCode);

    /** 采购价格对比明细（近12个月，非草稿）：物料/牌号/供应商/日期/单价 */
    @Query(value = "SELECT material_code, material_name, brand, supplier_name, unit_price, qty, total_amount, " +
            "date(purchase_date/1000, 'unixepoch', '+8 hours') AS purchase_date " +
            "FROM raw_material_purchase WHERE status != 'DRAFT' AND unit_price IS NOT NULL " +
            "AND purchase_date >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "ORDER BY material_code, purchase_date DESC", nativeQuery = true)
    List<Object[]> priceCompareSince(String sinceDate);

    /** 未到齐明细（已完成审核但到货数 < 采购数） */
    @Query(value = "SELECT order_no, supplier_name, material_code, material_name, qty, received_qty, total_amount, " +
            "date(purchase_date/1000, 'unixepoch', '+8 hours') AS purchase_date " +
            "FROM raw_material_purchase WHERE status != 'DRAFT' " +
            "AND (received_qty IS NULL OR received_qty < qty) ORDER BY order_no DESC", nativeQuery = true)
    List<Object[]> incompleteList();

    /** v5.9：到货完成率聚合（替代 findAll 内存累加）：总量/到货量/已齐数/总行数 */
    @Query(value = "SELECT COALESCE(SUM(qty),0), COALESCE(SUM(received_qty),0), " +
            "COALESCE(SUM(CASE WHEN COALESCE(received_qty,0) >= qty THEN 1 ELSE 0 END),0), COUNT(*) " +
            "FROM raw_material_purchase WHERE status != 'DRAFT'", nativeQuery = true)
    List<Object[]> completionStats();
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(order_no, INSTR(order_no,'-')+1) AS INTEGER)) FROM raw_material_purchase WHERE order_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
