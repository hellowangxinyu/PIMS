package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** v7.7 打样配方明细行：色浆（B）/原料（A/P/F/R/S），用量为实际打样用量（自由录入，不强制 100） */
@Entity @Table(name = "sample_formula_item")
public class SampleFormulaItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long formulaId;
    @Column(nullable = false, length = 30) public String materialCode;
    @Column(length = 100) public String materialName;
    @Column(length = 10) public String category;
    @Column(length = 10) public String subCategory;
    @Column(length = 10) public String unit = "kg";
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    public Integer sortOrder = 0;
}
