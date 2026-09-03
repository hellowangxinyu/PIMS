package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.StatOrderMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StatOrderMonthlyRepository extends JpaRepository<StatOrderMonthly, Long> {

    Optional<StatOrderMonthly> findByPeriodAndOrderType(String period, String orderType);

    /** 查某类型所有月份统计（按月份倒序） */
    List<StatOrderMonthly> findByOrderTypeOrderByPeriodDesc(String orderType);

    /** 查最近N个月的统计（所有类型） */
    @Query(value = "SELECT * FROM stat_order_monthly WHERE period >= ?1 ORDER BY period DESC, order_type", nativeQuery = true)
    List<StatOrderMonthly> findSince(String sincePeriod);

    /** 查所有统计 */
    List<StatOrderMonthly> findAllByOrderByPeriodDesc();
}
