package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 销售订单变更留痕（v5.47）：每次编辑订单记录前后差异明细 */
@Entity @Table(name = "sales_order_change_log")
public class SalesOrderChangeLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long orderId;

    @Column(length = 20)
    public String orderNo;

    /** 变更明细（人读文本，如"交期 08-01→08-15；明细[0]数量 100→120"） */
    @Column(nullable = false, length = 2000)
    public String detail;

    @Column(length = 50)
    public String operator;

    public LocalDateTime createTime = LocalDateTime.now();
}
