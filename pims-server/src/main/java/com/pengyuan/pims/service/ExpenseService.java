package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Expense;
import com.pengyuan.pims.repository.ExpenseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 费用单（v5.36）：无往来单据的其他收支登记 */
@Service
public class ExpenseService {

    private final ExpenseRepository repo;
    private final WriteQueue writeQueue;
    private final JdbcTemplate jdbc;

    public ExpenseService(ExpenseRepository repo, WriteQueue writeQueue, JdbcTemplate jdbc) {
        this.repo = repo;
        this.writeQueue = writeQueue;
        this.jdbc = jdbc;
    }

    public List<Expense> list() { return repo.findAllByOrderByCreateTimeDescIdDesc(); }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public Expense create(Expense e) {
        if (e.direction == null || e.direction.isBlank()) e.direction = "EXPENSE";
        if (!"EXPENSE".equals(e.direction) && !"INCOME".equals(e.direction)) {
            throw new IllegalArgumentException("方向必须是 EXPENSE(支出) 或 INCOME(收入)");
        }
        if (e.expenseType == null || e.expenseType.isBlank()) throw new IllegalArgumentException("费用类型不能为空");
        if (e.amount == null || e.amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("金额必须大于 0");
        if (e.occurDate == null) e.occurDate = LocalDate.now();
        return writeQueue.executeTx(() -> {
            if (e.docNo != null && !e.docNo.isBlank() && repo.existsByDocNo(e.docNo)) {
                throw new IllegalArgumentException("费用单号 " + e.docNo + " 已存在");   // v6.1.4 查重
            }
            if (e.docNo == null || e.docNo.isBlank()) {
                Integer maxSeq = repo.findMaxSeq("EXP-" + LocalDate.now().toString().replace("-", "") + "-%");
                e.docNo = String.format("EXP-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return repo.save(e);
        });
    }

    @Transactional
    public Expense update(Long id, Expense in) {
        Expense e = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("费用单不存在"));
        assertNoVoucher(e.docNo, "修改");
        if (in.expenseType == null || in.expenseType.isBlank()) throw new IllegalArgumentException("费用类型不能为空");
        if (in.amount == null || in.amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("金额必须大于 0");
        e.direction = "INCOME".equals(in.direction) ? "INCOME" : "EXPENSE";
        e.expenseType = in.expenseType;
        e.amount = in.amount;
        e.occurDate = in.occurDate == null ? e.occurDate : in.occurDate;
        e.method = in.method;
        e.partner = in.partner;
        e.handler = in.handler;
        e.remark = in.remark;
        e.updateTime = java.time.LocalDateTime.now();
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        Expense e = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("费用单不存在"));
        assertNoVoucher(e.docNo, "删除");
        repo.deleteById(id);
    }

    /** 已生成凭证的费用单改/删会造成凭证与单据脱节（金额对不上），先删凭证再操作 */
    private void assertNoVoucher(String docNo, String action) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM voucher WHERE source = 'EXPENSE' AND ref_doc_no = ?", Integer.class, docNo);
        if (n != null && n > 0) {
            throw new IllegalArgumentException("该费用单已生成凭证，不可" + action + "；请先在总账删除对应凭证");
        }
    }
}
