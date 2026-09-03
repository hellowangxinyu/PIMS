package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "purchase_order_item")
public class PurchaseOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long orderId;
    @Column(nullable = false, length = 30) public String materialCode;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(length = 10) public String unit;
    @Column(precision = 12, scale = 2) public BigDecimal unitPrice;
    @Column(precision = 14, scale = 2) public BigDecimal amount;
    @Column(precision = 14, scale = 3) public BigDecimal receivedQty = BigDecimal.ZERO;
    @Column(precision = 14, scale = 3) public BigDecimal returnQty = BigDecimal.ZERO;
    @Column(length = 500) public String remark;
}
