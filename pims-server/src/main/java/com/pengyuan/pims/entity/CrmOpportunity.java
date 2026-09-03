package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CRM 商机（v5.50）。
 * 阶段：LEAD 初步接触 → QUOTED 已报价 → SAMPLING 样品测试 → NEGOTIATING 商务谈判 → WON 成交 / LOST 流失。
 * company_name 可为未建档线索公司；成交（WON）时建议关联正式客户并录入 won_order_no 销售订单号。
 */
@Entity @Table(name = "crm_opportunity")
public class CrmOpportunity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 100)
    public String title;

    @Column(nullable = false, length = 100)
    public String companyName;

    /** 关联客户档案（可空=线索） */
    public Long customerId;

    @Column(length = 200)
    public String productInterest;

    @Column(precision = 14, scale = 2)
    public BigDecimal expectAmount;

    public LocalDate expectDate;

    @Column(nullable = false, length = 20)
    public String stage = "LEAD";

    @Column(length = 50)
    public String owner;

    /** 成交关联销售订单号 */
    @Column(length = 20)
    public String wonOrderNo;

    @Column(length = 200)
    public String lossReason;

    @Column(length = 500)
    public String remark;

    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
