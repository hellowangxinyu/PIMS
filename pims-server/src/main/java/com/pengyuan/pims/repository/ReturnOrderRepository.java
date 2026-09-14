package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ReturnOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, Long> {

    /** 按状态查询（采购员看待审核、仓管看待出库） */
        long countByTypeAndStatus(String type, String status);
List<ReturnOrder> findByStatusOrderByCreateTimeDesc(String status);
    java.util.List<ReturnOrder> findByCustomerIdOrderByCreateTimeDesc(Long customerId);
    /** 客户最近 N 条退货（客户360°有界查询，不拉全量） */
    java.util.List<ReturnOrder> findByCustomerIdOrderByCreateTimeDesc(Long customerId, org.springframework.data.domain.Pageable pageable);
    java.util.List<ReturnOrder> findBySupplierIdOrderByCreateTimeDesc(Long supplierId);

    /** 按退货类型查询（采购退货 PURCHASE_RETURN / 销售退货 SALES_RETURN） */
    List<ReturnOrder> findByTypeOrderByCreateTimeDesc(String type);

    /** 按类型 + 状态查询 */
    List<ReturnOrder> findByTypeAndStatusOrderByCreateTimeDesc(String type, String status);

    /** 按质检单号查询（防止 QC 重复生成退货单） */
    Optional<ReturnOrder> findByQcInspectionNo(String qcInspectionNo);

    /** 按原采购到货单 ID 查询 */
    List<ReturnOrder> findByRefArrivalId(Long refArrivalId);

    /** v6.1：按类型 + 参照销售出库单号查（油尾退回累计退回量防超校验） */
    List<ReturnOrder> findByTypeAndRefSalesOutboundNo(String type, String refSalesOutboundNo);

    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM return_order WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    // ==================== v9.4 报表退货冲减（审计口径缺陷：退货只冲应收不冲报表，收入虚高） ====================

    /** 月度退货冲减：(period, 负收入, 负成本, 负数量)——成本按原出库（ref_sales_outbound_no+物料）加权单位成本回溯 */
    @org.springframework.data.jpa.repository.Query(value = "SELECT strftime('%Y-%m', r.create_time/1000, 'unixepoch', '+8 hours') AS period, " +
            "COALESCE(SUM(-COALESCE(r.return_amount, r.qty * r.unit_price, 0)), 0), " +
            "COALESCE(SUM(-(r.qty * COALESCE((SELECT CASE WHEN SUM(so2.qty) > 0 THEN SUM(so2.cost) * 1.0 / SUM(so2.qty) ELSE 0 END " +
            "FROM sales_outbound so2 WHERE so2.doc_no = r.ref_sales_outbound_no AND so2.material_code = r.material_code AND so2.status = 'CONFIRMED'), 0))), 0), " +
            "COALESCE(SUM(-r.qty), 0) " +
            "FROM return_order r " +
            "WHERE r.type = 'SALES_RETURN' AND r.status = 'DONE' " +
            "AND r.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY period", nativeQuery = true)
    java.util.List<Object[]> monthlySalesReturnSince(String sinceDate);

    /** 客户维度退货冲减（负收入） */
    @org.springframework.data.jpa.repository.Query(value = "SELECT r.customer_name, COALESCE(SUM(-COALESCE(r.return_amount, r.qty * r.unit_price, 0)), 0) " +
            "FROM return_order r " +
            "WHERE r.type = 'SALES_RETURN' AND r.status = 'DONE' AND r.customer_name IS NOT NULL " +
            "AND r.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY r.customer_name", nativeQuery = true)
    java.util.List<Object[]> customerReturnRankSince(String sinceDate);

    /** 产品维度退货冲减（负收入） */
    @org.springframework.data.jpa.repository.Query(value = "SELECT r.material_name, COALESCE(SUM(-COALESCE(r.return_amount, r.qty * r.unit_price, 0)), 0) " +
            "FROM return_order r " +
            "WHERE r.type = 'SALES_RETURN' AND r.status = 'DONE' AND r.material_name IS NOT NULL " +
            "AND r.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY r.material_name", nativeQuery = true)
    java.util.List<Object[]> materialReturnRankSince(String sinceDate);

}
