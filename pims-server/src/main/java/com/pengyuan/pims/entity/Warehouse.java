package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "warehouse")
public class Warehouse {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 20)
    public String code;

    @Column(nullable = false, length = 50)
    public String name;

    @Column(nullable = false, length = 20)
    public String warehouseType;

    @Column(length = 20) public String processorId;
    @Column(length = 100) public String processorName; // 中文名称冗余
    @Column(length = 200) public String address;
    @Column(length = 50) public String contactPerson;
    @Column(length = 20) public String contactPhone;
    @Column(length = 500) public String remark;
    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
