package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProcessQcItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessQcItemRepository extends JpaRepository<ProcessQcItem, Long> {
    List<ProcessQcItem> findByStageIdOrderBySortOrderAsc(Long stageId);
    void deleteByStageIdIn(List<Long> stageIds);
}
