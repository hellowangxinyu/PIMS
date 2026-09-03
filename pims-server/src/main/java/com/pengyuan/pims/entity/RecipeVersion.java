package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配方版本
 * 状态流: DRAFT → RELEASED → ARCHIVED
 * 一个配方同一时间最多一个 RELEASED 版本
 */
@Entity @Table(name = "recipe_version")
public class RecipeVersion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long recipeId;              // 关联配方ID

    @Column(nullable = false, length = 20)
    public String versionNo;           // 版本号 V1.0 / V2.0

    @Column(precision = 14, scale = 3)
    public BigDecimal batchQty;        // 标准批量

    @Column(length = 10)
    public String unit;                // 单位 kg/L

    /** 状态: DRAFT / RELEASED / ARCHIVED */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(length = 50)
    public String releasedBy;          // 发布人

    public LocalDateTime releasedTime; // 发布时间

    @Column(length = 50)
    public String createdBy;           // 创建人

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
