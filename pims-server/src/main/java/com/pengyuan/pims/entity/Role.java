package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 系统角色 —— 可自定义配置
 */
@Entity
@Table(name = "sys_role")
public class Role {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String code;          // 角色编码，如 GM, BUYER, CUSTOM_ROLE

    @Column(nullable = false, length = 50)
    public String name;          // 角色名称，如 总经理、采购员

    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
