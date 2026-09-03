package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流运费（v5.66）：每次发货登记运费，按销售订单归集进成本/毛利。
 * borne: COMPANY 公司承担（进毛利成本与利润试算费用）/ CUSTOMER 客户到付（仅记录不进成本）。
 * 与费用单（expense 的 FREIGHT 类型）互斥使用：挂订单的发货运费登记在此，散运费走费用单——防利润试算双算。
 * shipDate 用字符串 YYYY-MM-DD 存储（SQLite LocalDate 映射兼容，同 period 类字段做法）。
 */
@Entity @Table(name = "shipping_log")
public class ShippingLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;               // SHIP-YYYY-NNNN

    /** 关联发货单（可空——直接挂订单的运费为空） */
    @Column(length = 30)
    public String outboundDocNo;

    /** 归集锚点：销售订单号（必填） */
    @Column(nullable = false, length = 30)
    public String salesOrderNo;

    @Column(length = 50)
    public String customerName;

    /** 承运商（字典 logistics_company） */
    @Column(length = 50)
    public String carrier;

    /** 物流单号（查询物流轨迹用） */
    @Column(length = 50)
    public String trackingNo;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal freight;

    @Column(nullable = false, length = 20)
    public String borne = "COMPANY";

    /** 发货日期 YYYY-MM-DD */
    @Column(length = 10)
    public String shipDate;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
