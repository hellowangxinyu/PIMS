package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 银行账户档案（v6.3 出纳对账）：日记账/流水/余额调节表的账户维度 */
@Entity
@Table(name = "bank_account")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 账户名称（与收付款单 bankAccount 字段的填写值一致） */
    @Column(nullable = false, length = 50)
    public String name;

    @Column(length = 30)
    public String accountNo;

    @Column(length = 100)
    public String bankName;

    /** 期初余额（启用对账时的账面余额） */
    @Column(precision = 16, scale = 2)
    public BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(length = 10)
    public String enabled = "1";

    public LocalDateTime createTime = LocalDateTime.now();
}
