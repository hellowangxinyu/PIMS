package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.VoucherEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VoucherEntryRepository extends JpaRepository<VoucherEntry, Long> {

    List<VoucherEntry> findByVoucherIdOrderByLineNoAsc(Long voucherId);

    List<VoucherEntry> findByVoucherIdInOrderByVoucherIdAscLineNoAsc(List<Long> voucherIds);

    void deleteByVoucherId(Long voucherId);

    boolean existsBySubjectCode(String subjectCode);
}
