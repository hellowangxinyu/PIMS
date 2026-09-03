package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    Optional<Material> findByCode(String code);
    List<Material> findByCodeContainingOrNameContaining(String code, String name);
    List<Material> findByCategory(String category);
    @Query("SELECT COUNT(DISTINCT m.category) FROM Material m WHERE m.category IS NOT NULL AND m.category <> ''")
    long countDistinctCategory();
    /** 查重：品名+牌号+大类+小类 完全一致 */
    boolean existsByNameAndBrandAndCategoryAndSubCategory(String name, String brand, String category, String subCategory);

    /** v5.15：编码全局查重（不允许重复编码） */
    boolean existsByCode(String code);

    /** v5.43.2 工作台统计口径：只数启用中的（排除禁用测试残留） */
    long countByEnabledTrue();

    /** 启用中的物料（批量路径一次建编码映射用） */
    List<Material> findByEnabledTrue();
}
