package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.LossLetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LossLetterTemplateRepository extends JpaRepository<LossLetterTemplate, Long> {

    /** 默认模板置顶 */
    List<LossLetterTemplate> findAllByOrderByIsDefaultDescIdAsc();

    Optional<LossLetterTemplate> findByIsDefaultTrue();

    List<LossLetterTemplate> findByEnabledTrueOrderByIsDefaultDescIdAsc();
}
