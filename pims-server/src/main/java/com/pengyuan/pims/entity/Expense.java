package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用单（v5.36）—— 无往来单据的其他收支
 * direction: EXPENSE 支出（运费/包装/水电/办公/差旅/维修/检测/其他）
 *            INCOME  其他收入（废料回收/租金/利息/补贴/其他）
 * 与应收/应付无关的日常收支在此登记，月度利润试算取数。
 */
@Entity @Table(name = "expense")
public class Expense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;                // EXP-YYYY-NNNN

    @Column(nullable = false, length = 20)
    public String direction = "EXPENSE";

    /** 费用类型（字典 expense_type / other_income_type） */
    @Column(nullable = false, length = 50)
    public String expenseType;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    public LocalDate occurDate;

    /** 支付方式：BANK/CASH/ACCEPTANCE/WECHAT/OTHER */
    @Column(length = 20)
    public String method = "BANK";

    /** 往来对象（物流公司/房东等，纯文本） */
    @Column(length = 100)
    public String partner;

    @Column(length = 50)
    public String handler;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
