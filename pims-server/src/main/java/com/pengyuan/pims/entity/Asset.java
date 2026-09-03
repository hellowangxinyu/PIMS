package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定资产卡片（v5.62）—— docNo: FA-YYYY-NNNN
 * 平均年限法：月折旧 = originalValue × (1 − residualRate/100) / usefulLifeMonths
 * expenseSubject 折旧费用科目（默认 6602.05 管理费用-折旧费，生产设备可改 5101 制造费用等任意启用科目）
 * status: IN_USE 在用 / SCRAPPED 报废（报废当月停提）
 * 计提规则：购入次月起提；已提总额 = 原值×(1−残值率)，最后一月提尾差
 */
@Entity @Table(name = "asset")
public class Asset {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    @Column(nullable = false, length = 100)
    public String name;

    /** 字典 asset_category：BUILDING/MACHINE/VEHICLE/ELECTRONIC/OTHER */
    @Column(nullable = false, length = 20)
    public String category;

    public LocalDate purchaseDate;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal originalValue;

    /** 预计使用年限（月） */
    @Column(nullable = false)
    public Integer usefulLifeMonths;

    /** 残值率（百分数，默认 5） */
    @Column(precision = 5, scale = 2)
    public BigDecimal residualRate = new BigDecimal("5");

    @Column(nullable = false, length = 20)
    public String expenseSubject = "6602.05";

    @Column(length = 100)
    public String location;

    @Column(length = 50)
    public String keeper;

    @Column(nullable = false, length = 20)
    public String status = "IN_USE";

    public LocalDate scrapDate;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
