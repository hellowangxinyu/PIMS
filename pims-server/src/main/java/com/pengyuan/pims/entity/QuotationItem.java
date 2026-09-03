package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "quotation_item")
public class QuotationItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long quotationId;
    @Column(nullable = false, length = 30) public String materialCode;
    @Column(length = 100) public String materialName;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(length = 10) public String unit;
    @Column(precision = 12, scale = 2) public BigDecimal unitPrice;
    @Column(precision = 14, scale = 2) public BigDecimal amount;
    @Column(length = 500) public String remark;
}
