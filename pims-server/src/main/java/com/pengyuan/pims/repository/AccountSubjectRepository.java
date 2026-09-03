package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.AccountSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountSubjectRepository extends JpaRepository<AccountSubject, Long> {

    List<AccountSubject> findAllByOrderByCodeAsc();

    Optional<AccountSubject> findByCode(String code);

    List<AccountSubject> findByParentCode(String parentCode);
}
