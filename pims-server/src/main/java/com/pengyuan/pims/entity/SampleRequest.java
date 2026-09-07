package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v5.53 打样/样品申请。状态机（涂料打样链路）：
 * APPLIED 已申请 → ASSIGNED 已派发（v7.7 选打样员）→ COLORING 调色中（自动在研发进度建条目）
 * → FORMULATED 已录配方（v7.7 打样配方保存，自动生成成品物料）→ SENT 已寄样
 * → 反馈：SATISFIED 客户满意 / ADJUST 需调整（adjustCount+1，回到调色）
 * → WON 已转单（填订单号，研发进度结案）/ LOST 未成交（研发进度结案）
 */
@Entity @Table(name = "sample_request")
public class SampleRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String sampleNo;
    /** 正式客户（可选，线索客户只填 customerName） */
    public Long customerId;
    @Column(nullable = false, length = 100) public String customerName;
    /** 关联正式物料（可选） */
    @Column(length = 30) public String materialCode;
    /** 意向产品/颜色要求（必填文本） */
    @Column(nullable = false, length = 500) public String materialDesc;
    @Column(precision = 14, scale = 3) public BigDecimal qty = BigDecimal.ONE;
    @Column(length = 10) public String unit = "kg";
    @Column(length = 50) public String applicant;
    public LocalDate applyDate;
    @Column(nullable = false, length = 20) public String status = "APPLIED";
    @Column(length = 50) public String colorist;
    @Column(length = 500) public String colorNote;
    /** 调整轮次（客户反馈需调整回到调色的次数） */
    public Integer adjustCount = 0;
    public LocalDate sendDate;
    @Column(length = 50) public String expressNo;
    @Column(length = 1000) public String feedbackContent;
    public LocalDate feedbackDate;
    @Column(length = 20) public String wonOrderNo;
    @Column(length = 200) public String lossReason;
    /** 联动的研发进度条目 id（COLORING 时自动创建） */
    public Long rdProgressId;
    /** v7.7.2 关联打样（复样参考）：指向历史打样单 id，打样员录配方时可参考/带入其配方；替代原"关联物料"语义 */
    public Long refSampleId;
    /** v7.7 派发的打样员（账号名）；打样任务页只看自己名下的单 */
    @Column(length = 50) public String assignee;
    /** 派发时间 / 打样员接收时间 */
    public LocalDateTime assignTime;
    public LocalDateTime receiveTime;
    @Column(length = 500) public String remark;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;

    /** 状态中文名（非持久化） */
    @Transient public String statusLabel;
}
