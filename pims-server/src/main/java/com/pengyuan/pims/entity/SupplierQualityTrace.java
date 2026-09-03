package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v5.59 供应商质量追溯单。批号驱动：品控按物料+批号发起，系统自动带出原始采购入库信息（快照存单），
 * 打印损失沟通函（多模板）发供应商；采购跟进处理（协商折让/赔款/补货/换货/供应商拒绝赔付等）。
 * 状态机：PROCESSING 处理中（登记即处理中，可编辑/删除纠错）→ RESOLVED 已处理
 * （处理完毕强制填处理结果：结果类型+处理说明必填，赔款时赔付金额必填；不可编辑不可删除，永久留档）。
 */
@Entity @Table(name = "supplier_quality_trace")
public class SupplierQualityTrace {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String traceNo;                  // ZS-YYYY-NNNN

    public Long supplierId;
    @Column(nullable = false, length = 100)
    public String supplierName;

    @Column(length = 30) public String purchaseOrderNo;   // 带出：采购单号/委外订单号
    /** 来源单据类型：PURCHASE 采购 / OUTSOURCE 委外加工（成品漆大头，代工厂按加工费口径），null=未分类 */
    @Column(length = 20) public String orderCategory;
    @Column(length = 30) public String materialCode;
    @Column(length = 100) public String materialName;
    /** 追溯锚点：必须精确到批号 */
    @Column(nullable = false, length = 30)
    public String batchNo;

    public LocalDate arrivalDate;           // 带出：到货日期
    public BigDecimal purchaseQty;          // 快照：入库数量
    public BigDecimal purchaseUnitPrice;    // 快照：采购单价
    public BigDecimal purchaseAmount;       // 快照：采购金额（数量×单价）

    @Column(length = 20) public String qcInspectionNo;    // 带出：质检单号
    @Column(length = 20) public String qcStatus;          // 带出：台账质检状态

    public LocalDate issueDate;             // 发现日期
    @Column(length = 20) public String category;          // 问题类型：色差/性能不达标/结块沉淀/包装破损/杂质超标/批次不稳/其他
    @Column(nullable = false, length = 2000) public String description;

    public BigDecimal lossAmount;           // 损失金额（品控评定）

    @Column(nullable = false, length = 20)
    public String status = "PROCESSING";

    @Column(length = 30) public String resultType;        // 处理结果类型：协商折让/赔款/补货/换货/供应商拒绝赔付/免赔/其他
    @Column(precision = 14, scale = 2) public BigDecimal compensationAmount;
    @Column(length = 1000) public String resultRemark;
    @Column(length = 50) public String handler;
    public LocalDate resolveDate;

    @Column(nullable = false) public Integer printCount = 0;

    /** v5.60 制单人（品控发起人） */
    @Column(length = 50) public String createdBy;

    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
