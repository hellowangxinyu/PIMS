package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "accounts_receivable")
public class AccountsReceivable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String docNo;
    @Column(nullable = false) public Long customerId;

    /** 客户名称（@Transient 不入库，查询时关联填充，避免前端显示ID） */
    @Transient
    public String customerName;
    @Column(length = 20) public String salesOrderNo;
    @Column(length = 20) public String contractNo;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount;
    @Column(precision = 14, scale = 2) public BigDecimal receivedAmount = BigDecimal.ZERO;
    public LocalDate dueDate;
    @Column(nullable = false, length = 20) public String status = "UNPAID";
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
