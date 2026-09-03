package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OtherOutbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface OtherOutboundRepository extends JpaRepository<OtherOutbound, Long> {
    List<OtherOutbound> findByOrderByCreateTimeDesc();
    List<OtherOutbound> findByMaterialCode(String materialCode);
    Optional<OtherOutbound> findByDocNo(String docNo);
    /** 按退货单 ID 查询出库单（防止重复出库） */
    Optional<OtherOutbound> findByReturnOrderId(Long returnOrderId);

    /** v5.9：关键字分页搜索（docNo/materialCode/materialName/batchNo 模糊匹配） */
    @Query("SELECT o FROM OtherOutbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.materialCode LIKE %:kw% OR o.materialName LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<OtherOutbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM other_outbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
