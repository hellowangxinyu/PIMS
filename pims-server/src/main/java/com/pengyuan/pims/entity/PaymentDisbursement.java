package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款单
 * 记录每一笔付款流水，关联冲减应付账款(AP)。
 */
@Entity @Table(name = "payment_disbursement")
public class PaymentDisbursement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 单号：PD-YYYY-NNNN */
    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    /** 关联应付单ID（可空，允许独立付款后由财务手工核销） */
    public Long apId;

    /** 关联应付单号（冗余，便于列表展示） */
    @Column(length = 20)
    public String apDocNo;

    @Column(nullable = false)
    public Long supplierId;

    @Column(length = 50)
    public String supplierName;

    /** 付款金额 */
    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    /** 付款方式：CASH(现金) / BANK(银行) / ACCEPTANCE(承兑) */
    @Column(nullable = false, length = 20)
    public String method = "BANK";

    /** 付款银行账户（可空） */
    @Column(length = 50)
    public String bankAccount;

    /** 付款日期 */
    @Column(nullable = false)
    public LocalDate payDate;

    /** 经办人 */
    @Column(length = 50)
    public String operator;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
}
