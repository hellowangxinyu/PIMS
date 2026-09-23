package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.QualityInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QualityInspectionRepository extends JpaRepository<QualityInspection, Long>, JpaSpecificationExecutor<QualityInspection> {

    List<QualityInspection> findByOrderByCreateTimeDesc();

    List<QualityInspection> findByTypeOrderByCreateTimeDesc(String type);

    /** 按类型全部质检单（批量预取分组用，无排序——与逐单 findByRefDocNoAndType 的行序一致） */
    List<QualityInspection> findByType(String type);

        long countByStatus(String status);
    long countByStatusAndRefDocType(String status, String refDocType);
List<QualityInspection> findByStatusOrderByCreateTimeDesc(String status);

    List<QualityInspection> findByTypeAndStatusOrderByCreateTimeDesc(String type, String status);

    Optional<QualityInspection> findByInspectionNo(String inspectionNo);

    List<QualityInspection> findByRefDocNoAndType(String refDocNo, String type);

    List<QualityInspection> findByArrivalId(Long arrivalId);

    /** 查询某物料+批次是否有合格/让步的质检记录 */
    List<QualityInspection> findByMaterialCodeAndBatchNoAndStatusIn(String materialCode, String batchNo, List<String> statuses);

    /** v5.37：某物料+批次+状态（复检防重复发起用） */
    List<QualityInspection> findByMaterialCodeAndBatchNoAndStatusOrderByCreateTimeDesc(String materialCode, String batchNo, String status);

    /** v5.39：某物料+批次的全部复检单（任意状态，自动触发防重复用——判过的批次不再自动建单） */
    List<QualityInspection> findByMaterialCodeAndBatchNoAndRefDocTypeOrderByCreateTimeDesc(String materialCode, String batchNo, String refDocType);

    List<QualityInspection> findByMaterialCodeAndStatusIn(String materialCode, List<String> statuses);

    /** 月度质检单数与判定分布（近N个月） */
    @Query(value = "SELECT strftime('%Y-%m', create_time/1000, 'unixepoch', '+8 hours') AS period, status, COUNT(*) " +
            "FROM quality_inspection WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY period, status ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyResultSince(String sinceDate);

    /** 不合格（退货）物料TOP10 */
    @Query(value = "SELECT material_name, COUNT(*) FROM quality_inspection " +
            "WHERE status = 'REJECT' AND material_name IS NOT NULL " +
            "AND create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY material_name ORDER BY COUNT(*) DESC LIMIT 10", nativeQuery = true)
    List<Object[]> rejectMaterialTop(String sinceDate);

    /** 质检明细（近N个月，v5.9 替代 findAll+内存过滤；inspect_date 毫秒由 Java 转字符串——SQLite date() 结果列有 JDBC 类型推断坑） */
    @Query(value = "SELECT inspection_no, ref_doc_type, ref_doc_no, material_name, material_code, batch_no, " +
            "material_category, qty, status, inspector, inspect_date, result_remark " +
            "FROM quality_inspection WHERE create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "ORDER BY create_time DESC", nativeQuery = true)
    List<Object[]> detailSince(String sinceDate);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(inspection_no, -4) AS INTEGER)) FROM quality_inspection WHERE inspection_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
