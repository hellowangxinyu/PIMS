package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SalaryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalaryItemRepository extends JpaRepository<SalaryItem, Long> {

    List<SalaryItem> findBySheetIdOrderByIdAsc(Long sheetId);

    void deleteBySheetId(Long sheetId);

    boolean existsByEmployeeId(Long employeeId);
}
