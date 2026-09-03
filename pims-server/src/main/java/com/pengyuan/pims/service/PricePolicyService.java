package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.PricePolicy;
import com.pengyuan.pims.repository.MaterialRepository;
import com.pengyuan.pims.repository.PricePolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售价格政策（v6.3 第二批）：按物料（精确）+ 物料大类（兜底）维护阶梯价。
 * 取价规则：数量已达到的最高阶梯；同档优先物料精确 > 大类兜底；须在生效期内且启用。
 */
@Service
public class PricePolicyService {

    private static final Logger log = LoggerFactory.getLogger(PricePolicyService.class);

    private final PricePolicyRepository repo;
    private final MaterialRepository materialRepo;

    public PricePolicyService(PricePolicyRepository repo, MaterialRepository materialRepo) {
        this.repo = repo;
        this.materialRepo = materialRepo;
    }

    public List<PricePolicy> list() {
        return repo.findAllByOrderByCreateTimeDescIdDesc();
    }

    @Transactional
    public PricePolicy create(PricePolicy p) {
        validate(p);
        if (p.createdBy == null || p.createdBy.isBlank()) p.createdBy = "系统";
        PricePolicy saved = repo.save(p);
        log.info("价格政策新增: 物料={} 大类={} 阶梯≥{} 单价={}",
                p.materialCode, p.materialCategory, p.minQty, p.unitPrice);
        return saved;
    }

    @Transactional
    public PricePolicy update(Long id, PricePolicy in) {
        PricePolicy p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("价格政策不存在"));
        validate(in);
        p.materialCode = blankToNull(in.materialCode);
        p.materialCategory = blankToNull(in.materialCategory);
        p.minQty = in.minQty;
        p.unitPrice = in.unitPrice;
        p.effectiveDate = in.effectiveDate;
        p.expiryDate = in.expiryDate;
        p.status = "DISABLED".equals(in.status) ? "DISABLED" : "ENABLED";
        p.remark = in.remark;
        return repo.save(p);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    private void validate(PricePolicy p) {
        if (p.unitPrice == null || p.unitPrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("单价必须大于 0");
        if (p.minQty == null || p.minQty.compareTo(BigDecimal.ONE) < 0)
            throw new IllegalArgumentException("阶梯数量至少为 1");
        if (p.effectiveDate == null) p.effectiveDate = LocalDate.now();
        if (p.expiryDate != null && p.expiryDate.isBefore(p.effectiveDate))
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        String code = blankToNull(p.materialCode);
        String cat = blankToNull(p.materialCategory);
        if (code == null && cat == null)
            throw new IllegalArgumentException("物料编码与物料大类至少填一项（编码为空则按大类兜底）");
        if (code != null) {
            // 冗余回填大类（下单侧物料校验同款）
            materialRepo.findByCode(code).ifPresent(m -> p.materialCategory = m.category);
        }
        p.materialCode = code;
        p.materialCategory = cat != null ? cat : p.materialCategory;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * 取价（下单带出用）：物料精确档优先，数量达到的最高阶梯；无精确档回落大类兜底档。
     * @return {price, policyId, tier(档位描述)} 或 {price:null}（无适用政策——保持手填/最近成交价）
     */
    public Map<String, Object> match(String materialCode, BigDecimal qty) {
        Map<String, Object> r = new LinkedHashMap<>();
        BigDecimal q = (qty == null || qty.compareTo(BigDecimal.ONE) < 0) ? BigDecimal.ONE : qty;
        LocalDate today = LocalDate.now();

        PricePolicy hit = pickTier(repo.findByMaterialCodeAndStatusOrderByMinQtyAsc(materialCode, "ENABLED"), q, today);
        String tierKind = "物料档";
        if (hit == null) {
            String cat = materialRepo.findByCode(materialCode).map(m -> m.category).orElse(null);
            if (cat != null) {
                hit = pickTier(repo.findByMaterialCodeIsNullAndMaterialCategoryAndStatusOrderByMinQtyAsc(cat, "ENABLED"), q, today);
                tierKind = "大类档";
            }
        }
        if (hit != null) {
            r.put("price", hit.unitPrice);
            r.put("policyId", hit.id);
            r.put("tier", tierKind + " ≥" + hit.minQty.stripTrailingZeros().toPlainString());
        } else {
            r.put("price", null);
        }
        return r;
    }

    /** 在启用且生效期内的档位里，取数量已达到的最高阶梯（列表已按 minQty 升序，从后往前找第一个达标） */
    private PricePolicy pickTier(List<PricePolicy> tiers, BigDecimal qty, LocalDate today) {
        for (int i = tiers.size() - 1; i >= 0; i--) {
            PricePolicy p = tiers.get(i);
            boolean inEffect = !today.isBefore(p.effectiveDate) && (p.expiryDate == null || !today.isAfter(p.expiryDate));
            if (inEffect && qty.compareTo(p.minQty) >= 0) return p;
        }
        return null;
    }
}
