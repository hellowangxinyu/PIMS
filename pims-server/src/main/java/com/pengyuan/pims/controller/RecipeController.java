package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Recipe;
import com.pengyuan.pims.entity.RecipeVersion;
import com.pengyuan.pims.service.RecipeService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 配方管理接口
 */
@RestController
@RequestMapping("/api/recipe")
public class RecipeController {

    private final RecipeService service;
    private final UserService userService;
    private final com.pengyuan.pims.service.ExcelImportService excelImportService;

    public RecipeController(RecipeService service, UserService userService,
                            com.pengyuan.pims.service.ExcelImportService excelImportService) {
        this.service = service;
        this.userService = userService;
        this.excelImportService = excelImportService;
    }

    /** v5.56 配方 Excel 导入模板 */
    @GetMapping("/import/template")
    @SaCheckPermission("recipe:write")
    public void importTemplate(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        excelImportService.downloadRecipeTemplate(response);
    }

    /** v5.56 配方 Excel 导入（全量校验，任一错整体拒绝；通过则单事务落库） */
    @PostMapping("/import")
    @SaCheckPermission("recipe:write")
    public Result<?> importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            int count = excelImportService.importRecipes(file, userService.currentOperatorName());
            return Result.ok(Map.of("count", count));
        } catch (com.pengyuan.pims.common.ExcelImportException e) {
            return new Result<>(400, e.getMessage(), e.getErrors());
        }
    }

    // ==================== 配方 CRUD ====================

    @GetMapping
    @SaCheckPermission("recipe:read")
    public Result list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category) {
        return Result.ok(service.list(keyword, category));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("recipe:read")
    public Result detail(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @SaCheckPermission("recipe:write")
    public Result create(@RequestBody Map<String, Object> body) {
        Recipe recipe = new Recipe();
        recipe.productCode = (String) body.get("productCode");
        recipe.productName = (String) body.get("productName");
        recipe.recipeType = body.get("recipeType") != null ? (String) body.get("recipeType") : "TINTING";
        recipe.category = (String) body.get("category");
        recipe.description = (String) body.get("description");
        recipe.processTemplateId = body.get("processTemplateId") != null ? Long.valueOf(body.get("processTemplateId").toString()) : null;
        recipe.qcTemplateId = body.get("qcTemplateId") != null ? Long.valueOf(body.get("qcTemplateId").toString()) : null;               // v5.81
        recipe.packagingStandardId = body.get("packagingStandardId") != null ? Long.valueOf(body.get("packagingStandardId").toString()) : null; // v5.81
        return Result.ok(service.create(recipe));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("recipe:write")
    public Result update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Recipe recipe = new Recipe();
        recipe.productCode = (String) body.get("productCode");
        recipe.productName = (String) body.get("productName");
        recipe.recipeType = body.get("recipeType") != null ? (String) body.get("recipeType") : "TINTING";
        recipe.category = (String) body.get("category");
        recipe.description = (String) body.get("description");
        recipe.enabled = body.get("enabled") != null ? Boolean.valueOf(body.get("enabled").toString()) : true;
        recipe.processTemplateId = body.get("processTemplateId") != null ? Long.valueOf(body.get("processTemplateId").toString()) : null;
        recipe.qcTemplateId = body.get("qcTemplateId") != null ? Long.valueOf(body.get("qcTemplateId").toString()) : null;               // v5.81
        recipe.packagingStandardId = body.get("packagingStandardId") != null ? Long.valueOf(body.get("packagingStandardId").toString()) : null; // v5.81
        return Result.ok(service.update(id, recipe));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("recipe:write")
    public Result delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 启用/禁用配方（禁用后任何单据不可引用） */
    @PutMapping("/{id}/enabled")
    @SaCheckPermission("recipe:write")
    public Result toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(service.toggleEnabled(id, enabled));
    }

    // ==================== 版本管理 ====================

    @GetMapping("/{id}/versions")
    @SaCheckPermission("recipe:read")
    public Result versions(@PathVariable Long id) {
        return Result.ok(service.listVersions(id));
    }

    /** v6.3 配方变更日志：版本创建/修改/发布/归档/树保存全留痕 */
    @GetMapping("/{id}/changes")
    @SaCheckPermission(value = "recipe:read")
    public Result<?> changes(@PathVariable Long id) {
        return Result.ok(service.listChanges(id));
    }

    @PostMapping("/{id}/version")
    @SaCheckPermission("recipe:write")
    public Result createVersion(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        RecipeVersion input = new RecipeVersion();
        if (body != null) {
            if (body.get("batchQty") != null) {
                input.batchQty = new BigDecimal(body.get("batchQty").toString());
            }
            input.unit = (String) body.get("unit");
            input.remark = (String) body.get("remark");
        }
        return Result.ok(service.createVersion(id, input));
    }

    @PutMapping("/version/{versionId}")
    @SaCheckPermission("recipe:write")
    public Result updateVersion(@PathVariable Long versionId, @RequestBody Map<String, Object> body) {
        RecipeVersion v = new RecipeVersion();
        if (body.get("batchQty") != null) {
            v.batchQty = new BigDecimal(body.get("batchQty").toString());
        }
        v.unit = (String) body.get("unit");
        v.remark = (String) body.get("remark");
        return Result.ok(service.updateVersion(versionId, v));
    }

    @PostMapping("/version/{versionId}/release")
    @SaCheckPermission("recipe:write")
    public Result release(@PathVariable Long versionId) {
        String operator = userService.currentOperatorName();
        return Result.ok(service.release(versionId, operator));
    }

    @DeleteMapping("/version/{versionId}")
    @SaCheckPermission("recipe:write")
    public Result deleteVersion(@PathVariable Long versionId) {
        service.deleteVersion(versionId);
        return Result.ok(null);
    }

    // ==================== 配方树 ====================

    @GetMapping("/version/{versionId}/tree")
    @SaCheckPermission("recipe:read")
    public Result getTree(@PathVariable Long versionId) {
        return Result.ok(service.getTree(versionId));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/version/{versionId}/tree")
    @SaCheckPermission("recipe:write")
    public Result saveTree(@PathVariable Long versionId, @RequestBody List<Map<String, Object>> treeData) {
        service.saveTree(versionId, treeData);
        return Result.ok(null);
    }

    @GetMapping("/version/{versionId}/expand")
    @SaCheckPermission("recipe:read")
    public Result expand(@PathVariable Long versionId,
                         @RequestParam(required = false) BigDecimal qty) {
        return Result.ok(service.expandTree(versionId, qty));
    }

    /** v5.6：订单配方明细（半成品保留为一行不展开，供生产/委外订单引用） */
    @GetMapping("/version/{versionId}/plan")
    @SaCheckPermission("recipe:read")
    public Result plan(@PathVariable Long versionId,
                       @RequestParam(required = false) BigDecimal qty) {
        return Result.ok(service.expandForOrder(versionId, qty));
    }

    // ==================== 已发布配方（供生产订单选择） ====================

    @GetMapping("/released")
    @SaCheckPermission("recipe:read")
    public Result released() {
        return Result.ok(service.listReleased());
    }

    // ==================== 成本计算 ====================

    /** 单个配方版本成本：批量成本 + 单位成本 */
    @GetMapping("/version/{versionId}/cost")
    @SaCheckPermission("recipe:read")
    public Result versionCost(@PathVariable Long versionId) {
        return Result.ok(service.calcVersionCost(versionId));
    }

    /** 所有已发布配方成本清单（成品/半成品配方成本直出） */
    @GetMapping("/costs")
    @SaCheckPermission("recipe:read")
    public Result releasedCosts() {
        return Result.ok(service.listReleasedCosts());
    }

    /** 物料价格映射（库存加权均价→采购价回退，与配方树成本同源），供配方页选物料时实时显示单价 */
    @GetMapping("/material-prices")
    @SaCheckPermission("recipe:read")
    public Result materialPrices() {
        return Result.ok(service.getMaterialPriceMap());
    }

    /** v5.35：油尾库可用物料列表（制漆配方加「油尾」节点用） */
    @GetMapping("/tailing-options")
    @SaCheckPermission("recipe:read")
    public Result tailingOptions() {
        return Result.ok(service.tailingOptions());
    }

    // ==================== 溯源 ====================

    /** 溯源：根据配方版本ID递归展开完整配方谱系 */
    @GetMapping("/version/{versionId}/trace")
    @SaCheckPermission("recipe:read")
    public Result trace(@PathVariable Long versionId,
                        @RequestParam(required = false) BigDecimal qty) {
        return Result.ok(service.traceRecipe(versionId, qty));
    }

    /** v5.6：按配方 ID 溯源（订单半成品行 ref_recipe_id 存的是配方 ID，取其最新 RELEASED 版本溯源） */
    @GetMapping("/{recipeId}/trace")
    @SaCheckPermission("recipe:read")
    public Result traceByRecipe(@PathVariable Long recipeId,
                                @RequestParam(required = false) BigDecimal qty) {
        return Result.ok(service.traceLatestReleased(recipeId, qty));
    }
}
