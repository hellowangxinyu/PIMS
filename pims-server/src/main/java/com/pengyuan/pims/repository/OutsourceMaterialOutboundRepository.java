package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OutsourceMaterialOutbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OutsourceMaterialOutboundRepository extends JpaRepository<OutsourceMaterialOutbound, Long> {
    List<OutsourceMaterialOutbound> findByOutsourceOrderNo(String orderNo);
    List<OutsourceMaterialOutbound> findByOrderByCreateTimeDesc();

    /** 已发料（CONFIRMED 出库单）的委外订单号集合（可参照订单过滤，替代逐单查询 N+1） */
    @Query("SELECT DISTINCT o.outsourceOrderNo FROM OutsourceMaterialOutbound o WHERE o.status = 'CONFIRMED' AND o.outsourceOrderNo IS NOT NULL")
    List<String> findConfirmedOrderNos();

    /** v4.8：按委外订单汇总实际材料成本（数据库 GROUP BY，替代全表拉取后内存累加） */
    @Query("SELECT o.outsourceOrderNo, SUM(o.cost) FROM OutsourceMaterialOutbound o " +
           "WHERE o.status = 'CONFIRMED' AND o.cost IS NOT NULL GROUP BY o.outsourceOrderNo")
    List<Object[]> sumCostGroupByOutsourceOrderNo();

    /** v5.9：关键字分页搜索（docNo/materialCode/batchNo 模糊匹配） */
    @Query("SELECT o FROM OutsourceMaterialOutbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.materialCode LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<OutsourceMaterialOutbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM outsource_material_outbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
