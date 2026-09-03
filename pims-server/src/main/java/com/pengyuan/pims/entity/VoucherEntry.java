package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 凭证分录（v5.61 总账体系）
 * subject_code/subject_name 为录入时快照——科目改名不影响历史凭证展示，
 * 但余额计算以 subject_code 为准（编码不可改）。
 * digest 为摘要；借贷金额可空（空视为 0），一行只能有借或贷一侧。
 * aux_type/aux_name 辅助核算（CUSTOMER/SUPPLIER，收款冲应收、付款冲应付时带出），仅供明细账筛选。
 */
@Entity @Table(name = "voucher_entry")
public class VoucherEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long voucherId;

    public Integer lineNo;

    @Column(nullable = false, length = 20)
    public String subjectCode;

    @Column(nullable = false, length = 100)
    public String subjectName;

    /** 摘要 */
    @Column(length = 200)
    public String digest;

    @Column(precision = 14, scale = 2)
    public BigDecimal debit = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    public BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 20)
    public String auxType;

    @Column(length = 100)
    public String auxName;
}
