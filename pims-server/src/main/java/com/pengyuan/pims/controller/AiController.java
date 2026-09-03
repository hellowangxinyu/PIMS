package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 智能助手接口
 * - GET  /api/ai/config    读取配置（ai:read）
 * - PUT  /api/ai/config    保存配置（ai:write）
 * - POST /api/ai/chat      对话端口（ai:read，预留：AI 可查库统计/分析）
 * - POST /api/ai/test      测试连接（ai:read）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService service;
    public AiController(AiService service) { this.service = service; }

    @GetMapping("/config")
    @SaCheckPermission(value = "ai:read")
    public Result<Map<String, String>> getConfig() {
        return Result.ok(service.getConfig());
    }

    @PutMapping("/config")
    @SaCheckPermission(value = "ai:write")
    public Result<Void> saveConfig(@RequestBody Object body) {
        if (!(body instanceof Map<?, ?> m)) throw new IllegalArgumentException("请求体必须是 JSON 对象");
        service.saveConfig(asMap(m));
        return Result.ok();
    }

    @PostMapping("/chat")
    @SaCheckPermission(value = "ai:read")
    public Result<Map<String, Object>> chat(@RequestBody Object body) throws Exception {
        if (!(body instanceof Map<?, ?> m)) throw new IllegalArgumentException("请求体必须是 JSON 对象");
        List<Map<String, Object>> messages = m.get("messages") instanceof List<?> l
                ? cast(l) : List.of();
        return Result.ok(service.chat(messages));
    }

    @PostMapping("/test")
    @SaCheckPermission(value = "ai:read")
    public Result<Map<String, Object>> test() throws Exception {
        return Result.ok(service.testConnection());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cast(List<?> l) {
        return (List<Map<String, Object>>) (List<?>) l;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Map<?, ?> m) {
        return (Map<String, Object>) (Map<?, ?>) m;
    }
}
