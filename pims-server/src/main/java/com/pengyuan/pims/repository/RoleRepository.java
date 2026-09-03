package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /** v6.1.7：用户更新时的角色白名单校验（含自定义角色） */
    boolean existsByCode(String code);
    Optional<Role> findByCode(String code);
}
