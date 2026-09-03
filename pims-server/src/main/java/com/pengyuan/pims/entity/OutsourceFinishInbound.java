package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "outsource_finish_inbound")
public class OutsourceFinishInbound {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String docNo;
    @Column(nullable = false, length = 20) public String outsourceOrderNo;
    @Column(length = 100) public String productName;
    @Column(length = 50) public String productCode;
    @Column(length = 30) public String batchNo;
    @Column(nullable = false, length = 20) public String warehouseId;
    @Column(length = 20) public String locationId;
    /** 分库名称（冗余字段，便于展示，与生产入库保持一致） */
    @Column(length = 50) public String zoneName;
    /** 库位名称（冗余字段，便于展示，与生产入库保持一致） */
    @Column(length = 50) public String locationName;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    /** 理论产出量（配方批量，来自委外订单） */
    @Column(precision = 14, scale = 3) public BigDecimal theoreticalQty;
    /** 得率(%) = 实际产出 / 理论产出 × 100 */
    @Column(precision = 8, scale = 2) public BigDecimal yieldRate;
    @Column(length = 10) public String unit;
    @Column(nullable = false, length = 20) public String status = "DRAFT";
    @Column(length = 50) public String createdBy;
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
