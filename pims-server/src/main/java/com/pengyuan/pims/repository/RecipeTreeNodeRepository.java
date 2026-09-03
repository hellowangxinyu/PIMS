package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.RecipeTreeNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeTreeNodeRepository extends JpaRepository<RecipeTreeNode, Long> {
    List<RecipeTreeNode> findByVersionIdOrderBySortOrder(Long versionId);
    void deleteByVersionId(Long versionId);
}
