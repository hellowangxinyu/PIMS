package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.SampleRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SampleRequestRepository extends JpaRepository<SampleRequest, Long> {

    List<SampleRequest> findAllByOrderByCreateTimeDescIdDesc();

    List<SampleRequest> findByStatusOrderByCreateTimeDescIdDesc(String status);

    List<SampleRequest> findByCustomerIdOrderByCreateTimeDescIdDesc(Long customerId);

    List<SampleRequest> findByCustomerIdAndStatusOrderByCreateTimeDescIdDesc(Long customerId, String status);

    /** v5.24 口径：取指定前缀最大单号序号（防并发撞号 + 删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(sample_no, -4) AS INTEGER)) FROM sample_request WHERE sample_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
