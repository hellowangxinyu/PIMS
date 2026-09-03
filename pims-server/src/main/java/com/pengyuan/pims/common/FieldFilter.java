package com.pengyuan.pims.common;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 敏感字段过滤器 —— 根据用户权限过滤返回数据中的敏感字段
 * 用于实现字段级权限控制
 */
public class FieldFilter {

    private static final Logger log = LoggerFactory.getLogger(FieldFilter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 检查当前用户是否拥有指定权限
     */
    public static boolean hasPerm(String permCode) {
        try {
            List<String> perms = StpUtil.getPermissionList();
            return perms.contains(permCode);
        } catch (Exception e) {
            log.warn("权限检查失败: {}", permCode, e);
            return false;
        }
    }

    /**
     * v5.69 金额可见性检查：优先查模块级金额码（如 invoice:amount），兼容全局 finance:amount。
     * 用法：FieldFilter.hasAmountPerm("invoice") → 检查 invoice:amount OR finance:amount
     */
    public static boolean hasAmountPerm(String moduleCode) {
        return hasPerm(moduleCode + ":amount") || hasPerm("finance:amount");
    }

    /**
     * 过滤单个对象中的敏感字段
     * @param obj 实体对象
     * @param fieldsToRemove 需要移除的字段名列表
     * @return 过滤后的 Map（若无字段需移除则返回原对象）
     */
    public static Object filterFields(Object obj, String... fieldsToRemove) {
        if (obj == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.convertValue(obj, Map.class);
            for (String field : fieldsToRemove) {
                map.remove(field);
            }
            return map;
        } catch (Exception e) {
            log.warn("字段过滤失败，返回原对象", e);
            return obj;
        }
    }

    /**
     * 过滤列表中的每个对象的敏感字段
     */
    public static List<?> filterListFields(List<?> list, String... fieldsToRemove) {
        if (list == null || list.isEmpty()) return list;
        try {
            return list.stream().map(item -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = mapper.convertValue(item, Map.class);
                for (String field : fieldsToRemove) {
                    map.remove(field);
                }
                return map;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("列表字段过滤失败，返回原列表", e);
            return list;
        }
    }

    // ===== 采购价格字段 =====
    public static final String[] PURCHASE_PRICE_FIELDS = {
        "unitPrice", "lastUnitPrice", "increaseRate", "increaseAmount", "totalAmount"
    };

    // ===== 财务金额字段 =====
    public static final String[] FINANCE_AMOUNT_FIELDS = {
        "amount", "paidAmount", "receivedAmount"
    };

    // ===== 供应商付款条件字段 =====
    public static final String[] SUPPLIER_PAYMENT_FIELDS = {
        "paymentTerms", "paymentMethod"
    };
}
