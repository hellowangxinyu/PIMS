package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 生产订单配方明细
 * 每条记录代表配方中的一种原料及用量
 */
@Entity @Table(name = "production_order_item")
public class ProductionOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long orderId;               // 关联生产订单ID

    @Column(nullable = false, length = 30)
    public String materialCode;        // 原料编码

    @Column(length = 100)
    public String materialName;        // 原料品名

    @Column(length = 100)
    public String spec;                // 规格

    @Column(length = 10)
    public String unit;                // 单位

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;             // 用量（对应batchQty的配比量）

    /** v5.6：节点类型 MATERIAL(原料)/SUB_RECIPE(半成品)；半成品为常备库存保留为一行，不展开原料 */
    @Column(length = 20)
    public String nodeType;

    /** v5.6：半成品节点引用的子配方 ID（溯源用） */
    public Long refRecipeId;

    @Column(length = 200)
    public String remark;
}
