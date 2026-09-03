package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "sales_order")
public class SalesOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String orderNo;
    @Column(nullable = false) public Long customerId;
    /** 客户名称（冗余存储，便于列表直出） */
    @Column(length = 100) public String customerName;
    public LocalDate orderDate;

    /** v5.75 税率%（默认13，单据级可改；含税单价→不含税=含税÷(1+税率%)） */
    @Column(precision = 5, scale = 2)
    public java.math.BigDecimal taxRate = java.math.BigDecimal.valueOf(13);
    @Column(length = 20) public String contractNo;
    @Column(nullable = false, length = 20) public String status = "DRAFT";
    @Column(precision = 14, scale = 2) public BigDecimal totalAmount = BigDecimal.ZERO;
    /** v5.27：发货仓库由排产环节确定，下单不选（空串=待排产） */
    @Column(nullable = false, length = 20) public String sourceWarehouseId = "";
    public LocalDate expectedShipDate;
    @Column(length = 50) public String createdBy;
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;

    /** 品名摘要（非持久化，列表展示用，多个明细以逗号分隔） */
    @Transient
    public String materialNames;
    /** 数量摘要（非持久化，列表展示用，多个明细以逗号分隔） */
    @Transient
    public String materialQtySummary;
    /** 是否含半成品(B/制浆)明细（非持久化，生产订单参照销售订单按配方类型筛选用） */
    @Transient
    public boolean hasB;
    /** 是否含成品(C/制漆)明细（非持久化，生产订单参照销售订单按配方类型筛选用） */
    @Transient
    public boolean hasC;
    /** 累计公司承担运费（v5.66 物流模块归集，列表带出） */
    @Transient
    public java.math.BigDecimal freightTotal;
}
