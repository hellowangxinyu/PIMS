package com.pengyuan.pims.config;

import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import com.pengyuan.pims.service.CodingRuleService;
import com.pengyuan.pims.service.DictItemService;
import com.pengyuan.pims.service.IsolatedZoneService;
import com.pengyuan.pims.service.RoleService;
import com.pengyuan.pims.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 系统启动数据初始化（幂等）：字典种子、编码规则、默认用户/角色、科目与业务映射、细粒度权限同步等。
 * 原则：只在对应数据为空时写入，重启不重复；新增种子请走独立 Initializer（参考 TaxSchemaInitializer）。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final WarehouseRepository warehouseRepo;
    private final IsolatedZoneService isolatedZoneService;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final DictItemService dictService;
    private final CodingRuleService codingRuleService;
    private final InventoryLedgerRepository ledgerRepo;
    private final RawMaterialPurchaseRepository rawRepo;
    private final FinishedProductPurchaseRepository finishedRepo;

    public DataInitializer(WarehouseRepository warehouseRepo, UserRepository userRepo,
                           PasswordEncoder passwordEncoder, RoleService roleService,
                           DictItemService dictService, CodingRuleService codingRuleService,
                           InventoryLedgerRepository ledgerRepo,
                           RawMaterialPurchaseRepository rawRepo,
                           FinishedProductPurchaseRepository finishedRepo,
                           IsolatedZoneService isolatedZoneService,
                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.warehouseRepo = warehouseRepo;
        this.isolatedZoneService = isolatedZoneService;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.dictService = dictService;
        this.codingRuleService = codingRuleService;
        this.ledgerRepo = ledgerRepo;
        this.rawRepo = rawRepo;
        this.finishedRepo = finishedRepo;
    }

    @Override
    public void run(String... args) {
        initWarehouses();
        permSyncOnce("meeting", this::syncMeetingPermissions);
        permSyncOnce("crm", this::syncCrmPermissions);
        permSyncOnce("sampleComplaint", this::syncSampleComplaintPermissions);
        permSyncOnce("strace", this::syncStracePermissions);
        removeDeprecatedPuRule();
        // v5.58：隔离分库补建抽到 IsolatedZoneService（新增/启用仓库时也会即时联动补建）
        isolatedZoneService.ensureAll();
        initRoles();
        permSyncOnce("recipe", this::syncRecipePermissions);
        permSyncOnce("task", this::syncTaskPermissions);
        initAdminUser();
        initDictItems();
        initCodingRules();
        fixInventoryMaterialCode();
    }

    private void initWarehouses() {
        if (warehouseRepo.count() > 0) return;
        Warehouse own = new Warehouse(); own.code = "WH-OWN-PY"; own.name = "芃远自有仓"; own.warehouseType = "OWN_RAW"; own.address = "广东芃远"; warehouseRepo.save(own);
        Warehouse bf = new Warehouse(); bf.code = "WH-OUT-BF-SD"; bf.name = "邦弗特山东委外仓"; bf.warehouseType = "OUT_RAW"; bf.processorId = "BENEFULL_SD"; bf.processorName = "邦弗特（山东）"; warehouseRepo.save(bf);
        Warehouse sr = new Warehouse(); sr.code = "WH-OUT-SR-GD"; sr.name = "昇润广东委外仓"; sr.warehouseType = "OUT_RAW"; sr.processorId = "SHENGRUN_GD"; sr.processorName = "昇润（广东）"; warehouseRepo.save(sr);
        log.info("种子数据：已初始化 3 个仓库");
    }

    /**
     * v6.1 权限复活治理：历史模块的权限同步只在首启跑一次（sys_config 记标记）。
     * 此前每次重启都按"是否拥有关联权限"自动补发，管理员在权限矩阵里收回的权限重启后会被复活。
     * 新角色/新模块的授权统一走权限矩阵手动配置，不再靠启动时猜测补发。
     */
    private void permSyncOnce(String module, Runnable task) {
        try {
            String key = "perm.sync.once." + module;
            var exists = jdbcTemplate.queryForList("SELECT key_name FROM sys_config WHERE key_name = ?", key);
            if (!exists.isEmpty()) return;
            task.run();
            jdbcTemplate.update("INSERT INTO sys_config (key_name, value_text) VALUES (?, 'done')", key);
        } catch (Exception e) { log.warn("权限同步({})失败: {}", module, e.getMessage()); }
    }

    /** v5.59：供应商质量追溯权限同步——有 qc:read（品控）或 purchase:read（采购）的角色自动补 strace:read/write */
    private void syncStracePermissions() {
        try {
            for (var role : roleService.listRoles()) {
                var perms = roleService.getRolePermissions(role.code);
                if (perms.isEmpty()) continue;
                boolean related = perms.contains("qc:read") || perms.contains("purchase:read");
                if (!related || perms.contains("strace:read")) continue;
                perms.add("strace:read");
                perms.add("strace:write");
                roleService.setRolePermissions(role.code, perms);
                log.info("v5.59 权限同步: 角色 {} 已补供应商质量追溯权限", role.code);
            }
        } catch (Exception e) { log.warn("供应商质量追溯权限同步失败: {}", e.getMessage()); }
    }

    /** v5.53：打样+投诉权限同步——有 sales:read 的角色补 sample+complaint；有 qc:read 的角色补 complaint（质量岗处理投诉） */
    private void syncSampleComplaintPermissions() {
        try {
            for (var role : roleService.listRoles()) {
                var perms = roleService.getRolePermissions(role.code);
                if (perms.isEmpty()) continue;
                boolean sales = perms.contains("sales:read");
                boolean qc = perms.contains("qc:read");
                if (!sales && !qc) continue;
                boolean changed = false;
                if (sales && !perms.contains("sample:read")) { perms.add("sample:read"); perms.add("sample:write"); changed = true; }
                if (!perms.contains("complaint:read")) { perms.add("complaint:read"); perms.add("complaint:write"); changed = true; }
                if (changed) {
                    roleService.setRolePermissions(role.code, perms);
                    log.info("v5.53 权限同步: 角色 {} 已补打样/投诉权限", role.code);
                }
            }
        } catch (Exception e) { log.warn("打样/投诉权限同步失败: {}", e.getMessage()); }
    }

    /** v5.50：CRM 权限同步——现有角色中拥有 sales:read 的（销售相关，含 SS）自动补 crm:read/write */
    private void syncCrmPermissions() {
        try {
            for (var role : roleService.listRoles()) {
                var perms = roleService.getRolePermissions(role.code);
                if (perms.isEmpty() || !perms.contains("sales:read")) continue;
                if (perms.contains("crm:read")) continue;
                perms.add("crm:read");
                perms.add("crm:write");
                roleService.setRolePermissions(role.code, perms);
                log.info("CRM 权限同步: 角色 {} 已补 crm:read/write", role.code);
            }
        } catch (Exception e) { log.warn("CRM 权限同步失败: {}", e.getMessage()); }
    }

    /** v5.44：周度会议权限同步——现有角色中拥有 finance:read 的（管理层，含生产库 admin 的 SS 角色）自动补 meeting:read/write */
    private void syncMeetingPermissions() {
        try {
            for (var role : roleService.listRoles()) {
                var perms = roleService.getRolePermissions(role.code);
                if (perms.isEmpty() || !perms.contains("finance:read")) continue;
                if (perms.contains("meeting:read")) continue;
                perms.add("meeting:read");
                perms.add("meeting:write");
                roleService.setRolePermissions(role.code, perms);
                log.info("周度会议权限同步: 角色 {} 已补 meeting:read/write", role.code);
            }
        } catch (Exception e) { log.warn("周度会议权限同步失败: {}", e.getMessage()); }
    }

    /** v5.42.1 幂等清理：聚酯=聚氨酯（用户口径），删除空的「聚氨酯树脂 RU」小类规则与字典项（存量库） */
    private void removeDeprecatedPuRule() {
        try {
            int rules = jdbcUpdateSafe("DELETE FROM coding_rule WHERE sub_category_code = 'RU' AND (current_seq IS NULL OR current_seq = 0)");
            int dicts = jdbcUpdateSafe("DELETE FROM dict_item WHERE type = 'material_sub_category' AND value = 'RU'");
            if (rules + dicts > 0) log.info("口径统一（聚酯=聚氨酯）：已删除空的聚氨酯树脂 RU 规则 {} 条、字典 {} 条", rules, dicts);
        } catch (Exception e) { log.warn("RU 清理失败: {}", e.getMessage()); }
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private int jdbcUpdateSafe(String sql) {
        try { return jdbcTemplate.update(sql); } catch (Exception e) { return 0; }
    }

    private void initRoles() {
        if (roleService.listRoles().size() > 0) return;

        for (var entry : RoleService.PRESET_ROLE_PERMISSIONS.entrySet()) {
            String code = entry.getKey();
            String name = UserService.ROLE_NAMES.getOrDefault(code, code);
            Role role = new Role();
            role.code = code;
            role.name = name;
            role.enabled = true;
            roleService.createRole(role);
            roleService.setRolePermissions(code, entry.getValue());
        }
        log.info("种子数据：已初始化 {} 个角色及权限", RoleService.PRESET_ROLE_PERMISSIONS.size());
    }

    private void initAdminUser() {
        if (userRepo.findByUsername("admin").isPresent()) return;
        User admin = new User(); admin.username = "admin"; admin.password = passwordEncoder.encode("admin123"); admin.realName = "系统管理员"; admin.role = "GM"; admin.enabled = true; admin.mustChangePwd = true; userRepo.save(admin);   // v6.1.3 Java 侧直置（堵 PerfIndex SQL 置位的时序洞）
        User buyer = new User(); buyer.username = "buyer"; buyer.password = passwordEncoder.encode("buyer123"); buyer.realName = "采购员"; buyer.role = "BUYER"; buyer.enabled = true; buyer.mustChangePwd = true; userRepo.save(buyer);
        log.info("种子数据：已初始化管理员 admin/admin123，采购员 buyer/buyer123");
    }

    /** 同步新增的权限到已有角色（每次启动时检查） */
    private void syncRecipePermissions() {
        var permRepo = this.roleService;
        // 需要添加权限的角色（新增 production、qc 等权限）
        String[][] rolePerms = {
            {"GM", "recipe:read", "recipe:write", "production:read", "production:write", "qc:read", "qc:write", "ai:read", "ai:write", "log:read", "process:read", "process:write"},
            {"SS", "recipe:read", "recipe:write", "production:read", "production:write", "qc:read", "qc:write", "ai:read", "ai:write", "log:read", "process:read", "process:write"},
            {"PM", "recipe:read", "recipe:write", "production:read", "production:write", "qc:read", "qc:write", "inventory:write", "outsource:write"},
            {"TECH_LEAD", "recipe:read", "recipe:write", "production:read", "production:write", "qc:read"},
            {"TECHNICIAN", "recipe:read", "production:read"},
            {"OUTSOURCE", "production:read", "inventory:write"},
            {"STOREKEEPER", "qc:read", "production:read", "production:write"},
            {"BUYER", "qc:read"},
            {"QC", "qc:read", "qc:write", "production:read"}
        };
        for (String[] entry : rolePerms) {
            String roleCode = entry[0];
            var existing = permRepo.getRolePermissions(roleCode);
            if (existing.isEmpty()) continue;
            boolean changed = false;
            var updated = new java.util.ArrayList<>(existing);
            for (int i = 1; i < entry.length; i++) {
                if (!updated.contains(entry[i])) {
                    updated.add(entry[i]);
                    changed = true;
                }
            }
            if (changed) {
                permRepo.setRolePermissions(roleCode, updated);
                log.info("权限同步：角色 {} 已添加新权限", roleCode);
            }
        }
    }

    /** v5.67 任务督办权限同步：所有已有角色补 task:read（都能看任务）；管理层角色补 task:write（创建/指派/取消） */
    private void syncTaskPermissions() {
        try {
            for (var role : roleService.listRoles()) {
                var perms = roleService.getRolePermissions(role.code);
                if (perms.isEmpty()) continue;
                boolean changed = false;
                var updated = new java.util.ArrayList<>(perms);
                if (!updated.contains("task:read")) { updated.add("task:read"); changed = true; }
                // 有 meeting:write 的角色视为管理层，补 task:write
                if (perms.contains("meeting:write") && !updated.contains("task:write")) {
                    updated.add("task:write"); changed = true;
                }
                if (changed) {
                    roleService.setRolePermissions(role.code, updated);
                    log.info("任务督办权限同步：角色 {} 已补 task 权限", role.code);
                }
            }
        } catch (Exception e) {
            log.warn("任务督办权限同步失败: {}", e.getMessage());
        }
    }

    private void initDictItems() {
        if (dictService.listAll().size() > 0) return;
        // 物料大类（按编码规则）
        addDict("material_category", "助剂", "A", 1);
        addDict("material_category", "颜料", "P", 2);
        addDict("material_category", "填料", "F", 3);
        addDict("material_category", "树脂", "R", 4);
        addDict("material_category", "溶剂", "S", 5);
        addDict("material_category", "半成品", "B", 6);
        addDict("material_category", "成品", "C", 7);
        // 物料小类（按编码规则）
        addDict("material_sub_category", "催化剂", "AC", 1);
        addDict("material_sub_category", "分散剂", "AD", 2);
        addDict("material_sub_category", "消泡剂", "AF", 3);
        addDict("material_sub_category", "固化剂", "AH", 4);
        addDict("material_sub_category", "蜡", "AM", 5);
        addDict("material_sub_category", "密着剂", "AR", 6);
        addDict("material_sub_category", "防沉剂", "AT", 7);
        addDict("material_sub_category", "表面控制", "AV", 8);
        addDict("material_sub_category", "抗氧化剂", "AW", 9);
        addDict("material_sub_category", "功能助剂", "AX", 10);
        addDict("material_sub_category", "有机颜料", "PJ", 11);
        addDict("material_sub_category", "无机颜料", "PK", 12);
        addDict("material_sub_category", "金属颜料", "PM", 13);
        addDict("material_sub_category", "珠光颜料", "PW", 14);
        addDict("material_sub_category", "消光粉", "FB", 15);
        addDict("material_sub_category", "砂面粉", "FS", 16);
        addDict("material_sub_category", "增量填料", "FT", 17);
        addDict("material_sub_category", "丙烯酸树脂", "RA", 18);
        addDict("material_sub_category", "纤维素酯类", "RC", 19);
        addDict("material_sub_category", "环氧树脂", "RE", 20);
        addDict("material_sub_category", "氟碳树脂", "RF", 21);
        addDict("material_sub_category", "聚酯树脂", "RP", 22);
        addDict("material_sub_category", "氨基树脂", "RZ", 24);
        addDict("material_sub_category", "芳烃", "SA", 25);
        addDict("material_sub_category", "酯类", "SE", 26);
        addDict("material_sub_category", "酮类", "SG", 27);
        addDict("material_sub_category", "醇类", "SH", 28);
        addDict("material_sub_category", "醚类", "SX", 29);
        addDict("material_sub_category", "混合溶剂", "SY", 30);
        addDict("material_sub_category", "白浆", "BW", 31);
        addDict("material_sub_category", "红浆", "BR", 32);
        addDict("material_sub_category", "黄浆", "BY", 33);
        addDict("material_sub_category", "绿浆", "BG", 34);
        addDict("material_sub_category", "黑浆", "BB", 35);
        addDict("material_sub_category", "蓝浆", "BL", 36);
        addDict("material_sub_category", "底漆", "CD", 37);
        addDict("material_sub_category", "清漆", "CQ", 38);
        addDict("material_sub_category", "背漆", "CB", 39);
        addDict("material_sub_category", "面漆-白色系", "CW", 40);
        addDict("material_sub_category", "面漆-红色系", "CR", 41);
        addDict("material_sub_category", "面漆-黄色系", "CY", 42);
        addDict("material_sub_category", "面漆-绿色系", "CG", 43);
        addDict("material_sub_category", "面漆-蓝色系", "CL", 44);
        addDict("material_sub_category", "面漆-黑色系", "CK", 45);
        addDict("material_sub_category", "面漆-其他色", "CO", 46);
        // 付款条件
        addDict("payment_terms", "款到发货", "PREPAID", 1);
        addDict("payment_terms", "账期30天", "CREDIT_30", 2);
        addDict("payment_terms", "账期60天", "CREDIT_60", 3);
        addDict("payment_terms", "月结", "MONTHLY", 4);
        addDict("payment_terms", "两月结", "TWO_MONTH", 5);
        addDict("payment_terms", "三月结", "THREE_MONTH", 6);
        addDict("payment_terms", "自定义账期", "CUSTOM_CREDIT", 7);
        // 付款方式
        addDict("payment_method", "承兑", "ACCEPTANCE", 1);
        addDict("payment_method", "电汇", "TRANSFER", 2);
        addDict("payment_method", "现金", "CASH", 3);
        addDict("payment_method", "支票", "CHECK", 4);
        // 其他出库原因（v5.27）
        addDict("outbound_reason", "报废", "SCRAP", 1);
        addDict("outbound_reason", "样品", "SAMPLE", 2);
        addDict("outbound_reason", "盘亏", "LOSS", 3);
        addDict("outbound_reason", "退货", "RETURN", 4);
        addDict("outbound_reason", "其他", "OTHER", 5);
        // 其他入库原因（v5.27）
        addDict("inbound_reason", "退货", "RETURN", 1);
        addDict("inbound_reason", "盘盈", "SURPLUS", 2);
        addDict("inbound_reason", "赠品", "GIFT", 3);
        addDict("inbound_reason", "其他", "OTHER", 4);
        // 客户等级（v5.27）
        addDict("customer_level", "A级", "A", 1);
        addDict("customer_level", "B级", "B", 2);
        addDict("customer_level", "C级", "C", 3);
        log.info("种子数据：已初始化字典项 {} 条", dictService.listAll().size());
    }

    private void addDict(String type, String label, String value, int order) {
        DictItem item = new DictItem();
        item.type = type;
        item.label = label;
        item.value = value;
        item.sortOrder = order;
        dictService.create(item);
    }

    private void initCodingRules() {
        if (codingRuleService.listAll().size() > 0) return;
        // 助剂 A
        addRule("助剂", "A", "催化剂", "AC", 40);
        addRule("助剂", "A", "分散剂", "AD", 41);
        addRule("助剂", "A", "消泡剂", "AF", 42);
        addRule("助剂", "A", "固化剂", "AH", 43);
        addRule("助剂", "A", "蜡", "AM", 44);
        addRule("助剂", "A", "密着剂", "AR", 45);
        addRule("助剂", "A", "防沉剂", "AT", 46);
        addRule("助剂", "A", "表面控制", "AV", 47);
        addRule("助剂", "A", "抗氧化剂", "AW", 48);
        addRule("助剂", "A", "功能助剂", "AX", 49);
        // 颜料 P
        addRule("颜料", "P", "有机颜料", "PJ", 20);
        addRule("颜料", "P", "无机颜料", "PK", 21);
        addRule("颜料", "P", "金属颜料", "PM", 22);
        addRule("颜料", "P", "珠光颜料", "PW", 23);
        // 填料 F
        addRule("填料", "F", "消光粉", "FB", 30);
        addRule("填料", "F", "砂面粉", "FS", 31);
        addRule("填料", "F", "增量填料", "FT", 32);
        // 树脂 R
        addRule("树脂", "R", "丙烯酸树脂", "RA", 10);
        addRule("树脂", "R", "纤维素酯类", "RC", 11);
        addRule("树脂", "R", "环氧树脂", "RE", 12);
        addRule("树脂", "R", "氟碳树脂", "RF", 13);
        addRule("树脂", "R", "聚酯树脂", "RP", 14);
        addRule("树脂", "R", "氨基树脂", "RZ", 16);
        // 溶剂 S
        addRule("溶剂", "S", "芳烃", "SA", 50);
        addRule("溶剂", "S", "酯类", "SE", 51);
        addRule("溶剂", "S", "酮类", "SG", 52);
        addRule("溶剂", "S", "醇类", "SH", 53);
        addRule("溶剂", "S", "醚类", "SX", 54);
        addRule("溶剂", "S", "混合溶剂", "SY", 55);
        // 半成品 B
        addRule("半成品", "B", "白浆", "BW", 60);
        addRule("半成品", "B", "红浆", "BR", 61);
        addRule("半成品", "B", "黄浆", "BY", 62);
        addRule("半成品", "B", "绿浆", "BG", 63);
        addRule("半成品", "B", "黑浆", "BB", 64);
        addRule("半成品", "B", "蓝浆", "BL", 65);
        // 成品 C
        addRule("成品", "C", "底漆", "CD", 70);
        addRule("成品", "C", "清漆", "CQ", 71);
        addRule("成品", "C", "背漆", "CB", 72);
        addRule("成品", "C", "面漆-白色系", "CW", 73);
        addRule("成品", "C", "面漆-红色系", "CR", 74);
        addRule("成品", "C", "面漆-黄色系", "CY", 75);
        addRule("成品", "C", "面漆-绿色系", "CG", 76);
        addRule("成品", "C", "面漆-蓝色系", "CL", 77);
        addRule("成品", "C", "面漆-黑色系", "CK", 78);
        addRule("成品", "C", "面漆-其他色", "CO", 79);
        log.info("种子数据：已初始化编码规则 {} 条", codingRuleService.listAll().size());
    }

    private void addRule(String category, String categoryCode, String subCategory, String subCategoryCode, int numberStart) {
        CodingRule rule = new CodingRule();
        rule.category = category;
        rule.categoryCode = categoryCode;
        rule.subCategory = subCategory;
        rule.subCategoryCode = subCategoryCode;
        rule.numberStart = numberStart;
        rule.currentSeq = 0;
        codingRuleService.create(rule);
    }

    /** 修复旧数据：库存台账中 materialCode 存了品名的问题 */
    private void fixInventoryMaterialCode() {
        var ledgers = ledgerRepo.findAll();
        int fixed = 0;
        for (var ledger : ledgers) {
            if (ledger.materialName != null && !ledger.materialName.isBlank()) continue;
            String storedValue = ledger.materialCode;
            // 情况1：materialCode实际存的是品名，从采购单中找到真正的编码
            var rawOpt = rawRepo.findAll().stream()
                    .filter(r -> storedValue.equals(r.materialName)).findFirst();
            if (rawOpt.isPresent()) {
                ledger.materialName = storedValue;
                ledger.materialCode = rawOpt.get().materialCode;
                ledgerRepo.save(ledger);
                fixed++;
                continue;
            }
            var finOpt = finishedRepo.findAll().stream()
                    .filter(f -> storedValue.equals(f.materialName)).findFirst();
            if (finOpt.isPresent()) {
                ledger.materialName = storedValue;
                ledger.materialCode = finOpt.get().materialCode;
                ledgerRepo.save(ledger);
                fixed++;
                continue;
            }
            // 情况2：materialCode正确但品名为空，按编码查找采购单补充品名
            var rawByCode = rawRepo.findAll().stream()
                    .filter(r -> storedValue.equals(r.materialCode)).findFirst();
            if (rawByCode.isPresent() && rawByCode.get().materialName != null) {
                ledger.materialName = rawByCode.get().materialName;
                ledgerRepo.save(ledger);
                fixed++;
                continue;
            }
            var finByCode = finishedRepo.findAll().stream()
                    .filter(f -> storedValue.equals(f.materialCode)).findFirst();
            if (finByCode.isPresent() && finByCode.get().materialName != null) {
                ledger.materialName = finByCode.get().materialName;
                ledgerRepo.save(ledger);
                fixed++;
            }
        }
        if (fixed > 0) log.info("数据修复：已修正 {} 条库存台账的物料编码/品名", fixed);
    }
}
