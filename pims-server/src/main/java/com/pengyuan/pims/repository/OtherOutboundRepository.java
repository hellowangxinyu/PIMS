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
    @Query("SELECT o FROM OtherOutbound o WHERE (:kw = '' OR LOWER(o.docNo) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(o.materialCode) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(o.materialName) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(o.batchNo) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<OtherOutbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, LENGTH(doc_no)-3, 4) AS INTEGER)) FROM other_outbound WHERE LOWER(doc_no) LIKE LOWER(?1)", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
