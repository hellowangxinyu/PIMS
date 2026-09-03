package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.CrmOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CrmOpportunityRepository extends JpaRepository<CrmOpportunity, Long> {
    List<CrmOpportunity> findAllByOrderByCreateTimeDescIdDesc();
    List<CrmOpportunity> findByStageOrderByCreateTimeDesc(String stage);
    List<CrmOpportunity> findByStageNotInOrderByCreateTimeDesc(List<String> stages);
}
