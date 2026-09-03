package com.pengyuan.pims.entity;

import jakarta.persistence.*;

/**
 * 工序步骤（操作指令 + 参数）。
 * description 支持 {{N}} 占位符：N 为配方顶层物料的投料顺序（按 sortOrder），
 * 展示/打印时自动替换为对应物料名称/编码，从而让工艺模板与具体配方解耦。
 */
@Entity
@Table(name = "process_step")
public class ProcessStep {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long stageId;

    /** 步骤编号：A / B / C / D */
    @Column(length = 10)
    public String stepCode;

    /** 操作描述，支持 {{1}} {{2}} 占位符关联配方物料 */
    @Column(length = 1000)
    public String description;

    /** 参数：500-600rpm / 30min / 2-3圈/秒 等 */
    @Column(length = 200)
    public String params;

    public Integer sortOrder = 0;
}
