package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 其他入库单
 * 非采购/生产/委外场景的入库（如退货入库、盘盈、赠品、其他），库存直接增加
 */
@Entity @Table(name = "other_inbound")
public class OtherInbound {

    /** v5.76 税率%（默认13，单据级可改） */
    @jakarta.persistence.Column(precision = 5, scale = 2)
    public java.math.BigDecimal taxRate = java.math.BigDecimal.valueOf(13);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;               // OTHER-IN-YYYY-NNNN

    @Column(nullable = false, length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 30)
    public String batchNo;

    @Column(nullable = false, length = 20)
    public String warehouseId;         // 入库仓库

    @Column(length = 20)
    public String locationId;          // 库位

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;

    @Column(precision = 12, scale = 4)
    public BigDecimal price;           // 单价

    @Column(length = 10)
    public String unit;

    /** 入库原因: RETURN(退货)/SURPLUS(盘盈)/GIFT(赠品)/OTHER(其他) */
    @Column(length = 20)
    public String reason;

    // ============ 退货关联字段（reason=RETURN 时使用）============

    /** 退货关联原单据类型: SALES_OUTBOUND(销售退货) / PURCHASE_ARRIVAL(采购退货) */
    @Column(length = 20)
    public String returnRefType;

    /** 退货关联原单据号（销售出库单号 / 采购到货关联采购单号） */
    @Column(length = 30)
    public String returnRefDocNo;

    /** 退货关联原单据 ID（销售出库单 ID / 采购到货单 ID） */
    public Long returnRefId;

    /** 销售退货冗余客户 ID */
    public Long customerId;

    /** 采购退货冗余供应商 ID */
    public Long supplierId;

    /** 退货金额 = 退货数量 × 原单价，用于冲减应收/应付 */
    @Column(precision = 14, scale = 2)
    public BigDecimal returnAmount;

    /** 冲减状态: NONE(未冲减) / APPLIED(已冲减) */
    @Column(length = 20)
    public String returnOffsetStatus = "NONE";

    // ============ 财务字段（非退货场景可选生成应付） ============

    /** 是否生成应付账款 */
    public Boolean genFinance = false;

    /** 应付金额（genFinance=true 时必填） */
    @Column(precision = 14, scale = 2)
    public BigDecimal financeAmount;

    /** 供应商ID（genFinance=true 时必填） */
    public Long financePartnerId;

    /** 供应商名称（冗余，便于展示） */
    @Column(length = 50)
    public String financePartnerName;

    /** 已生成的应付单号（冗余，便于追溯） */
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
