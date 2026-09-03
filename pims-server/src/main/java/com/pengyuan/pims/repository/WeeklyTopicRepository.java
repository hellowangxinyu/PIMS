package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.WeeklyTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WeeklyTopicRepository extends JpaRepository<WeeklyTopic, Long> {
        java.util.List<WeeklyTopic> findByClosedDateIsNull();
    /** 未关闭且计划日早于指定日的议题数（仪表盘逾期议题，替代拉全量内存计数） */
    long countByClosedDateIsNullAndPlanDateBefore(java.time.LocalDate date);
List<WeeklyTopic> findAllByOrderByPlanDateDescIdDesc();
    List<WeeklyTopic> findByOwnerOrderByPlanDateDesc(String owner);
}
