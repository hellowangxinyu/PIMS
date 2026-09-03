package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 物料用量月度汇总（v4.8 新增，低库存预警报表数据源）
 * 由 SQLite 触发器 trg_movement_usage 在库存异动插入时增量维护：
 *  - out_qty：当月出库用量合计（仅 PRODUCTION_OUT/OUTSOURCE_OUT/OTHER_OUT/SALES_OUT，口径与低库存报表一致）
 *  - usage_days：当月有出库记录的去重天数
 * v5.34：增加 warehouse_id 维度——低库存预警按仓库计算
 * (material_code, period, warehouse_id) 唯一，见 uk_stat_material_usage
 */
@Entity @Table(name = "stat_material_usage")
public class StatMaterialUsage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 30)
    public String materialCode;

    /** 统计月份 YYYY-MM */
    @Column(nullable = false, length = 7)
    public String period;

    /** v5.34：出库仓库ID（'' = 历史无仓库维度数据） */
    @Column(name = "warehouse_id", length = 20)
    public String warehouseId;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal outQty;

    @Column(nullable = false)
    public Integer usageDays;
}
