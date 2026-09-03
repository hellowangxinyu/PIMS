package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProcessStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessStepRepository extends JpaRepository<ProcessStep, Long> {
    List<ProcessStep> findByStageIdOrderBySortOrderAsc(Long stageId);
    void deleteByStageIdIn(List<Long> stageIds);
}
