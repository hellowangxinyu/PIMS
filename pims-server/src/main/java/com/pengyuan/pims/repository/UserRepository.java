package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    java.util.List<User> findByRole(String role);
}
