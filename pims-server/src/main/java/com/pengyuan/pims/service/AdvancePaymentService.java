package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.AccountsPayable;
import com.pengyuan.pims.entity.AccountsReceivable;
import com.pengyuan.pims.entity.AdvancePayment;
import com.pengyuan.pims.repository.AccountsPayableRepository;
import com.pengyuan.pims.repository.AccountsReceivableRepository;
import com.pengyuan.pims.repository.AdvancePaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 预收/预付款（v5.36）
 * 冲抵联动：预收冲应收复用 FinanceService.receivePayment（AR 余额/状态流转一致），
 * 预付冲应付复用 makePayment；预存单余额随冲抵递减，用完自动置 USED。
 */
@Service
public class AdvancePaymentService {

    private static final Logger log = LoggerFactory.getLogger(AdvancePaymentService.class);

    private final AdvancePaymentRepository repo;
    private final AccountsReceivableRepository arRepo;
    private final AccountsPayableRepository apRepo;
    private final FinanceService financeService;
    private final WriteQueue writeQueue;

    public AdvancePaymentService(AdvancePaymentRepository repo,
                                 AccountsReceivableRepository arRepo,
                                 AccountsPayableRepository apRepo,
                                 FinanceService financeService,
                                 WriteQueue writeQueue) {
        this.repo = repo;
        this.arRepo = arRepo;
        this.apRepo = apRepo;
        this.financeService = financeService;
        this.writeQueue = writeQueue;
    }

    public List<AdvancePayment> list() { return repo.findAllByOrderByCreateTimeDescIdDesc(); }

    // v8.6（N3）：去 @Transactional——方法内 executeTx 已锁内包事务，外层注解=旧时序（先开事务后抢锁）
    public AdvancePayment create(AdvancePayment a) {
        if (a.direction == null || a.direction.isBlank()) a.direction = "RECEIVE";
        if (!"RECEIVE".equals(a.direction) && !"PAY".equals(a.direction)) {
            throw new IllegalArgumentException("方向必须是 RECEIVE(预收) 或 PAY(预付)");
        }
        if (a.amount == null || a.amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("金额必须大于 0");
        if (a.partnerId == null) throw new IllegalArgumentException(a.direction.equals("RECEIVE") ? "请选择客户" : "请选择供应商");
        if (a.payDate == null) a.payDate = LocalDate.now();
        return writeQueue.executeTx(() -> {
            if (a.docNo == null || a.docNo.isBlank()) {
                Integer maxSeq = repo.findMaxSeq("ADV-" + LocalDate.now().toString().replace("-", "") + "-%");
                a.docNo = String.format("ADV-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return repo.save(a);
        });
    }

    // v8.6（N3）：去 @Transactional——方法内 executeTx 已锁内包事务，外层注解=旧时序（先开事务后抢锁）
    public void delete(Long id) {
        AdvancePayment a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("预存单不存在"));
        if (a.usedAmount != null && a.usedAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("已冲抵 " + a.usedAmount + " 元，不可删除");
        }
        repo.deleteById(id);
    }

    /** 预收冲应收：advance.usedAmount += amount，AR 按收款处理（receivedAmount/状态流转） */
    // v8.1（P0-5）：锁内包事务（内部调 receivePayment/makePayment 可重入）
    public void applyToAr(Long advanceId, Long arId, BigDecimal amount) {        writeQueue.executeTx(() -> {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("冲抵金额必须大于 0");
        AdvancePayment adv = repo.findById(advanceId).orElseThrow(() -> new IllegalArgumentException("预存单不存在"));
        if (!"RECEIVE".equals(adv.direction)) throw new IllegalArgumentException("只有预收单可冲应收");
        AccountsReceivable ar = arRepo.findById(arId).orElseThrow(() -> new IllegalArgumentException("应收单不存在"));
        if (ar.customerId != null && !ar.customerId.equals(adv.partnerId)) {
            throw new IllegalArgumentException("预收单与应收单客户不一致");
        }
        BigDecimal remain = adv.amount.subtract(adv.usedAmount == null ? BigDecimal.ZERO : adv.usedAmount);
        if (amount.compareTo(remain) > 0) throw new IllegalArgumentException("冲抵金额超过预收余额 " + remain);
        financeService.receivePayment(arId, amount);
        advanceUsed(adv, amount);
        log.info("预收冲应收: advance={} ar={} amount={}", adv.docNo, ar.docNo, amount);
   
        });
    }

    /** 预付冲应付：advance.usedAmount += amount，AP 按付款处理 */
    // v8.1（P0-5）：锁内包事务（内部调 receivePayment/makePayment 可重入）
    public void applyToAp(Long advanceId, Long apId, BigDecimal amount) {        writeQueue.executeTx(() -> {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("冲抵金额必须大于 0");
        AdvancePayment adv = repo.findById(advanceId).orElseThrow(() -> new IllegalArgumentException("预存单不存在"));
        if (!"PAY".equals(adv.direction)) throw new IllegalArgumentException("只有预付单可冲应付");
        AccountsPayable ap = apRepo.findById(apId).orElseThrow(() -> new IllegalArgumentException("应付单不存在"));
        if (ap.supplierId != null && !ap.supplierId.equals(adv.partnerId)) {
            throw new IllegalArgumentException("预付单与应付单供应商不一致");
        }
        BigDecimal remain = adv.amount.subtract(adv.usedAmount == null ? BigDecimal.ZERO : adv.usedAmount);
        if (amount.compareTo(remain) > 0) throw new IllegalArgumentException("冲抵金额超过预付余额 " + remain);
        financeService.makePayment(apId, amount);
        advanceUsed(adv, amount);
        log.info("预付冲应付: advance={} ap={} amount={}", adv.docNo, ap.docNo, amount);
   
        });
    }

    private void advanceUsed(AdvancePayment adv, BigDecimal amount) {
        adv.usedAmount = (adv.usedAmount == null ? BigDecimal.ZERO : adv.usedAmount).add(amount);
        if (adv.usedAmount.compareTo(adv.amount) >= 0) adv.status = "USED";
        else adv.status = "PARTIAL";
        adv.updateTime = java.time.LocalDateTime.now();
        repo.save(adv);
    }
}
