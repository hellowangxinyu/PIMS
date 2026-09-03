package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 银行流水（v6.3 出纳对账）：网银对账单 Excel 导入，与系统收付款单勾对。
 * amount 带符号：收入为正、支出为负（对账单常见两列金额时由导入器合并）。
 */
@Entity
@Table(name = "bank_statement")
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long accountId;

    /** 交易日期 */
    @Column(nullable = false)
    public LocalDate txDate;

    /** 带符号金额（收 + / 支 −） */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal amount;

    /** 对账单余额（可空；最后一笔用于期末银行余额） */
    @Column(precision = 16, scale = 2)
    public BigDecimal balance;

    @Column(length = 200)
    public String summary;

    /** 对方户名（辅助匹配） */
    @Column(length = 100)
    public String counterparty;

    /** 勾对关联：RECEIPT / DISBURSEMENT（null=未勾对） */
    @Column(length = 20)
    public String refType;

    public Long refId;

    /** UNMATCHED / MATCHED */
    @Column(length = 10)
    public String status = "UNMATCHED";

    @Column(length = 30)
    public String importBatch;

    public LocalDateTime createTime = LocalDateTime.now();
}
