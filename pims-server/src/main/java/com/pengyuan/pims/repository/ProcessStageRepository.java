package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProcessStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessStageRepository extends JpaRepository<ProcessStage, Long> {
    List<ProcessStage> findByTemplateIdOrderBySortOrderAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
