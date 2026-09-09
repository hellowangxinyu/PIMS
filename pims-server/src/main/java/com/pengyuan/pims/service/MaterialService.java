package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.repository.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 物料主档：CRUD、编码规则校验（v5.65 属性语义码按类分流）、禁用拦截（禁用物料不可采购/销售）。
 * 编码新规：半成品 B 7 位、成品 C 8 位分组流水，禁 I/L/O 易混字符。
 */
@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final MaterialRepository repo;
    private final CodingRuleService codingRuleService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public MaterialService(MaterialRepository repo, CodingRuleService codingRuleService,
                           org.springframework.jdbc.core.JdbcTemplate jdbc, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.repo = repo;
        this.codingRuleService = codingRuleService;
        this.jdbc = jdbc;
    }

    public List<Material> listAll() { return repo.findAll(); }
    public Optional<Material> getById(Long id) { return repo.findById(id); }
    public Optional<Material> getByCode(String code) { return repo.findByCode(code); }
    public List<Material> search(String keyword) { return repo.findByCodeContainingOrNameContaining(keyword, keyword); }
    public List<Material> listByCategory(String category) { return repo.findByCategory(category); }

    /**
     * v5.1：校验平替物料配置——仅原材料（大类 A/P/F/R/S）之间可平替；不能替代自身；物料必须存在
     */
    private void validateAlternatives(Material m) {
        if (m.alternativeCodes == null || m.alternativeCodes.isBlank()) return;
        // 当前物料必须是原材料
        if (m.category == null || !"APFRS".contains(m.category)) {
            throw new IllegalArgumentException("仅原材料（大类 A/P/F/R/S）可配置平替物料，当前大类为 " + (m.category == null ? "空" : m.category));
        }
        for (String raw : m.alternativeCodes.split(",")) {
            String code = raw.trim();
            if (code.isEmpty()) continue;
            if (code.equals(m.code)) {
                throw new IllegalArgumentException("平替物料不能是物料自身: " + code);
            }
            Material alt = repo.findByCode(code).orElse(null);
            if (alt == null) {
                throw new IllegalArgumentException("平替物料不存在: " + code);
            }
            if (alt.category == null || !"APFRS".contains(alt.category)) {
                throw new IllegalArgumentException("平替物料仅限原材料（大类 A/P/F/R/S）: " + code + "（" + alt.name + "）");
            }
        }
    }

    /** 解析逗号分隔的平替编码集合（去空、去重、排序，保证存储稳定） */
    private java.util.Set<String> parseCodes(String codes) {
        java.util.Set<String> set = new java.util.TreeSet<>();
        if (codes != null && !codes.isBlank()) {
            for (String c : codes.split(",")) {
                String t = c.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        return set;
    }

    /**
     * v5.2：平替关系为**双向**——A 配置平替 B 后，B 自动获得平替 A（配方中可来回切换）。
     * 增量同步：新增的平替物料反向加入自身；移除的平替物料反向移除自身；去重幂等。
     * @param m        当前物料（已保存新配置）
     * @param oldCodes 保存前的平替配置（新增/编辑场景）；新建传 null
     */
    private void syncAlternativeBacklinks(Material m, String oldCodes) {
        java.util.Set<String> oldSet = parseCodes(oldCodes);
        java.util.Set<String> newSet = parseCodes(m.alternativeCodes);
        // 新增的平替物料：反向加入当前物料
        for (String code : newSet) {
            if (oldSet.contains(code)) continue;
            Material alt = repo.findByCode(code).orElse(null);
            if (alt == null) continue;
            java.util.Set<String> altSet = parseCodes(alt.alternativeCodes);
            if (altSet.add(m.code)) {
                alt.alternativeCodes = String.join(",", altSet);
                repo.save(alt);
                log.info("平替双向同步: {} 反向加入平替 {}", alt.code, m.code);
            }
        }
        // 移除的平替物料：反向移除当前物料
        for (String code : oldSet) {
            if (newSet.contains(code)) continue;
            Material alt = repo.findByCode(code).orElse(null);
            if (alt == null) continue;
            java.util.Set<String> altSet = parseCodes(alt.alternativeCodes);
            if (altSet.remove(m.code)) {
                alt.alternativeCodes = altSet.isEmpty() ? "" : String.join(",", altSet);
                repo.save(alt);
                log.info("平替双向同步: {} 反向移除平替 {}", alt.code, m.code);
            }
        }
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    /** v5.40 编码防呆 + v5.65 新体系：按大类分流格式——原料类 6 位（小类+序号）、半成品 B 7 位（小类+主材+序号）、成品 C 8 位（小类+主材+色系+序号）；首位字母必须等于大类；public 供 Excel 导入复用 */
    public void validateCodeFormat(String code, String category) {
        if (code == null) throw new IllegalArgumentException("物料编码不能为空");
        String fmtErr = "C".equals(category)
                ? "成品编码须为 9 位（漆型 2 位 + 主材 1 位 + 色系 1 位 + 序号 5 位，如 CWTH00010）: "
                : "B".equals(category)
                ? "半成品编码须为 8 位（小类 2 位 + 主材 1 位 + 序号 5 位，如 BWFT00010）: "
                : "物料编码须为 6 位（小类码 2 位字母 + 4 位序号，如 AC0001）: ";
        int len = "C".equals(category) ? 9 : ("B".equals(category) ? 8 : 6);
        int seqLen = len == 6 ? 4 : 5;   // v5.89.1 原料仍 4 位序号；B/C 5 位（v5.89 曾误把原料也改 5 位）
        if (code.length() != len || !code.substring(0, 2).matches("[A-Z]{2}")
                || !code.substring(len - seqLen).matches("[0-9]{" + seqLen + "}")
                || ("C".equals(category) && !code.substring(2, len - seqLen).matches("[A-Z]{2}"))
                || ("B".equals(category) && !code.substring(2, len - seqLen).matches("[A-Z]{1}"))) {
            throw new IllegalArgumentException(fmtErr + code);
        }
        if (category != null && !category.isBlank() && !code.substring(0, 1).equals(category)) {
            throw new IllegalArgumentException(String.format(
                    "编码首位须与物料大类一致（%s 类编码应以 %s 开头）: %s", category, category, code));
        }
        // v5.86.1 字母段防呆（原料/半成品/成品统一）：禁 I/L/O/Z（与 1/0/2 手写极易混）
        String letters = code.substring(0, len - seqLen);
        for (char c : letters.toCharArray()) {
            if ("ILOZ".indexOf(c) >= 0)
                throw new IllegalArgumentException("编码字母段不能使用易混字符 " + c + "（I/L/O/Z 与 1/0/2 手写极易混）: " + code);
        }
    }

    public Material create(Material m) {
        return writeQueue.executeTx(() -> {
            // v5.42.2：编码在保存成功那一刻才取号（保存时自动生成）——填单中途放弃/取消不占号，
            // 全局连续号永不出空洞；此前"选完小类即预生成"会浪费放弃的号（用户要求：未保存编码可复用，不跳号）
            if (m.code == null || m.code.isBlank()) {
                // v5.65 取号分流：成品=漆型+主材+色系+分组流水（8位）、半成品=小类+主材+分组流水（7位）、原料=原全局连续体系（6位）
                if ("C".equals(m.category)) {
                    m.code = codingRuleService.generateProductCode(m.subCategory, m.mainMaterial, m.colorSeries);
                } else if ("B".equals(m.category)) {
                    m.code = codingRuleService.generateSemiCode(m.subCategory, m.mainMaterial);
                } else {
                    m.code = codingRuleService.generateCode(m.subCategory);
                }
            }
            // v5.40 防呆校验：新码 6 位格式（小类码2位+序号4位），且小类码首字母必须=物料大类字母
            validateCodeFormat(m.code, m.category);
            // v5.16：所有字段必填（后端兜底）
            if (m.name == null || m.name.isBlank()) throw new IllegalArgumentException("品名不能为空");
            if (m.brand == null || m.brand.isBlank()) throw new IllegalArgumentException("牌号不能为空");
            if (m.category == null || m.category.isBlank()) throw new IllegalArgumentException("大类不能为空");
            if (m.subCategory == null || m.subCategory.isBlank()) throw new IllegalArgumentException("小类不能为空");
            if (m.shelfLifeDays == null) throw new IllegalArgumentException("质保期不能为空（无限制填 0）");
            // v5.16：成品默认品牌归属=芃远（自产），仅外购（选成品供应商）时才为供应商名称
            if ("C".equals(m.category) && (m.brandOwner == null || m.brandOwner.isBlank())) {
                m.brandOwner = "芃远";
            }
            // v5.17：成品主材必填（聚酯/氟碳/环氧/丙烯酸）
            if ("C".equals(m.category) && (m.mainMaterial == null || m.mainMaterial.isBlank())) {
                throw new IllegalArgumentException("成品请选择主材（聚酯/氟碳/环氧/丙烯酸）");
            }
            if (repo.existsByCode(m.code)) {
                throw new IllegalArgumentException("物料编码 " + m.code + " 已存在，不允许重复录入");
            }
            if (repo.existsByNameAndBrandAndCategoryAndSubCategory(m.name, m.brand, m.category, m.subCategory)) {
                throw new IllegalArgumentException("已存在完全相同的物料（品名+牌号+分类一致），不允许重复录入");
            }
            // 成品物料默认品牌归属为「芃远」（自产），外购成品可由前端传入成品供应商名称
            if ("C".equals(m.category) && (m.brandOwner == null || m.brandOwner.isBlank())) {
                m.brandOwner = "芃远";
            }
            validateAlternatives(m);
            Material saved = repo.save(m);
            syncAlternativeBacklinks(saved, null);
            return saved;
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public Material update(Long id, Material m) {
        return writeQueue.executeTx(() -> {
            Material exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("物料不存在"));
            // v5.91：编码修改需单独权限 material:code-edit（未授权任何人不可改码）；新码须过格式+唯一校验
            if (m.code != null && !m.code.isBlank() && !m.code.equals(exist.code)) {
                if (!cn.dev33.satoken.stp.StpUtil.hasPermission("material:code-edit")) {
                    throw new IllegalArgumentException("物料编码修改需要「修改编码」权限（material:code-edit），请联系管理员授权");
                }
                if (repo.existsByCode(m.code)) throw new IllegalArgumentException("编码 " + m.code + " 已被其他物料使用");
                validateCodeFormat(m.code, exist.category);
                codingRuleService.validateCustomCode(m.code, exist.category);   // v5.92 有权限也必须符合全部编码规则
                exist.code = m.code;
            }
            String oldCodes = exist.alternativeCodes;
            // 牌号必填（与新建一致）：编辑/恢复启用均不允许置空
            if (m.brand == null || m.brand.isBlank()) {
                throw new IllegalArgumentException("牌号不能为空");
            }
            exist.name = m.name;
            exist.brand = m.brand;
            exist.category = m.category;
            exist.subCategory = m.subCategory;
            exist.shelfLifeDays = m.shelfLifeDays;
            exist.brandOwner = m.brandOwner;
            exist.alternativeCodes = m.alternativeCodes;
            exist.updateTime = java.time.LocalDateTime.now();
            validateAlternatives(exist);
            Material saved = repo.save(exist);
            syncAlternativeBacklinks(saved, oldCodes);
            return saved;
    
        });
    }

    /**
     * v5.43.1 禁用/启用物料：被单据引用删不掉的物料走禁用——禁用后不可再被新单据选用（前端下拉过滤），
     * 不可采购/销售下单（后端硬校验）；已有库存的出入库不受限（存量清理通道：正常出库消化或其他出库-报废）。
     */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void setEnabled(Long id, boolean enabled) {
        writeQueue.executeTx(() -> {
            Material m = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("物料不存在"));
            m.enabled = enabled;
            m.updateTime = java.time.LocalDateTime.now();
            repo.save(m);
    
        });
    }

    /** v5.43.1 物料可用性校验（采购/销售等新单据创建时硬校验，防绕过前端过滤） */
    public void assertUsable(String code, String scene) {
        if (code == null || code.isBlank()) return;
        Material m = repo.findByCode(code).orElse(null);
        if (m != null && !Boolean.TRUE.equals(m.enabled)) {
            throw new IllegalArgumentException(String.format("物料 %s（%s）已禁用，不能%s；如需恢复请先在物料管理中启用", m.code, m.name, scene));
        }
    }

    /** 引用物料编码的单据表（与编码迁移器同源清单：台账/流水/配方/质检/出入库/采购销售/汇总/盘库） */
    private static final String[] REF_TABLES = {
            "finished_product_purchase", "inventory_ledger", "inventory_movement", "inventory_movement_archive",
            "other_inbound", "other_outbound", "outsource_material_consume", "outsource_material_outbound",
            "outsource_order_item", "production_order_item", "production_outbound", "purchase_arrival",
            "purchase_order_item", "quality_inspection", "raw_material_purchase", "recipe_tree_node",
            "return_order", "sales_order_item", "sales_outbound", "stat_inventory_daily",
            "stat_material_usage", "stock_check"
    };

    /**
     * v5.43 删除物料（用户规则）：
     * ① 被任何单据引用或有台账记录 → 禁止删除（提示占用来源）；
     * ② 确实无引用无库存（没用的物料）→ 真删除，且编码数字回收入池——新建物料时优先复用释放的编码。
     */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void delete(Long id) {
        writeQueue.executeTx(() -> {
            Material m = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("物料不存在"));
            // 引用检查：23 张单据表 + 台账（台账行本身即出入库痕迹，无论剩余数量）
            for (String table : REF_TABLES) {
                Integer cnt = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE material_code = ?", Integer.class, m.code);
                if (cnt != null && cnt > 0) {
                    throw new IllegalArgumentException(String.format(
                            "物料 %s（%s）已被业务数据引用（%s 有 %d 条记录），不能删除；如需停用请使用停用功能", m.code, m.name, table, cnt));
                }
            }
            // 平替反向链接清理（被删物料挂在别的物料平替列表里时解除）
            clearAlternativeBacklinks(m.code);
            // 真删除 + 编码回池（数字位回收，新建任何类别物料时优先复用）
            String code = m.code;
            repo.delete(m);
            if (code != null && code.length() == 6) {
                try {
                    jdbc.update("INSERT OR IGNORE INTO released_code_seq (seq) VALUES (?)", Integer.valueOf(code.substring(2)));
                    log.info("物料删除: {} {} — 编码数字 {} 已回收待复用", code, m.name, code.substring(2));
                } catch (Exception e) {
                    log.warn("编码回收失败（不影响删除）: {}", e.getMessage());
                }
            }
    
        });
    }

    /** 清理其他物料平替列表里对本编码的引用（平替是软引用，不算"被单据使用"） */
    private void clearAlternativeBacklinks(String code) {
        try {
            for (var other : repo.findAll()) {
                if (other.alternativeCodes == null || !other.alternativeCodes.contains(code)) continue;
                java.util.List<String> kept = java.util.Arrays.stream(other.alternativeCodes.split(","))
                        .map(String::trim).filter(c -> !c.isEmpty() && !c.equals(code))
                        .collect(java.util.stream.Collectors.toList());
                other.alternativeCodes = String.join(",", kept);
                repo.save(other);
            }
        } catch (Exception e) { log.warn("平替引用清理失败: {}", e.getMessage()); }
    }
}
