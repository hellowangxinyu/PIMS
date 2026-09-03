package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务汇总单行表
 * 全局只维护一行(id=1)，由 SQLite 触发器在应收/应付变动时自动更新
 * Dashboard 读取此表即可秒级获得应收应付汇总
 */
@Entity
@Table(name = "stat_finance_summary")
public class StatFinanceSummary {

    @Id
    public Long id = 1L;

    /** 应收总额 */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal arTotal = BigDecimal.ZERO;

    /** 已回款总额 */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal arReceived = BigDecimal.ZERO;

    /** 应付总额 */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal apTotal = BigDecimal.ZERO;

    /** 已付款总额 */
    @Column(nullable = false, precision = 16, scale = 2)
    public BigDecimal apPaid = BigDecimal.ZERO;

    public LocalDateTime updateTime;
}
