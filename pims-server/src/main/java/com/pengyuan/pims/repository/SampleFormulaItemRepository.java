package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SampleFormulaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SampleFormulaItemRepository extends JpaRepository<SampleFormulaItem, Long> {

    List<SampleFormulaItem> findByFormulaIdOrderBySortOrder(Long formulaId);

    void deleteByFormulaId(Long formulaId);
}
