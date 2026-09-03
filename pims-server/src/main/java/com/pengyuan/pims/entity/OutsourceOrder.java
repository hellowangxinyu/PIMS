package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委外订单（配方表）
 * 与生产订单类似：产品名 + 批量 + 配方明细，指定代工厂加工
 */
@Entity @Table(name = "outsource_order")
public class OutsourceOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String orderNo;             // OO-YYYY-NNNN

    @Column(nullable = false, length = 100)
    public String productName;         // 产品名称（委外加工品）

    @Column(length = 50)
    public String productCode;         // 产品编码

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal batchQty;        // 生产批量

    @Column(length = 10)
    public String unit;                // kg/L/桶

    @Column(length = 100)
    public String processor;           // 代工厂名称

    /** 代工厂供应商ID（关联Supplier，用于生成应付账款） */
    public Long supplierId;

    /** 加工费单价（元/单位），用于委外入库时计算应付加工费 */
    @Column(precision = 12, scale = 2)
    public BigDecimal processingFee;

    /** 参照的配方版本ID（可选） */
    public Long recipeVersionId;

    /** 状态: DRAFT/CONFIRMED/COMPLETED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(length = 50)
    public String createdBy;

    /** v5.27：来源销售订单号（销售订单一键转委外时记录，可追溯） */
    @Column(length = 20)
    public String salesOrderNo;

    /** v5.27：排产顺序号（排产时分配，排产中心按此排序；null=未排产） */
    public Integer scheduleSeq;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
    /** v5.26：打印次数（每次打印 +1） */
    @Column(nullable = false)
    public Integer printCount = 0;

    /**
     * v5.27：展示状态（非落库，列表返回时按单据事件推导）
     * OUTSOURCED=已委外 INBOUND=已入库(入库单DONE) SHIPPED=已发货(关联销售订单已发货)
     */
    @Transient
    public String displayStatus;
}
