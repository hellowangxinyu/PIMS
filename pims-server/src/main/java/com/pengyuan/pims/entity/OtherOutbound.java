package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 其他出库单
 * 非销售/委外/生产场景的出库（如报废、样品、盘亏等），库存直接减少
 */
@Entity @Table(name = "other_outbound")
public class OtherOutbound {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;               // OTHER-OUT-YYYY-NNNN

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 30)
    public String batchNo;

    @Column(nullable = false, length = 20)
    public String warehouseId;         // 出库仓库

    /** 出库库位（采购退货出库时记录具体库位） */
    @Column(length = 20)
    public String locationId;

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;

    @Column(length = 10)
    public String unit;

    /** 批号库存单价（出库时按批号直取） */
    @Column(precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 实际材料成本 = qty × unitPrice */
    @Column(precision = 14, scale = 2)
    public BigDecimal cost;

    /** 出库原因: SCRAP(报废)/SAMPLE(样品)/LOSS(盘亏)/RETURN(退货)/OTHER(其他) */
    @Column(length = 20)
    public String reason;

    // ============ 退货关联字段（reason=RETURN 时回填退货单）============

    /** 关联退货单 ID */
    public Long returnOrderId;

    /** 关联退货单号（便于列表展示与追溯） */
    @Column(length = 20)
    public String returnRefDocNo;

    // ============ 财务字段（非退货场景可选生成应收） ============

    /** 是否生成应收账款 */
    public Boolean genFinance = false;

    /** 应收金额（genFinance=true 时必填） */
    @Column(precision = 14, scale = 2)
    public BigDecimal financeAmount;

    /** 客户ID（genFinance=true 时必填） */
    public Long financePartnerId;

    /** 客户名称（冗余，便于展示） */
    @Column(length = 50)
    public String financePartnerName;

    /** 已生成的应收单号（冗余，便于追溯） */
    @Column(length = 20)
    public String financeDocNo;

    /** 状态: DRAFT/CONFIRMED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
