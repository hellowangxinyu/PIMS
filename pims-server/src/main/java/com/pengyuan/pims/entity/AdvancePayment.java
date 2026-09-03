package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预收/预付款（v5.36）
 * direction: RECEIVE 客户预收（先打款后发货）/ PAY 供应商预付（先付款后到货）
 * 冲抵：预收冲应收（AR.receivedAmount+）、预付冲应付（AP.paidAmount+）
 * status: UNUSED 未使用 / PARTIAL 部分冲抵 / USED 已用完
 */
@Entity @Table(name = "advance_payment")
public class AdvancePayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;                // ADV-YYYY-NNNN

    @Column(nullable = false, length = 20)
    public String direction = "RECEIVE";

    public Long partnerId;

    @Column(length = 100)
    public String partnerName;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(length = 20)
    public String method = "BANK";

    public LocalDate payDate;

    @Column(nullable = false, length = 20)
    public String status = "UNUSED";

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
