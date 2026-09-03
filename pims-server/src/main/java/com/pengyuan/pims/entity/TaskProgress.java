package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务进度汇报日志（v5.67）：员工汇报进度 + 状态变迁节点。
 * actionType: START（开始执行）/ UPDATE（进度汇报）/ COMPLETE（完成）/ CANCEL（取消）/ REOPEN（重开）/ CREATE（创建）
 */
@Entity @Table(name = "task_progress")
public class TaskProgress {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long taskId;

    @Column(nullable = false, length = 50)
    public String reporter;

    @Column(length = 1000)
    public String content;

    /** START/UPDATE/COMPLETE/CANCEL/REOPEN/CREATE */
    @Column(nullable = false, length = 20)
    public String actionType;

    public LocalDateTime createTime = LocalDateTime.now();
}
