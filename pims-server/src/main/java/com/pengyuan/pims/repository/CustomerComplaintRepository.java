package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.CustomerComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerComplaintRepository extends JpaRepository<CustomerComplaint, Long> {

    List<CustomerComplaint> findAllByOrderByCreateTimeDescIdDesc();

    List<CustomerComplaint> findByStatusOrderByCreateTimeDescIdDesc(String status);

    List<CustomerComplaint> findByCustomerIdOrderByCreateTimeDescIdDesc(Long customerId);

    List<CustomerComplaint> findByCustomerIdAndStatusOrderByCreateTimeDescIdDesc(Long customerId, String status);

    /** v5.24 口径：取指定前缀最大单号序号（防并发撞号 + 删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(complaint_no, -4) AS INTEGER)) FROM customer_complaint WHERE complaint_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
