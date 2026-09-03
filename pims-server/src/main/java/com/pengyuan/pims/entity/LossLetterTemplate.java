package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * v5.59 损失沟通函模板（多模板，界面可维护）。四段正文可编辑、支持占位符（{{batchNo}}/{{lossAmount}} 等），
 * 函件骨架（公司抬头/致供应商/批次采购信息表/损失金额强调/落款盖章栏）由前端 lossLetterPrint.js 固定渲染，
 * 用户只改措辞不会改坏版式。
 */
@Entity @Table(name = "loss_letter_template")
public class LossLetterTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 50)
    public String name;

    /** 开头语 */
    @Column(length = 1000) public String openingText;
    /** 问题与损失正文 */
    @Column(nullable = false, length = 2000) public String bodyText;
    /** 处理要求 */
    @Column(length = 1000) public String requireText;
    /** 结尾语 */
    @Column(length = 1000) public String closingText;

    @Column(nullable = false) public Boolean isDefault = false;
    @Column(nullable = false) public Boolean enabled = true;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
