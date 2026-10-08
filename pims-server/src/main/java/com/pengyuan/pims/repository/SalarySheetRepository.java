package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SalarySheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SalarySheetRepository extends JpaRepository<SalarySheet, Long> {

    List<SalarySheet> findAllByOrderByPeriodDescIdDesc();

    Optional<SalarySheet> findByPeriod(String period);

    /** 取指定前缀最大单号序号（SAL-YYYYMM-NNNN 按期间编序） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, LENGTH(doc_no)-3, 4) AS INTEGER)) FROM salary_sheet WHERE LOWER(doc_no) LIKE LOWER(?1)", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
