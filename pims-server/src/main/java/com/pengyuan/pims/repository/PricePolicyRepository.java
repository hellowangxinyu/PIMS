package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.PricePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PricePolicyRepository extends JpaRepository<PricePolicy, Long> {

    /** 取某物料全部启用档（阶梯匹配在内存做，档位数很少） */
    List<PricePolicy> findByMaterialCodeAndStatusOrderByMinQtyAsc(String materialCode, String status);

    /** 取某大类兜底档 */
    List<PricePolicy> findByMaterialCodeIsNullAndMaterialCategoryAndStatusOrderByMinQtyAsc(String materialCategory, String status);

    /** 管理列表全量（价格档位数量级小） */
    List<PricePolicy> findAllByOrderByCreateTimeDescIdDesc();
}
