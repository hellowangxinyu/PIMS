package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 配方主表
 * 每个配方对应一个产品（成品/半成品），包含多个版本
 */
@Entity @Table(name = "recipe")
public class Recipe {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String recipeNo;            // 配方编号 RCP-NNNN

    @Column(length = 30)
    public String productCode;         // 关联物料编码（成品/半成品）

    @Column(nullable = false, length = 100)
    public String productName;         // 品名

    /** 配方类型: GRINDING=研磨(制浆), TINTING=调色(制漆) */
    @Column(nullable = false, length = 20)
    public String recipeType = "TINTING";

    /** 绑定的工艺路线 id（保存必填；工艺展示/打印按此取数） */
    public Long processTemplateId;
    /** v5.81 配方绑定质检模板（按成品/半成品三维匹配过滤；空=质检单创建时自动匹配） */
    public Long qcTemplateId;
    /** v5.81 包装标准（桶/袋/托盘），计入理论成本 ⌈批量÷容量⌉×单价 */
    public Long packagingStandardId;

    @Column(length = 50)
    public String category;            // 产品分类

    @Column(length = 500)
    public String description;         // 描述

    public Boolean enabled = true;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
    /** v5.26：打印次数（每次打印 +1） */
    @Column(nullable = false)
    public Integer printCount = 0;
    /** v5.26：被引用使用次数（订单头部引用配方版本 + 订单明细引用子配方，实时统计不落库） */
    @Transient
    public Integer usageCount = 0;
}
