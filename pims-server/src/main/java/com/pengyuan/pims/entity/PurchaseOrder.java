package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "purchase_order")
public class PurchaseOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String orderNo;
    public Long supplierId;   // v6.3 放宽：MRP 请购单供应商待定（转采购时补全）
    public LocalDate orderDate;
    @Column(nullable = false, length = 20) public String status = "DRAFT";
    @Column(precision = 14, scale = 2) public BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(length = 200) public String paymentTerms;
    @Column(length = 20) public String targetWarehouseId;   // v6.3 放宽：同上，仓库待定
    public LocalDate expectedDeliveryDate;
    @Column(length = 50) public String createdBy;
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
