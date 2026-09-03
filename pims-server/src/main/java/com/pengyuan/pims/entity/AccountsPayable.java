package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "accounts_payable")
public class AccountsPayable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    @Column(nullable = false)
    public Long supplierId;

    /** 供应商名称（@Transient 不入库，查询时关联填充，避免前端显示ID） */
    @Transient
    public String supplierName;

    /** v5.27：到货单信息（@Transient 不入库，查询时关联填充：到货单#id 物料 ×数量） */
    @Transient
    public String arrivalInfo;

    @Column(length = 20)
    public String purchaseOrderNo;

    /** v5.27：立账依据的到货单 ID（采购应付按到货单立账，非按采购订单整单） */
    public Long arrivalId;

    @Column(length = 20)
    public String outsourceOrderNo;

    @Column(nullable = false, length = 20)
    public String payableType;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    @Column(precision = 14, scale = 2)
    public BigDecimal paidAmount = BigDecimal.ZERO;

    public LocalDate dueDate;

    @Column(nullable = false, length = 20)
    public String status = "UNPAID";

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();

    public LocalDateTime updateTime;
}
