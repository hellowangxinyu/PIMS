package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 50)
    public String username;

    @Column(nullable = false, length = 100)
    // v6.1 安全：密码哈希不回传前端；v6.1.2 修复：JsonIgnore 连反序列化也挡（创建用户时 password 收不到，
    // 报 rawPassword cannot be null）——改 WRITE_ONLY：请求可传入、响应不回传
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    public String password;
    /** v6.1 安全：首次登录/重置后强制改密（true=登录后前端弹强制改密框，改完置 false） */
    public Boolean mustChangePwd;

    @Column(nullable = false, length = 50)
    public String realName;

    @Column(length = 20) public String phone;

    @Column(nullable = false, length = 30)
    public String role;

    public Boolean enabled = true;
    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
