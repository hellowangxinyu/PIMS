package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "customer")
public class Customer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(length = 200) public String address;
    @Column(length = 50) public String contactPerson;
    @Column(length = 20) public String contactPhone;
    @Column(length = 10) public String abcLevel;
    /** 收款条件（账期缓冲）：PREPAID/CREDIT_30/CREDIT_60/MONTHLY/TWO_MONTH/THREE_MONTH 或 账期N天 */
    @Column(length = 500) public String paymentTerms;
    /** 收款方式：TRANSFER电汇 / ACCEPTANCE承兑 */
    @Column(length = 20) public String paymentMethod;
    @Column(length = 500) public String remark;
    /** v5.52 信用额度（空/0=不限额）：下单时校验 当前应收欠款+本单金额，超出弹预警 */
    @Column(precision = 14, scale = 2) public BigDecimal creditLimit;
    public Boolean enabled = true;

    /** v5.27：拉黑标记（拉黑即禁用 enabled=false；解除拉黑恢复 enabled=true） */
    public Boolean blacklisted = false;

    // ===== v5.27 合同需方（甲方）信息，打印销售合同时直接取用 =====
    @Column(length = 50) public String legalPerson;   // 法定代表人
    @Column(length = 100) public String bankName;     // 开户银行
    @Column(length = 50) public String bankAccount;   // 银行账号
    @Column(length = 30) public String taxNo;         // 纳税人识别号

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
