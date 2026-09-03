package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案（v5.62 工资核算）—— 独立于 sys_user（工人不一定有登录账号）
 * dept: PRODUCTION 生产 / SALES 销售 / ADMIN 行政 / TECH 技术 / QC 质检 / OTHER 其他
 *      计提工资凭证按 dept 归集借方科目（biz:salary:dept: 映射）
 */
@Entity @Table(name = "employee")
public class Employee {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 50)
    public String name;

    @Column(nullable = false, length = 20)
    public String dept;

    @Column(length = 50)
    public String position;

    public LocalDate hireDate;

    /** 离职日期（可空；离职后工资单不再带出） */
    public LocalDate leaveDate;

    @Column(nullable = false, length = 20)
    public String status = "ENABLED";

    /** 基本工资（月薪，工资单默认带出可改） */
    @Column(precision = 12, scale = 2)
    public BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(length = 50)
    public String bankCard;

    @Column(length = 30)
    public String phone;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
