package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByOrderByIdAsc();

    List<Employee> findByStatusOrderByIdAsc(String status);
}
