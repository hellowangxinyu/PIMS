package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单异常处置记录
 * 投入产出比 < 95% 的已完工订单进入异常处理；一个订单一条记录（按 orderNo 唯一关联）。
 * 记录异常原因、处置措施与闭环过程。投出比为触发时快照（订单实时值见 ProductionOrder.ioRatio @Transient）。
 */
@Entity @Table(name = "production_order_exception")
public class ProductionOrderException {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String orderNo;            // 关联生产订单号

    /** 触发异常时的投出比快照(%) */
    @Column(precision = 8, scale = 2)
    public BigDecimal ioRatio;

    @Column(precision = 14, scale = 3)
    public BigDecimal inputQty;       // 投入量快照

    @Column(precision = 14, scale = 3)
    public BigDecimal outputQty;      // 产出量快照

    @Column(length = 200)
    public String reason;             // 异常原因

    @Column(length = 500)
    public String measure;            // 处置措施

    /** PENDING(待处理) / PROCESSING(处理中) / CLOSED(已闭环) */
    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    @Column(length = 50)
    public String handler;            // 处理人

    @Column(length = 500)
    public String remark;

    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
    public LocalDateTime closedTime;  // 闭环时间
}
