package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 记账凭证（v5.61 总账体系）—— 统一「记」字
 * docNo: VCH-YYYY-NNNN（按凭证日期年份编序）
 * period: 记账期间 YYYY-MM（独立列，等值查询禁函数包列）
 * source: MANUAL 手工 / RECEIPT 收款单 / PAYMENT 付款单 / EXPENSE 费用单 / INVOICE 发票 / TRANSFER 结转损益
 * status: DRAFT 草稿（可改可删）/ POSTED 已记账（进一切账簿报表）
 * source+refDocNo 唯一性由 Service 查重保证（业务单据防重复生成）
 */
@Entity @Table(name = "voucher")
public class Voucher {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String docNo;

    @Column(nullable = false)
    public LocalDate voucherDate;

    /** 记账期间 YYYY-MM（voucher_date 派生落库） */
    @Column(nullable = false, length = 10)
    public String period;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    public BigDecimal totalCredit = BigDecimal.ZERO;

    /** 附单据张数 */
    public Integer attachmentCount;

    @Column(nullable = false, length = 20)
    public String source = "MANUAL";

    /** 来源业务单号（MANUAL 为空） */
    @Column(length = 30)
    public String refDocNo;

    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    /** 制单人 */
    @Column(length = 50)
    public String createdBy;

    /** 记账人 */
    @Column(length = 50)
    public String postedBy;

    public LocalDateTime postedTime;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;

    /** 分录（非持久化，Service 组装） */
    @Transient
    public List<VoucherEntry> entries;
}
