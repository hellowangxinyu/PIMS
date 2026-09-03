package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘库单据
 * 支持三种类型：盘盈(STOCK_GAIN)、盘亏(STOCK_LOSS)、库位调整(LOCATION_ADJUST)
 */
@Entity @Table(name = "stock_check")
public class StockCheck {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String docNo;               // CHK-YYYY-NNNN

    /** 单据类型: STOCK_GAIN / STOCK_LOSS / LOCATION_ADJUST */
    @Column(nullable = false, length = 20)
    public String docType;

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 30)
    public String batchNo;

    @Column(nullable = false, length = 20)
    public String warehouseId;

    /** 系统数量（盘盈/盘亏时记录） */
    @Column(precision = 14, scale = 3)
    public BigDecimal systemQty;

    /** 实盘数量（盘盈/盘亏时记录） */
    @Column(precision = 14, scale = 3)
    public BigDecimal actualQty;

    /** 差异数量（正=盘盈，负=盘亏） */
    @Column(precision = 14, scale = 3)
    public BigDecimal diffQty;

    /** 原库位ID（库位调整时用） */
    @Column(length = 20)
    public String fromLocationId;

    @Column(length = 50)
    public String fromLocationName;

    /** 目标库位ID（库位调整时用） */
    @Column(length = 20)
    public String toLocationId;

    @Column(length = 50)
    public String toLocationName;

    /** 调整数量（库位调整时用） */
    @Column(precision = 14, scale = 3)
    public BigDecimal adjustQty;

    /** 状态: CONFIRMED */
    @Column(nullable = false, length = 20)
    public String status = "CONFIRMED";

    @Column(length = 50)
    public String operator;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
}
