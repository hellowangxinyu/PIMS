package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "coding_rule")
public class CodingRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 50)
    public String category;

    @Column(nullable = false, length = 10)
    public String categoryCode;

    @Column(nullable = false, length = 50)
    public String subCategory;

    @Column(nullable = false, length = 10)
    public String subCategoryCode;

    @Column(nullable = false)
    public Integer numberStart;

    @Column(nullable = false)
    public Integer currentSeq = 0;

    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
