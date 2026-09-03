package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 固定资产月度折旧记录（v5.62）
 * (period, assetId) 业务唯一（Service 校验防重复计提）；voucher_id 关联生成的计提凭证（可空）
 */
@Entity @Table(name = "asset_depreciation")
public class AssetDepreciation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 期间 YYYY-MM */
    @Column(nullable = false, length = 10)
    public String period;

    @Column(nullable = false)
    public Long assetId;

    @Column(nullable = false, length = 100)
    public String assetName;

    @Column(nullable = false, length = 20)
    public String expenseSubject;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal amount;

    public Long voucherId;

    public LocalDateTime createTime = LocalDateTime.now();
}
