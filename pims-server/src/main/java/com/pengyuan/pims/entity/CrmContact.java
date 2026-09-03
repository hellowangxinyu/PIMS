package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** CRM 联系人（v5.50）：挂客户档案（customer_id），或独立线索联系人（company_name 任意） */
@Entity @Table(name = "crm_contact")
public class CrmContact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 关联客户档案（可空=线索联系人，尚未建档） */
    public Long customerId;

    @Column(nullable = false, length = 100)
    public String companyName;

    @Column(nullable = false, length = 50)
    public String name;

    @Column(length = 50)
    public String title;

    @Column(length = 30)
    public String phone;

    @Column(length = 50)
    public String wechat;

    @Column(length = 100)
    public String email;

    /** 主要联系人 */
    public Boolean isPrimary = false;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
