package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByOrderByIdDesc();

    List<Asset> findAllByOrderByIdAsc();

    List<Asset> findByStatusOrderByIdAsc(String status);

    /** 取指定前缀最大单号序号（FA-YYYY-NNNN） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM asset WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);
}
