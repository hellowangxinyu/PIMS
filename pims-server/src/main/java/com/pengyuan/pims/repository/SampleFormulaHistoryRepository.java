package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SampleFormulaHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SampleFormulaHistoryRepository extends JpaRepository<SampleFormulaHistory, Long> {

    List<SampleFormulaHistory> findByFormulaIdOrderByCreateTimeAsc(Long formulaId);
}
