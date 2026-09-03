package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SupplierQualityTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SupplierQualityTraceRepository extends JpaRepository<SupplierQualityTrace, Long> {

    List<SupplierQualityTrace> findAllByOrderByCreateTimeDescIdDesc();

    List<SupplierQualityTrace> findByStatusOrderByCreateTimeDescIdDesc(String status);

    List<SupplierQualityTrace> findBySupplierIdOrderByCreateTimeDescIdDesc(Long supplierId);

    List<SupplierQualityTrace> findBySupplierIdAndStatusOrderByCreateTimeDescIdDesc(Long supplierId, String status);

    /** 取指定前缀最大单号序号（防并发撞号 + 删除不错位），照 CustomerComplaintRepository 口径 */
    @Query(value = "SELECT MAX(CAST(SUBSTR(trace_no, -4) AS INTEGER)) FROM supplier_quality_trace WHERE trace_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
