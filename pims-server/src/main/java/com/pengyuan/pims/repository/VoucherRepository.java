package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    List<Voucher> findAllByOrderByVoucherDateDescIdDesc();

    Optional<Voucher> findBySourceAndRefDocNo(String source, String refDocNo);

    boolean existsBySourceAndRefDocNo(String source, String refDocNo);

    boolean existsByPeriodAndStatus(String period, String status);

    List<Voucher> findByPeriodOrderByVoucherDateAscIdAsc(String period);

    /** 取指定前缀最大单号序号（防并发撞号，删除不错位） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM voucher WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
