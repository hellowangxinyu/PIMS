package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票登记（v5.36）
 * direction: OUTPUT 销项发票（开给客户）/ INPUT 进项发票（供应商开来）
 * status: NORMAL 正常 / FLUSHED 已红冲（红冲生成一条负数对冲发票，原单指向红字单）
 * 金额口径：amount 不含税、taxAmount 税额、totalAmount 价税合计（前端录入后自动计算）
 */
@Entity @Table(name = "invoice")
public class Invoice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;                // INV-YYYY-NNNN

    /** 纸面发票号码（税务系统号码，手工录入） */
    @Column(length = 30)
    public String invoiceNo;

    @Column(nullable = false, length = 20)
    public String direction = "OUTPUT";

    /** CUSTOMER 客户 / SUPPLIER 供应商 */
    @Column(nullable = false, length = 20)
    public String partnerType = "CUSTOMER";

    public Long partnerId;

    @Column(length = 100)
    public String partnerName;

    /** 购方/销方税号（开票时从客户档案带出，可改） */
    @Column(length = 30)
    public String partnerTaxNo;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount = BigDecimal.ZERO;

    /** 税率（百分数整数：13/9/6/0） */
    public Integer taxRate = 13;

    @Column(precision = 14, scale = 2)
    public BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    public BigDecimal totalAmount = BigDecimal.ZERO;

    public LocalDate invoiceDate;

    @Column(nullable = false, length = 20)
    public String status = "NORMAL";

    /** 红冲后指向红字发票单号 */
    @Column(length = 20)
    public String flushDocNo;

    /** 关联业务单号（销售订单号/采购订单号，可空） */
    @Column(length = 30)
    public String refOrderNo;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
