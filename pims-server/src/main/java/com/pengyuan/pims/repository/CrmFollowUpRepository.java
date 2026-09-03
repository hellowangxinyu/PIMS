package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.CrmFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CrmFollowUpRepository extends JpaRepository<CrmFollowUp, Long> {
    List<CrmFollowUp> findByOpportunityIdOrderByFollowDateDescIdDesc(Long opportunityId);
    List<CrmFollowUp> findByCustomerIdOrderByFollowDateDescIdDesc(Long customerId);
    List<CrmFollowUp> findByNextDateNotNullAndNextDateLessThanEqual(java.time.LocalDate date);
}
