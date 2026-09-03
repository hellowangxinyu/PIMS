package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 研发进度（v5.44）——对应管委会周度会议 Excel「研发进度」页 */
@Entity @Table(name = "rd_progress")
public class RdProgress {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 提出时间 */
    public LocalDate raiseDate;

    /** 提出人 */
    @Column(nullable = false, length = 50)
    public String owner;

    /** 分类（字典 weekly_topic_category 共用） */
    @Column(nullable = false, length = 20)
    public String category;

    /** 内容 */
    @Column(nullable = false, length = 2000)
    public String content;

    /** 结果/进展 */
    @Column(length = 2000)
    public String result;

    /** 下次跟进时间 */
    public LocalDate nextDate;

    /** 已结案时间（非空=已结案） */
    public LocalDate closedDate;

    /** 进度备注 */
    @Column(length = 500)
    public String progress;

    @Column(length = 50)
    public String createdBy;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
