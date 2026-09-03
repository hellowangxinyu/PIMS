package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收款单
 * 记录每一笔收款流水，关联冲减应收账款(AR)。
 * 一个 AR 可分多次收款（多次收款单），一个收款单也可一次冲多个AR（本版暂只支持一单一冲）。
 */
@Entity @Table(name = "payment_receipt")
public class PaymentReceipt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 单号：PR-YYYY-NNNN */
    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    /** 关联应收单ID（可空，允许独立收款后由财务手工核销） */
    public Long arId;

    /** 关联应收单号（冗余，便于列表展示） */
    @Column(length = 20)
    public String arDocNo;

    @Column(nullable = false)
    public Long customerId;

    @Column(length = 50)
    public String customerName;

    /** 收款金额 */
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    /** 收款方式：CASH(现金) / BANK(银行) / ACCEPTANCE(承兑) */
    @Column(nullable = false, length = 20)
    public String method = "BANK";

    /** 收款银行账户（可空） */
    @Column(length = 50)
    public String bankAccount;

    /** 收款日期 */
    @Column(nullable = false)
    public LocalDate receiptDate;

    /** 经办人 */
    @Column(length = 50)
    public String operator;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
}
