package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.FinishedProductPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FinishedProductPurchaseRepository extends JpaRepository<FinishedProductPurchase, Long>, JpaSpecificationExecutor<FinishedProductPurchase> {
    java.util.Optional<FinishedProductPurchase> findByOrderNo(String orderNo);

    /** v5.95 多物料批量单：按 单号+物料 精确取行（同单号多行时 findByOrderNo 会 non-unique） */
    java.util.Optional<FinishedProductPurchase> findFirstByOrderNoAndMaterialCode(String orderNo, String materialCode);

    /** v5.73 到货带出含税单价 */
    java.util.Optional<FinishedProductPurchase> findFirstByOrderNoAndMaterialCodeAndUnitPriceIsNotNull(String orderNo, String materialCode);

    /** v5.11：工作台最新采购订单（按创建时间倒序） */
    List<FinishedProductPurchase> findAllByOrderByCreateTimeDesc();
    /** 按订单号查询全部明细（一个采购合同允许多个物料明细） */
    List<FinishedProductPurchase> findAllByOrderNo(String orderNo);

    /** 按订单号集合批量查（退货参照列表预取采购价用） */
    List<FinishedProductPurchase> findAllByOrderNoIn(java.util.Collection<String> orderNos);
    
    /** 查找未完全到货的订单（到货数量为NULL或 < 采购数量） */
    @Query("SELECT f FROM FinishedProductPurchase f WHERE f.status = 'APPROVED' AND (f.receivedQty IS NULL OR f.receivedQty < f.qty)")
    List<FinishedProductPurchase> findIncompleteOrders();

    /** 月度成品采购金额（近N个月） */
    @Query(value = "SELECT strftime('%Y-%m', purchase_date/1000, 'unixepoch', '+8 hours') AS period, COALESCE(SUM(total_amount), 0) " +
            "FROM finished_product_purchase WHERE purchase_date >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "AND status != 'DRAFT' GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyAmountSince(String sinceDate);

    /** 未到齐明细（已完成审核但到货数 < 采购数） */
    @Query(value = "SELECT order_no, supplier_name, material_code, material_name, qty, received_qty, total_amount, " +
            "date(purchase_date/1000, 'unixepoch', '+8 hours') AS purchase_date " +
            "FROM finished_product_purchase WHERE status != 'DRAFT' " +
            "AND (received_qty IS NULL OR received_qty < qty) ORDER BY order_no DESC", nativeQuery = true)
    List<Object[]> incompleteList();

    /** v5.9：到货完成率聚合（替代 findAll 内存累加） */
    @Query(value = "SELECT COALESCE(SUM(qty),0), COALESCE(SUM(received_qty),0), " +
            "COALESCE(SUM(CASE WHEN COALESCE(received_qty,0) >= qty THEN 1 ELSE 0 END),0), COUNT(*) " +
            "FROM finished_product_purchase WHERE status != 'DRAFT'", nativeQuery = true)
    List<Object[]> completionStats();
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(order_no, INSTR(order_no,'-')+1) AS INTEGER)) FROM finished_product_purchase WHERE order_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
