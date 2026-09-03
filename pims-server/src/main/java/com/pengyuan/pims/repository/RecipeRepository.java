package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByOrderByCreateTimeDesc();

    /** v5.27：按产品编码查配方（销售订单转生产/转委外时自动匹配配方用） */
    Optional<Recipe> findFirstByProductCode(String productCode);

    /** 按品名查配方（名称唯一性校验用；返回 List 以兼容历史同名数据，避免 Optional 多结果报错） */
    List<Recipe> findByProductName(String productName);

    @Query("SELECT r FROM Recipe r WHERE (:keyword IS NULL OR r.productName LIKE %:keyword% OR r.recipeNo LIKE %:keyword%) " +
           "AND (:category IS NULL OR r.category = :category) ORDER BY r.createTime DESC")
    List<Recipe> search(String keyword, String category);

    @Query(value = "SELECT MAX(CAST(SUBSTR(recipe_no, 5) AS INTEGER)) FROM recipe", nativeQuery = true)
    Integer maxRecipeNoSeq();

    /**
     * v5.26：配方被引用次数（实时统计，不落库）
     * 引用来源：生产/委外订单头部引用配方版本 + 订单明细引用子配方
     * 返回列：[recipeId, usageCount]
     */
    @Query(value = """
            SELECT recipe_id, SUM(cnt) AS total FROM (
              SELECT rv.recipe_id AS recipe_id, COUNT(*) AS cnt
              FROM production_order po JOIN recipe_version rv ON po.recipe_version_id = rv.id
              GROUP BY rv.recipe_id
              UNION ALL
              SELECT rv.recipe_id, COUNT(*)
              FROM outsource_order oo JOIN recipe_version rv ON oo.recipe_version_id = rv.id
              GROUP BY rv.recipe_id
              UNION ALL
              SELECT ref_recipe_id, COUNT(*) FROM production_order_item WHERE ref_recipe_id IS NOT NULL GROUP BY ref_recipe_id
              UNION ALL
              SELECT ref_recipe_id, COUNT(*) FROM outsource_order_item WHERE ref_recipe_id IS NOT NULL GROUP BY ref_recipe_id
            ) GROUP BY recipe_id
            """, nativeQuery = true)
    List<Object[]> usageCounts();

    /** 引用某工艺路线的配方数（删除路线前拦截校验用） */
    long countByProcessTemplateId(Long processTemplateId);

    /** v5.81 按产品取最新启用配方（质检单反查绑定模板） */
    java.util.Optional<Recipe> findTopByProductCodeAndEnabledTrueOrderByUpdateTimeDescIdDesc(String productCode);
}
