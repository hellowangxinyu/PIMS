package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SalesOrderChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalesOrderChangeLogRepository extends JpaRepository<SalesOrderChangeLog, Long> {
    List<SalesOrderChangeLog> findByOrderIdOrderByCreateTimeDesc(Long orderId);
}
