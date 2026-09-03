package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 库位（分库下的具体存储位置）
 */
@Entity
@Table(name = "warehouse_location")
public class WarehouseLocation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long zoneId;

    @Column(nullable = false, length = 20) public String code;

    @Column(nullable = false, length = 50) public String name;

    @Column(length = 500) public String remark;

    public Integer sortOrder = 0;

    public Boolean enabled = true;

    public LocalDateTime createTime = LocalDateTime.now();

    public LocalDateTime updateTime;
}
