package com.pengyuan.pims.entity;

import jakarta.persistence.*;

/**
 * 工序质检项（细度 / 出口温度 等）。
 * 区别于入库质检（quality_inspection 一单一判定）：此处为工序级、可多次检测的预定义项，
 * 打印指导单时按 testTimes 生成空白检测行。
 */
@Entity
@Table(name = "process_qc_item")
public class ProcessQcItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long stageId;

    @Column(nullable = false, length = 50)
    public String name;          // 细度 / 出口温度

    @Column(length = 100)
    public String standard;      // ≤10μm / ≤65℃

    /** 检测次数（打印按此出空白行，如细度测 5 次） */
    public Integer testTimes = 1;

    @Column(length = 20)
    public String unit;

    @Column(length = 200)
    public String method;

    public Integer sortOrder = 0;
}
