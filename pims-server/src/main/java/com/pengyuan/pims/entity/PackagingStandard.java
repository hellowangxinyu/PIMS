package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * v5.81 包装标准档案：桶/袋/托盘等（规格+每件容量+单价），配方绑定后计入理论成本
 * pack_type 对应字典 packaging_type：IRON_DRUM 铁桶/PLASTIC_DRUM 塑料桶/IBC 吨桶/BAG 编织袋/PALLET 托盘/OTHER
 */
@Entity @Table(name = "packaging_standard")
public class PackagingStandard {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 100)
    public String name;              // 如"20L 铁桶（马口铁）"

    @Column(nullable = false, length = 20)
    public String packType;          // 字典 packaging_type

    @Column(length = 100)
    public String spec;              // 规格描述，如 20L / 200L / 1t

    /** 每件可装容量（kg）——理论成本按 ⌈批量÷容量⌉×单价 计算；空=按整件计（如托盘） */
    @Column(precision = 10, scale = 3)
    public java.math.BigDecimal capacityKg;

    @Column(nullable = false, precision = 14, scale = 2)
    public java.math.BigDecimal unitPrice = java.math.BigDecimal.ZERO;

    @Column(length = 500)
    public String remark;

    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
