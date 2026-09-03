package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会计科目（v5.61 总账体系）
 * 两级结构：parent_code 为空为一级科目，非空为明细科目（编码如 6602.01）
 * category: ASSET 资产 / LIABILITY 负债 / EQUITY 权益 / COST 成本 / PL 损益
 * direction: DR 借（余额在借方）/ CR 贷（余额在贷方，如累计折旧、收入类）
 * opening_balance/opening_direction: 期初建账数（建账启用期间之前的存量余额）
 */
@Entity @Table(name = "account_subject")
public class AccountSubject {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    /** 上级科目编码（一级科目为空） */
    @Column(length = 20)
    public String parentCode;

    @Column(nullable = false, length = 20)
    public String category;

    @Column(nullable = false, length = 5)
    public String direction;

    /** ENABLED / DISABLED（已引用科目只可停用不可删除） */
    @Column(nullable = false, length = 20)
    public String status = "ENABLED";

    /** 期初建账余额（正数） */
    @Column(precision = 14, scale = 2)
    public BigDecimal openingBalance = BigDecimal.ZERO;

    /** 期初余额方向 DR/CR */
    @Column(length = 5)
    public String openingDirection;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
