package com.pengyuan.pims.entity;

import jakarta.persistence.*;

/**
 * 角色-模块权限关联
 */
@Entity
@Table(name = "sys_role_permission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"roleCode", "permCode"}))
public class RolePermission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 30)
    public String roleCode;      // 关联 sys_role.code

    @Column(nullable = false, length = 30)
    public String permCode;      // 权限码，如 supplier:read
}
