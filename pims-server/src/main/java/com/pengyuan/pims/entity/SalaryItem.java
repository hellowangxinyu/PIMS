package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 工资单明细（v5.62 工资核算）—— 一人一行
 * 应发 gross = base + bonus + piecework − deduction
 * 实发 net = gross − socialIns − incomeTax（代扣社保/个税由会计用现成工具算好手填）
 * employee_name/dept 为快照；gross/net 由 Service 计算落库
 */
@Entity @Table(name = "salary_item")
public class SalaryItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long sheetId;

    @Column(nullable = false)
    public Long employeeId;

    @Column(nullable = false, length = 50)
    public String employeeName;

    @Column(nullable = false, length = 20)
    public String dept;

    @Column(precision = 12, scale = 2)
    public BigDecimal base = BigDecimal.ZERO;

    /** 奖金/补贴 */
    @Column(precision = 12, scale = 2)
    public BigDecimal bonus = BigDecimal.ZERO;

    /** 计件工资（手填，依据车间自己的计件记录） */
    @Column(precision = 12, scale = 2)
    public BigDecimal piecework = BigDecimal.ZERO;

    /** 扣款（请假等） */
    @Column(precision = 12, scale = 2)
    public BigDecimal deduction = BigDecimal.ZERO;

    /** 代扣社保（个人部分） */
    @Column(precision = 12, scale = 2)
    public BigDecimal socialIns = BigDecimal.ZERO;

    /** 代扣个税 */
    @Column(precision = 12, scale = 2)
    public BigDecimal incomeTax = BigDecimal.ZERO;

    /** 应发合计（计算落库） */
    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal gross = BigDecimal.ZERO;

    /** 实发合计（计算落库） */
    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal net = BigDecimal.ZERO;
}
