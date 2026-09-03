package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.AdvancePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AdvancePaymentRepository extends JpaRepository<AdvancePayment, Long> {

    List<AdvancePayment> findAllByOrderByCreateTimeDescIdDesc();

    /** 冲抵选择：该往来对象未用完的预收/预付单，按时间升序 */
    List<AdvancePayment> findByDirectionAndPartnerIdAndStatusNotOrderByCreateTimeAsc(
            String direction, Long partnerId, String status);

    /** 取指定前缀最大单号序号（防并发撞号，删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM advance_payment WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
