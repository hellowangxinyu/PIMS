package com.pengyuan.pims.controller;

import com.pengyuan.pims.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 打印计数接口（v5.26）
 * 打印单据时调用 +1，列表页展示打印次数
 */
@RestController
@RequestMapping("/api/print-count")
public class PrintController {

    private final JdbcTemplate jdbc;

    public PrintController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 单据类型 → {表名, 单号列}（表名/列名硬编码常量，无注入风险） */
    private static final Map<String, String[]> DOC_TABLES = Map.of(
            "PRODUCTION_ORDER", new String[]{"production_order", "order_no"},
            "OUTSOURCE_ORDER", new String[]{"outsource_order", "order_no"},
            "RECIPE", new String[]{"recipe", "recipe_no"},
            "QUALITY_INSPECTION", new String[]{"quality_inspection", "inspection_no"},
            "SUPPLIER_QUALITY_TRACE", new String[]{"supplier_quality_trace", "trace_no"}
    );

    /**
     * 打印次数 +1（原子 UPDATE，SQLite 单写者天然安全）
     * @param body {docType, docNo}，返回最新打印次数
     */
    @PostMapping
    @cn.dev33.satoken.annotation.SaCheckLogin   // v6.1.4 补登录校验（原匿名可刷计数）
    public Result<Integer> record(@RequestBody Map<String, String> body) {
        String docType = body.get("docType");
        String docNo = body.get("docNo");
        if (docType == null || docNo == null || docNo.isBlank()) {
            throw new IllegalArgumentException("缺少单据类型或单号");
        }
        String[] table = DOC_TABLES.get(docType);
        if (table == null) {
            throw new IllegalArgumentException("不支持的打印单据类型: " + docType);
        }
        int updated = jdbc.update("UPDATE " + table[0] + " SET print_count = print_count + 1 WHERE " + table[1] + " = ?", docNo);
        if (updated == 0) {
            throw new IllegalArgumentException("单据不存在: " + docNo);
        }
        // SQLite INTEGER 经 JDBC 可能返回 Long，统一按 Long 取
        Long count = jdbc.queryForObject(
                "SELECT print_count FROM " + table[0] + " WHERE " + table[1] + " = ?",
                Long.class, docNo);
        return Result.ok(count == null ? 0 : count.intValue());
    }
}
