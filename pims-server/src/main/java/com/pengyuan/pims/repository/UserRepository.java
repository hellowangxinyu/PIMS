package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    java.util.List<User> findByRole(String role);

    /** v7.7 派发人选：启用用户（sample:read 即可拉取，无需 user:read） */
    java.util.List<User> findByEnabledTrue();
}
