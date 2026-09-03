package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.CrmContact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CrmContactRepository extends JpaRepository<CrmContact, Long> {
    List<CrmContact> findAllByOrderByCreateTimeDescIdDesc();
    List<CrmContact> findByCustomerIdOrderByIsPrimaryDescCreateTimeDesc(Long customerId);
}
