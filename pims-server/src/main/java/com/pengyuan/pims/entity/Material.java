package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "material")
public class Material {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(length = 50) public String brand;
    @Column(length = 50) public String category;
    @Column(length = 50) public String subCategory;

    /** v5.17 成品三维分类：主材（树脂体系：聚酯/氟碳/环氧/丙烯酸） */
    @Column(length = 20) public String mainMaterial;

    /** v5.17 成品三维分类：色系（黑/白/灰/蓝/绿/红/黄） */
    @Column(length = 20) public String colorSeries;
    /** 品牌归属：成品物料(C)的品牌归属，自产成品默认"芃远"，外购成品为对应成品供应商名称 */
    @Column(length = 100) public String brandOwner;
    /** 保质期天数（null表示不限） */
    public Integer shelfLifeDays;
    /** 平替物料编码（v5.1，逗号分隔，仅原材料 A/P/F/R/S；配方树中可直接切换） */
    @Column(length = 500)
    public String alternativeCodes;
    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
