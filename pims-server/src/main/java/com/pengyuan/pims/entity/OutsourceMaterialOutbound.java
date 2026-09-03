package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委外材料出库单（OUT-002）
 * v4.5：从指定调出仓向委外工厂发料，直接扣减调出仓库存（不再调拨到委外仓）
 */
@Entity @Table(name = "outsource_material_outbound")
public class OutsourceMaterialOutbound {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;               // OUT-IO-YYYY-NNNN

    @Column(nullable = false, length = 20)
    public String outsourceOrderNo;    // 关联委外生产单

    @Column(nullable = false, length = 20)
    public String processorId;         // 代工厂ID

    /** 代工厂名称（v4.5新增，冗余存储便于展示） */
    @Column(length = 100)
    public String processorName;

    @Column(nullable = false, length = 20)
    public String fromWarehouseId;     // 调出仓（芃远自有仓）

    /** 调入仓（委外仓），v4.5起不再使用，保留字段兼容历史数据 */
    @Column(length = 20)
    public String toWarehouseId;

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 30)
    public String batchNo;

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

    @Column(length = 10) public String unit;

    /** 批号库存单价（出库时按批号直取） */
    @Column(precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 实际材料成本 = qty × unitPrice */
    @Column(precision = 14, scale = 2)
    public BigDecimal cost;

    /** 状态: DRAFT/CONFIRMED/SIGNED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    /** 签收数量（代工厂确认） */
    @Column(precision = 14, scale = 3)
    public BigDecimal signedQty;

    /** 签收差异 */
    @Column(precision = 14, scale = 3)
    public BigDecimal signedDiff;

    @Column(length = 200)
    public String signedDiffReason;

    @Column(length = 50) public String createdBy;
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
