package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProcessTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplate, Long> {
    Optional<ProcessTemplate> findByRecipeType(String recipeType);
    List<ProcessTemplate> findByRecipeTypeOrderByUpdateTimeDesc(String recipeType);
    List<ProcessTemplate> findAllByOrderByUpdateTimeDesc();
    Optional<ProcessTemplate> findFirstByRecipeTypeAndIsDefaultTrue(String recipeType);
    boolean existsByRecipeType(String recipeType);
}
