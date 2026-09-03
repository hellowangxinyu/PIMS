package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByNameContaining(String keyword);
    /** 查重：名称完全一致 */
    boolean existsByName(String name);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(code, -4) AS INTEGER)) FROM customer WHERE code LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    // ==================== v5.27 拉黑：下拉选择仅启用 ====================
    List<Customer> findByEnabledTrue();
    List<Customer> findByNameContainingAndEnabledTrue(String keyword);

    /** v5.27：管理列表——正常 + 拉黑可见，已删除（enabled=false 且未拉黑）不可见 */
    @Query("SELECT c FROM Customer c WHERE c.enabled = true OR c.blacklisted = true ORDER BY c.createTime DESC")
    List<Customer> findAllActiveOrBlacklisted();

    /** v5.27：管理列表搜索（同样排除已删除） */
    @Query("SELECT c FROM Customer c WHERE (c.enabled = true OR c.blacklisted = true) AND c.name LIKE %:kw%")
    List<Customer> findByNameContainingActiveOrBlacklisted(@org.springframework.data.repository.query.Param("kw") String kw);

    /** v5.43.2 工作台统计口径：只数启用中的（排除禁用测试残留） */
    long countByEnabledTrue();
}
