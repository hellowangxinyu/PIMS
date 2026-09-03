package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单（配方表）
 * 涂料公司核心：定义产品配方，列出所需原料及用量
 */
@Entity @Table(name = "production_order")
public class ProductionOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String orderNo;             // MO-YYYY-NNNN

    @Column(nullable = false, length = 100)
    public String productName;         // 产品名称（涂料品名）

    @Column(length = 50)
    public String productCode;         // 产品编码（可选）

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal batchQty;        // 生产批量（如 1000 kg）

    @Column(length = 10)
    public String unit;                // 单位 kg/L/桶

    /** 状态: DRAFT/CONFIRMED/COMPLETED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    public Long recipeVersionId;       // 参照的配方版本ID（可选，用于追溯）

    /** v5.27：来源销售订单号（销售订单一键转生产时记录，可追溯） */
    @Column(length = 20)
    public String salesOrderNo;

    /** v5.27：排产顺序号（排产时分配，排产中心按此排序；null=未排产） */
    public Integer scheduleSeq;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
    /** v5.26：打印次数（每次打印 +1） */
    @Column(nullable = false)
    public Integer printCount = 0;

    /**
     * v5.27：展示状态（非落库，列表返回时按单据事件推导）
     * SCHEDULED=已排产 FEED=已投料(已出库领料) INBOUND=已入库(入库单DONE) SHIPPED=已发货(关联销售订单已发货)
     */
    @Transient
    public String displayStatus;

    /**
     * 投入产出比相关（非落库，列表返回时实时计算）
     * - inputQty  投入量 = 该订单所有 CONFIRMED 出库单(领料) qty 之和
     * - outputQty 产出量 = 该订单所有 DONE 入库单 qty 之和
     * - ioRatio   投入产出比(%) = 产出 / 投入 × 100，≥95% NORMAL，<95% ABNORMAL
     * - ioStatus  NORMAL / ABNORMAL / UNKNOWN(投入为0，无法计算)
     *
     * 注：当前投入/产出单位均为 kg，直接按重量 SUM；若未来出现 L/桶 投入物料，需先做单位换算。
     */
    @Transient public BigDecimal inputQty;
    @Transient public BigDecimal outputQty;
    @Transient public BigDecimal ioRatio;
    @Transient public String ioStatus;
}
