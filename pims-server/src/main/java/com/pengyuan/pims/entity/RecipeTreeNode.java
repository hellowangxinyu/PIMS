package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 配方树节点（BOM明细）
 * 支持多级嵌套：成品 → 半成品/原料 → 原料
 */
@Entity @Table(name = "recipe_tree_node")
public class RecipeTreeNode {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long versionId;             // 关联配方版本ID

    public Long parentNodeId;          // 父节点ID（null=顶层直接子节点）

    /** 节点类型: MATERIAL（原料）/ SUB_RECIPE（半成品子配方） */
    @Column(nullable = false, length = 20)
    public String nodeType;

    @Column(length = 30)
    public String materialCode;        // 物料编码

    @Column(length = 100)
    public String materialName;        // 品名

    @Column(length = 100)
    public String spec;                // 规格

    @Column(length = 50)
    public String category;            // 物料大类

    @Column(length = 50)
    public String subCategory;         // 物料小类

    @Column(length = 10)
    public String unit;                // 单位

    @Column(nullable = false, precision = 14, scale = 3)
    public BigDecimal qty;             // 用量（相对父节点一个批次）

    public Long refRecipeId;           // SUB_RECIPE时引用的配方ID

    public Integer sortOrder = 0;      // 排序

    @Column(length = 200)
    public String remark;
}
