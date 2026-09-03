package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.QcTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QcTemplateItemRepository extends JpaRepository<QcTemplateItem, Long> {

    List<QcTemplateItem> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);

    void deleteByTemplateId(Long templateId);
}
