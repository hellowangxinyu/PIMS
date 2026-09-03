package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProductionOrderException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductionOrderExceptionRepository extends JpaRepository<ProductionOrderException, Long> {
    Optional<ProductionOrderException> findByOrderNo(String orderNo);
}
