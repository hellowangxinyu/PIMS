package com.pengyuan.pims.common;

import java.util.*;

/**
 * v5.69 功能页级权限定义 + 粗细码展开映射
 *
 * 核心机制：旧粗码（如 purchase:read）自动展开为该域全部功能页的细码（如 raw-material-purchase:read 等），
 * 反向：持有任意细码也自动补回粗码。存量角色零迁移、控制器零改动可用；新角色可只勾细码实现精确控制。
 */
public final class FineGrainedPermissions {

    private FineGrainedPermissions() {}

    // ==================== 旧粗码 → 细码展开映射 ====================

    public static final Map<String, List<String>> EXPANSION = buildExpansion();

    private static Map<String, List<String>> buildExpansion() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // 采购域
        expand(m, "purchase:read", "raw-material-purchase", "finished-purchase", "purchase-arrival", "purchase-return");
        expand(m, "purchase:write", "raw-material-purchase", "finished-purchase", "purchase-arrival", "purchase-return");
        expand(m, "purchase:audit", "raw-material-purchase", "finished-purchase", "purchase-arrival");
        expand(m, "purchase:reverse-audit", "raw-material-purchase", "finished-purchase", "purchase-arrival");
        // 销售域
        expand(m, "sales:read", "quotation", "sales-order", "sales-outbound", "sales-return", "tailing-return", "shipping");
        expand(m, "sales:write", "quotation", "sales-order", "sales-outbound", "sales-return", "tailing-return", "shipping");
        expand(m, "sales:audit", "quotation", "sales-order");
        expand(m, "sales:reverse-audit", "quotation", "sales-order");
        // 财务域
        expand(m, "finance:read", "voucher", "payment-receipt", "payment-disbursement", "invoice", "expense",
                "advance", "salary", "employee", "asset", "account-subject", "finance-report", "costing");
        expand(m, "finance:write", "voucher", "payment-receipt", "payment-disbursement", "invoice", "expense",
                "advance", "salary", "employee", "asset", "account-subject", "costing");
        expand(m, "finance:audit", "voucher");
        expand(m, "finance:reverse-audit", "voucher");
        // 生产域
        expand(m, "production:read", "production-order", "production-outbound", "production-inbound",
                "quality-inspection", "qc-template", "abnormal-order", "schedule");
        expand(m, "production:write", "production-order", "production-outbound", "production-inbound",
                "quality-inspection", "qc-template", "abnormal-order", "schedule");
        // 委外域
        expand(m, "outsource:read", "outsource-order", "outsource-outbound", "outsource-inbound");
        expand(m, "outsource:write", "outsource-order", "outsource-outbound", "outsource-inbound");
        expand(m, "outsource:audit", "outsource-order");
        expand(m, "outsource:reverse-audit", "outsource-order");
        // 库存域
        expand(m, "inventory:read", "inventory-view", "stock-check", "other-outbound", "other-inbound");
        expand(m, "inventory:write", "stock-check", "other-outbound", "other-inbound");
        // 金额可见性：finance:amount → 各模块独立金额码
        m.put("finance:amount", List.of(
            "finance-ar:amount", "invoice:amount", "voucher:amount", "salary:amount", "employee:amount",
            "expense:amount", "asset:amount", "advance:amount", "cost:amount", "shipping:amount",
            "finance-report:amount", "account-subject:amount"
        ));
        return Collections.unmodifiableMap(m);
    }

    private static void expand(Map<String, List<String>> m, String coarseCode, String... pagePrefixes) {
        String op = coarseCode.substring(coarseCode.indexOf(':') + 1);
        List<String> fines = new ArrayList<>();
        for (String p : pagePrefixes) fines.add(p + ":" + op);
        m.put(coarseCode, Collections.unmodifiableList(fines));
    }

    // ==================== 权限展开（正反向） ====================

    /**
     * 正向：粗码→展开全部细码；反向：任意细码→补回粗码。
     * 用于 StpInterfaceImpl.getPermissionList()——存量角色零迁移可用，新角色勾细码也能过旧 Controller。
     */
    public static Set<String> expandPermissions(Collection<String> raw) {
        Set<String> result = new LinkedHashSet<>(raw);
        // 正向展开
        for (String p : raw) {
            List<String> fines = EXPANSION.get(p);
            if (fines != null) result.addAll(fines);
        }
        // 反向补充：持有任意细码→补回粗码
        for (Map.Entry<String, List<String>> e : EXPANSION.entrySet()) {
            for (String fine : e.getValue()) {
                if (result.contains(fine)) { result.add(e.getKey()); break; }
            }
        }
        return result;
    }
}
