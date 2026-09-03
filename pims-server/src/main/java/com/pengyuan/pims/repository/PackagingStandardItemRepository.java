package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PackagingStandardItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackagingStandardItemRepository extends JpaRepository<PackagingStandardItem, Long> {
    List<PackagingStandardItem> findByPackagingIdOrderBySortOrderAscIdAsc(Long packagingId);
    void deleteByPackagingId(Long packagingId);
}
