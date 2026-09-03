package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务督办（v5.67）：领导安排工作 → 员工汇报进度。
 * owner=主执行人（username），collaborators=协作人（逗号分隔）。
 * status: PENDING 待接收 / IN_PROGRESS 进行中 / COMPLETED 已完成 / CANCELLED 已取消。
 * 逾期=未完成且过 due_date（前端派生）。
 */
@Entity @Table(name = "task")
public class Task {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 25)
    public String docNo;               // TASK-YYYYMMDD-NNNN

    @Column(nullable = false, length = 200)
    public String title;

    @Column(length = 2000)
    public String description;

    /** 主执行人（username） */
    @Column(nullable = false, length = 50)
    public String owner;

    /** 协作人（逗号分隔 username） */
    @Column(length = 500)
    public String collaborators;

    /** HIGH/MEDIUM/LOW（默认 MEDIUM） */
    @Column(nullable = false, length = 10)
    public String priority = "MEDIUM";

    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    public LocalDate dueDate;

    public LocalDateTime completedAt;

    @Column(length = 1000)
    public String completedNote;

    /** 指派人（领导） */
    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;

    /** 进度记录（非持久化，Service 组装） */
    @Transient
    public List<TaskProgress> progressList;
}
