package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.RecipeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {
    List<RecipeVersion> findByRecipeIdOrderByCreateTimeDesc(Long recipeId);
    Optional<RecipeVersion> findByRecipeIdAndStatus(Long recipeId, String status);

    /** v5.6：按配方取最新 RELEASED 版本（半成品溯源用） */
    Optional<RecipeVersion> findFirstByRecipeIdAndStatusOrderByIdDesc(Long recipeId, String status);
    List<RecipeVersion> findByStatus(String status);
    void deleteByRecipeId(Long recipeId);
}
