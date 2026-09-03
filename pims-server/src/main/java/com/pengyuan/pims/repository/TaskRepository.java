package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOrderByCreateTimeDescIdDesc();

    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM task WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
