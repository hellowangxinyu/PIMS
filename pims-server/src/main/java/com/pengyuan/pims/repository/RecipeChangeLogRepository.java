package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.RecipeChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeChangeLogRepository extends JpaRepository<RecipeChangeLog, Long> {

    List<RecipeChangeLog> findByRecipeIdOrderByCreateTimeDescIdDesc(Long recipeId);
}
