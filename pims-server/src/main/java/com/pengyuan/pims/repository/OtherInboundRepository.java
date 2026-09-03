package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OtherInbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface OtherInboundRepository extends JpaRepository<OtherInbound, Long> {
    List<OtherInbound> findByOrderByCreateTimeDesc();
    List<OtherInbound> findByMaterialCode(String materialCode);
    Optional<OtherInbound> findByDocNo(String docNo);

    /** v5.4：按退货关联 ID 查询（防止销售退货重复入库） */
    Optional<OtherInbound> findByReturnRefId(Long returnRefId);

    /** v5.9：关键字分页搜索（docNo/materialCode/materialName/batchNo 模糊匹配） */
    @Query("SELECT o FROM OtherInbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.materialCode LIKE %:kw% OR o.materialName LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<OtherInbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM other_inbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
