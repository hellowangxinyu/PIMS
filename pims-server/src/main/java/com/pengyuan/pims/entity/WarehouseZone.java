package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 分库（仓库下的子库，如树脂库、溶剂库、助剂库等）
 */
@Entity
@Table(name = "warehouse_zone")
public class WarehouseZone {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public Long warehouseId;

    @Column(nullable = false, length = 20) public String code;

    @Column(nullable = false, length = 50) public String name;

    /** v5.38 分库类型：null=普通分库 / UNQUALIFIED=隔离区（原不合格品库） / TAILING=油尾区（原油尾库） */
    @Column(length = 20) public String zoneType;

    @Column(length = 500) public String remark;

    public Integer sortOrder = 0;

    public Boolean enabled = true;

    public LocalDateTime createTime = LocalDateTime.now();

    public LocalDateTime updateTime;
}
