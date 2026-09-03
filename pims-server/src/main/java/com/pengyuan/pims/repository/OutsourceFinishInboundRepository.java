package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OutsourceFinishInbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OutsourceFinishInboundRepository extends JpaRepository<OutsourceFinishInbound, Long> {
    List<OutsourceFinishInbound> findByOutsourceOrderNo(String orderNo);
    List<OutsourceFinishInbound> findByOrderByCreateTimeDesc();
    Optional<OutsourceFinishInbound> findByDocNo(String docNo);

    /** 委外订单进度：某订单已确认入库的总数量 */
    @Query(value = "SELECT COALESCE(SUM(qty), 0) FROM outsource_finish_inbound WHERE outsource_order_no = ?1 AND status = 'DONE'", nativeQuery = true)
    BigDecimal sumDoneQtyByOrderNo(String outsourceOrderNo);

    /** v5.26：委外订单进度批量查询——全部已入库订单的入库量（GROUP BY 一次取回，替代逐单 N+1） */
    @Query(value = "SELECT outsource_order_no, COALESCE(SUM(qty), 0) FROM outsource_finish_inbound WHERE status = 'DONE' GROUP BY outsource_order_no", nativeQuery = true)
    List<Object[]> sumDoneQtyGroupByOrderNo();

    /** 已入库（DONE/CONFIRMED，含待质检）的委外订单号集合（可参照订单过滤，替代逐单查询 N+1） */
    @Query("SELECT DISTINCT f.outsourceOrderNo FROM OutsourceFinishInbound f WHERE f.status IN ('DONE','CONFIRMED') AND f.outsourceOrderNo IS NOT NULL")
    List<String> findDoneOrConfirmedOrderNos();

    /** 代工厂得率统计（已完成入库单据）：批次数、平均得率、入库总量 */
    @Query(value = "SELECT o.processor, COUNT(*), COALESCE(AVG(f.yield_rate), 0), COALESCE(SUM(f.qty), 0) " +
            "FROM outsource_finish_inbound f JOIN outsource_order o ON f.outsource_order_no = o.order_no " +
            "WHERE f.status = 'DONE' AND o.processor IS NOT NULL AND trim(o.processor) != '' " +
            "GROUP BY o.processor ORDER BY AVG(f.yield_rate) DESC", nativeQuery = true)
    List<Object[]> processorYield();

    /** v5.9：得率/损耗报表——已确认入库单按时间过滤（替代 findAll 全表扫） */
    @Query("SELECT f FROM OutsourceFinishInbound f WHERE f.status = 'CONFIRMED' AND f.createTime >= ?1 ORDER BY f.createTime")
    List<OutsourceFinishInbound> findConfirmedSince(java.time.LocalDateTime sinceTime);

    /** v5.9：关键字分页搜索（docNo/productName/batchNo 模糊匹配） */
    @Query("SELECT o FROM OutsourceFinishInbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.productName LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<OutsourceFinishInbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM outsource_finish_inbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
