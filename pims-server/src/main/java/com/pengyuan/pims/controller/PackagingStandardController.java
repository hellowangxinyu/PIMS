package com.pengyuan.pims.controller;

import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.PackagingStandard;
import com.pengyuan.pims.entity.PackagingStandardItem;
import com.pengyuan.pims.repository.PackagingStandardRepository;
import com.pengyuan.pims.repository.PackagingStandardItemRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * v5.81 包装标准档案；v5.82 组合包装（一套 = 桶+袋+托盘多明细，套单价=Σ qty×单价，成本=⌈批量÷套容量⌉×套单价）
 */
@RestController
@RequestMapping("/api/packaging-standard")
public class PackagingStandardController {

    private final PackagingStandardRepository repo;
    private final PackagingStandardItemRepository itemRepo;

    public PackagingStandardController(PackagingStandardRepository repo, PackagingStandardItemRepository itemRepo,
                                      com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.writeQueue = writeQueue;
    }

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.9（A2）

    @GetMapping
    @cn.dev33.satoken.annotation.SaCheckPermission(value = "recipe:read")   // v6.1.4 补权限
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Boolean enabled) {
        List<PackagingStandard> all = repo.findByOrderByIdAsc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (PackagingStandard p : all) {
            if (Boolean.TRUE.equals(enabled) && Boolean.FALSE.equals(p.enabled)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id);
            m.put("name", p.name);
            m.put("packType", p.packType);
            m.put("spec", p.spec);
            m.put("capacityKg", p.capacityKg);
            m.put("unitPrice", p.unitPrice);
            m.put("remark", p.remark);
            m.put("enabled", p.enabled);
            List<PackagingStandardItem> items = itemRepo.findByPackagingIdOrderBySortOrderAscIdAsc(p.id);
            m.put("items", items);
            // 套单价：有明细按组合 Σ，无明细回退单件价
            BigDecimal setPrice = items.stream()
                    .map(i -> i.unitPrice.multiply(i.qty))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("setPrice", items.isEmpty() ? p.unitPrice : setPrice.setScale(2, RoundingMode.HALF_UP));
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping
    @cn.dev33.satoken.annotation.SaCheckPermission(value = "recipe:write")   // v6.1.4 补权限
    // v8.9（A2）：去 @Transactional，锁内包事务
    public Result<PackagingStandard> create(@RequestBody Map<String, Object> body) {
        return writeQueue.executeTx(() -> {
            PackagingStandard p = parse(body);
            p.createTime = java.time.LocalDateTime.now();
            PackagingStandard saved = repo.save(p);
            saveItems(saved.id, body.get("items"));
            return Result.ok(saved);
        });
    }

    @PutMapping("/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = "recipe:write")   // v6.1.4 补权限
    public Result<PackagingStandard> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return writeQueue.executeTx(() -> {
            PackagingStandard exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("包装标准不存在"));
            PackagingStandard in = parse(body);
            exist.name = in.name;
            exist.packType = in.packType;
            exist.spec = in.spec;
            exist.capacityKg = in.capacityKg;
            exist.unitPrice = in.unitPrice;
            exist.remark = in.remark;
            exist.enabled = in.enabled;
            exist.updateTime = java.time.LocalDateTime.now();
            repo.save(exist);
            saveItems(id, body.get("items"));
            return Result.ok(exist);
        });
    }

    @DeleteMapping("/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = "recipe:write")   // v6.1.4 补权限
    public Result<?> delete(@PathVariable Long id) {
        writeQueue.executeTx(() -> {
            itemRepo.deleteByPackagingId(id);
            repo.deleteById(id);
            return null;
        });
        return Result.ok();
    }

    @SuppressWarnings("unchecked")
    private void saveItems(Long packagingId, Object raw) {
        itemRepo.deleteByPackagingId(packagingId);
        if (!(raw instanceof List<?> list) || list.isEmpty()) return;
        int sort = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> im)) continue;
            PackagingStandardItem it = new PackagingStandardItem();
            it.packagingId = packagingId;
            it.name = str(im.get("name"));
            it.packType = str(im.get("packType"));
            it.spec = str(im.get("spec"));
            it.qty = dec(im.get("qty"), BigDecimal.ONE);
            it.unitPrice = dec(im.get("unitPrice"), BigDecimal.ZERO);
            it.sortOrder = sort++;
            if (it.name != null && !it.name.isBlank()) itemRepo.save(it);
        }
    }

    @SuppressWarnings("unchecked")
    private PackagingStandard parse(Map<String, Object> body) {
        PackagingStandard p = new PackagingStandard();
        p.name = str(body.get("name"));
        p.packType = str(body.get("packType"));
        p.spec = str(body.get("spec"));
        Object cap = body.get("capacityKg");
        p.capacityKg = cap != null && !cap.toString().isBlank() ? new BigDecimal(cap.toString()) : null;
        p.unitPrice = dec(body.get("unitPrice"), BigDecimal.ZERO);
        p.remark = str(body.get("remark"));
        p.enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());
        if (p.name == null || p.name.isBlank()) throw new IllegalArgumentException("包装标准名称不能为空");
        return p;
    }

    private String str(Object o) { return o == null ? null : o.toString().isBlank() ? null : o.toString(); }
    private BigDecimal dec(Object o, BigDecimal dft) {
        try { return o == null || o.toString().isBlank() ? dft : new BigDecimal(o.toString()); }
        catch (Exception e) { return dft; }
    }
}
