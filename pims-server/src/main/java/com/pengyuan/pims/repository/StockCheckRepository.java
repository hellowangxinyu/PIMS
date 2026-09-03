package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.StockCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface StockCheckRepository extends JpaRepository<StockCheck, Long> {
    List<StockCheck> findByOrderByCreateTimeDesc();
    List<StockCheck> findByDocTypeOrderByCreateTimeDesc(String docType);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM stock_check WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

}
