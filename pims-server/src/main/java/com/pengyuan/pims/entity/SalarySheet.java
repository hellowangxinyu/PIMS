package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 月度工资单（v5.62 工资核算）—— 每期间一张
 * docNo: SAL-YYYYMM-NNNN；period 全局唯一（Service 校验）
 * status: DRAFT 可编辑 / CONFIRMED 已确认锁定（确认后才可生成计提/发放凭证）
 * gross 应发 = Σ(基本+奖金+计件−扣款)；net 实发 = 应发 − 代扣社保 − 代扣个税（明细行计算后汇总落库）
 */
@Entity @Table(name = "salary_sheet")
public class SalarySheet {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    /** 期间 YYYY-MM */
    @Column(nullable = false, length = 10)
    public String period;

    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal totalGross = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal totalNet = BigDecimal.ZERO;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 50)
    public String confirmedBy;

    public LocalDateTime confirmedTime;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;

    /** 明细行（非持久化，Service 组装，一人一行） */
    @Transient
    public List<SalaryItem> items;
}
