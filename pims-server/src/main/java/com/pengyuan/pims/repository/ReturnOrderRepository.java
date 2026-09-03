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

}
