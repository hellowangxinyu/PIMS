package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色与权限管理 —— 层级化权限控制
 * 权限层级：模块可见 → 单据操作 → 审核/反审核 → 敏感字段
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final RoleRepository roleRepo;
    private final RolePermissionRepository permRepo;
    private final UserRepository userRepo;

    /** 所有可配置的权限码（含旧粗码 + v5.69 功能页细码 + 字段级权限） */
    public static final List<String> ALL_PERMISSIONS = buildAllPermissions();

    private static List<String> buildAllPermissions() {
        List<String> codes = new ArrayList<>(List.of(
            // ===== 旧粗码（保留向后兼容，可继续使用） =====
            "supplier:read","supplier:write","supplier:delete","supplier:payment",
            "customer:read","customer:write","customer:delete",
            "material:read","material:write","material:delete","material:code-edit",
            "warehouse:read","warehouse:write","warehouse:delete",
            "inventory:read","inventory:write",
            "recipe:read","recipe:write",
            "process:read","process:write",
            "production:read","production:write",
            "purchase:read","purchase:write","purchase:audit","purchase:reverse-audit","purchase:price",
            "sales:read","sales:write","sales:audit","sales:reverse-audit",
            "outsource:read","outsource:write","outsource:audit","outsource:reverse-audit",
            "finance:read","finance:write","finance:audit","finance:reverse-audit","finance:amount",
            "meeting:read","meeting:write",
            "crm:read","crm:write",
            "sample:read","sample:write","complaint:read","complaint:write",
            "strace:read","strace:write",
            "qc:read","qc:write",
            "user:read","user:write","user:delete",
            "dict:read","dict:write",
            "ai:read","ai:write",
            "log:read", "task:read", "task:write"
        ));
        // ===== v5.69 功能页细码（从展开映射自动派生） =====
        for (List<String> fines : com.pengyuan.pims.common.FineGrainedPermissions.EXPANSION.values()) {
            codes.addAll(fines);
        }
        // 细码特有的操作（粗码没有的，如各页独立删除权）
        codes.addAll(List.of(
            "invoice:delete", "expense:delete", "voucher:delete", "advance:delete",
            "salary:delete", "asset:delete", "shipping:delete", "quotation:delete",
            "employee:delete",   // v7.7.9：权限树可勾但白名单漏收，保存被静默过滤（SS 丢此码的根因）
            "sales-order:delete", "sales-outbound:delete", "sales-return:delete",
            "raw-material-purchase:delete", "finished-purchase:delete",
            "purchase-arrival:delete", "purchase-return:delete",
            "production-order:delete", "outsource-order:delete",
            "quality-inspection:delete", "qc-template:delete",
            "recipe:delete", "process:delete", "crm:delete", "sample:delete", "complaint:delete"
        ));
        return Collections.unmodifiableList(codes.stream().distinct().toList());
    }

    /**
     * v5.69 权限树结构（功能页级）：每行=一个功能页，叶子=read/write/delete/audit/reverse-audit+字段权限。
     * 粗码域（如 purchase:read）通过 FineGrainedPermissions.EXPANSION 自动展开兼容。
     */
    public static List<Map<String, Object>> buildPermissionTree() {
        return List.of(
            // ===== 基础数据 =====
            module("supplier", "供应商", List.of(
                leaf("supplier:read", "查看"), leaf("supplier:write", "编辑"), leaf("supplier:delete", "删除"),
                leaf("supplier:payment", "付款条件")
            )),
            module("customer", "客户", List.of(
                leaf("customer:read", "查看"), leaf("customer:write", "编辑"), leaf("customer:delete", "删除")
            )),
            module("material", "物料", List.of(
                leaf("material:read", "查看"), leaf("material:write", "编辑"), leaf("material:delete", "删除"),
                leaf("material:code-edit", "修改编码")   // v5.91 单独权限：未授权任何人不可改物料编码
            )),
            module("warehouse", "仓库", List.of(
                leaf("warehouse:read", "查看"), leaf("warehouse:write", "编辑"), leaf("warehouse:delete", "删除")
            )),
            // ===== 采购（4 功能页） =====
            module("raw-material-purchase", "原料采购", List.of(
                leaf("raw-material-purchase:read", "查看"), leaf("raw-material-purchase:write", "编辑"),
                leaf("raw-material-purchase:delete", "删除"), leaf("raw-material-purchase:audit", "审核"),
                leaf("raw-material-purchase:reverse-audit", "反审核"),
                leaf("purchase:price", "查看价格(含税/不含税/税额)")
            )),
            module("finished-purchase", "成品采购", List.of(
                leaf("finished-purchase:read", "查看"), leaf("finished-purchase:write", "编辑"),
                leaf("finished-purchase:delete", "删除"), leaf("finished-purchase:audit", "审核"),
                leaf("finished-purchase:reverse-audit", "反审核"),
                leaf("purchase:price", "查看价格(含税/不含税/税额)")
            )),
            module("purchase-arrival", "采购到货", List.of(
                leaf("purchase-arrival:read", "查看"), leaf("purchase-arrival:write", "编辑"),
                leaf("purchase-arrival:delete", "删除"), leaf("purchase-arrival:audit", "审核"),
                leaf("purchase-arrival:reverse-audit", "反审核")
            )),
            module("purchase-return", "采购退货", List.of(
                leaf("purchase-return:read", "查看"), leaf("purchase-return:write", "编辑"), leaf("purchase-return:delete", "删除")
            )),
            // ===== 生产（7 功能页） =====
            module("recipe", "配方管理", List.of(
                leaf("recipe:read", "查看"), leaf("recipe:write", "编辑"), leaf("recipe:delete", "删除")
            )),
            module("process", "工艺路线", List.of(
                leaf("process:read", "查看"), leaf("process:write", "编辑"), leaf("process:delete", "删除")
            )),
            module("schedule", "排产中心", List.of(
                leaf("schedule:read", "查看"), leaf("schedule:write", "排产操作")
            )),
            module("production-order", "生产订单", List.of(
                leaf("production-order:read", "查看"), leaf("production-order:write", "编辑"), leaf("production-order:delete", "删除")
            )),
            module("abnormal-order", "异常订单", List.of(
                leaf("abnormal-order:read", "查看"), leaf("abnormal-order:write", "编辑"), leaf("abnormal-order:delete", "删除")
            )),
            module("production-outbound", "生产领料", List.of(
                leaf("production-outbound:read", "查看"), leaf("production-outbound:write", "领料/退料/作废")
            )),
            module("production-inbound", "生产入库", List.of(
                leaf("production-inbound:read", "查看"), leaf("production-inbound:write", "编辑")
            )),
            module("quality-inspection", "质检管理", List.of(
                leaf("quality-inspection:read", "查看"), leaf("quality-inspection:write", "QC判定")
            )),
            module("qc-template", "质检模板", List.of(
                leaf("qc-template:read", "查看"), leaf("qc-template:write", "编辑"), leaf("qc-template:delete", "删除")
            )),
            // ===== 销售（6 功能页） =====
            module("quotation", "报价单", List.of(
                leaf("quotation:read", "查看"), leaf("quotation:write", "编辑"), leaf("quotation:delete", "删除")
            )),
            module("sales-order", "销售订单", List.of(
                leaf("sales-order:read", "查看"), leaf("sales-order:write", "编辑"), leaf("sales-order:delete", "删除")
            )),
            module("sales-outbound", "销售出库", List.of(
                leaf("sales-outbound:read", "查看"), leaf("sales-outbound:write", "编辑"), leaf("sales-outbound:delete", "删除")
            )),
            module("sales-return", "销售退货", List.of(
                leaf("sales-return:read", "查看"), leaf("sales-return:write", "编辑"), leaf("sales-return:delete", "删除")
            )),
            module("tailing-return", "油尾退回", List.of(
                leaf("tailing-return:read", "查看"), leaf("tailing-return:write", "编辑")
            )),
            module("shipping", "物流运费", List.of(
                leaf("shipping:read", "查看"), leaf("shipping:write", "登记/编辑"), leaf("shipping:delete", "删除"),
                leaf("shipping:amount", "查看运费金额")
            )),
            // ===== 委外（3 功能页） =====
            module("outsource-order", "委外订单", List.of(
                leaf("outsource-order:read", "查看"), leaf("outsource-order:write", "编辑"), leaf("outsource-order:delete", "删除")
            )),
            module("outsource-outbound", "委外出库", List.of(
                leaf("outsource-outbound:read", "查看"), leaf("outsource-outbound:write", "发料")
            )),
            module("outsource-inbound", "委外入库", List.of(
                leaf("outsource-inbound:read", "查看"), leaf("outsource-inbound:write", "编辑")
            )),
            // ===== 库存（4 功能页） =====
            module("inventory-view", "库存查询", List.of(
                leaf("inventory-view:read", "查看")
            )),
            module("stock-check", "盘库管理", List.of(
                leaf("stock-check:read", "查看"), leaf("stock-check:write", "盘库/库位调整")
            )),
            module("other-outbound", "其他出库", List.of(
                leaf("other-outbound:read", "查看"), leaf("other-outbound:write", "编辑")
            )),
            module("other-inbound", "其他入库", List.of(
                leaf("other-inbound:read", "查看"), leaf("other-inbound:write", "编辑")
            )),
            // ===== 财务（12 功能页） =====
            module("voucher", "会计凭证", List.of(
                leaf("voucher:read", "查看"), leaf("voucher:write", "编辑"), leaf("voucher:delete", "删除"),
                leaf("voucher:audit", "记账"), leaf("voucher:reverse-audit", "反记账"),
                leaf("voucher:amount", "查看金额")
            )),
            module("payment-receipt", "收款单", List.of(
                leaf("payment-receipt:read", "查看"), leaf("payment-receipt:write", "编辑"), leaf("payment-receipt:delete", "删除")
            )),
            module("payment-disbursement", "付款单", List.of(
                leaf("payment-disbursement:read", "查看"), leaf("payment-disbursement:write", "编辑"), leaf("payment-disbursement:delete", "删除")
            )),
            module("invoice", "发票管理", List.of(
                leaf("invoice:read", "查看"), leaf("invoice:write", "编辑"), leaf("invoice:delete", "删除"),
                leaf("invoice:amount", "查看金额")
            )),
            module("expense", "费用管理", List.of(
                leaf("expense:read", "查看"), leaf("expense:write", "编辑"), leaf("expense:delete", "删除"),
                leaf("expense:amount", "查看金额")
            )),
            module("advance", "预收预付", List.of(
                leaf("advance:read", "查看"), leaf("advance:write", "编辑"), leaf("advance:delete", "删除"),
                leaf("advance:amount", "查看金额")
            )),
            module("salary", "工资管理", List.of(
                leaf("salary:read", "查看"), leaf("salary:write", "编辑"), leaf("salary:delete", "删除"),
                leaf("salary:amount", "查看工资金额")
            )),
            module("employee", "员工档案", List.of(
                leaf("employee:read", "查看"), leaf("employee:write", "编辑"), leaf("employee:delete", "删除"),
                leaf("employee:amount", "查看基本工资")
            )),
            module("asset", "固定资产", List.of(
                leaf("asset:read", "查看"), leaf("asset:write", "编辑"), leaf("asset:delete", "删除"),
                leaf("asset:amount", "查看原值/折旧")
            )),
            module("account-subject", "科目与期初", List.of(
                leaf("account-subject:read", "查看"), leaf("account-subject:write", "编辑"),
                leaf("account-subject:amount", "查看期初余额")
            )),
            module("costing", "计价设置", List.of(
                leaf("costing:read", "查看"), leaf("costing:write", "变更计价方式"),
                leaf("cost:amount", "查看成本金额")
            )),
            module("finance-report", "财务报表", List.of(
                leaf("finance-report:read", "查看"), leaf("finance:amount", "查看全部金额（全局）"), leaf("finance-ar:amount", "应收应付金额")
            )),
            // ===== 其他 =====
            module("sample", "打样样品", List.of(leaf("sample:read", "查看"), leaf("sample:write", "编辑"), leaf("sample:delete", "删除"))),
            module("complaint", "客户投诉", List.of(leaf("complaint:read", "查看"), leaf("complaint:write", "编辑"), leaf("complaint:delete", "删除"))),
            module("strace", "供应商质量追溯", List.of(leaf("strace:read", "查看"), leaf("strace:write", "编辑"))),
            module("crm", "CRM 客户经营", List.of(leaf("crm:read", "查看"), leaf("crm:write", "编辑"), leaf("crm:delete", "删除"))),
            module("meeting", "每周议题", List.of(leaf("meeting:read", "查看"), leaf("meeting:write", "编辑"))),
            module("task", "任务督办", List.of(
                leaf("task:read", "查看（含汇报进度）"), leaf("task:write", "管理（创建/指派/确认完成）")
            )),
            module("user", "用户管理", List.of(leaf("user:read", "查看"), leaf("user:write", "编辑"), leaf("user:delete", "删除"))),
            module("dict", "数据字典", List.of(leaf("dict:read", "查看"), leaf("dict:write", "编辑"))),
            module("ai", "AI 智能助手", List.of(leaf("ai:read", "查看/对话"), leaf("ai:write", "配置管理"))),
            module("log", "操作日志", List.of(leaf("log:read", "查看")))
        );
    }

    // ==================== v5.68 权限矩阵（表格化 UI 用）====================

    /**
     * 权限矩阵结构——前端表格化勾选：行=模块、列=操作类型+字段权限。
     * 每行 {moduleKey, moduleLabel, read, write, delete, audit, reverseAudit, fieldPerms: [{code, label}]}
     * 操作列的值=权限码（无此操作=null）；fieldPerms=该模块特有的字段级权限。
     */
    public static List<Map<String, Object>> buildPermissionMatrix() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> mod : buildPermissionTree()) {
            String moduleKey = (String) mod.get("id");
            String moduleLabel = (String) mod.get("label");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) mod.get("children");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("moduleKey", moduleKey);
            row.put("moduleLabel", moduleLabel);
            for (Map<String, Object> leaf : children) {
                String permCode = (String) leaf.get("id");
                String permLabel = (String) leaf.get("label");
                if (permCode.endsWith(":read")) row.put("read", permCode);
                else if (permCode.endsWith(":write")) row.put("write", permCode);
                else if (permCode.endsWith(":delete")) row.put("delete", permCode);
                else if (permCode.endsWith(":audit")) row.put("audit", permCode);
                else if (permCode.endsWith(":reverse-audit")) row.put("reverseAudit", permCode);
                else {
                    // 字段级权限（price/amount/payment 等）
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> fps = (List<Map<String, Object>>) row.computeIfAbsent("fieldPerms",
                            k -> new java.util.ArrayList<Map<String, Object>>());
                    Map<String, Object> fp = new LinkedHashMap<>();
                    fp.put("code", permCode);
                    fp.put("label", permLabel);
                    fps.add(fp);
                }
            }
            result.add(row);
        }
        return result;
    }

    private static Map<String, Object> module(String key, String label, List<Map<String, Object>> children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", key);
        node.put("label", label);
        node.put("children", children);
        return node;
    }

    private static Map<String, Object> leaf(String id, String label) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        return node;
    }

    /** 预置角色定义（首次初始化时使用） */
    public static final Map<String, List<String>> PRESET_ROLE_PERMISSIONS = Map.ofEntries(
        Map.entry("GM", ALL_PERMISSIONS),
        Map.entry("PM", List.of("supplier:read","supplier:write","customer:read","material:read","material:write","warehouse:read","inventory:read","inventory:write","recipe:read","recipe:write","production:read","production:write","purchase:read","purchase:write","purchase:audit","purchase:reverse-audit","purchase:price","sales:read","outsource:read","outsource:write","finance:read","finance:amount","qc:read","qc:write","dict:read","dict:write")),
        Map.entry("BUYER", List.of("supplier:read","supplier:write","customer:read","material:read","material:write","warehouse:read","inventory:read","purchase:read","purchase:write","purchase:price","sales:read","outsource:read","qc:read","dict:read")),
        Map.entry("TECH_LEAD", List.of("material:read","material:write","warehouse:read","inventory:read","recipe:read","recipe:write","production:read","production:write","supplier:read","customer:read","purchase:read","sales:read","outsource:read","qc:read","dict:read","dict:write")),
        Map.entry("TECHNICIAN", List.of("material:read","warehouse:read","inventory:read","recipe:read","production:read","supplier:read","customer:read","dict:read")),
        Map.entry("OUTSOURCE", List.of("outsource:read","outsource:write","inventory:read","inventory:write","warehouse:read","material:read","supplier:read","customer:read","purchase:read","sales:read","production:read","dict:read")),
        Map.entry("STOREKEEPER", List.of("warehouse:read","warehouse:write","inventory:read","inventory:write","production:read","production:write","outsource:read","outsource:write","material:read","supplier:read","customer:read","purchase:read","sales:read","qc:read","dict:read")),
        Map.entry("SALES", List.of("customer:read","customer:write","sales:read","sales:write","finance:read","finance:amount","material:read","warehouse:read","inventory:read","supplier:read","purchase:read","outsource:read","dict:read")),
        Map.entry("FINANCE", List.of("finance:read","finance:write","finance:audit","finance:reverse-audit","finance:amount","supplier:read","supplier:payment","customer:read","material:read","warehouse:read","inventory:read","purchase:read","purchase:price","sales:read","outsource:read","dict:read")),
        Map.entry("QC", List.of("supplier:read","customer:read","material:read","warehouse:read","inventory:read","purchase:read","sales:read","outsource:read","production:read","finance:read","dict:read","qc:read","qc:write"))
    );

    public RoleService(RoleRepository roleRepo, RolePermissionRepository permRepo, UserRepository userRepo, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.roleRepo = roleRepo;
        this.permRepo = permRepo;
        this.userRepo = userRepo;
    }

    // ============ 角色 CRUD ============

    public List<Role> listRoles() { return roleRepo.findAll(); }

    public Optional<Role> getRole(Long id) { return roleRepo.findById(id); }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public Role createRole(Role role) {
        return writeQueue.executeTx(() -> {
            if (roleRepo.findByCode(role.code).isPresent())
                throw new IllegalArgumentException("角色编码已存在: " + role.code);
            role.createTime = LocalDateTime.now();
            return roleRepo.save(role);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public Role updateRole(Long id, Role role) {
        return writeQueue.executeTx(() -> {
            Role exist = roleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
            exist.name = role.name;
            exist.enabled = role.enabled;
            exist.updateTime = LocalDateTime.now();
            return roleRepo.save(exist);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void deleteRole(Long id) {
        writeQueue.executeTx(() -> {
            Role role = roleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
            // 踢出所有使用此角色的用户
            List<User> users = userRepo.findByRole(role.code);
            for (User u : users) {
                try { StpUtil.logout(u.id); } catch (Exception ignored) {}
            }
            permRepo.deleteByRoleCode(role.code);
            role.enabled = false;
            roleRepo.save(role);
            log.info("角色已禁用: {} ({}), 已踢出 {} 个用户", role.code, role.name, users.size());
    
        });
    }

    // ============ 权限配置 ============

    /** 获取某角色当前拥有的权限码列表 */
    public List<String> getRolePermissions(String roleCode) {
        return permRepo.findByRoleCode(roleCode).stream()
                .map(rp -> rp.permCode)
                .collect(Collectors.toList());
    }

    /** 设置某角色的权限（覆盖式，先删后增），同时踢出该角色下所有已登录用户 */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void setRolePermissions(String roleCode, List<String> permCodes) {
        writeQueue.executeTx(() -> {
            // 白名单校验
            List<String> validPerms = permCodes.stream()
                    .filter(ALL_PERMISSIONS::contains)
                    .collect(Collectors.toList());
            if (validPerms.size() != permCodes.size()) {
                log.warn("角色 {} 权限配置包含无效权限码，已过滤", roleCode);
            }
            permRepo.deleteByRoleCode(roleCode);
            for (String code : validPerms) {
                RolePermission rp = new RolePermission();
                rp.roleCode = roleCode;
                rp.permCode = code;
                permRepo.save(rp);
            }
            // 踢出该角色的所有已登录用户，使权限变更立即生效
            List<User> users = userRepo.findByRole(roleCode);
            for (User u : users) {
                try { StpUtil.logout(u.id); } catch (Exception ignored) {}
            }
            log.info("角色 {} 权限已更新 ({}个权限), 已踢出 {} 个用户", roleCode, validPerms.size(), users.size());
    
        });
    }

    /** 查询所有可用权限码（前端勾选用） */
    public List<String> getAllPermissions() { return ALL_PERMISSIONS; }

    /** 查询权限树结构（前端 el-tree 层级选择用） */
    public List<Map<String, Object>> getPermissionTree() { return buildPermissionTree(); }

    // ============ 权限加载（供 StpInterfaceImpl 调用） ============

    /** 根据角色编码获取权限码列表 */
    public List<String> getPermissionCodes(String roleCode) {
        return permRepo.findByRoleCode(roleCode).stream()
                .map(rp -> rp.permCode)
                .collect(Collectors.toList());
    }
}
