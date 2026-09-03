package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 科目映射（v5.61 总账体系）—— 业务转凭证与现金流量的默认科目配置
 * map_key 前缀区分两类：
 *   biz:expense:{费用类型}          费用单类型 → 费用科目（如 biz:expense:FREIGHT → 6601.01）
 *   biz:method:{RECEIPT|PAYMENT}:{方式}  收付款方式 → 资金科目（如 biz:method:RECEIPT:CASH → 1001）
 *   cf:{对方科目编码}               现金流量表归集（如 cf:1122 → CF01 销售收现）
 * 页面可改，未配置时转凭证弹窗中留空由人工选择。
 */
@Entity @Table(name = "account_mapping")
public class AccountMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 60)
    public String mapKey;

    @Column(nullable = false, length = 20)
    public String subjectCode;

    @Column(length = 200)
    public String remark;

    public LocalDateTime updateTime;
}
