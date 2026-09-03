package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProductionInbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductionInboundRepository extends JpaRepository<ProductionInbound, Long> {
    List<ProductionInbound> findByOrderByCreateTimeDesc();
    Optional<ProductionInbound> findByDocNo(String docNo);
    /** 按生产订单号查所有入库单（用于重复参照校验） */
    List<ProductionInbound> findByProductionOrderNo(String productionOrderNo);

    /** 生产进度：某订单已确认入库的总数量 */
    @Query(value = "SELECT COALESCE(SUM(qty), 0) FROM production_inbound WHERE production_order_no = ?1 AND status = 'DONE'", nativeQuery = true)
    BigDecimal sumDoneQtyByOrderNo(String productionOrderNo);

    /** v5.26：生产进度批量查询——全部已入库订单的入库量（GROUP BY 一次取回，替代逐单 N+1） */
    @Query(value = "SELECT production_order_no, COALESCE(SUM(qty), 0) FROM production_inbound WHERE status = 'DONE' GROUP BY production_order_no", nativeQuery = true)
    List<Object[]> sumDoneQtyGroupByOrderNo();

    /** 已入库（DONE/CONFIRMED，含待质检）的生产订单号集合（可参照订单过滤，替代逐单查询 N+1） */
    @Query("SELECT DISTINCT p.productionOrderNo FROM ProductionInbound p WHERE p.status IN ('DONE','CONFIRMED') AND p.productionOrderNo IS NOT NULL")
    List<String> findDoneOrConfirmedOrderNos();

    /** v5.9：得率/损耗报表——已确认入库单按时间过滤（替代 findAll 全表扫） */
    @Query("SELECT p FROM ProductionInbound p WHERE p.status = 'CONFIRMED' AND p.createTime >= ?1 ORDER BY p.createTime")
    List<ProductionInbound> findConfirmedSince(LocalDateTime sinceTime);

    /** v5.9：关键字分页搜索（docNo/productName/batchNo 模糊匹配） */
    @Query("SELECT o FROM ProductionInbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.productName LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<ProductionInbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM production_inbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
