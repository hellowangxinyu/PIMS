package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 订单月度统计汇总表
 * 按「月份 + 订单类型」聚合，由 SQLite 触发器自动维护
 * order_type: PURCHASE / SALES / OUTSOURCE
 */
@Entity
@Table(name = "stat_order_monthly",
    uniqueConstraints = @UniqueConstraint(columnNames = {"period", "orderType"}))
public class StatOrderMonthly {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 月份，格式 yyyy-MM */
    @Column(nullable = false, length = 7)
    public String period;

    /** 订单类型: PURCHASE / SALES / OUTSOURCE */
    @Column(nullable = false, length = 20)
    public String orderType;

    /** 当月有效订单数（排除 DRAFT） */
    @Column(nullable = false)
    public Integer orderCount = 0;

    /** 当月订单总金额 */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal totalAmount = BigDecimal.ZERO;
}
