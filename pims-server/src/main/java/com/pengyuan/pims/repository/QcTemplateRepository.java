package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.QcTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QcTemplateRepository extends JpaRepository<QcTemplate, Long> {

    List<QcTemplate> findByOrderByCreateTimeDesc();

    List<QcTemplate> findByApplyCategoryOrderByIsDefaultDescCreateTimeDesc(String applyCategory);

    /** v5.81 三维打分匹配：取大类下全部启用模板 */
    java.util.List<QcTemplate> findByApplyCategoryAndEnabledTrue(String applyCategory);

    Optional<QcTemplate> findFirstByApplyCategoryAndIsDefaultTrueAndEnabledTrueOrderByCreateTimeAsc(String applyCategory);

    boolean existsByApplyCategory(String applyCategory);
}
