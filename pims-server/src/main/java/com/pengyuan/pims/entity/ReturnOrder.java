package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货单
 * 采购退货（PURCHASE_RETURN）：QC 来料质检判定"退货" → 自动生成退货单(DRAFT) → 采购员审核(APPROVED/REJECTED)
 *          → 仓管参照退货单做退货出库(DONE)，货退给供应商，出库并冲减应付账款
 * 销售退货（SALES_RETURN）：客户退货，手工创建退货单(DRAFT) → 销售员审核(APPROVED/REJECTED)
 *          → 仓管参照退货单做退货入库(DONE)，库存增加并冲减应收账款
 */
@Entity @Table(name = "return_order")
public class ReturnOrder {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 退货单号: RO-YYYY-NNNN(采购退货) / SR-YYYY-NNNN(销售退货) */
    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    /** 退货类型: PURCHASE_RETURN(采购退货) / SALES_RETURN(销售退货) */
    @Column(nullable = false, length = 20)
    public String type;

    /** 原采购到货单 ID（QC 判定来源） */
    public Long refArrivalId;

    /** 原采购到货关联的采购订单号（用于冲减应付） */
    @Column(length = 30)
    public String purchaseOrderNo;

    /** 关联的质检单号（QC 判定 REJECT 自动生成时回填） */
    @Column(length = 30)
    public String qcInspectionNo;

    /** 原销售订单号（销售退货冲减应收用） */
    @Column(length = 30)
    public String salesOrderNo;

    /** 原销售出库单号（客户退货参照出库单创建时回填） */
    @Column(length = 30)
    public String refSalesOutboundNo;

    /** 客户 ID（销售退货） */
    public Long customerId;

    /** 客户名称（销售退货，冗余便于展示） */
    @Column(length = 100)
    public String customerName;

    @Column(nullable = false, length = 30)
    public String materialCode;

    /** v5.27：手工库存退货锁定的库存批号（创建时选择，出库按该批号退） */
    @Column(length = 50)
    public String batchNo;

    @Column(length = 100)
    public String materialName;

    @Column(length = 10)
    public String unit;

    /** 退货数量 */
    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;

    /** 单价（取原采购订单明细单价，用于冲减应付） */
    @Column(precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 退货金额 = qty × unitPrice */
    @Column(precision = 14, scale = 2)
    public BigDecimal returnAmount;

    /** v5.35 油尾退回：结算方式 DISCOUNT_RETURN(折价退回) / PAID_RECYCLE(付费回收) */
    @Column(length = 20)
    public String settleType;

    /** v5.35 油尾退回：油尾库库位 ID（入库精确到库位；v5.38.2 起库位由体系自动路由，仅存确认结果） */
    @Column(length = 20)
    public String locationId;

    /** v5.38.2 油尾退回：入库目标仓（该仓的聚酯/氟碳油尾库） */
    @Column(length = 20)
    public String warehouseId;

    /** v5.35 油尾退回：油尾库库位名称（冗余展示） */
    @Column(length = 50)
    public String locationName;

    /** 供应商 ID */
    public Long supplierId;

    /** 状态: DRAFT(待审核) / APPROVED(已审核待出库/待入库) / REJECTED(已驳回) / DONE(已完成) */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    /** 关联的退货出库单号（采购退货仓管出库后回填） */
    @Column(length = 20)
    public String outboundDocNo;

    /** 关联的退货入库单号（销售退货仓管入库后回填） */
    @Column(length = 20)
    public String inboundDocNo;

    /** 审核人 */
    @Column(length = 50)
    public String approvedBy;

    /** 审核时间 */
    public LocalDateTime approveTime;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
