package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售出库单
 * 销售发货时创建，确认后库存减少
 */
@Entity @Table(name = "sales_outbound")
public class SalesOutbound {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String docNo;               // SALES-OUT-YYYY-NNNN

    @Column(length = 30)
    public String salesOrderNo;        // 关联销售订单号（可选）

    @Column(length = 100)
    public String customerName;        // 客户名称

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 30)
    public String batchNo;

    @Column(nullable = false, length = 20)
    public String warehouseId;         // 出库仓库

    @Column(length = 20)
    public String locationId;          // 库位ID（批号所在库位）

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;

    @Column(length = 10)
    public String unit;

    /** 批号库存单价（出库时按批号直取） */
    @Column(precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 实际成本 = qty × unitPrice */
    @Column(precision = 14, scale = 2)
    public BigDecimal cost;

    /** 状态: DRAFT/CONFIRMED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    @Column(nullable = false)
public Integer printCount = 0;

public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
