package com.pengyuan.pims.entity;

import jakarta.persistence.*;

/**
 * v5.82 组合包装明细：一套包装由多个物料组成（桶+内衬袋+托盘…）
 * qty = 每套数量（托盘可 0.05 = 每 20 桶 1 个）；小计 = qty × unitPrice
 */
@Entity @Table(name = "packaging_standard_item")
public class PackagingStandardItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long packagingId;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(length = 20)
    public String packType;   // 字典 packaging_type（可空）

    @Column(length = 100)
    public String spec;

    @Column(nullable = false, precision = 10, scale = 3)
    public java.math.BigDecimal qty = java.math.BigDecimal.ONE;

    @Column(nullable = false, precision = 14, scale = 2)
    public java.math.BigDecimal unitPrice = java.math.BigDecimal.ZERO;

    public Integer sortOrder = 1;
}
