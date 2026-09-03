package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.AssetDepreciation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetDepreciationRepository extends JpaRepository<AssetDepreciation, Long> {

    List<AssetDepreciation> findByPeriodOrderByIdAsc(String period);

    /** v6.1.5（高B）：按凭证精确删草稿口径折旧记录——删 period#N 补提草稿时若按整期清，
     *  会把已 POSTED 基准凭证的记录也清掉，全量重算后与账上重复计提 */
    void deleteByVoucherId(Long voucherId);

    List<AssetDepreciation> findByAssetIdOrderByPeriodAsc(Long assetId);

    List<AssetDepreciation> findByAssetIdInOrderByAssetIdAsc(List<Long> assetIds);

    boolean existsByPeriodAndAssetId(String period, Long assetId);
}
