package com.pengyuan.pims.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 质检单检测项（v5.32：质检单创建时从质检模板快照，模板后续修改不影响本单）
 * 判定时逐项填写 measured_value（实测值）与 item_result（单项判定）。
 */
@Entity
@Table(name = "quality_inspection_item")
public class QualityInspectionItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "inspection_id", nullable = false)
    public Long inspectionId;

    /** 来源模板（快照追溯；模板删除后仅作参考） */
    @Column(name = "template_id")
    public Long templateId;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(length = 200)
    public String standard;

    @Column(length = 30)
    public String unit;

    @Column(length = 200)
    public String method;

    /** 实测值（自由文本：23.5 / 透明 / 合格） */
    @Column(name = "measured_value", length = 200)
    public String measuredValue;

    /** 单项判定：PASS合格 / FAIL不合格 / 空未检 */
    @Column(name = "item_result", length = 20)
    public String itemResult;

    public Integer sortOrder = 0;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
