package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.RdProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RdProgressRepository extends JpaRepository<RdProgress, Long> {
    List<RdProgress> findAllByOrderByRaiseDateDescIdDesc();
}
