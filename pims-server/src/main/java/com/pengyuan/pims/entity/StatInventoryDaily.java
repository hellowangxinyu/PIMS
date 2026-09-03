package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 库存变动日汇总表
 * 按「日期 + 物料 + 仓库」聚合出入库数据，由 SQLite 触发器在 inventory_movement 写入时自动维护
 * 支持按日/月粒度快速查询库存吞吐，避免扫描百万级异动流水
 */
@Entity
@Table(name = "stat_inventory_daily",
    uniqueConstraints = @UniqueConstraint(columnNames = {"statDate", "materialCode", "warehouseId"}))
public class StatInventoryDaily {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 日期，格式 yyyy-MM-dd */
    @Column(nullable = false, length = 10)
    public String statDate;

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(nullable = false, length = 20)
    public String warehouseId;

    /** 当日入库总量 */
    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal inQty = BigDecimal.ZERO;

    /** 当日出库总量 */
    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal outQty = BigDecimal.ZERO;

    /** 当日入库金额（入量×单价估算） */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal inAmount = BigDecimal.ZERO;
}
