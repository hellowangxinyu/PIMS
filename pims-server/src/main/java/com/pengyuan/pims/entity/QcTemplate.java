package com.pengyuan.pims.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 质检模板（v5.32：按物料大类区分检测内容）
 * apply_category 对应 material_category 字典：A助剂/P颜料/F填料/R树脂/S溶剂/B半成品/C成品。
 * 同类别可建多套，仅 is_default=1 且启用的模板在质检单创建时自动快照。
 */
@Entity
@Table(name = "qc_template")
public class QcTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 100)
    public String name;           // 模板名称，如"溶剂来料检验模板"

    @Column(name = "apply_category", nullable = false, length = 10)
    public String applyCategory;  // 适用物料大类 A/P/F/R/S/B/C
    /** v5.81 三维匹配（空=不限）：成品按 小类+主材+色系，半成品按 小类+主材 */
    @Column(length = 50) public String subCategory;
    @Column(length = 20) public String mainMaterial;
    @Column(length = 20) public String colorSeries;

    @Column(name = "is_default")
    public Boolean isDefault = false;

    public Boolean enabled = true;

    @Column(length = 500)
    public String remark;

    /** 创建人（v5.60 制单人体系补录：历史模板为空显示空白） */
    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
