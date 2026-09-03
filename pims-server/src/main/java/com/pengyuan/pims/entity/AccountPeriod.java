package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 账务期间（v5.61 总账体系）—— 结账状态记录
 * 只有结过账的期间才有行；closed=1 表示已结账（该期间禁止一切凭证写操作）。
 * 反结账仅允许从最近已结期间逐月往前。
 */
@Entity @Table(name = "account_period")
public class AccountPeriod {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 期间 YYYY-MM */
    @Column(nullable = false, unique = true, length = 10)
    public String period;

    @Column(nullable = false)
    public Boolean closed = true;

    @Column(length = 50)
    public String closedBy;

    public LocalDateTime closeTime;
}
