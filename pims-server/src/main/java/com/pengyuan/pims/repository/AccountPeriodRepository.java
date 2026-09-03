package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.AccountPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountPeriodRepository extends JpaRepository<AccountPeriod, Long> {

    Optional<AccountPeriod> findByPeriod(String period);

    List<AccountPeriod> findAllByOrderByPeriodDesc();
}
