package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.DictItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictItemRepository extends JpaRepository<DictItem, Long> {
    List<DictItem> findByTypeAndEnabledTrueOrderBySortOrderAsc(String type);
    List<DictItem> findByEnabledTrueOrderByTypeAscSortOrderAsc();
}
