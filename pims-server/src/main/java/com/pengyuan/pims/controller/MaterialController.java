package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.service.MaterialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService service;
    private final com.pengyuan.pims.repository.DictItemRepository dictRepo;
    private final com.pengyuan.pims.service.ExcelImportService excelImportService;
    private final com.pengyuan.pims.service.UserService userService;
    public MaterialController(MaterialService service, com.pengyuan.pims.repository.DictItemRepository dictRepo,
                              com.pengyuan.pims.service.ExcelImportService excelImportService,
                              com.pengyuan.pims.service.UserService userService) {
        this.service = service;
        this.dictRepo = dictRepo;
        this.excelImportService = excelImportService;
        this.userService = userService;
    }

    /** v5.56 物料 Excel 导入模板 */
    @GetMapping("/import/template")
    @SaCheckPermission(value = "material:write")
    public void importTemplate(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        excelImportService.downloadMaterialTemplate(response);
    }

    /** v5.56 物料 Excel 导入（全量校验，任一错整体拒绝；通过则单事务落库） */
    @PostMapping("/import")
    @SaCheckPermission(value = "material:write")
    public Result<?> importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            int count = excelImportService.importMaterials(file, userService.currentOperatorName());
            return Result.ok(Map.of("count", count));
        } catch (com.pengyuan.pims.common.ExcelImportException e) {
            return new Result<>(400, e.getMessage(), e.getErrors());
        }
    }

    @GetMapping
    @SaCheckPermission(value = "material:read")
    public Result<List<Material>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String enabled) {
        // v5.14：状态过滤（启用中/已禁用）；默认全部，前端默认传 enabled=true 只显示启用中
        if (enabled != null && !enabled.isBlank()) {
            boolean onlyEnabled = Boolean.parseBoolean(enabled);
            java.util.List<Material> all = (category != null && !category.isBlank())
                    ? service.listByCategory(category)
                    : (keyword != null && !keyword.isBlank() ? service.search(keyword) : service.listAll());
            return Result.ok(all.stream().filter(m -> onlyEnabled
                    ? Boolean.TRUE.equals(m.enabled) : !Boolean.TRUE.equals(m.enabled)).toList());
        }
        // 优先按大类过滤，其次按关键字搜索
        if (category != null && !category.isBlank()) return Result.ok(service.listByCategory(category));
        if (keyword != null && !keyword.isBlank()) return Result.ok(service.search(keyword));
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "material:read")
    public Result<?> get(@PathVariable Long id) {
        var opt = service.getById(id);
        if (opt.isPresent()) return Result.ok(opt.get());
        return Result.fail("物料不存在");
    }

    @PostMapping
    @SaCheckPermission(value = "material:write")
    public Result<Material> create(@RequestBody Material m) { return Result.ok(service.create(m)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "material:write")
    public Result<Material> update(@PathVariable Long id, @RequestBody Material m) {
        return Result.ok(service.update(id, m));
    }

    /** v5.43.1 禁用/启用（被引用删不掉的物料走禁用：不可再用、不可采购） */
    @PutMapping("/{id}/enabled")
    @SaCheckPermission(value = "material:write")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        service.setEnabled(id, value);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "material:delete")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }

    // ==================== 导出（v5.23） ====================

    /** 物料导出：与列表页相同筛选（enabled/category/keyword），大类/小类/主材/色系映射中文 */
    @GetMapping("/export")
    @SaCheckPermission(value = "material:read")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String enabled,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<Material> all;
        if (enabled != null && !enabled.isBlank()) {
            boolean onlyEnabled = Boolean.parseBoolean(enabled);
            all = (category != null && !category.isBlank())
                    ? service.listByCategory(category)
                    : (keyword != null && !keyword.isBlank() ? service.search(keyword) : service.listAll());
            all = all.stream().filter(m -> onlyEnabled
                    ? Boolean.TRUE.equals(m.enabled) : !Boolean.TRUE.equals(m.enabled)).toList();
        } else if (category != null && !category.isBlank()) {
            all = service.listByCategory(category);
        } else if (keyword != null && !keyword.isBlank()) {
            all = service.search(keyword);
        } else {
            all = service.listAll();
        }
        // 字典 value → label 映射（大类/小类/主材/色系中文显示，与页面一致）
        Map<String, String> dictLabel = new java.util.HashMap<>();
        for (com.pengyuan.pims.entity.DictItem d : dictRepo.findByEnabledTrueOrderByTypeAscSortOrderAsc()) {
            dictLabel.put(d.type + "|" + d.value, d.label);
        }
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Material m : all) {
            rows.add(new Object[]{
                    m.code, m.name, m.brand,
                    dictLabel.getOrDefault("material_category|" + m.category, m.category),
                    dictLabel.getOrDefault("material_sub_category|" + m.subCategory, m.subCategory),
                    dictLabel.getOrDefault("material_main_material|" + m.mainMaterial, m.mainMaterial),
                    dictLabel.getOrDefault("material_color_series|" + m.colorSeries, m.colorSeries),
                    m.shelfLifeDays == null || m.shelfLifeDays == 0 ? "不限" : m.shelfLifeDays + "天",
                    m.brandOwner, m.alternativeCodes,
                    Boolean.TRUE.equals(m.enabled) ? "启用" : "已禁用"
            });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "物料档案-" + java.time.LocalDate.now(), "物料档案",
                new String[]{"编码", "品名", "牌号", "大类", "小类", "主材", "色系", "质保期", "品牌归属", "平替物料", "状态"},
                rows);
    }
}
