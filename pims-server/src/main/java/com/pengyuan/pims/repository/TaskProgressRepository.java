package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.TaskProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskProgressRepository extends JpaRepository<TaskProgress, Long> {

    List<TaskProgress> findByTaskIdOrderByCreateTimeAscIdAsc(Long taskId);

    List<TaskProgress> findByTaskIdInOrderByTaskIdAscCreateTimeAscIdAsc(List<Long> taskIds);

    void deleteByTaskId(Long taskId);
}
