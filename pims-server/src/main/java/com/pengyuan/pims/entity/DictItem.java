package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "dict_item")
public class DictItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 50)
    public String type;

    @Column(nullable = false, length = 100)
    public String value;

    @Column(length = 200) public String label;
    public Integer sortOrder = 0;
    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
}
