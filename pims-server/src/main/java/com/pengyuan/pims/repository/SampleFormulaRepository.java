package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SampleFormula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SampleFormulaRepository extends JpaRepository<SampleFormula, Long> {

    Optional<SampleFormula> findBySampleRequestId(Long sampleRequestId);

    List<SampleFormula> findAllByOrderByCreateTimeDescIdDesc();

    /** 转制漆导入下拉：未转过的配方（converted_recipe_id IS NULL），按新到旧 */
    @Query("select f from SampleFormula f where f.convertedRecipeId is null order by f.id desc")
    List<SampleFormula> findUnconverted();

    /** v7.7 取号：FY-日期-NNNN 前缀最大序号（并发防重，WriteQueue 锁内调用） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(formula_no, -4) AS INTEGER)) FROM sample_formula WHERE formula_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
