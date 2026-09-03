package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.OutsourceMaterialConsume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutsourceMaterialConsumeRepository extends JpaRepository<OutsourceMaterialConsume, Long> {
    List<OutsourceMaterialConsume> findByOutsourceOrderNo(String orderNo);
}
