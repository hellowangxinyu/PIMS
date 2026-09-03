package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存异动日志
 * 所有库存余额变化必须由单据驱动，每笔异动审计留痕
 */
@Entity @Table(name = "inventory_movement")
public class InventoryMovement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 驱动单据类型: PURCHASE_IN/OUTSOURCE_OUT/OUTSOURCE_IN/SALES_OUT/PRODUCTION_OUT/OTHER_OUT/TRANSFER/ADJUSTMENT */
    @Column(nullable = false, length = 20)
    public String docType;

    @Column(nullable = false, length = 30)
    public String docNo;               // 单据号

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 30)
    public String batchNo;

    @Column(nullable = false, length = 20)
    public String warehouseId;

    /** v5.46：流水精确到库位 */
    @Column(length = 20) public String locationId;         // 操作的物理仓

    /** 异动方向: IN / OUT */
    @Column(nullable = false, length = 5)
    public String direction;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;             // 本次变动数量

    @Column(precision = 14, scale = 3)
    public BigDecimal qtyBefore;       // 变动前余额

    @Column(precision = 14, scale = 3)
    public BigDecimal qtyAfter;        // 变动后余额

    @Column(nullable = false, length = 20)
    public String ownershipType;       // 快照：当时所有权归属

    @Column(length = 50)
    public String operator;            // 操作人

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
}
