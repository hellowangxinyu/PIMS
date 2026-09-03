package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "sales_order_item")
public class SalesOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long orderId;
    @Column(nullable = false, length = 30) public String materialCode;
    /** 品名（冗余存储，便于展示） */
    @Column(length = 100) public String materialName;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(length = 10) public String unit;
    @Column(precision = 12, scale = 2) public BigDecimal unitPrice;
    @Column(precision = 14, scale = 2) public BigDecimal amount;
    @Column(precision = 14, scale = 3) public BigDecimal shippedQty = BigDecimal.ZERO;
    @Column(precision = 14, scale = 3) public BigDecimal returnQty = BigDecimal.ZERO;
    @Column(length = 500) public String remark;
}
