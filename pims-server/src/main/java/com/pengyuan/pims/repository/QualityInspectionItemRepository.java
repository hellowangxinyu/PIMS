package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.QualityInspectionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QualityInspectionItemRepository extends JpaRepository<QualityInspectionItem, Long> {

    List<QualityInspectionItem> findByInspectionIdOrderBySortOrderAscIdAsc(Long inspectionId);

    List<QualityInspectionItem> findByInspectionIdInOrderByInspectionIdAscSortOrderAscIdAsc(Collection<Long> inspectionIds);

    void deleteByInspectionId(Long inspectionId);
}
