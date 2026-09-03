package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "outsource_material_consume")
public class OutsourceMaterialConsume {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 20) public String inboundDocNo;
    @Column(nullable = false, length = 20) public String outsourceOrderNo;
    @Column(nullable = false, length = 30) public String materialCode;
    @Column(length = 30) public String batchNo;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal consumeQty;
    @Column(precision = 14, scale = 3) public BigDecimal remainQty;
    @Column(length = 10) public String unit;
    @Column(length = 200) public String remark;
}
