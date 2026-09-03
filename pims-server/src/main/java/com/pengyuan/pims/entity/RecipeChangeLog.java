package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 配方变更日志（v6.3 第二批）：版本创建/修改/发布/归档/树保存全留痕，审计追溯"3 月用的哪个版本、谁改的什么"。
 */
@Entity
@Table(name = "recipe_change_log")
public class RecipeChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long recipeId;

    @Column(length = 30)
    public String recipeNo;

    @Column(length = 100)
    public String productName;

    public Long versionId;

    @Column(length = 10)
    public String versionNo;

    /** CREATE / UPDATE / RELEASE / ARCHIVE / TREE_SAVE / DELETE */
    @Column(length = 20)
    public String action;

    @Column(length = 1000)
    public String detail;

    @Column(length = 50)
    public String operator;

    public LocalDateTime createTime = LocalDateTime.now();
}
