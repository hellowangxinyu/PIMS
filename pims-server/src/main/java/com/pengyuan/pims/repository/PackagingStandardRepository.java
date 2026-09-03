package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PackagingStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackagingStandardRepository extends JpaRepository<PackagingStandard, Long> {
    List<PackagingStandard> findByOrderByIdAsc();
}
