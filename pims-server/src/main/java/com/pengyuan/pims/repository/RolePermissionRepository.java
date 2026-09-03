package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /** 查某角色的所有权限码 */
    List<RolePermission> findByRoleCode(String roleCode);

    /** 查拥有某权限的所有角色 */
    List<RolePermission> findByPermCode(String permCode);

    /** 删除某角色的所有权限（重新配置时用） */
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.roleCode = ?1")
    void deleteByRoleCode(String roleCode);
}
