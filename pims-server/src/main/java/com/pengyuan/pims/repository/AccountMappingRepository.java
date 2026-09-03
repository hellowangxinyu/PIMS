package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountMappingRepository extends JpaRepository<AccountMapping, Long> {

    List<AccountMapping> findAllByOrderByMapKeyAsc();

    Optional<AccountMapping> findByMapKey(String mapKey);
}
