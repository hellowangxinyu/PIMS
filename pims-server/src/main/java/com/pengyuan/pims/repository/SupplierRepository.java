package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * 供应商数据访问层
 * 支持按名称搜索、按类型过滤
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    /** 按名称模糊搜索（仅启用状态） */
    List<Supplier> findByNameContaining(String keyword);
    /** 查询所有启用的供应商 */
    List<Supplier> findByEnabledTrue();
    /** 按类型查询启用的供应商（MATERIAL/FINISHED） */
    List<Supplier> findByTypeAndEnabledTrue(String type);
    /** 按类型+名称模糊搜索（仅启用状态） */
    List<Supplier> findByTypeAndNameContainingAndEnabledTrue(String type, String keyword);
    /** 查重：名称+类型完全一致 */
    boolean existsByNameAndType(String name, String type);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(code, -4) AS INTEGER)) FROM supplier WHERE code LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    // ==================== v5.27 拉黑：管理列表 ====================
    // 管理列表显示"正常 + 已拉黑"（排除纯删除的），拉黑的可解除；下拉选择仍只走 enabled=true 的

    /** 管理列表：全部非删除供应商（enabled 或 blacklisted） */
    @Query("SELECT s FROM Supplier s WHERE s.enabled = true OR s.blacklisted = true ORDER BY s.createTime DESC")
    List<Supplier> findAllActiveOrBlacklisted();

    /** 管理列表按类型：正常 + 已拉黑 */
    @Query("SELECT s FROM Supplier s WHERE s.type = ?1 AND (s.enabled = true OR s.blacklisted = true) ORDER BY s.createTime DESC")
    List<Supplier> findByTypeActiveOrBlacklisted(String type);

    /** 管理列表按名称搜索：正常 + 已拉黑 */
    @Query("SELECT s FROM Supplier s WHERE (s.enabled = true OR s.blacklisted = true) AND s.name LIKE %?1% ORDER BY s.createTime DESC")
    List<Supplier> searchActiveOrBlacklisted(String keyword);

    /** 管理列表按类型+名称搜索：正常 + 已拉黑 */
    @Query("SELECT s FROM Supplier s WHERE s.type = ?1 AND (s.enabled = true OR s.blacklisted = true) AND s.name LIKE %?2% ORDER BY s.createTime DESC")
    List<Supplier> searchByTypeActiveOrBlacklisted(String type, String keyword);

    /** 下拉选择：按名称搜索（仅启用） */
    List<Supplier> findByNameContainingAndEnabledTrue(String keyword);

    /** v5.43.2 工作台统计口径：只数启用中的（排除禁用测试残留） */
    long countByEnabledTrue();
}
