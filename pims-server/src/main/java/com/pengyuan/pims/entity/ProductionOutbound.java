package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产领料出库单
 * 生产领料从仓库出库，库存直接减少
 */
@Entity @Table(name = "production_outbound")
public class ProductionOutbound {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;               // PROD-OUT-YYYY-NNNN（退料单为 PROD-RET-YYYY-NNNN）

    /** 单据类型: ISSUE=领料（默认） / RETURN=退料（qty/cost 为负数行，成本 SUM 口径自动净额） */
    @Column(nullable = false, length = 20)
    public String docType = "ISSUE";

    @Column(length = 20)
    public String productionOrderNo;   // 参照的生产订单号

    @Column(length = 100)
    public String productName;         // 产品名称（来自生产订单）

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 30)
    public String batchNo;

    @Column(nullable = false, length = 20)
    public String warehouseId;         // 出库仓库

    /** 库位ID（v4.5新增，前端按库位分行选择批次时记录，用于精确扣减对应库位库存） */
    @Column(length = 20)
    public String locationId;

    /** 分库名称（冗余字段，便于展示） */
    @Column(length = 50)
    public String zoneName;

    /** 库位名称（冗余字段，便于展示） */
    @Column(length = 50)
    public String locationName;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;

    @Column(length = 10)
    public String unit;

    /** 批号库存单价（出库时按批号直取） */
    @Column(precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 实际材料成本 = qty × unitPrice */
    @Column(precision = 14, scale = 2)
    public BigDecimal cost;

    /** 状态: DRAFT/CONFIRMED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    /** v6.8：补领原因（COLOR_ADJUST 色差调整 / OVER_CONSUME 超耗补充；正常领料/退料为空） */
    @Column(length = 20) public String supplementType;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
