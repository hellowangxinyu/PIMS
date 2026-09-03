package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 工艺路线（标准工艺模板）：同配方可建多条命名路线，配方绑定其中一条 */
@Entity
@Table(name = "process_template")
public class ProcessTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 配方类型：GRINDING / TINTING（仅分类，同类型允许多条路线） */
    @Column(nullable = false, length = 20)
    public String recipeType;

    @Column(nullable = false, length = 50)
    public String name;

    /** 包装要求（投料单/工艺指导单打印时带上） */
    @Column(length = 1000)
    public String packingRequirement;

    /** 该类型的默认路线（配方回填/自动选中用，每类型至多一条） */
    @Column(nullable = false)
    public Boolean isDefault = false;

    public Boolean enabled = true;

    /** 创建人（v5.60 制单人体系补录：历史路线为空显示空白） */
    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime;
    public LocalDateTime updateTime;
}
