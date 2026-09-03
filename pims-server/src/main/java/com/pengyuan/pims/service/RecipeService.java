package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.FinishedProductPurchase;
import com.pengyuan.pims.entity.InventoryLedger;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.ProcessTemplate;
import com.pengyuan.pims.entity.RawMaterialPurchase;
import com.pengyuan.pims.entity.Recipe;
import com.pengyuan.pims.entity.RecipeTreeNode;
import com.pengyuan.pims.entity.RecipeVersion;
import com.pengyuan.pims.entity.Warehouse;
import com.pengyuan.pims.repository.FinishedProductPurchaseRepository;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import com.pengyuan.pims.repository.MaterialRepository;
import com.pengyuan.pims.repository.ProcessTemplateRepository;
import com.pengyuan.pims.repository.RawMaterialPurchaseRepository;
import com.pengyuan.pims.repository.RecipeRepository;
import com.pengyuan.pims.repository.RecipeTreeNodeRepository;
import com.pengyuan.pims.repository.RecipeVersionRepository;
import com.pengyuan.pims.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配方管理服务
 * 包含：配方CRUD、版本管理、配方树操作、树展开算法
 */
@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final RecipeRepository recipeRepo;
    private final com.pengyuan.pims.repository.RecipeChangeLogRepository changeLogRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;   // v6.3 buildPriceMap 聚合   // v6.3 变更日志
    private final RecipeVersionRepository versionRepo;
    private final RecipeTreeNodeRepository treeNodeRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final RawMaterialPurchaseRepository rawPurchaseRepo;
    private final FinishedProductPurchaseRepository finishedPurchaseRepo;
    private final ProcessTemplateRepository processTemplateRepo;
    private final com.pengyuan.pims.repository.PackagingStandardRepository packagingRepo;   // v5.81 包装成本
    private final com.pengyuan.pims.repository.PackagingStandardItemRepository packagingItemRepo;   // v5.82 组合包装
    private final MaterialRepository materialRepo;
    private final WarehouseRepository warehouseRepo;
    // v5.24：全局写锁（配方编号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;

    public RecipeService(RecipeRepository recipeRepo,
                         RecipeVersionRepository versionRepo,
                         RecipeTreeNodeRepository treeNodeRepo,
                         InventoryLedgerRepository ledgerRepo,
                         RawMaterialPurchaseRepository rawPurchaseRepo,
                         FinishedProductPurchaseRepository finishedPurchaseRepo,
                         ProcessTemplateRepository processTemplateRepo,
                         MaterialRepository materialRepo,
                         WarehouseRepository warehouseRepo,
                         WriteQueue writeQueue, com.pengyuan.pims.repository.PackagingStandardRepository packagingRepo, com.pengyuan.pims.repository.PackagingStandardItemRepository packagingItemRepo,
                                 com.pengyuan.pims.repository.RecipeChangeLogRepository changeLogRepo,
                                 org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.recipeRepo = recipeRepo;
        this.versionRepo = versionRepo;
        this.treeNodeRepo = treeNodeRepo;
        this.ledgerRepo = ledgerRepo;
        this.rawPurchaseRepo = rawPurchaseRepo;
        this.finishedPurchaseRepo = finishedPurchaseRepo;
        this.processTemplateRepo = processTemplateRepo;
        this.packagingRepo = packagingRepo;
        this.packagingItemRepo = packagingItemRepo;
        this.materialRepo = materialRepo;
        this.warehouseRepo = warehouseRepo;
        this.writeQueue = writeQueue;
        this.changeLogRepo = changeLogRepo;
        this.jdbc = jdbc;
    }

    /** 校验配方绑定的工艺路线：必填、存在、类型一致 */
    private void validateProcessRoute(Recipe recipe) {
        if (recipe.processTemplateId == null) throw new IllegalArgumentException("请选择工艺路线");
        // v5.99 四必填：质检模板、包装标准（调色/磨浆一致）
        if (recipe.qcTemplateId == null) throw new IllegalArgumentException("请选择质检模板");
        if (recipe.packagingStandardId == null) throw new IllegalArgumentException("请选择包装标准");
        ProcessTemplate t = processTemplateRepo.findById(recipe.processTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + recipe.processTemplateId));
        if (!t.recipeType.equals(recipe.recipeType))
            throw new IllegalArgumentException("工艺路线类型与配方类型不一致");
    }

    // ==================== 配方 CRUD ====================

    public List<Recipe> list(String keyword, String category) {
        List<Recipe> recipes;
        if ((keyword == null || keyword.isBlank()) && (category == null || category.isBlank())) {
            recipes = recipeRepo.findByOrderByCreateTimeDesc();
        } else {
            recipes = recipeRepo.search(
                    (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                    (category == null || category.isBlank()) ? null : category.trim()
            );
        }
        // v5.26：填充配方引用次数（实时统计：订单头部引用 + 订单明细子配方引用）
        java.util.Map<Long, Integer> usage = new java.util.HashMap<>();
        for (Object[] row : recipeRepo.usageCounts()) {
            usage.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        for (Recipe r : recipes) {
            r.usageCount = usage.getOrDefault(r.id, 0);
        }
        return recipes;
    }

    public Recipe getById(Long id) {
        return recipeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配方不存在"));
    }

    /**
     * 创建配方，自动创建 V1.0 草稿版本
     */
    @Transactional
    public Recipe create(Recipe recipe) {
        validateProcessRoute(recipe);
        checkProductNameDuplicate(recipe, null);
        // v5.24：编号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.execute(() -> {
            // 生成配方编号 RCP-NNNN
            Integer maxSeq = recipeRepo.maxRecipeNoSeq();
            int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
            recipe.recipeNo = String.format("RCP-%04d", nextSeq);
            recipe.enabled = true;
            recipe.createTime = LocalDateTime.now();
            recipeRepo.save(recipe);

            // 自动创建 V1.0 草稿版本
            RecipeVersion v = new RecipeVersion();
            v.recipeId = recipe.id;
            v.versionNo = "V1.0";
            v.status = "DRAFT";
            v.batchQty = BigDecimal.valueOf(100);
            v.createTime = LocalDateTime.now();
            versionRepo.save(v);

            log.info("配方创建: {} 品名={}", recipe.recipeNo, recipe.productName);
            return recipe;
        });
    }

    @Transactional
    public Recipe update(Long id, Recipe updated) {
        Recipe recipe = getById(id);
        recipe.productCode = updated.productCode;
        recipe.productName = updated.productName;
        recipe.recipeType = updated.recipeType;
        recipe.category = updated.category;
        recipe.description = updated.description;
        recipe.enabled = updated.enabled;
        recipe.processTemplateId = updated.processTemplateId;
        recipe.qcTemplateId = updated.qcTemplateId;               // v5.81 绑定质检模板
        recipe.packagingStandardId = updated.packagingStandardId; // v5.81 包装标准（进理论成本）
        // v5.99 四必填：更新同样强制
        if (recipe.qcTemplateId == null) throw new IllegalArgumentException("请选择质检模板");
        if (recipe.packagingStandardId == null) throw new IllegalArgumentException("请选择包装标准");
        validateProcessRoute(recipe);
        checkProductNameDuplicate(recipe, id);
        recipe.updateTime = LocalDateTime.now();
        recipeRepo.save(recipe);
        return recipe;
    }

    /**
     * 配方名称(productName)唯一性校验：去前后空格后比较，编辑时排除自身 id。
     * 命中重复抛 IllegalArgumentException → GlobalExceptionHandler 转 400 → 前端 Toast 自动提示。
     */
    private void checkProductNameDuplicate(Recipe recipe, Long excludeId) {
        if (recipe.productName == null || recipe.productName.isBlank()) return;
        recipe.productName = recipe.productName.trim();
        boolean dup = recipeRepo.findByProductName(recipe.productName).stream()
                .anyMatch(r -> excludeId == null || !r.id.equals(excludeId));
        if (dup) {
            throw new IllegalArgumentException("配方名称「" + recipe.productName + "」已存在，不允许重复");
        }
    }

    /**
     * 启用/禁用配方
     * 禁用后，任何单据（生产订单、委外订单）不可引用该配方
     */
    @Transactional
    public Recipe toggleEnabled(Long id, boolean enabled) {
        Recipe recipe = getById(id);
        recipe.enabled = enabled;
        recipe.updateTime = LocalDateTime.now();
        recipeRepo.save(recipe);
        log.info("配方{}: {} 品名={}", enabled ? "启用" : "禁用", recipe.recipeNo, recipe.productName);
        return recipe;
    }

    /**
     * 校验配方是否可被单据引用（必须存在且启用）
     * @param recipeId 配方ID
     */
    public void checkReferenceable(Long recipeId) {
        if (recipeId == null) return;
        Recipe recipe = recipeRepo.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("配方不存在"));
        if (!Boolean.TRUE.equals(recipe.enabled)) {
            throw new IllegalArgumentException("配方 " + recipe.recipeNo + " 已禁用，不可被单据引用");
        }
    }

    @Transactional
    public void delete(Long id) {
        Recipe recipe = getById(id);
        // 检查是否有 RELEASED 版本
        Optional<RecipeVersion> released = versionRepo.findByRecipeIdAndStatus(id, "RELEASED");
        if (released.isPresent()) {
            throw new IllegalArgumentException("存在已发布版本，无法删除配方");
        }
        // 删除所有版本及其树节点
        List<RecipeVersion> versions = versionRepo.findByRecipeIdOrderByCreateTimeDesc(id);
        for (RecipeVersion v : versions) {
            treeNodeRepo.deleteByVersionId(v.id);
        }
        versionRepo.deleteByRecipeId(id);
        recipeRepo.deleteById(id);
        log.info("配方删除: {}", recipe.recipeNo);
    }

    // ==================== 版本管理 ====================

    public List<RecipeVersion> listVersions(Long recipeId) {
        getById(recipeId); // 校验存在
        return versionRepo.findByRecipeIdOrderByCreateTimeDesc(recipeId);
    }

    public RecipeVersion getVersion(Long versionId) {
        return versionRepo.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("配方版本不存在"));
    }

    /**
     * 新建版本（从最新 RELEASED 版本复制树），版本号自动递增
     */
    @Transactional
    public RecipeVersion createVersion(Long recipeId, RecipeVersion input) {
        Recipe recipe = getById(recipeId);

        // 检查是否已有 DRAFT 版本
        Optional<RecipeVersion> draft = versionRepo.findByRecipeIdAndStatus(recipeId, "DRAFT");
        if (draft.isPresent()) {
            throw new IllegalArgumentException("已存在草稿版本，请先发布或删除");
        }

        // 计算版本号
        List<RecipeVersion> versions = versionRepo.findByRecipeIdOrderByCreateTimeDesc(recipeId);
        int nextMajor = versions.size() + 1;
        String versionNo = "V" + nextMajor + ".0";

        RecipeVersion v = new RecipeVersion();
        v.recipeId = recipeId;
        v.versionNo = versionNo;
        v.status = "DRAFT";
        v.batchQty = input != null && input.batchQty != null ? input.batchQty : BigDecimal.valueOf(100);
        v.unit = "kg"; // v4.9：所有物料单位统一为公斤
        v.remark = input != null ? input.remark : null;
        v.createTime = LocalDateTime.now();
        versionRepo.save(v);
        { var rec = recipeRepo.findById(recipeId).orElse(null);   // v6.3 变更日志
          if (rec != null) logChange(recipeId, rec.recipeNo, rec.productName, v.id, v.versionNo,
                  "CREATE", "新建版本（批量 " + (v.batchQty != null ? v.batchQty : "-") + "kg）", v.createdBy); }

        // 从最新 RELEASED 版本复制树
        Optional<RecipeVersion> released = versionRepo.findByRecipeIdAndStatus(recipeId, "RELEASED");
        if (released.isPresent()) {
            copyTree(released.get().id, v.id);
        }

        log.info("配方 {} 新建版本 {}", recipe.recipeNo, versionNo);
        return v;
    }

    @Transactional
    public RecipeVersion updateVersion(Long versionId, RecipeVersion updated) {
        RecipeVersion v = getVersion(versionId);
        if (!"DRAFT".equals(v.status)) {
            throw new IllegalArgumentException("只有草稿版本可编辑");
        }
        java.math.BigDecimal oldBatch = v.batchQty;
        v.batchQty = updated.batchQty;
        v.unit = "kg"; // v4.9：所有物料单位统一为公斤
        v.remark = updated.remark;
        v.updateTime = LocalDateTime.now();
        versionRepo.save(v);
        { var rec = recipeRepo.findById(v.recipeId).orElse(null);   // v6.3 变更日志
          if (rec != null) logChange(v.recipeId, rec.recipeNo, rec.productName, v.id, v.versionNo,
                  "UPDATE", (oldBatch != null && updated.batchQty != null && oldBatch.compareTo(updated.batchQty) != 0
                          ? "批量 " + oldBatch + "→" + updated.batchQty : "版本信息修改"), updated.createdBy); }
        return v;
    }

    /**
     * 发布版本：旧 RELEASED 自动归档
     */
    @Transactional
    public RecipeVersion release(Long versionId, String operator) {
        RecipeVersion v = getVersion(versionId);
        if (!"DRAFT".equals(v.status)) {
            throw new IllegalArgumentException("只有草稿版本可发布");
        }

        // 归档旧的 RELEASED 版本
        Optional<RecipeVersion> oldReleased = versionRepo.findByRecipeIdAndStatus(v.recipeId, "RELEASED");
        if (oldReleased.isPresent()) {
            RecipeVersion old = oldReleased.get();
            old.status = "ARCHIVED";
            old.updateTime = LocalDateTime.now();
            versionRepo.save(old);
        }

        v.status = "RELEASED";
        v.releasedBy = operator;
        v.releasedTime = LocalDateTime.now();
        if (v.effectiveDate == null) v.effectiveDate = java.time.LocalDate.now();   // v6.3：默认发布即生效
        v.updateTime = LocalDateTime.now();
        versionRepo.save(v);
        var rec = recipeRepo.findById(v.recipeId).orElse(null);
        if (oldReleased.isPresent() && rec != null) {
            logChange(v.recipeId, rec.recipeNo, rec.productName, oldReleased.get().id, oldReleased.get().versionNo,
                    "ARCHIVE", "新版本 " + v.versionNo + " 发布，旧版本自动归档", operator);
        }
        if (rec != null) {
            logChange(v.recipeId, rec.recipeNo, rec.productName, v.id, v.versionNo,
                    "RELEASE", "发布生效（生效日 " + v.effectiveDate + "，批量 " + v.batchQty + "kg）", operator);
        }
        log.info("配方版本发布: recipeId={} version={}", v.recipeId, v.versionNo);
        return v;
    }

    @Transactional
    public void deleteVersion(Long versionId) {
        RecipeVersion v = getVersion(versionId);
        if (!"DRAFT".equals(v.status)) {
            throw new IllegalArgumentException("只有草稿版本可删除");
        }
        treeNodeRepo.deleteByVersionId(versionId);
        versionRepo.deleteById(versionId);
    }

    // ==================== 配方树操作 ====================

    /**
     * 获取树结构（嵌套 JSON）
     */
    public List<Map<String, Object>> getTree(Long versionId) {
        getVersion(versionId); // 校验存在
        List<RecipeTreeNode> nodes = treeNodeRepo.findByVersionIdOrderBySortOrder(versionId);
        return buildTree(nodes, null, buildPriceMap(), new HashSet<>());
    }

    /**
     * 保存整棵树（仅 DRAFT，全量替换）
     * 前端传入嵌套结构，递归保存
     * 保存前查重：与同一配方下其他版本的树结构对比，完全一致则拒绝
     */
    @Transactional
    public void saveTree(Long versionId, List<Map<String, Object>> treeData) {
        RecipeVersion v = getVersion(versionId);
        if (!"DRAFT".equals(v.status)) {
            throw new IllegalArgumentException("只有草稿版本可编辑配方树");
        }

        // 校验：根节点物料用量之和必须等于标准批量
        if (treeData != null && !treeData.isEmpty()) {
            java.math.BigDecimal totalQty = java.math.BigDecimal.ZERO;
            for (Map<String, Object> node : treeData) {
                totalQty = totalQty.add(toBigDecimal(node.get("qty")));
            }
            java.math.BigDecimal batchQty = v.batchQty != null ? v.batchQty : java.math.BigDecimal.valueOf(100);
            if (totalQty.subtract(batchQty).abs().compareTo(new java.math.BigDecimal("0.001")) > 0) {
                throw new IllegalArgumentException("配方树物料添加量之和（" + totalQty.stripTrailingZeros().toPlainString() + "）必须等于标准批量（" + batchQty.stripTrailingZeros().toPlainString() + "）");
            }
        }

        // v5.35：油尾节点校验（制漆配方消化油尾：主材体系一致 + 编码相同或色系相同，且油尾库有库存）
        if (treeData != null && !treeData.isEmpty()) {
            validateTailingNodes(v.recipeId, treeData);
        }

        // 查重：生成待保存树的指纹，与同配方其他版本对比
        if (treeData != null && !treeData.isEmpty()) {
            String newFingerprint = buildFingerprint(treeData);
            List<RecipeVersion> siblings = versionRepo.findByRecipeIdOrderByCreateTimeDesc(v.recipeId);
            for (RecipeVersion sib : siblings) {
                if (sib.id.equals(versionId)) continue;
                List<RecipeTreeNode> existingNodes = treeNodeRepo.findByVersionIdOrderBySortOrder(sib.id);
                if (existingNodes.isEmpty()) continue;
                String existFingerprint = buildFingerprintFromNodes(existingNodes);
                if (newFingerprint.equals(existFingerprint)) {
                    throw new IllegalArgumentException("配方内容与版本 " + sib.versionNo + " 完全一致，不允许重复保存");
                }
            }
        }

        // 全量替换
        treeNodeRepo.deleteByVersionId(versionId);
        treeNodeRepo.flush();

        if (treeData != null) {
            saveTreeNodes(treeData, versionId, null);
        }
        log.info("配方树保存: versionId={} 节点数={}", versionId, treeData != null ? treeData.size() : 0);
        { var rec = recipeRepo.findById(v.recipeId).orElse(null);   // v6.3 变更日志（树为全删重建，记节点数与构成摘要）
          if (rec != null) {
              StringBuilder sb = new StringBuilder();
              if (treeData != null) for (Map<String, Object> n : treeData) {
                  if (sb.length() > 0) sb.append("，");
                  sb.append(String.valueOf(n.get("materialName"))).append("×").append(n.get("qty"));
              }
              String summary = sb.length() == 0 ? "空" : (sb.length() > 400 ? sb.substring(0, 400) + "…" : sb.toString());
              logChange(v.recipeId, rec.recipeNo, rec.productName, v.id, v.versionNo,
                      "TREE_SAVE", "保存配方树（" + (treeData == null ? 0 : treeData.size()) + " 节点）：" + summary, v.createdBy);
          } }
    }

    /**
     * v5.35：油尾库当前有库存的成品物料（制漆配方加「油尾」节点可选列表）
     */
    public List<Map<String, Object>> tailingOptions() {
        // v5.38：油尾库存按 qcStatus=TAILING 标识判断（油尾库已降级为宿主仓下的油尾区分库）
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, BigDecimal> qtyByCode = new HashMap<>();
        for (InventoryLedger l : ledgerRepo.findAll()) {
            if (!"TAILING".equals(l.qcStatus)) continue;
            if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            qtyByCode.merge(l.materialCode, l.qty, BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : qtyByCode.entrySet()) {
            Material m = materialRepo.findByCode(e.getKey()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", e.getKey());
            row.put("materialName", m != null ? m.name : null);
            row.put("mainMaterial", m != null ? m.mainMaterial : null);
            row.put("colorSeries", m != null ? m.colorSeries : null);
            row.put("category", m != null ? m.category : null);
            row.put("qty", e.getValue().stripTrailingZeros());
            result.add(row);
        }
        return result;
    }

    /**
     * v5.35：油尾节点硬校验——油尾物料必须为 C 类成品、油尾库有库存、
     * 主材体系与配方产品一致 且（产品编码相同 或 色系相同）
     */
    @SuppressWarnings("unchecked")
    private void validateTailingNodes(Long recipeId, List<Map<String, Object>> nodes) {
        if (nodes == null) return;
        Recipe recipe = recipeRepo.findById(recipeId).orElse(null);
        Material product = recipe != null && recipe.productCode != null
                ? materialRepo.findByCode(recipe.productCode).orElse(null) : null;
        validateTailingNodeList(nodes, recipe, product);
    }

    @SuppressWarnings("unchecked")
    private void validateTailingNodeList(List<Map<String, Object>> nodes, Recipe recipe, Material product) {
        if (nodes == null) return;
        for (Map<String, Object> node : nodes) {
            Object children = node.get("children");
            if (children instanceof List<?> list) {
                validateTailingNodeList((List<Map<String, Object>>) list, recipe, product);
            }
            if (!"OIL_TAIL".equals(node.get("nodeType"))) continue;
            // v5.72：配方不再允许添加油尾——油尾只能在创建生产订单时添加，且仅限生产成品漆的订单
            throw new IllegalArgumentException("配方不允许添加油尾：油尾只能在创建生产订单时添加（仅限生产成品漆的订单），请删除配方中的油尾节点后保存");
        }
    }

    /** 从前端传入的嵌套树数据生成指纹（规范化字符串） */
    @SuppressWarnings("unchecked")
    private String buildFingerprint(List<Map<String, Object>> treeData) {
        List<String> parts = new ArrayList<>();
        collectFingerprint(treeData, parts);
        Collections.sort(parts);
        return String.join("|", parts);
    }

    @SuppressWarnings("unchecked")
    private void collectFingerprint(List<Map<String, Object>> nodes, List<String> parts) {
        if (nodes == null) return;
        for (Map<String, Object> node : nodes) {
            String code = node.get("materialCode") != null ? node.get("materialCode").toString() : "";
            String qty = node.get("qty") != null ? new BigDecimal(node.get("qty").toString()).stripTrailingZeros().toPlainString() : "0";
            String type = node.get("nodeType") != null ? node.get("nodeType").toString() : "MATERIAL";
            String ref = node.get("refRecipeId") != null ? node.get("refRecipeId").toString() : "";
            parts.add(type + ":" + code + ":" + qty + ":" + ref);
            Object children = node.get("children");
            if (children instanceof List<?> childList) {
                collectFingerprint((List<Map<String, Object>>) childList, parts);
            }
        }
    }

    /** 从已持久化的树节点生成指纹 */
    private String buildFingerprintFromNodes(List<RecipeTreeNode> nodes) {
        List<String> parts = new ArrayList<>();
        for (RecipeTreeNode node : nodes) {
            String code = node.materialCode != null ? node.materialCode : "";
            String qty = node.qty != null ? node.qty.stripTrailingZeros().toPlainString() : "0";
            String type = node.nodeType != null ? node.nodeType : "MATERIAL";
            String ref = node.refRecipeId != null ? node.refRecipeId.toString() : "";
            parts.add(type + ":" + code + ":" + qty + ":" + ref);
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    /**
     * 溯源：递归展开配方树，保留层级结构（子配方也展开）
     * 返回完整配方谱系，用于追溯成品漆的所有原始原料
     */
    /**
     * v5.6：按配方 ID 溯源（订单半成品行 refRecipeId 存配方 ID）
     * 取该配方最新 RELEASED 版本后递归展开
     */
    public Map<String, Object> traceLatestReleased(Long recipeId, BigDecimal targetQty) {
        RecipeVersion v = versionRepo.findFirstByRecipeIdAndStatusOrderByIdDesc(recipeId, "RELEASED")
                .orElseThrow(() -> new IllegalArgumentException("该半成品无已发布配方版本，无法溯源"));
        return traceRecipe(v.id, targetQty);
    }

    public Map<String, Object> traceRecipe(Long versionId, BigDecimal targetQty) {        RecipeVersion v = getVersion(versionId);
        Recipe recipe = recipeRepo.findById(v.recipeId).orElse(null);

        BigDecimal batchQty = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0
                ? v.batchQty : BigDecimal.ONE;
        BigDecimal ratio = targetQty != null
                ? targetQty.divide(batchQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipeId", recipe != null ? recipe.id : null);
        result.put("recipeNo", recipe != null ? recipe.recipeNo : null);
        result.put("recipeType", recipe != null ? recipe.recipeType : null);
        result.put("productName", recipe != null ? recipe.productName : null);
        result.put("versionId", v.id);
        result.put("versionNo", v.versionNo);
        result.put("batchQty", targetQty != null ? targetQty : batchQty);
        result.put("unit", v.unit);

        List<RecipeTreeNode> nodes = treeNodeRepo.findByVersionIdOrderBySortOrder(versionId);
        java.util.Set<Long> visited0 = new java.util.HashSet<>();
        if (recipe != null && recipe.id != null) visited0.add(recipe.id);   // 根先入集，防配方直接引用自己
        result.put("materials", traceNodes(nodes, null, ratio, visited0));
        return result;
    }

    /** 递归溯源节点，保留层级 */
    private List<Map<String, Object>> traceNodes(List<RecipeTreeNode> allNodes, Long parentId, BigDecimal ratio, java.util.Set<Long> visited) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<RecipeTreeNode> children = allNodes.stream()
                .filter(n -> Objects.equals(n.parentNodeId, parentId))
                .collect(Collectors.toList());

        for (RecipeTreeNode node : children) {
            BigDecimal actualQty = node.qty.multiply(ratio).setScale(3, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeType", node.nodeType);
            item.put("materialCode", node.materialCode);
            item.put("materialName", node.materialName);
            item.put("spec", node.spec);
            item.put("unit", node.unit);
            item.put("qty", actualQty);

            if ("SUB_RECIPE".equals(node.nodeType) && node.refRecipeId != null) {
                // 子配方：递归展开其 RELEASED 版本
                Recipe subRecipe = recipeRepo.findById(node.refRecipeId).orElse(null);
                // v6.1.1 修复：visited 真正参与判重（首版只传参未使用，循环引用仍会栈溢出）
                boolean revisited = !visited.add(node.refRecipeId);
                Optional<RecipeVersion> subReleased = revisited
                        ? Optional.empty() : versionRepo.findByRecipeIdAndStatus(node.refRecipeId, "RELEASED");
                item.put("refRecipeId", node.refRecipeId);
                item.put("refRecipeNo", subRecipe != null ? subRecipe.recipeNo : null);
                item.put("refRecipeType", subRecipe != null ? subRecipe.recipeType : null);
                if (subReleased.isPresent()) {
                    RecipeVersion subV = subReleased.get();
                    BigDecimal subBatch = subV.batchQty != null && subV.batchQty.compareTo(BigDecimal.ZERO) > 0
                            ? subV.batchQty : BigDecimal.ONE;
                    BigDecimal subRatio = actualQty.divide(subBatch, 6, RoundingMode.HALF_UP);
                    List<RecipeTreeNode> subNodes = treeNodeRepo.findByVersionIdOrderBySortOrder(subV.id);
                    item.put("children", traceNodes(subNodes, null, subRatio, visited));
                }
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 展开树为扁平原料清单（递归计算用量）
     * 用于生产订单参照配方时自动生成明细
     */
    public List<Map<String, Object>> expandTree(Long versionId, BigDecimal targetQty) {
        RecipeVersion v = getVersion(versionId);
        if (!"RELEASED".equals(v.status)) {
            throw new IllegalArgumentException("只有已发布版本可展开");
        }
        // 校验配方未被禁用（禁用配方不可被单据引用）
        checkReferenceable(v.recipeId);
        BigDecimal batchQty = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0
                ? v.batchQty : BigDecimal.ONE;
        BigDecimal ratio = targetQty != null
                ? targetQty.divide(batchQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        List<RecipeTreeNode> nodes = treeNodeRepo.findByVersionIdOrderBySortOrder(versionId);
        List<Map<String, Object>> result = new ArrayList<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        if (v.recipeId != null) visited.add(v.recipeId);   // 根先入集，防配方直接引用自己
        expandNodes(nodes, null, ratio, result, visited);
        return result;
    }

    /**
     * v5.6：订单配方明细（半成品保留为一行，不展开原料）
     * 半成品为常备库存物料，生产/委外时直接领用半成品本身；半成品可通过 traceRecipe 另行溯源到原料。
     * 返回顶层节点（原料 + 半成品），半成品行带 nodeType=SUB_RECIPE 与 refRecipeId。
     */
    public List<Map<String, Object>> expandForOrder(Long versionId, BigDecimal targetQty) {
        RecipeVersion v = getVersion(versionId);
        if (!"RELEASED".equals(v.status)) {
            throw new IllegalArgumentException("只有已发布版本可展开");
        }
        // 校验配方未被禁用（禁用配方不可被单据引用）
        checkReferenceable(v.recipeId);
        BigDecimal batchQty = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0
                ? v.batchQty : BigDecimal.ONE;
        BigDecimal ratio = targetQty != null
                ? targetQty.divide(batchQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        List<RecipeTreeNode> nodes = treeNodeRepo.findByVersionIdOrderBySortOrder(versionId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (RecipeTreeNode node : nodes) {
            if (node.parentNodeId != null) continue; // 仅顶层节点（半成品不递归展开）
            // v5.72：配方不再携带油尾——存量配方版本中的油尾节点展开时跳过（油尾在下生产订单时人为添加）
            if ("OIL_TAIL".equals(node.nodeType)) continue;
            BigDecimal actualQty = node.qty.multiply(ratio).setScale(3, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeType", node.nodeType);
            item.put("materialCode", node.materialCode);
            item.put("materialName", node.materialName);
            item.put("spec", node.spec);
            item.put("unit", node.unit);
            item.put("qty", actualQty);
            item.put("remark", node.remark);
            if ("SUB_RECIPE".equals(node.nodeType)) {
                item.put("refRecipeId", node.refRecipeId);
            }
            result.add(item);
        }
        return result;
    }

    /**
     * v5.27：按产品编码匹配已发布配方版本（销售订单转生产/转委外时自动带配方用）
     * 匹配规则：配方主档 productCode = 物料编码，取最新 RELEASED 版本；无配方返回 null
     */
    public RecipeVersion findReleasedVersionByProductCode(String productCode) {
        if (productCode == null || productCode.isBlank()) return null;
        return recipeRepo.findFirstByProductCode(productCode)
                .flatMap(r -> versionRepo.findFirstByRecipeIdAndStatusOrderByIdDesc(r.id, "RELEASED"))
                .orElse(null);
    }

    /**
     * 获取所有有 RELEASED 版本的配方（供生产订单选择）
     */
    public List<Map<String, Object>> listReleased() {
        List<RecipeVersion> releasedVersions = versionRepo.findByStatus("RELEASED");
        List<Map<String, Object>> result = new ArrayList<>();
        for (RecipeVersion v : releasedVersions) {
            Recipe recipe = recipeRepo.findById(v.recipeId).orElse(null);
            if (recipe == null || !Boolean.TRUE.equals(recipe.enabled)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recipeId", recipe.id);
            item.put("recipeNo", recipe.recipeNo);
            item.put("recipeType", recipe.recipeType);
            item.put("productName", recipe.productName);
            item.put("productCode", recipe.productCode);
            item.put("category", recipe.category);
            item.put("processTemplateId", recipe.processTemplateId);
            item.put("versionId", v.id);
            item.put("versionNo", v.versionNo);
            item.put("batchQty", v.batchQty);
            item.put("unit", v.unit);
            result.add(item);
        }
        return result;
    }

    // ==================== 成本计算 ====================

    /**
     * 计算配方版本成本（按标准批量）
     * 材料成本 = 用量 × 库存加权平均单价（无库存取最近采购价）
     * 半成品成本 = 用量 × 子配方（RELEASED版本）单位成本，递归展开
     */
    public Map<String, Object> calcVersionCost(Long versionId) {
        return calcVersionCost(versionId, buildPriceMap());
    }

    /** 重载：调用方已在循环外构建一次价格映射时复用（批量场景避免每单全量加载台账/采购表三张表） */
    public Map<String, Object> calcVersionCost(Long versionId, Map<String, BigDecimal> priceMap) {
        RecipeVersion v = getVersion(versionId);
        Recipe recipe = recipeRepo.findById(v.recipeId).orElse(null);
        BigDecimal batchCost = calcTreeCost(treeNodeRepo.findByVersionIdOrderBySortOrder(versionId),
                null, BigDecimal.ONE, priceMap, new HashSet<>());
        BigDecimal batchQty = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0
                ? v.batchQty : BigDecimal.ONE;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionId", v.id);
        result.put("versionNo", v.versionNo);
        result.put("recipeId", recipe != null ? recipe.id : null);
        result.put("recipeType", recipe != null ? recipe.recipeType : null);
        result.put("productName", recipe != null ? recipe.productName : null);
        result.put("batchQty", batchQty);
        result.put("unit", v.unit);
        result.put("batchCost", batchCost.setScale(2, RoundingMode.HALF_UP));
        result.put("unitCost", batchCost.divide(batchQty, 4, RoundingMode.HALF_UP));
        return result;
    }

    /**
     * 所有已发布配方的成本清单（成品成本/半成品成本直出）
     */
    public List<Map<String, Object>> listReleasedCosts() {
        Map<String, BigDecimal> priceMap = buildPriceMap();
        List<Map<String, Object>> result = new ArrayList<>();
        for (RecipeVersion v : versionRepo.findByStatus("RELEASED")) {
            Recipe recipe = recipeRepo.findById(v.recipeId).orElse(null);
            if (recipe == null) continue;
            BigDecimal batchCost = calcTreeCost(treeNodeRepo.findByVersionIdOrderBySortOrder(v.id),
                    null, BigDecimal.ONE, priceMap, new HashSet<>());
            BigDecimal batchQty = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0
                    ? v.batchQty : BigDecimal.ONE;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recipeId", recipe.id);
            item.put("recipeNo", recipe.recipeNo);
            item.put("recipeType", recipe.recipeType);
            item.put("productName", recipe.productName);
            item.put("versionId", v.id);
            item.put("versionNo", v.versionNo);
            item.put("batchQty", batchQty);
            item.put("unit", v.unit);
            // v5.81 包装成本：⌈批量÷每件容量⌉×单价（无容量按整件 1 件计）；无绑定包装标准为 0
            BigDecimal packCost = packagingCost(recipe.packagingStandardId, batchQty);
            BigDecimal total = batchCost.add(packCost);
            item.put("materialCost", batchCost.setScale(2, RoundingMode.HALF_UP));
            item.put("packagingCost", packCost.setScale(2, RoundingMode.HALF_UP));
            item.put("packagingName", packagingName(recipe.packagingStandardId));
            item.put("batchCost", total.setScale(2, RoundingMode.HALF_UP));
            item.put("unitCost", total.divide(batchQty, 4, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }

    /** v5.82 包装成本：⌈批量÷套容量⌉×套单价（套单价=组合明细 Σ qty×单价，无明细回退单件价） */
    private BigDecimal packagingCost(Long packagingStandardId, BigDecimal batchQty) {
        if (packagingStandardId == null) return BigDecimal.ZERO;
        var ps = packagingRepo.findById(packagingStandardId).orElse(null);
        if (ps == null) return BigDecimal.ZERO;
        var items = packagingItemRepo.findByPackagingIdOrderBySortOrderAscIdAsc(packagingStandardId);
        BigDecimal unitCost = items.isEmpty() ? ps.unitPrice
                : items.stream().map(i -> i.unitPrice.multiply(i.qty)).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pieces = 1;
        if (ps.capacityKg != null && ps.capacityKg.compareTo(BigDecimal.ZERO) > 0
                && batchQty != null && batchQty.compareTo(BigDecimal.ZERO) > 0) {
            pieces = batchQty.divide(ps.capacityKg, 0, RoundingMode.CEILING).longValue();
        }
        return unitCost.multiply(BigDecimal.valueOf(pieces));
    }

    private String packagingName(Long packagingStandardId) {
        if (packagingStandardId == null) return null;
        return packagingRepo.findById(packagingStandardId).map(p -> p.name).orElse(null);
    }

    /**
     * 递归计算树成本：原料按单价×用量，子配方按单位成本×用量
     * visited 用于防止配方循环引用导致死循环
     */
    private BigDecimal calcTreeCost(List<RecipeTreeNode> allNodes, Long parentId,
                                    BigDecimal ratio, Map<String, BigDecimal> priceMap, Set<Long> visited) {
        BigDecimal total = BigDecimal.ZERO;
        List<RecipeTreeNode> children = allNodes.stream()
                .filter(n -> Objects.equals(n.parentNodeId, parentId))
                .collect(Collectors.toList());

        for (RecipeTreeNode node : children) {
            BigDecimal actualQty = node.qty.multiply(ratio);
            if ("SUB_RECIPE".equals(node.nodeType) && node.refRecipeId != null && !visited.contains(node.refRecipeId)) {
                BigDecimal unitCost = recipeUnitCost(node.refRecipeId, priceMap, visited);
                total = total.add(actualQty.multiply(unitCost));
            } else if (node.materialCode != null) {
                BigDecimal price = priceMap.getOrDefault(node.materialCode, BigDecimal.ZERO);
                total = total.add(actualQty.multiply(price));
            }
        }
        return total;
    }

    /** 配方（RELEASED版本）单位成本 = 批量成本 ÷ 标准批量 */
    private BigDecimal recipeUnitCost(Long recipeId, Map<String, BigDecimal> priceMap, Set<Long> visited) {
        Optional<RecipeVersion> released = versionRepo.findByRecipeIdAndStatus(recipeId, "RELEASED");
        if (released.isEmpty()) return BigDecimal.ZERO;
        RecipeVersion v = released.get();
        Set<Long> next = new HashSet<>(visited);
        next.add(recipeId);
        BigDecimal batchCost = calcTreeCost(treeNodeRepo.findByVersionIdOrderBySortOrder(v.id),
                null, BigDecimal.ONE, priceMap, next);
        BigDecimal batchQty = v.batchQty != null && v.batchQty.compareTo(BigDecimal.ZERO) > 0
                ? v.batchQty : BigDecimal.ONE;
        return batchCost.divide(batchQty, 6, RoundingMode.HALF_UP);
    }

    /**
     * 物料单价映射：优先库存加权平均价（Σ金额 ÷ Σ数量），
     * 无库存时回退最近一次采购单价（原料采购/成品采购）
     */
    /** 物料价格映射（供前端选物料时实时显示单价，与配方树成本同源） */
    public Map<String, BigDecimal> getMaterialPriceMap() {
        return buildPriceMap();
    }

    private Map<String, BigDecimal> buildPriceMap() {
        Map<String, BigDecimal> priceMap = new HashMap<>();
        // v6.3 第四批：库存加权均价改 SQL 聚合（原 Java 全表加载逐行累加——台账到几十万行时配方页明显变慢）；
        // 排除隔离行（REJECT/TAILING/EXPIRED 不可用于生产，v6.1.6 口径），amount 为空按 qty×unit_price 兜底
        for (var row : jdbc.queryForList("""
                SELECT material_code AS code, SUM(qty) AS q,
                       SUM(CASE WHEN amount IS NOT NULL THEN amount ELSE qty * COALESCE(unit_price, 0) END) AS a
                FROM inventory_ledger
                WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED'))
                GROUP BY material_code
                """)) {
            BigDecimal qty = toBigDecimal(row.get("q"));
            BigDecimal amount = toBigDecimal(row.get("a"));
            if (qty.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(BigDecimal.ZERO) > 0) {
                priceMap.put(String.valueOf(row.get("code")), amount.divide(qty, 4, RoundingMode.HALF_UP));
            }
        }
        // 回退：最近原料采购单价（v6.1.6：按创建时间倒序取最新——原 findAll 无序，取到哪单算哪单）
        for (RawMaterialPurchase p : rawPurchaseRepo.findAllByOrderByCreateTimeDesc()) {
            if (p.materialCode == null || p.unitPrice == null || priceMap.containsKey(p.materialCode)) continue;
            priceMap.put(p.materialCode, p.unitPrice);
        }
        // 回退：最近成品采购单价（v6.1.6：同上按时间倒序）
        for (FinishedProductPurchase p : finishedPurchaseRepo.findAllByOrderByCreateTimeDesc()) {
            if (p.materialCode == null || p.unitPrice == null || priceMap.containsKey(p.materialCode)) continue;
            priceMap.put(p.materialCode, p.unitPrice);
        }
        return priceMap;
    }

    // ==================== 内部方法 ====================

    /** 将扁平节点列表构建为嵌套树（附带单价与成本直出字段） */
    private List<Map<String, Object>> buildTree(List<RecipeTreeNode> allNodes, Long parentId,
                                                Map<String, BigDecimal> priceMap, Set<Long> visited) {
        return allNodes.stream()
                .filter(n -> Objects.equals(n.parentNodeId, parentId))
                .map(n -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", n.id);
                    map.put("nodeType", n.nodeType);
                    map.put("materialCode", n.materialCode);
                    map.put("materialName", n.materialName);
                    map.put("spec", n.spec);
                    map.put("category", n.category);
                    map.put("subCategory", n.subCategory);
                    map.put("unit", n.unit);
                    map.put("qty", n.qty);
                    map.put("refRecipeId", n.refRecipeId);
                    map.put("sortOrder", n.sortOrder);
                    map.put("remark", n.remark);

                    // 成本直出：原料=库存单价×用量，半成品=子配方单位成本×用量
                    if ("SUB_RECIPE".equals(n.nodeType) && n.refRecipeId != null && !visited.contains(n.refRecipeId)) {
                        BigDecimal unitCost = recipeUnitCost(n.refRecipeId, priceMap, visited);
                        map.put("unitPrice", unitCost.setScale(4, RoundingMode.HALF_UP));
                        map.put("cost", n.qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP));
                    } else if (n.materialCode != null) {
                        BigDecimal price = priceMap.getOrDefault(n.materialCode, BigDecimal.ZERO);
                        map.put("unitPrice", price.setScale(4, RoundingMode.HALF_UP));
                        map.put("cost", n.qty.multiply(price).setScale(2, RoundingMode.HALF_UP));
                    }

                    List<Map<String, Object>> children = buildTree(allNodes, n.id, priceMap, visited);
                    if (!children.isEmpty()) {
                        map.put("children", children);
                    }
                    return map;
                })
                .collect(Collectors.toList());
    }

    /** 递归保存树节点 */
    private void saveTreeNodes(List<Map<String, Object>> nodes, Long versionId, Long parentNodeId) {
        if (nodes == null) return;
        int order = 0;
        for (Map<String, Object> nodeData : nodes) {
            RecipeTreeNode node = new RecipeTreeNode();
            node.versionId = versionId;
            node.parentNodeId = parentNodeId;
            node.nodeType = (String) nodeData.get("nodeType");
            node.materialCode = (String) nodeData.get("materialCode");
            node.materialName = (String) nodeData.get("materialName");
            node.spec = (String) nodeData.get("spec");
            node.category = (String) nodeData.get("category");
            node.subCategory = (String) nodeData.get("subCategory");
            node.unit = "kg"; // v4.9：所有物料单位统一为公斤
            node.qty = toBigDecimal(nodeData.get("qty"));
            node.refRecipeId = toLong(nodeData.get("refRecipeId"));
            node.sortOrder = order++;
            node.remark = (String) nodeData.get("remark");
            treeNodeRepo.save(node);

            // 递归保存子节点
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) nodeData.get("children");
            if (children != null && !children.isEmpty()) {
                saveTreeNodes(children, versionId, node.id);
            }
        }
    }

    /** 复制树（从源版本到目标版本） */
    private void copyTree(Long sourceVersionId, Long targetVersionId) {
        List<RecipeTreeNode> sourceNodes = treeNodeRepo.findByVersionIdOrderBySortOrder(sourceVersionId);
        if (sourceNodes.isEmpty()) return;

        // 建立旧ID → 新ID 的映射
        Map<Long, Long> idMapping = new HashMap<>();
        // 先保存所有节点（不带parentNodeId）
        for (RecipeTreeNode src : sourceNodes) {
            RecipeTreeNode copy = new RecipeTreeNode();
            copy.versionId = targetVersionId;
            copy.nodeType = src.nodeType;
            copy.materialCode = src.materialCode;
            copy.materialName = src.materialName;
            copy.spec = src.spec;
            copy.unit = src.unit;
            copy.qty = src.qty;
            copy.refRecipeId = src.refRecipeId;
            copy.sortOrder = src.sortOrder;
            copy.remark = src.remark;
            treeNodeRepo.save(copy);
            idMapping.put(src.id, copy.id);
        }
        // 再更新 parentNodeId
        for (RecipeTreeNode src : sourceNodes) {
            if (src.parentNodeId != null) {
                Long newId = idMapping.get(src.id);
                Long newParentId = idMapping.get(src.parentNodeId);
                RecipeTreeNode copy = treeNodeRepo.findById(newId).orElse(null);
                if (copy != null && newParentId != null) {
                    copy.parentNodeId = newParentId;
                    treeNodeRepo.save(copy);
                }
            }
        }
    }

    /** 递归展开节点，计算实际用量 */
    private void expandNodes(List<RecipeTreeNode> allNodes, Long parentId,
                             BigDecimal ratio, List<Map<String, Object>> result, java.util.Set<Long> visited) {
        // v6.1（中#7）：visited 防循环引用（与 calcTreeCost 同保护）
        List<RecipeTreeNode> children = allNodes.stream()
                .filter(n -> Objects.equals(n.parentNodeId, parentId))
                .collect(Collectors.toList());

        for (RecipeTreeNode node : children) {
            BigDecimal actualQty = node.qty.multiply(ratio).setScale(3, RoundingMode.HALF_UP);

            if ("SUB_RECIPE".equals(node.nodeType) && node.refRecipeId != null) {
                // v6.1.1 修复：visited 真正参与判重（首版只传参未使用，循环引用仍会栈溢出）
                if (!visited.add(node.refRecipeId)) continue;
                // 找到子配方的 RELEASED 版本，递归展开
                Optional<RecipeVersion> subReleased = versionRepo.findByRecipeIdAndStatus(node.refRecipeId, "RELEASED");
                if (subReleased.isPresent()) {
                    RecipeVersion subVersion = subReleased.get();
                    BigDecimal subBatch = subVersion.batchQty != null && subVersion.batchQty.compareTo(BigDecimal.ZERO) > 0
                            ? subVersion.batchQty : BigDecimal.ONE;
                    BigDecimal subRatio = actualQty.divide(subBatch, 6, RoundingMode.HALF_UP);
                    List<RecipeTreeNode> subNodes = treeNodeRepo.findByVersionIdOrderBySortOrder(subVersion.id);
                    expandNodes(subNodes, null, subRatio, result, visited);
                }
            } else {
                // 原料节点，直接输出
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materialCode", node.materialCode);
                item.put("materialName", node.materialName);
                item.put("spec", node.spec);
                item.put("unit", node.unit);
                item.put("qty", actualQty);
                item.put("remark", node.remark);
                result.add(item);
            }
        }
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long) return (Long) val;
        return Long.valueOf(val.toString());
    }
    /** v6.3 变更日志 */
    private void logChange(Long recipeId, String recipeNo, String productName, Long versionId, String versionNo,
                           String action, String detail, String operator) {
        try {
            com.pengyuan.pims.entity.RecipeChangeLog lg = new com.pengyuan.pims.entity.RecipeChangeLog();
            lg.recipeId = recipeId;
            lg.recipeNo = recipeNo;
            lg.productName = productName;
            lg.versionId = versionId;
            lg.versionNo = versionNo;
            lg.action = action;
            lg.detail = detail;
            lg.operator = operator == null || operator.isBlank() ? "系统" : operator;
            changeLogRepo.save(lg);
        } catch (Exception e) {
            log.warn("配方变更日志写入失败（不影响业务）: {}", e.getMessage());
        }
    }

    public java.util.List<com.pengyuan.pims.entity.RecipeChangeLog> listChanges(Long recipeId) {
        return changeLogRepo.findByRecipeIdOrderByCreateTimeDescIdDesc(recipeId);
    }

}

