package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售价格政策（v6.3 第二批）：
 * 维度——物料编码精确档（可空）> 物料大类兜底档；同物料多行按 min_qty 阶梯（量大优惠），
 * 取「数量已达到的最高阶梯」。下单时自动带出，仍可手工改价（软约束）。
 */
@Entity
@Table(name = "price_policy")
public class PricePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 物料编码（精确档；为空时按 material_category 兜底） */
    @Column(length = 30)
    public String materialCode;

    /** 物料大类（兜底档用；A 原料 / B 半成品 / C 成品） */
    @Column(length = 5)
    public String materialCategory;

    /** 阶梯门槛（最小数量，默认 1 = 基础价；越大单价越低即量大优惠） */
    @Column(precision = 14, scale = 3)
    public BigDecimal minQty = BigDecimal.ONE;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 生效日期（含） */
    @Column(nullable = false)
    public LocalDate effectiveDate;

    /** 失效日期（含，空=长期有效） */
    public LocalDate expiryDate;

    /** ENABLED / DISABLED */
    @Column(length = 10)
    public String status = "ENABLED";

    @Column(length = 200)
    public String remark;

    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime = LocalDateTime.now();
}
