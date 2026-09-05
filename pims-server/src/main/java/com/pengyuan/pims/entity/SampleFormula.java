package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * v7.7 打样配方（与打样单 1:1）。打样员在打样任务页录入：
 * 用料明细（色浆/原料，自由用量不强制 100）+ 成品三维分类（小类/主材/色系）+ 中文名。
 * 首次保存自动创建 C 类成品物料（9 位编码自动生成）并回填 material_code；
 * 转制漆配方时按标准批量 100 折算成配方树（尾差归末行守恒）。
 */
@Entity @Table(name = "sample_formula")
public class SampleFormula {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20) public String formulaNo;

    @Column(nullable = false, unique = true) public Long sampleRequestId;

    /** 首次保存时生成的成品物料编码（9 位，如 CWTH00010） */
    @Column(length = 30) public String materialCode;
    @Column(length = 100) public String materialName;

    @Column(length = 10) public String subCategory;
    @Column(length = 10) public String mainMaterial;
    /** 色系必填（C 类取码强制，与面漆/底漆无关） */
    @Column(length = 10) public String colorSeries;

    /** 打样总量=明细自由合计（kg） */
    @Column(precision = 14, scale = 3) public BigDecimal totalQty;

    /** 估算成本 元/kg = Σ(用量×移动加权均价) ÷ 总量（与配方树成本同源口径） */
    @Column(precision = 14, scale = 2) public BigDecimal estCost;

    /** 留样位置（留样柜号/架位，打印配方单带上） */
    @Column(length = 20) public String sampleLocation;

    /** 转制漆配方后回写（幂等防重复转） */
    public Long convertedRecipeId;
    public LocalDateTime convertedTime;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
