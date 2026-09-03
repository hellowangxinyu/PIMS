package com.pengyuan.pims.service;

import com.pengyuan.pims.common.ExcelImportException;
import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.Recipe;
import com.pengyuan.pims.entity.RecipeTreeNode;
import com.pengyuan.pims.entity.RecipeVersion;
import com.pengyuan.pims.entity.Supplier;
import com.pengyuan.pims.repository.DictItemRepository;
import com.pengyuan.pims.repository.MaterialRepository;
import com.pengyuan.pims.repository.ProcessTemplateRepository;
import com.pengyuan.pims.repository.RecipeRepository;
import com.pengyuan.pims.repository.RecipeTreeNodeRepository;
import com.pengyuan.pims.repository.RecipeVersionRepository;
import com.pengyuan.pims.repository.SupplierRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主数据 Excel 导入（v5.56：供应商 / 物料 / 配方）
 * 与期初导入同款范式：模板下载（数据页 + 填写说明页）→ 解析全量校验（任一错整体拒绝并返回全部错误行）
 * → 单事务写入（全量校验前置数据用批量预取，写入走 WriteQueue，遵循编码规范）。
 */
@Service
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private static final Map<String, String> CATEGORY_CN = Map.of(
            "A", "助剂", "P", "颜料", "F", "填料", "R", "树脂", "S", "溶剂", "B", "半成品", "C", "成品");
    private static final Map<String, String> SUPPLIER_TYPE_CN = Map.of(
            "MATERIAL", "材料", "FINISHED", "成品", "PROCESSOR", "代工厂");
    private static final Map<String, String> PAYMENT_TERMS_CN = Map.of(
            "款到发货", "PREPAID", "货到付款", "COD", "账期30天", "CREDIT_30", "账期60天", "CREDIT_60",
            "月结", "MONTHLY", "两月结", "TWO_MONTH", "三月结", "THREE_MONTH");

    private final SupplierRepository supplierRepo;
    private final MaterialRepository materialRepo;
    private final DictItemRepository dictItemRepo;
    private final CodingRuleService codingRuleService;
    private final MaterialService materialService;
    private final RecipeRepository recipeRepo;
    private final RecipeVersionRepository versionRepo;
    private final RecipeTreeNodeRepository treeNodeRepo;
    private final ProcessTemplateRepository processTemplateRepo;
    private final WriteQueue writeQueue;

    public ExcelImportService(SupplierRepository supplierRepo, MaterialRepository materialRepo,
                              DictItemRepository dictItemRepo, CodingRuleService codingRuleService,
                              MaterialService materialService, RecipeRepository recipeRepo,
                              RecipeVersionRepository versionRepo, RecipeTreeNodeRepository treeNodeRepo,
                              ProcessTemplateRepository processTemplateRepo, WriteQueue writeQueue) {
        this.supplierRepo = supplierRepo;
        this.materialRepo = materialRepo;
        this.dictItemRepo = dictItemRepo;
        this.codingRuleService = codingRuleService;
        this.materialService = materialService;
        this.recipeRepo = recipeRepo;
        this.versionRepo = versionRepo;
        this.treeNodeRepo = treeNodeRepo;
        this.processTemplateRepo = processTemplateRepo;
        this.writeQueue = writeQueue;
    }

    // ==================== 供应商 ====================

    private static final String[] SUPPLIER_HEADERS = {"供应商名称*", "类型", "付款条件", "付款方式", "加工费(元/吨)"};

    public void downloadSupplierTemplate(HttpServletResponse response) throws IOException {
        excelResponse(response, "供应商导入模板");
        try (var writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true)) {
            writer.renameSheet("供应商数据");
            writer.writeRow(Arrays.asList(SUPPLIER_HEADERS), true);
            writer.writeRow(Arrays.asList("示例化工有限公司（请删除本行）", "材料", "月结", "银行转账", ""));
            writer.setSheet("填写说明");
            writer.writeRow(Arrays.asList("列名", "必填", "说明"));
            writer.writeRow(Arrays.asList("供应商名称", "是", "同一类型下名称不允许重复；编码系统自动生成（SUP-年份-序号）"));
            writer.writeRow(Arrays.asList("类型", "否", "材料(默认)/成品/代工厂"));
            writer.writeRow(Arrays.asList("付款条件", "否", "款到发货/货到付款/账期30天/账期60天/月结/两月结/三月结，或自定义如「账期45天」"));
            writer.writeRow(Arrays.asList("付款方式", "否", "如 银行转账/承兑/现金"));
            writer.writeRow(Arrays.asList("加工费(元/吨)", "否", "数字，不能为负；代工厂委外加工费单价"));
            writer.flush(response.getOutputStream());
        }
    }

    @Transactional
    public int importSuppliers(MultipartFile file, String operator) {
        List<Map<String, Object>> raw = readAll(file, SUPPLIER_HEADERS, "供应商");
        List<Map<String, Object>> errors = new ArrayList<>();
        Set<String> inFileKeys = new HashSet<>();
        List<Supplier> rows = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int rowNo = i + 2;
            Map<String, Object> m = raw.get(i);
            if (isEmptyRow(m)) continue;
            String name = str(m.get("供应商名称")).trim();
            if (name.isBlank()) {
                errors.add(err(rowNo, "供应商名称不能为空"));
                continue;
            }
            String typeCn = str(m.get("类型")).trim();
            String type = mapSupplierType(typeCn);
            if (type == null) {
                errors.add(err(rowNo, "类型「" + typeCn + "」无法识别（可填：材料/成品/代工厂，留空默认材料）"));
                continue;
            }
            if (!inFileKeys.add(name + "|" + type)) {
                errors.add(err(rowNo, "供应商「" + name + "」在文件内重复（同名称同类型）"));
                continue;
            }
            if (supplierRepo.existsByNameAndType(name, type)) {
                errors.add(err(rowNo, "已存在相同名称和类型的供应商「" + name + "」"));
                continue;
            }
            Supplier s = new Supplier();
            s.name = name;
            s.type = type;
            String terms = str(m.get("付款条件")).trim();
            if (!terms.isBlank()) {
                String mapped = PAYMENT_TERMS_CN.get(terms);
                s.paymentTerms = mapped != null ? mapped : terms;  // 未匹配的按自定义账期原文保存
            }
            s.paymentMethod = blankToNull(str(m.get("付款方式")).trim());
            BigDecimal fee = toBd(m.get("加工费"));
            if (fee != null && fee.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(err(rowNo, "加工费不能为负"));
                continue;
            }
            s.processingFee = fee;
            rows.add(s);
        }
        requireNoErrors(errors, "供应商");
        return writeQueue.execute(() -> {
            Integer maxSeq = supplierRepo.findMaxSeq("SUP-" + LocalDate.now().toString().replace("-", "") + "-%");
            long seq = maxSeq == null ? 0 : maxSeq;
            for (Supplier s : rows) {
                seq++;
                s.code = String.format("SUP-%s-%04d", LocalDate.now().toString().replace("-", ""), seq);
                supplierRepo.save(s);
            }
            log.info("供应商导入完成: {} 家，操作人 {}", rows.size(), operator);
            return rows.size();
        });
    }

    // ==================== 物料 ====================

    private static final String[] MATERIAL_HEADERS = {"品名*", "牌号*", "大类*", "小类*", "主材", "色系", "品牌归属", "质保期(天)*", "物料编码(留空自动)"};

    public void downloadMaterialTemplate(HttpServletResponse response) throws IOException {
        excelResponse(response, "物料导入模板");
        // 小类清单进说明页（按字典动态生成，避免模板与编码规则脱节）
        StringBuilder subs = new StringBuilder();
        for (var d : dictItemRepo.findByTypeAndEnabledTrueOrderBySortOrderAsc("material_sub_category")) {
            if (subs.length() > 0) subs.append("、");
            subs.append(d.value).append("(").append(d.label).append(")");
        }
        try (var writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true)) {
            writer.renameSheet("物料数据");
            writer.writeRow(Arrays.asList(MATERIAL_HEADERS), true);
            writer.writeRow(Arrays.asList("示例助剂（请删除本行）", "BYK-051", "A", "AC", "", "", "", 365, ""));
            writer.setSheet("填写说明");
            writer.writeRow(Arrays.asList("列名", "必填", "说明"));
            writer.writeRow(Arrays.asList("品名/牌号", "是", "品名+牌号+大类+小类完全一致视为重复，不允许导入"));
            writer.writeRow(Arrays.asList("大类", "是", "A助剂 / P颜料 / F填料 / R树脂 / S溶剂 / B半成品 / C成品（填字母或中文均可）"));
            writer.writeRow(Arrays.asList("小类", "是", "两字母代码（首位字母须=大类），有效值：" + subs));
            writer.writeRow(Arrays.asList("主材", "成品必填", "聚酯/氟碳/环氧/丙烯酸"));
            writer.writeRow(Arrays.asList("品牌归属", "否", "成品默认芃远；外购成品填供应商名称"));
            writer.writeRow(Arrays.asList("质保期(天)", "是", "整数，无限制填 0"));
            writer.writeRow(Arrays.asList("物料编码", "否", "留空按编码规则自动取号（6位=小类码+序号）；填写则须为未占用的合法新码"));
            writer.flush(response.getOutputStream());
        }
    }

    @Transactional
    public int importMaterials(MultipartFile file, String operator) {
        List<Map<String, Object>> raw = readAll(file, MATERIAL_HEADERS, "物料");
        // 批量预取（编码规范：主数据一次建 Map）
        Set<String> subCats = new HashSet<>();
        for (var d : dictItemRepo.findByTypeAndEnabledTrueOrderBySortOrderAsc("material_sub_category")) {
            subCats.add(d.value);
        }
        Set<String> usedCodes = new HashSet<>();
        for (Material m : materialRepo.findAll()) usedCodes.add(m.code);
        Set<String> nameKeys = new HashSet<>();
        for (Material m : materialRepo.findAll()) {
            nameKeys.add(m.name + "|" + m.brand + "|" + m.category + "|" + m.subCategory);
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        Set<String> inFileCodes = new HashSet<>();
        Set<String> inFileNames = new HashSet<>();
        List<Material> rows = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int rowNo = i + 2;
            Map<String, Object> m = raw.get(i);
            if (isEmptyRow(m)) continue;
            Material mat = new Material();
            mat.name = str(m.get("品名")).trim();
            mat.brand = str(m.get("牌号")).trim();
            if (mat.name.isBlank() || mat.brand.isBlank()) {
                errors.add(err(rowNo, "品名、牌号不能为空"));
                continue;
            }
            mat.category = mapCategory(str(m.get("大类")).trim());
            if (mat.category == null) {
                errors.add(err(rowNo, "大类「" + str(m.get("大类")) + "」无法识别（A助剂/P颜料/F填料/R树脂/S溶剂/B半成品/C成品）"));
                continue;
            }
            mat.subCategory = str(m.get("小类")).trim().toUpperCase();
            if (mat.subCategory.isBlank() || !subCats.contains(mat.subCategory)) {
                errors.add(err(rowNo, "小类「" + mat.subCategory + "」不存在（请按模板说明页的两字母代码填写）"));
                continue;
            }
            if (mat.subCategory.charAt(0) != mat.category.charAt(0)) {
                errors.add(err(rowNo, "小类 " + mat.subCategory + " 首字母与大类 " + mat.category + " 不一致"));
                continue;
            }
            mat.mainMaterial = blankToNull(str(m.get("主材")).trim());
            if ("C".equals(mat.category) && mat.mainMaterial == null) {
                errors.add(err(rowNo, "成品必须填写主材（聚酯/氟碳/环氧/丙烯酸）"));
                continue;
            }
            mat.colorSeries = blankToNull(str(m.get("色系")).trim());
            mat.brandOwner = blankToNull(str(m.get("品牌归属")).trim());
            if ("C".equals(mat.category) && mat.brandOwner == null) mat.brandOwner = "芃远";
            Integer shelf = toInt(m.get("质保期"));
            if (shelf == null || shelf < 0) {
                errors.add(err(rowNo, "质保期须为非负整数（无限制填 0）"));
                continue;
            }
            mat.shelfLifeDays = shelf;
            String code = str(m.get("物料编码")).trim().toUpperCase();
            if (!code.isBlank()) {
                if (usedCodes.contains(code) || !inFileCodes.add(code)) {
                    errors.add(err(rowNo, "物料编码 " + code + " 已存在或在文件内重复"));
                    continue;
                }
                try {
                    materialService.validateCodeFormat(code, mat.category);
                } catch (IllegalArgumentException e) {
                    errors.add(err(rowNo, e.getMessage()));
                    continue;
                }
                mat.code = code;
            }
            String nameKey = mat.name + "|" + mat.brand + "|" + mat.category + "|" + mat.subCategory;
            if (nameKeys.contains(nameKey) || !inFileNames.add(nameKey)) {
                errors.add(err(rowNo, "物料「" + mat.name + " " + mat.brand + "」已存在或在文件内重复（品名+牌号+大类+小类）"));
                continue;
            }
            rows.add(mat);
        }
        requireNoErrors(errors, "物料");
        return writeQueue.execute(() -> {
            java.util.List<Material> materialBatch = new java.util.ArrayList<>();
            for (Material mat : rows) {
                if (mat.code == null || mat.code.isBlank()) {
                    // v5.65 取号分流：B/C 类走属性编码（缺主材/色系抛行级错误），原料走原全局连续体系
                    if ("C".equals(mat.category)) {
                        mat.code = codingRuleService.generateProductCode(mat.subCategory, mat.mainMaterial, mat.colorSeries);
                    } else if ("B".equals(mat.category)) {
                        mat.code = codingRuleService.generateSemiCode(mat.subCategory, mat.mainMaterial);
                    } else {
                        mat.code = codingRuleService.generateCode(mat.subCategory);  // 逐行取号（编码规则自增，锁内安全）
                    }
                }
                materialBatch.add(mat);
                if (materialBatch.size() >= 500) { materialRepo.saveAll(materialBatch); materialBatch.clear(); }
            }
            if (!materialBatch.isEmpty()) materialRepo.saveAll(materialBatch);
            log.info("物料导入完成: {} 条，操作人 {}", rows.size(), operator);
            return rows.size();
        });
    }

    // ==================== 配方 ====================

    private static final String[] RECIPE_HEADERS = {"品名*", "配方类型*", "工艺路线名称*", "批量", "单位", "成品物料编码", "物料编码", "用量*", "备注"};

    public void downloadRecipeTemplate(HttpServletResponse response) throws IOException {
        excelResponse(response, "配方导入模板");
        // 工艺路线清单进说明页（按名称导入的依据）
        StringBuilder routes = new StringBuilder();
        for (var t : processTemplateRepo.findAllByOrderByUpdateTimeDesc()) {
            if (routes.length() > 0) routes.append("、");
            routes.append(t.name).append("(").append("GRINDING".equals(t.recipeType) ? "制浆" : "制漆").append(")");
        }
        try (var writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true)) {
            writer.renameSheet("配方数据");
            writer.writeRow(Arrays.asList(RECIPE_HEADERS), true);
            writer.writeRow(Arrays.asList("示例白漆（请删除本行）", "制漆", "", 100, "kg", "", "R00001", 60, "首行填头部字段"));
            writer.writeRow(Arrays.asList("示例白漆（请删除本行）", "", "", "", "", "", "AC0001", 1, "后续行只填品名+明细"));
            writer.setSheet("填写说明");
            writer.writeRow(Arrays.asList("列名", "必填", "说明"));
            writer.writeRow(Arrays.asList("品名", "是", "同一配方的多层明细写多行（品名相同的行归入同一配方）；品名=产品名，不允许与现有配方重复"));
            writer.writeRow(Arrays.asList("配方类型", "是", "制浆(GRINDING) / 制漆(TINTING)，只在每个配方首行填写"));
            writer.writeRow(Arrays.asList("工艺路线名称", "是", "必须已存在且类型与配方一致，当前可选：" + (routes.isEmpty() ? "（无，请先在工艺路线中创建）" : routes)));
            writer.writeRow(Arrays.asList("批量/单位", "否", "标准批量（默认 100）、kg 或 L（默认 kg），只在首行填写"));
            writer.writeRow(Arrays.asList("成品物料编码", "否", "关联的成品/半成品物料编码，可留空"));
            writer.writeRow(Arrays.asList("物料编码/用量", "明细行填", "BOM 行：物料须已存在于物料档案；用量为每批用量，须大于 0"));
            writer.writeRow(Arrays.asList("子配方", "-", "暂不支持导入子配方层级，导入后可在配方编辑中添加"));
            writer.flush(response.getOutputStream());
        }
    }

    /** 配方一行（首行带头部字段 + 若干明细行） */
    private static class RecipeRow {
        String name;
        String recipeType;
        Long processTemplateId;
        String processName;
        BigDecimal batchQty = BigDecimal.valueOf(100);
        String unit = "kg";
        String productCode;
        String description;
        List<Object[]> lines = new ArrayList<>();  // {materialCode, qty}
        int firstRowNo;
    }

    @Transactional
    public int importRecipes(MultipartFile file, String operator) {
        List<Map<String, Object>> raw = readAll(file, RECIPE_HEADERS, "配方");
        // 批量预取：物料档案 + 现有配方名 + 工艺路线
        Map<String, Material> matByCode = new HashMap<>();
        for (Material m : materialRepo.findAll()) matByCode.put(m.code, m);
        Set<String> existNames = new HashSet<>();
        for (Recipe r : recipeRepo.findAll()) {
            if (r.productName != null) existNames.add(r.productName.trim());
        }
        Map<String, List<com.pengyuan.pims.entity.ProcessTemplate>> templates = new LinkedHashMap<>();
        for (var t : processTemplateRepo.findAllByOrderByUpdateTimeDesc()) {
            templates.computeIfAbsent(t.name, k -> new ArrayList<>()).add(t);
        }
        // 按品名分组成配方（保持文件顺序）
        Map<String, RecipeRow> byName = new LinkedHashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int rowNo = i + 2;
            Map<String, Object> m = raw.get(i);
            if (isEmptyRow(m)) continue;
            String name = str(m.get("品名")).trim();
            if (name.isBlank()) {
                errors.add(err(rowNo, "品名不能为空"));
                continue;
            }
            RecipeRow r = byName.computeIfAbsent(name, k -> {
                RecipeRow nr = new RecipeRow();
                nr.name = k;
                nr.firstRowNo = rowNo;
                return nr;
            });
            // 首行（该品名第一次出现）解析头部字段
            if (r.lines.isEmpty() && r.processTemplateId == null) {
                String typeCn = str(m.get("配方类型")).trim();
                r.recipeType = "制浆".equals(typeCn) || "GRINDING".equalsIgnoreCase(typeCn) ? "GRINDING"
                        : "制漆".equals(typeCn) || "TINTING".equalsIgnoreCase(typeCn) ? "TINTING" : null;
                r.processName = str(m.get("工艺路线名称")).trim();
                BigDecimal batch = toBd(m.get("批量"));
                if (batch != null && batch.compareTo(BigDecimal.ZERO) > 0) r.batchQty = batch;
                String unit = str(m.get("单位")).trim().toUpperCase();
                if ("KG".equals(unit) || "L".equals(unit)) r.unit = unit;
                r.productCode = blankToNull(str(m.get("成品物料编码")).trim().toUpperCase());
                if (r.productCode != null && !matByCode.containsKey(r.productCode)) {
                    errors.add(err(rowNo, "成品物料编码 " + r.productCode + " 不存在于物料档案"));
                }
                r.description = blankToNull(str(m.get("备注")).trim());
            }
            // 明细行
            String lineCode = str(m.get("物料编码")).trim().toUpperCase();
            BigDecimal qty = toBd(m.get("用量"));
            if (!lineCode.isBlank()) {
                Material mat = matByCode.get(lineCode);
                if (mat == null) {
                    errors.add(err(rowNo, "明细物料编码 " + lineCode + " 不存在于物料档案（请先导入/建立物料）"));
                    continue;
                }
                if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(err(rowNo, "明细物料 " + lineCode + " 用量必须大于 0"));
                    continue;
                }
                r.lines.add(new Object[]{lineCode, qty});
            } else if (qty != null) {
                errors.add(err(rowNo, "该行填了用量但缺少物料编码"));
            }
        }
        // 配方级校验（不短路：同一配方的多类问题一次性全部报出）
        for (RecipeRow r : byName.values()) {
            if (existNames.contains(r.name)) {
                errors.add(err(r.firstRowNo, "配方品名「" + r.name + "」已存在（配方品名不允许重复）"));
            }
            if (r.recipeType == null) {
                errors.add(err(r.firstRowNo, "配方类型无法识别（制浆/制漆），请在该配方首行填写"));
            }
            List<com.pengyuan.pims.entity.ProcessTemplate> matched = templates.getOrDefault(r.processName, List.of());
            if (r.processName.isBlank()) {
                errors.add(err(r.firstRowNo, "工艺路线名称不能为空（配方必须绑定工艺路线）"));
            } else if (matched.isEmpty()) {
                errors.add(err(r.firstRowNo, "工艺路线「" + r.processName + "」不存在（请先在工艺路线模块创建，或核对名称）"));
            } else if (matched.size() > 1) {
                errors.add(err(r.firstRowNo, "工艺路线名称「" + r.processName + "」存在多条，请重命名至唯一后导入"));
            } else {
                com.pengyuan.pims.entity.ProcessTemplate t = matched.get(0);
                if (!t.recipeType.equals(r.recipeType)) {
                    errors.add(err(r.firstRowNo, "工艺路线「" + r.processName + "」类型（"
                            + ("GRINDING".equals(t.recipeType) ? "制浆" : "制漆") + "）与配方类型不一致"));
                } else {
                    r.processTemplateId = t.id;
                }
            }
        }
        requireNoErrors(errors, "配方");
        List<RecipeRow> rows = new ArrayList<>(byName.values());
        return writeQueue.execute(() -> {
            Integer maxSeq = recipeRepo.maxRecipeNoSeq();
            long seq = maxSeq == null ? 0 : maxSeq;
            for (RecipeRow r : rows) {
                seq++;
                Recipe recipe = new Recipe();
                recipe.recipeNo = String.format("RCP-%04d", seq);
                recipe.productCode = r.productCode;
                recipe.productName = r.name;
                recipe.recipeType = r.recipeType;
                recipe.processTemplateId = r.processTemplateId;
                recipe.description = r.description;
                recipe.enabled = true;
                recipe.createTime = LocalDateTime.now();
                recipeRepo.save(recipe);
                RecipeVersion v = new RecipeVersion();
                v.recipeId = recipe.id;
                v.versionNo = "V1.0";
                v.status = "DRAFT";
                v.batchQty = r.batchQty;
                v.unit = r.unit;
                v.createTime = LocalDateTime.now();
                versionRepo.save(v);
                int sort = 0;
                for (Object[] line : r.lines) {
                    Material mat = matByCode.get((String) line[0]);
                    RecipeTreeNode node = new RecipeTreeNode();
                    node.versionId = v.id;
                    node.parentNodeId = null;
                    node.nodeType = "MATERIAL";
                    node.materialCode = mat.code;
                    node.materialName = mat.name;
                    node.spec = mat.brand;
                    node.category = mat.category;
                    node.subCategory = mat.subCategory;
                    node.unit = "kg";
                    node.qty = (BigDecimal) line[1];
                    node.sortOrder = ++sort;
                    treeNodeRepo.save(node);
                }
                log.info("配方导入: {} {}（{} 行明细）", recipe.recipeNo, r.name, r.lines.size());
            }
            log.info("配方导入完成: {} 个，操作人 {}", rows.size(), operator);
            return rows.size();
        });
    }

    // ==================== 公共工具 ====================

    private static void excelResponse(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded + ".xlsx");
    }

    /**
     * 读取首个 sheet 并校验表头（缺必需列直接整体拒绝）。
     * 表头键统一规范化（去掉必填星号 * 与括号说明，如「供应商名称*」「质保期(天)*」→「供应商名称」「质保期」），
     * 模板装饰与取值键解耦，模板列名加说明不影响解析。
     */
    private List<Map<String, Object>> readAll(MultipartFile file, String[] headers, String label) {
        List<Map<String, Object>> raw;
        try (cn.hutool.poi.excel.ExcelReader reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream())) {
            raw = reader.readAll();
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败：" + e.getMessage());
        }
        if (raw == null || raw.isEmpty()) {
            throw new ExcelImportException("文件为空，没有可导入的数据", List.of());
        }
        for (String required : headers) {
            String norm = normalizeKey(required);
            if (raw.get(0).keySet().stream().noneMatch(k -> normalizeKey(String.valueOf(k)).equals(norm))) {
                throw new ExcelImportException("模板格式不正确（缺少列：" + required + "），请下载最新" + label + "导入模板", List.of());
            }
        }
        // 键规范化重建（同名冲突保留先出现的列）
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (var e : row.entrySet()) {
                if (e.getKey() == null) continue;
                m.putIfAbsent(normalizeKey(String.valueOf(e.getKey())), e.getValue());
            }
            out.add(m);
        }
        return out;
    }

    private static String normalizeKey(String k) {
        return k.replace("*", "").split("\\(")[0].trim();
    }

    private static void requireNoErrors(List<Map<String, Object>> errors, String label) {
        if (!errors.isEmpty()) {
            throw new ExcelImportException(label + "导入校验失败：共 " + errors.size() + " 处错误，未写入任何数据", errors);
        }
    }

    private static Map<String, Object> err(int rowNo, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", rowNo);
        m.put("reason", reason);
        return m;
    }

    private static boolean isEmptyRow(Map<String, Object> m) {
        return m.values().stream().allMatch(v -> v == null || String.valueOf(v).isBlank());
    }

    private static String str(Object v) {
        // Excel 数字单元格可能读成 Double（如编码 2500），统一转成去尾零的字符串
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d)) return String.valueOf((long) d);
        }
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        BigDecimal bd = toBd(v);
        return bd == null ? null : bd.intValue();
    }

    private static String mapCategory(String v) {
        if (v.isBlank()) return null;
        String up = v.toUpperCase();
        if (CATEGORY_CN.containsKey(up)) return up;               // 字母
        for (var e : CATEGORY_CN.entrySet()) {
            if (e.getValue().equals(v)) return e.getKey();        // 中文
        }
        return null;
    }

    private static String mapSupplierType(String v) {
        if (v.isBlank()) return "MATERIAL";
        String up = v.toUpperCase();
        if (SUPPLIER_TYPE_CN.containsKey(up)) return up;
        for (var e : SUPPLIER_TYPE_CN.entrySet()) {
            if (e.getValue().equals(v)) return e.getKey();
        }
        return null;
    }
}
