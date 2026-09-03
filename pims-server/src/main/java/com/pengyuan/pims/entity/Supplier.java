package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 供应商实体
 * type 字段区分供应商类型：MATERIAL=材料供应商，FINISHED=成品供应商，PROCESSOR=代工厂
 */
@Entity
@Table(name = "supplier")
public class Supplier {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    /** 供应商类型：MATERIAL=材料供应商，FINISHED=成品供应商，PROCESSOR=代工厂 */
    @Column(length = 20)
    public String type = "MATERIAL";

    /** 付款条件（字典值：PREPAID/CREDIT_30/MONTHLY/TWO_MONTH/THREE_MONTH/CUSTOM_CREDIT） */
    @Column(length = 500) public String paymentTerms;

    /** 付款方式（字典值：ACCEPTANCE=承兑，TRANSFER=电汇） */
    @Column(length = 20) public String paymentMethod;

    /** 加工费（代工厂档案维护，委外订单选代工厂时自动带出） */
    public java.math.BigDecimal processingFee;

    public Boolean enabled = true;

    /** v5.27：拉黑标记（拉黑即禁用 enabled=false；解除拉黑恢复 enabled=true） */
    public Boolean blacklisted = false;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
