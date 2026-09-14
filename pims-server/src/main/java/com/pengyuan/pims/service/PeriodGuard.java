package com.pengyuan.pims.service;

import com.pengyuan.pims.repository.AccountPeriodRepository;
import org.springframework.stereotype.Service;

/**
 * v9.0（P1-4 审计）：锁期公共校验。
 * 此前已结期间校验只存在于凭证（VoucherService 7 处）与资产/成本结账，
 * 收付款、出入库、退货红冲等业务写路径全部可绕过——凭证冻结但业务流水照走，月结后账永远轧不平。
 * 口径（用户 2026-09-14 拍板）：已结账期间绝对禁止补录业务单据，如需调整先反结账。
 */
@Service
public class PeriodGuard {

    private final AccountPeriodRepository periodRepo;

    public PeriodGuard(AccountPeriodRepository periodRepo) {
        this.periodRepo = periodRepo;
    }

    /** 当前期间是否可写（无业务日期字段的流水（出入库/核销/红冲）按记账时刻所属期间判定） */
    public void checkCurrentOpen() {
        checkOpen(java.time.LocalDate.now());
    }

    /** 按业务日期校验所属期间未结账；日期为空按当天 */
    public void checkOpen(java.time.LocalDate bizDate) {
        checkOpen(bizDate == null ? java.time.LocalDate.now().toString().substring(0, 7)
                : bizDate.toString().substring(0, 7));
    }

    public void checkOpen(String period) {
        periodRepo.findByPeriod(period).filter(p -> Boolean.TRUE.equals(p.closed)).ifPresent(p -> {
            throw new IllegalArgumentException(period + " 已结账，该期间禁止业务单据写入（如需调整请先反结账）");
        });
    }
}
