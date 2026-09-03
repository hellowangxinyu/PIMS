package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每周议题（v5.44）——对应管委会周度会议 Excel「每周议题」页 */
@Entity @Table(name = "weekly_topic")
public class WeeklyTopic {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 责任人 */
    @Column(nullable = false, length = 50)
    public String owner;

    /** 分类（字典 weekly_topic_category：生产/调色/配方/客诉/物料/财务/物流/体系） */
    @Column(nullable = false, length = 20)
    public String category;

    /** 待办事项 */
    @Column(nullable = false, length = 2000)
    public String content;

    /** 结果/进展 */
    @Column(length = 2000)
    public String result;

    /** 计划完成时间 */
    public LocalDate planDate;

    /** 已结案时间（非空=已结案） */
    public LocalDate closedDate;

    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
