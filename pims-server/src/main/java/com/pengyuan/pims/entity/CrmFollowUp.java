package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** CRM 跟进记录（v5.50）：挂商机（opportunity_id）或客户（customer_id），至少其一 */
@Entity @Table(name = "crm_follow_up")
public class CrmFollowUp {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long opportunityId;

    public Long customerId;

    public LocalDate followDate;

    /** 方式：PHONE 电话 / VISIT 拜访 / WECHAT 微信 / EMAIL 邮件 / MEETING 会议 / OTHER 其他 */
    @Column(nullable = false, length = 20)
    public String method = "PHONE";

    @Column(nullable = false, length = 2000)
    public String content;

    /** 下次跟进日期（Dashboard 待办：到期提醒） */
    public LocalDate nextDate;

    @Column(length = 50)
    public String operator;

    public LocalDateTime createTime = LocalDateTime.now();
}
