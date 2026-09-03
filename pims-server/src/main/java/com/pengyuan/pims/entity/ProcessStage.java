package com.pengyuan.pims.entity;

import jakarta.persistence.*;

/** 工艺工序（预混 / 砂磨 / 打包留样送检 / 刷缸） */
@Entity
@Table(name = "process_stage")
public class ProcessStage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long templateId;

    /** 工序号：01 / 02 / 03 / 04 */
    @Column(length = 10)
    public String stageNo;

    @Column(nullable = false, length = 50)
    public String stageName;

    /** 责任岗位提示：配料人 / 砂磨工 / 检测人 */
    @Column(length = 50)
    public String roleHint;

    public Integer sortOrder = 0;
}
