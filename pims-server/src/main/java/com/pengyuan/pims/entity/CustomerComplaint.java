package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v5.53 客户投诉/质量反馈。状态机：
 * PROCESSING 处理中（登记即处理中，可编辑）→ RESOLVED 已处理（填原因+措施）→ CLOSED 已关闭（客户确认）。
 * 填了 batchNo 可追溯该批次全部出入库流水（含归档表）。
 */
@Entity @Table(name = "customer_complaint")
public class CustomerComplaint {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String complaintNo;
    public Long customerId;
    @Column(nullable = false, length = 100) public String customerName;
    @Column(length = 30) public String materialCode;
    @Column(length = 100) public String materialName;
    /** 涉及批号——质量追溯锚点 */
    @Column(length = 30) public String batchNo;
    /** 关联质检单号（可选） */
    @Column(length = 20) public String qcDocNo;
    /** 关联销售订单号（可选） */
    @Column(length = 20) public String salesOrderNo;
    public LocalDate complaintDate;
    /** 投诉分类：色差/性能不达标/结块沉淀/包装破损/其他 */
    @Column(length = 20) public String category;
    @Column(nullable = false, length = 2000) public String description;
    @Column(nullable = false, length = 20) public String status = "PROCESSING";
    /** 原因分析 */
    @Column(length = 1000) public String cause;
    /** 处理措施（退换/补货/赔偿等） */
    @Column(length = 1000) public String action;
    @Column(length = 50) public String handler;
    /** v5.60 制单人（登记人） */
    @Column(length = 50) public String createdBy;
    public LocalDate resolveDate;
    public LocalDate closeDate;
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
