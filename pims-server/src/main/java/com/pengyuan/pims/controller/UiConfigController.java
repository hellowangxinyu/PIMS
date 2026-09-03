package com.pengyuan.pims.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * v6.0 用户界面配置（账号级）：表格列宽等 UI 偏好存 sys_config，
 * 换电脑/换浏览器重新登录后恢复。key 由前端生成（pims.ui.cols.{用户名}.{表名}），
 * 只允许当前登录用户读写自己的 key（隔离：key 必须含自己的用户名，防越权改他人配置）。
 */
@RestController
@RequestMapping("/api/ui-config")
public class UiConfigController {

    private final JdbcTemplate jdbc;

    public UiConfigController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public Result<String> get(@RequestParam String key) {
        assertOwnKey(key);
        try {
            var rows = jdbc.queryForList("SELECT value_text FROM sys_config WHERE key_name = ?", key);
            return Result.ok(rows.isEmpty() ? null : String.valueOf(rows.get(0).get("value_text")));
        } catch (Exception e) {
            return Result.ok(null);
        }
    }

    @PostMapping
    public Result<?> save(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key 不能为空");
        assertOwnKey(key);
        if (value != null && value.length() > 100_000) throw new IllegalArgumentException("配置值过大");
        jdbc.update("INSERT INTO sys_config (key_name, value_text) VALUES (?, ?) " +
                "ON CONFLICT(key_name) DO UPDATE SET value_text = excluded.value_text", key, value);
        return Result.ok();
    }

    /** key 形如 pims.ui.cols.{username}.{table}——第二段必须是当前登录用户名 */
    private void assertOwnKey(String key) {
        String[] parts = key.split(java.util.regex.Pattern.quote("."), -1);
        if (parts.length < 5 || !"pims".equals(parts[0]) || !"ui".equals(parts[1]))
            throw new IllegalArgumentException("非法的界面配置 key: " + key);
        String owner = parts[3];
        var rows = jdbc.queryForList("SELECT id FROM sys_user WHERE username = ?", owner);
        // v6.1.2（新6）：两分支错误消息统一，消除"用户存在性"枚举 oracle
        if (rows.isEmpty()
                || !String.valueOf(rows.get(0).get("id")).equals(String.valueOf(StpUtil.getLoginIdAsLong())))
            throw new IllegalArgumentException("无权访问该配置");
    }
}
