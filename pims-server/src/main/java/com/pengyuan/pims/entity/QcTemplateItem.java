package com.pengyuan.pims.entity;

import jakarta.persistence.*;

/**
 * 质检模板检测项（v5.32）
 * 结构对齐工序质检项 process_qc_item：名称/标准要求/单位/检验方法。
 */
@Entity
@Table(name = "qc_template_item")
public class QcTemplateItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "template_id", nullable = false)
    public Long templateId;

    @Column(nullable = false, length = 100)
    public String name;           // 检测项目：外观 / 细度 / 粘度

    @Column(length = 200)
    public String standard;       // 标准要求：≤40μm / 按产品标准

    @Column(length = 30)
    public String unit;           // 单位：μm / KU / %

    @Column(length = 200)
    public String method;         // 检验方法/依据：GB/T 1724 刮板细度计

    public Integer sortOrder = 0;
}
