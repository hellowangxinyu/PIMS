package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产入库单
 * 参照生产订单，生产完成后产品（半成品/成品）入库
 */
@Entity @Table(name = "production_inbound")
public class ProductionInbound {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;               // PROD-IN-YYYY-NNNN

    @Column(length = 20)
    public String productionOrderNo;   // 参照的生产订单号

    @Column(length = 100)
    public String productName;         // 产品名称（来自生产订单）

    @Column(length = 50)
    public String productCode;         // 产品编码

    @Column(length = 30)
    public String batchNo;             // 生产批次号

    @Column(nullable = false, length = 20)
    public String warehouseId;         // 入库仓库

    @Column(length = 20)
    public String locationId;          // 库位ID

    @Column(length = 50)
    public String zoneName;            // 分库名称

    @Column(length = 50)
    public String locationName;        // 库位名称

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;             // 实际产出量（入库数量）

    /** 理论产出量（配方批量，来自生产订单） */
    @Column(precision = 14, scale = 3)
    public BigDecimal theoreticalQty;

    /** 得率(%) = 实际产出 / 理论产出 × 100 */
    @Column(precision = 8, scale = 2)
    public BigDecimal yieldRate;

    @Column(length = 10)
    public String unit;

    /** 状态: DRAFT/CONFIRMED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
