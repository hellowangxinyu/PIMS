package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {

    List<QuotationItem> findByQuotationId(Long quotationId);

    void deleteByQuotationId(Long quotationId);
}
