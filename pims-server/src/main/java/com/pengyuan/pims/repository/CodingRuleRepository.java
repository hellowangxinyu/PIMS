package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.CodingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodingRuleRepository extends JpaRepository<CodingRule, Long> {
    List<CodingRule> findByEnabledTrueOrderByCategoryCodeAscSubCategoryCodeAsc();
    Optional<CodingRule> findBySubCategoryCodeAndEnabledTrue(String subCategoryCode);
}
