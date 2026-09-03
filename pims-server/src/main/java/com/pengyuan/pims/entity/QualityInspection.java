package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质检单
 * 来料质检(INCOMING)：采购到货 → 待检 → QC判定 → 合格才入库
 * 出厂质检(OUTGOING)：生产入库 → 待检 → QC判定 → 合格才可销售出库
 */
@Entity @Table(name = "quality_inspection")
public class QualityInspection {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String inspectionNo;        // QC-IN-YYYY-NNNN / QC-OUT-YYYY-NNNN

    /** INCOMING(来料质检) / OUTGOING(出厂质检) */
    @Column(nullable = false, length = 20)
    public String type;

    /** 关联单据号（采购单号 / 生产入库单号） */
    @Column(length = 30)
    public String refDocNo;

    /** v6.1.2：关联到货单 ID（同订单同物料分批到货时区分各批的质检单，反审核按此精确隔离） */
    public Long arrivalId;

    /** 关联单据类型：PURCHASE / PRODUCTION_INBOUND */
    @Column(length = 30)
    public String refDocType;

    @Column(length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 30)
    public String batchNo;

    @Column(precision = 14, scale = 3)
    public BigDecimal qty;

    @Column(length = 10)
    public String unit;

    @Column(length = 20)
    public String warehouseId;

    @Column(length = 20)
    public String locationId;

    @Column(precision = 14, scale = 2)
    public BigDecimal unitPrice;

    /** 生产日期（用于保质期计算） */
    public LocalDate produceDate;

    /** 被检物料大类（v5.0）：A/P/F/R/S=材料、B=半成品、C=成品；用于质检页分类 Tab */
    @Column(length = 5)
    public String materialCategory;

    /** 状态: PENDING(待检) / PASS(合格) / CONCESSION(让步接收) / REJECT(退货/不合格) */
    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    /** QC检验员 */
    @Column(length = 50)
    public String inspector;

    /** 检验日期 */
    public LocalDate inspectDate;

    /** 判定说明 */
    @Column(length = 500)
    public String resultRemark;

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
    /** v5.26：打印次数（每次打印 +1） */
    @Column(nullable = false)
    public Integer printCount = 0;
}
