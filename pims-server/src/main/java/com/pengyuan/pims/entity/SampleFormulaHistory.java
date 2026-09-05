package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * v7.7 打样配方历史快照（纯存档无 UI）：saveFormula 覆盖更新前把旧明细 JSON 序列化追加。
 * 「客户说还是第一版那个白好看」时旧版可找回；round 对齐打样单 adjustCount 轮次。
 */
@Entity @Table(name = "sample_formula_history")
public class SampleFormulaHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long formulaId;

    /** 第几轮（=被覆盖时的轮次） */
    public Integer round;

    /** 旧明细 JSON：{items:[{code,name,category,subCategory,qty}...], totalQty, estCost} */
    @Column(columnDefinition = "TEXT") public String snapshot;

    public LocalDateTime createTime = LocalDateTime.now();
}
