package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v5.52 报价单。状态机：DRAFT 草稿 → QUOTED 已报价 → ACCEPTED 已转订单 / REJECTED 客户未接受。
 * 已报价且过了有效期 → 前端动态显示"已失效"（expired 非持久化），不写库。
 */
@Entity @Table(name = "quotation")
public class Quotation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String quoteNo;
    @Column(nullable = false) public Long customerId;
    @Column(length = 100) public String customerName;
    public LocalDate quoteDate;
    /** 有效期至，过了此日期不可转订单 */
    public LocalDate validUntil;
    @Column(nullable = false, length = 20) public String status = "DRAFT";
    @Column(precision = 14, scale = 2) public BigDecimal totalAmount = BigDecimal.ZERO;
    /** 转订单后回填生成的销售订单号 */
    @Column(length = 20) public String salesOrderNo;
    @Column(length = 500) public String remark;
    @Column(length = 50) public String createdBy;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;

    /** 品名摘要（非持久化） */
    @Transient public String materialNames;
    /** 已报价且过有效期（动态计算，不写库） */
    @Transient public boolean expired;
}
