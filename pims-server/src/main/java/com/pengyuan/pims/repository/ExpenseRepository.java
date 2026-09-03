package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    /** v6.1.4：客户端传入单号查重（可重复单号防线） */
    boolean existsByDocNo(String docNo);


    List<Expense> findAllByOrderByCreateTimeDescIdDesc();

    /** 取指定前缀最大单号序号（防并发撞号，删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM expense WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
