package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PurchaseArrival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PurchaseArrivalRepository extends JpaRepository<PurchaseArrival, Long> {
    List<PurchaseArrival> findByRefOrderNo(String refOrderNo);

    /** v5.96 到货单号取当日最大序号（INSTR 取 '-' 后全串，序号过 99 不错乱） */
    @org.springframework.data.jpa.repository.Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, 14) AS INTEGER)) FROM purchase_arrival WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxArrivalSeq(String prefix);


    /** v5.95.2 到货合格回填批号：单号+物料+数量精确定位到货行 */
    java.util.List<PurchaseArrival> findByRefOrderNoAndMaterialCodeAndQty(String refOrderNo, String materialCode, java.math.BigDecimal qty);
    List<PurchaseArrival> findByTypeOrderByCreateTimeDesc(String type);

    /** v5.9：按类型分页（到货列表） */
    org.springframework.data.domain.Page<PurchaseArrival> findByTypeOrderByCreateTimeDesc(String type, org.springframework.data.domain.Pageable pageable);

    /** v5.9：按类型 + 关键字分页（到货明细搜索，关键字覆盖合同号/供应商/编码/品名） */
    @Query("SELECT a FROM PurchaseArrival a WHERE a.type = :type AND (:kw = '' OR a.refOrderNo LIKE %:kw% " +
            "OR a.supplierName LIKE %:kw% OR a.materialCode LIKE %:kw% OR a.materialName LIKE %:kw%) " +
            "ORDER BY a.createTime DESC")
    org.springframework.data.domain.Page<PurchaseArrival> searchByTypeAndKeyword(
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);


    /** v5.9：关键字分页搜索（refOrderNo/supplierName/materialCode/materialName 模糊匹配） */
    @Query("SELECT o FROM PurchaseArrival o WHERE (:kw = '' OR o.refOrderNo LIKE %:kw% OR o.supplierName LIKE %:kw% OR o.materialCode LIKE %:kw% OR o.materialName LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<PurchaseArrival> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);

    /** v5.95.1 到货审核回写采购行：按 单号+物料 汇总已审核到货量 */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(a.qty),0) FROM PurchaseArrival a WHERE a.refOrderNo = ?1 AND a.materialCode = ?2 AND a.status = 'APPROVED'")
    java.math.BigDecimal sumApprovedQtyByOrderNoAndMaterial(String refOrderNo, String materialCode);

    /** v8.0（P0-8）：超收校验——排除指定到货单的已审核量（审核本单前算"其他单"的合计） */
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.qty),0) from PurchaseArrival a " +
            "where a.refOrderNo = ?1 and a.materialCode = ?2 and a.status = 'APPROVED' and a.id <> ?3")
    java.math.BigDecimal sumApprovedQtyByOrderNoAndMaterialExcluding(String refOrderNo, String materialCode, Long excludeId);
}