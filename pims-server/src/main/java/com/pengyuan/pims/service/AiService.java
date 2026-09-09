package com.pengyuan.pims.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/**
 * AI 智能助手服务
 * 1. 配置读写：接口协议（OpenAI 兼容 / Anthropic 兼容）/ 接口地址 / API Key / 模型名称 / 思考强度 / 启用开关
 * 2. 对话：双协议转发（OpenAI 兼容 /v1/chat/completions、Anthropic 兼容 /v1/messages），
 *    内置两个工具（list_tables / query_data），AI 可自主查库做数据统计与分析；
 *    工具机制可扩展（后续可加单据操作工具）
 * 3. 全库查询为只读（SQL 白名单校验 + 结果 LIMIT 兜底）
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int MAX_TOOL_ROUNDS = 10;
    private static final int RESULT_LIMIT = 100;
    private static final String PROTOCOL_ANTHROPIC = "anthropic";

    private static final String SYSTEM_PROMPT = """
            你是 PIMS 涂料生产管理系统的 AI 智能助手。系统包含采购、生产、销售、库存、委外、质检、财务、报表等业务数据。
            你可以使用工具查询数据库中的数据（只读）：
            - list_tables：查看数据库所有表及其字段结构
            - query_data：执行只读 SQL（仅 SELECT/WITH 查询）获取数据
            使用步骤：
            1. 需要数据时，先用 list_tables 了解表结构，再编写 SQL 通过 query_data 查询
            2. 根据查询结果用中文回答，可以做统计、对比、趋势等分析
            注意：数据是只读的，你只能查询不能修改；若数据不足或查询失败，如实说明原因。
            """;

    /** 系统提示词 + 当前日期（模型需要知道"上个月/本周"等相对时间） */
    private String buildSystemPrompt() {
        return SYSTEM_PROMPT + "\n今天是 " + java.time.LocalDate.now() + "（YYYY-MM-DD）。";
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiService(JdbcTemplate jdbc, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.jdbc = jdbc;
        this.writeQueue = writeQueue;
    }

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.10.1

    // ============ 配置读写 ============

    /** 读取 AI 配置 */
    public Map<String, String> getConfig() {
        Map<String, String> cfg = rawConfig();
        cfg.put("apiKey", mask(cfg.get("apiKey")));   // v6.1.4：对外回显掩码
        return cfg;
    }

    /** v6.1.4：内部配置（明文 Key）——chat/testConnection 调 AI 必须用原始 Key，不能走掩码的 getConfig */
    private Map<String, String> rawConfig() {
        Map<String, String> store = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT key_name, value_text FROM ai_config")) {
            store.put(String.valueOf(row.get("key_name")), row.get("value_text") == null ? "" : String.valueOf(row.get("value_text")));
        }
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("baseUrl", store.getOrDefault("ai_base_url", ""));
        cfg.put("apiKey", store.getOrDefault("ai_api_key", ""));
        cfg.put("model", store.getOrDefault("ai_model", ""));
        cfg.put("enabled", store.getOrDefault("ai_enabled", "false"));
        cfg.put("protocol", store.getOrDefault("ai_protocol", "openai"));
        cfg.put("thinking", store.getOrDefault("ai_thinking", "adaptive"));
        return cfg;
    }

    /** 保存 AI 配置 */
    public void saveConfig(Map<String, Object> body) {
        setConfig("ai_base_url", str(body.get("baseUrl")));
        // v6.1.4：空值不覆盖已存 Key（掩码回显后原样提交的保存请求不再清空密钥）
        String newKey = str(body.get("apiKey"));
        if (!newKey.isBlank()) setConfig("ai_api_key", newKey);
        setConfig("ai_model", str(body.get("model")));
        setConfig("ai_enabled", body.get("enabled") != null ? String.valueOf(body.get("enabled")) : "false");
        setConfig("ai_protocol", str(body.get("protocol")).isBlank() ? "openai" : str(body.get("protocol")));
        String thinking = str(body.get("thinking"));
        setConfig("ai_thinking", thinking.isBlank() ? "adaptive" : thinking);
        log.info("AI 配置已更新");
    }

    private void setConfig(String key, String value) {
        writeQueue.executeTx(() -> {   // v8.10.1（A2 残）：写进全局锁
            jdbc.update("INSERT INTO ai_config (key_name, value_text) VALUES (?, ?) " +
                    "ON CONFLICT(key_name) DO UPDATE SET value_text = excluded.value_text", key, value == null ? "" : value);
            return null;
        });
    }

    // ============ 对话（agent loop，双协议） ============

    /**
     * 对话入口：把用户消息（含历史）发给模型，模型可调用工具查库，
     * 工具结果回传后继续对话，直到模型给出最终回答（最多 MAX_TOOL_ROUNDS 轮）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(List<Map<String, Object>> messages) throws Exception {
        Map<String, String> cfg = rawConfig();   // v6.1.4：内部用明文 Key
        ensureReady(cfg);
        boolean anthropic = PROTOCOL_ANTHROPIC.equals(cfg.get("protocol"));

        // 会话历史（前端传的 user/assistant 消息）
        List<Map<String, Object>> history = new ArrayList<>();
        if (messages != null) history.addAll(messages);

        int rounds = 0;
        boolean withTools = true;
        String lastSig = null;
        int repeatCount = 0;
        while (rounds < MAX_TOOL_ROUNDS) {
            Map<String, Object> resp = anthropic
                    ? callAnthropic(cfg, history, withTools)
                    : callOpenAI(cfg, history, withTools);
            String text = (String) resp.get("text");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) resp.get("toolCalls");
            Map<String, Object> assistantMsg = (Map<String, Object>) resp.get("assistantMsg");

            if (toolCalls == null || toolCalls.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("reply", text == null ? "" : text);
                result.put("rounds", rounds);
                return result;
            }

            // 收敛兜底：连续 3 轮完全相同的工具调用说明模型陷入循环，强制其基于已有结果总结
            String sig = toolCalls.stream()
                    .map(c -> c.get("name") + ":" + String.valueOf(c.get("input")))
                    .sorted().collect(java.util.stream.Collectors.joining("|"));
            if (sig.equals(lastSig)) {
                repeatCount++;
            } else {
                lastSig = sig;
                repeatCount = 1;
            }
            if (repeatCount >= 3) {
                Map<String, Object> hint = new LinkedHashMap<>();
                hint.put("role", "user");
                hint.put("content", "你已连续多次调用相同的工具。请直接基于以上对话中已有的查询结果，用中文回答用户的问题，不要再调用任何工具。");
                history.add(hint);
                Map<String, Object> finalResp = anthropic
                        ? callAnthropic(cfg, history, false)
                        : callOpenAI(cfg, history, false);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("reply", String.valueOf(finalResp.get("text")));
                result.put("rounds", rounds + 1);
                return result;
            }

            // 模型请求调用工具：assistant 消息回传历史，再追加工具结果
            history.add(assistantMsg);
            if (anthropic) {
                // Anthropic 协议：多个 tool_result 必须合并到同一条 user 消息
                List<Map<String, Object>> blocks = new ArrayList<>();
                for (Map<String, Object> call : toolCalls) {
                    String toolResult;
                    try {
                        Object inputObj = call.get("input");
                        String argsJson = inputObj == null ? "{}"
                                : (inputObj instanceof String s ? s : mapper.writeValueAsString(inputObj));
                        toolResult = runTool(String.valueOf(call.get("name")), argsJson);
                    } catch (Exception e) {
                        toolResult = "工具执行失败: " + e.getMessage();
                    }
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", String.valueOf(call.get("id")));
                    block.put("content", toolResult);
                    blocks.add(block);
                }
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "user");
                toolMsg.put("content", blocks);
                history.add(toolMsg);
            } else {
                for (Map<String, Object> call : toolCalls) {
                    String toolResult;
                    try {
                        Object inputObj = call.get("input");
                        String argsJson = inputObj == null ? "{}"
                                : (inputObj instanceof String s ? s : mapper.writeValueAsString(inputObj));
                        toolResult = runTool(String.valueOf(call.get("name")), argsJson);
                    } catch (Exception e) {
                        toolResult = "工具执行失败: " + e.getMessage();
                    }
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", String.valueOf(call.get("id")));
                    toolMsg.put("content", toolResult);
                    history.add(toolMsg);
                }
            }
            rounds++;
            log.info("AI 工具调用第 {} 轮: {}", rounds, toolCalls.stream().map(c -> String.valueOf(c.get("name"))).toList());
        }
        // 已达轮次上限：给模型最后一次机会，基于已获取的数据总结回答（避免生硬报错）
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("role", "user");
        hint.put("content", "以上工具查询已获取较多数据。请直接基于已有的查询结果，用中文回答用户的问题；若数据不足以完整回答，请说明你已经查询到的部分。不要再调用任何工具。");
        history.add(hint);
        Map<String, Object> finalResp = anthropic
                ? callAnthropic(cfg, history, false)
                : callOpenAI(cfg, history, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", String.valueOf(finalResp.get("text")));
        result.put("rounds", MAX_TOOL_ROUNDS);
        return result;
    }

    /** 测试连接：最小请求验证配置可用性（按协议） */
    public Map<String, Object> testConnection() throws Exception {
        Map<String, String> cfg = rawConfig();   // v6.1.4：内部用明文 Key
        if (cfg.get("baseUrl").isBlank()) throw new IllegalArgumentException("请先填写接口地址");
        if (cfg.get("apiKey").isBlank()) throw new IllegalArgumentException("请先填写 API Key");
        if (cfg.get("model").isBlank()) throw new IllegalArgumentException("请先填写模型名称");
        boolean anthropic = PROTOCOL_ANTHROPIC.equals(cfg.get("protocol"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("model", cfg.get("model"));
        if (anthropic) {
            Map<String, Object> resp = callAnthropic(cfg, List.of(), false);
            result.put("reply", String.valueOf(resp.get("text")));
        } else {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "user");
            msg.put("content", "ping");
            List<Map<String, Object>> messages = List.of(msg);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", cfg.get("model"));
            payload.put("messages", messages);
            payload.put("max_tokens", 5);

            String resp = postJson(completionsUrl(cfg.get("baseUrl")), cfg.get("apiKey"), payload,
                    "Authorization", "Bearer " + cfg.get("apiKey"));
            Map<String, Object> parsed = parseJsonObject(resp);
            List<?> choices = (List<?>) parsed.get("choices");
            String reply = choices == null || choices.isEmpty() ? "" :
                    String.valueOf(((Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message")).get("content"));
            result.put("reply", reply);
        }
        return result;
    }

    // ============ OpenAI 兼容协议调用 ============

    /** OpenAI 兼容：组装 payload 调用 /chat/completions，返回 {text, toolCalls, assistantMsg} */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callOpenAI(Map<String, String> cfg, List<Map<String, Object>> history, boolean withTools) throws Exception {
        List<Map<String, Object>> requestMessages = new ArrayList<>();
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", buildSystemPrompt());
        requestMessages.add(sys);
        requestMessages.addAll(history);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", cfg.get("model"));
        payload.put("messages", requestMessages);
        payload.put("max_tokens", 16384); // 思考模型输出上限较小会导致回答被截断、模型反复调工具，需显式设大
        if (withTools) {
            payload.put("tools", buildOpenAITools());
            payload.put("tool_choice", "auto");
        }
        Object thinking = buildThinkingOpenAI(cfg.get("thinking"));
        if (thinking != null) payload.put("thinking", thinking);

        String resp = postJson(completionsUrl(cfg.get("baseUrl")), cfg.get("apiKey"), payload,
                "Authorization", "Bearer " + cfg.get("apiKey"));
        Map<String, Object> parsed = parseJsonObject(resp);
        if (parsed.get("error") != null) throw new IllegalArgumentException("AI 接口返回错误: " + parsed.get("error"));

        Map<String, Object> choice = ((List<Map<String, Object>>) parsed.get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", message.get("content") == null ? "" : String.valueOf(message.get("content")));
        result.put("assistantMsg", message);
        if (toolCalls == null || toolCalls.isEmpty()) {
            result.put("toolCalls", List.of());
        } else {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (Map<String, Object> tc : toolCalls) {
                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", String.valueOf(tc.get("id")));
                call.put("name", String.valueOf(fn.get("name")));
                call.put("input", String.valueOf(fn.get("arguments")));
                calls.add(call);
            }
            result.put("toolCalls", calls);
        }
        return result;
    }

    /** OpenAI 兼容工具定义 */
    private List<Map<String, Object>> buildOpenAITools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> t1 = new LinkedHashMap<>();
        t1.put("type", "function");
        Map<String, Object> f1 = new LinkedHashMap<>();
        f1.put("name", "list_tables");
        f1.put("description", "列出数据库中所有表及其字段结构，了解系统数据模型");
        f1.put("parameters", Map.of("type", "object", "properties", Map.of()));
        t1.put("function", f1);
        tools.add(t1);

        Map<String, Object> t2 = new LinkedHashMap<>();
        t2.put("type", "function");
        Map<String, Object> f2 = new LinkedHashMap<>();
        f2.put("name", "query_data");
        f2.put("description", "执行只读 SQL 查询（仅 SELECT/WITH），结果最多返回 " + RESULT_LIMIT + " 行");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("sql", Map.of("type", "string", "description", "只读 SQL 语句，如 SELECT * FROM sales_order LIMIT 10"));
        f2.put("parameters", Map.of("type", "object", "properties", props, "required", List.of("sql")));
        t2.put("function", f2);
        tools.add(t2);

        return tools;
    }

    /** 思考强度 → OpenAI 协议参数（MiniMax-M3：adaptive/disabled；省略=默认开启思考） */
    private Object buildThinkingOpenAI(String thinking) {
        if ("off".equals(thinking)) return Map.of("type", "disabled");
        if ("deep".equals(thinking)) return null; // 省略 = 模型默认开启思考
        return Map.of("type", "adaptive"); // 自动
    }

    // ============ Anthropic 兼容协议调用 ============

    /** Anthropic 兼容：组装 payload 调用 /v1/messages，返回 {text, toolCalls, assistantMsg} */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callAnthropic(Map<String, String> cfg, List<Map<String, Object>> history, boolean withTools) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", cfg.get("model"));
        payload.put("max_tokens", 8192);
        payload.put("system", buildSystemPrompt());
        if (history.isEmpty()) {
            // 测试连接场景（无历史）给一条默认消息
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", "user");
            m.put("content", "ping");
            payload.put("messages", List.of(m));
        } else {
            payload.put("messages", history);
        }
        if (withTools) payload.put("tools", buildAnthropicTools());
        Object thinking = buildThinkingAnthropic(cfg.get("thinking"));
        if (thinking != null) payload.put("thinking", thinking);

        String resp = postJson(anthropicUrl(cfg.get("baseUrl")), cfg.get("apiKey"), payload,
                "x-api-key", cfg.get("apiKey"));
        Map<String, Object> parsed = parseJsonObject(resp);
        if (parsed.get("error") != null) throw new IllegalArgumentException("AI 接口返回错误: " + parsed.get("error"));

        List<Map<String, Object>> content = (List<Map<String, Object>>) parsed.get("content");
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        // 回传用的 assistant 消息：thinking 块（含 signature）与 tool_use 块必须原样保留
        // （MiniMax Interleaved Thinking 要求：工具调用多轮需保留完整 reasoning 内容，否则模型状态错乱反复调工具）
        List<Map<String, Object>> assistantContent = new ArrayList<>();
        for (Map<String, Object> block : content) {
            String type = String.valueOf(block.get("type"));
            if ("text".equals(type)) {
                text.append(block.get("text"));
                assistantContent.add(block);
            } else if ("tool_use".equals(type)) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", String.valueOf(block.get("id")));
                call.put("name", String.valueOf(block.get("name")));
                call.put("input", block.get("input"));
                toolCalls.add(call);
                assistantContent.add(block);
            } else if ("thinking".equals(type)) {
                assistantContent.add(block); // 思考块仅回传（模型需要），不展示
            }
        }

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", assistantContent);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text.toString());
        result.put("assistantMsg", assistantMsg);
        result.put("toolCalls", toolCalls);
        return result;
    }

    /** Anthropic 兼容工具定义 */
    private List<Map<String, Object>> buildAnthropicTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> t1 = new LinkedHashMap<>();
        t1.put("name", "list_tables");
        t1.put("description", "列出数据库中所有表及其字段结构，了解系统数据模型");
        t1.put("input_schema", Map.of("type", "object", "properties", Map.of()));
        tools.add(t1);

        Map<String, Object> t2 = new LinkedHashMap<>();
        t2.put("name", "query_data");
        t2.put("description", "执行只读 SQL 查询（仅 SELECT/WITH），结果最多返回 " + RESULT_LIMIT + " 行");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("sql", Map.of("type", "string", "description", "只读 SQL 语句，如 SELECT * FROM sales_order LIMIT 10"));
        t2.put("input_schema", Map.of("type", "object", "properties", props, "required", List.of("sql")));
        tools.add(t2);

        return tools;
    }

    /** 思考强度 → Anthropic 协议参数（省略=关闭思考；adaptive=自动；enabled+budget_tokens=深度） */
    private Object buildThinkingAnthropic(String thinking) {
        if ("off".equals(thinking)) return null;
        if ("deep".equals(thinking)) return Map.of("type", "enabled", "budget_tokens", 8192);
        return Map.of("type", "adaptive");
    }

    // ============ 通用：地址拼接 / 请求发送 / 响应解析 ============

    /** 拼接 OpenAI 兼容地址：裸地址 / 带 /v1 / 已含 /chat/completions */
    private String completionsUrl(String baseUrl) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/chat/completions")) return u;
        return u + "/chat/completions";
    }

    /** 拼接 Anthropic 兼容地址：/anthropic / /v1/messages / 裸地址 */
    private String anthropicUrl(String baseUrl) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/v1/messages")) return u;
        if (u.endsWith("/anthropic")) return u + "/v1/messages";
        return u + "/anthropic/v1/messages";
    }

    private String postJson(String url, String apiKey, Object payload, String authHeaderName, String authHeaderValue) {
        String body;
        try {
            body = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("构造请求失败: " + e.getMessage());
        }
        HttpResponse httpResp;
        try {
            var builder = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .timeout(120000)
                    .body(body);
            if (authHeaderName != null && authHeaderValue != null) {
                builder.header(authHeaderName, authHeaderValue);
            }
            httpResp = builder.execute();
        } catch (Exception e) {
            throw new IllegalArgumentException("调用 AI 接口失败（请检查接口地址与网络）: " + e.getMessage());
        }
        int status = httpResp.getStatus();
        String resp = httpResp.body();
        if (status < 200 || status >= 300) {
            throw new IllegalArgumentException("AI 接口返回 HTTP " + status
                    + (resp == null || resp.isBlank() ? "" : "：" + truncate(resp, 200))
                    + "，请检查接口地址是否正确");
        }
        if (resp == null || resp.isBlank()) {
            throw new IllegalArgumentException("AI 接口返回空响应");
        }
        return resp;
    }

    /**
     * 解析 AI 接口响应为 JSON 对象；响应不是 JSON 对象（如纯数字/字符串/HTML）时给出可操作提示，
     * 避免 Jackson 类型错误被当成"系统异常"
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String resp) {
        JsonNode node;
        try {
            node = mapper.readTree(resp);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 接口返回的不是有效 JSON，请检查接口地址是否正确。响应内容: " + truncate(resp, 200));
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("AI 接口返回格式异常（期望 JSON 对象，实际为 " + node.getNodeType()
                    + "），请检查接口地址是否正确。响应内容: " + truncate(resp, 200));
        }
        return mapper.convertValue(node, Map.class);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ============ 工具执行 ============

    private String runTool(String name, String arguments) throws Exception {
        return switch (name) {
            case "list_tables" -> listTables();
            case "query_data" -> {
                String sql = mapper.readTree(arguments).path("sql").asText("");
                if (sql.isBlank()) throw new IllegalArgumentException("query_data 缺少 sql 参数");
                yield queryData(sql);
            }
            default -> "未知工具: " + name;
        };
    }

    /** 列出所有业务表及字段（sqlite_master + PRAGMA table_info） */
    private String listTables() throws Exception {
        List<Map<String, Object>> tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
        List<Map<String, Object>> schema = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            String tableName = String.valueOf(t.get("name"));
            // v6.1：敏感表不进枚举（防模型构造拖库查询）
            if (java.util.Arrays.asList(SENSITIVE_TABLES).contains(tableName)
                    || tableName.matches("operation_log_\\d{6}")) continue;
            List<Map<String, Object>> cols = jdbc.queryForList("PRAGMA table_info(" + tableName + ")");
            List<String> colNames = cols.stream().map(c -> String.valueOf(c.get("name"))).toList();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("table", tableName);
            entry.put("columns", colNames);
            schema.add(entry);
        }
        return mapper.writeValueAsString(schema);
    }

    /** 执行只读 SQL（白名单校验 + LIMIT 兜底），结果转 JSON */
    private String queryData(String sql) throws Exception {
        validateReadOnly(sql);

        // v8.4（C5）：资源三道闸——原实现 LIMIT 兜底会被子查询里的 LIMIT 字样绕过、
        // 无查询超时（WITH RECURSIVE 可挂死连接池）、行数硬限不可靠
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        // 外层子查询硬限：无论内部怎么写，最多返回 RESULT_LIMIT 行（覆盖原语句自身 LIMIT 的绕过）
        String finalSql = "SELECT * FROM (" + trimmed + ") LIMIT " + RESULT_LIMIT;
        // 查询超时 10s（防递归 CTE / 笛卡尔积挂死）——finally 恢复，异常不残留全局状态
        jdbc.setQueryTimeout(10);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(finalSql);
            return mapper.writeValueAsString(rows);
        } finally {
            jdbc.setQueryTimeout(0);
        }
    }

    /** 只读校验：去掉字符串字面量后，语句必须 SELECT/WITH 开头，且不含写操作关键字 */
    /** v6.1.4：apiKey 掩码（前 4 后 4，中间 ***），短 Key 全掩 */
    private static String mask(String key) {
        if (key == null || key.length() <= 8) return key == null || key.isEmpty() ? "" : "****";
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }

    private void validateReadOnly(String sql) {
        // v6.1.3：超长 SQL（含递归 CTE 深度攻击）先行截断
        if (sql == null || sql.length() > 4000) {
            throw new IllegalArgumentException("SQL 过长（限 4000 字符）");
        }
        // v6.1.2：先归一化再匹配，堵住全部书写变体——
        // 块注释/行注释 → 空白；"xx"/[xx]/`xx`（SQLite 标识符引用）→ 裸标识符；'xx' 字符串字面量 → 空白；
        // 连续空白压缩为单空格。此前对原文正则匹配，FROM/**/sys_user、FROM"sys_user"、FROM[sys_user] 均可绕过
        String norm = sql
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ")
                .replaceAll("\"([^\"]+)\"", "$1")
                .replaceAll("\\[([^\\]]+)\\]", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("'(?:[^']|'')*'", " ")
                .replaceAll("\\s+", " ")
                .toUpperCase();
        String trimmed = norm.trim();
        if (!(trimmed.startsWith("SELECT") || trimmed.startsWith("WITH"))) {
            throw new IllegalArgumentException("仅允许只读查询（SQL 须以 SELECT/WITH 开头）");
        }
        String[] banned = {"INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "ATTACH", "DETACH", "VACUUM",
                "GRANT", "REVOKE", "REPLACE", "TRUNCATE", "PRAGMA", "BEGIN", "COMMIT", "ROLLBACK",
                "SAVEPOINT", "RELEASE"};
        for (String b : banned) {
            if (trimmed.matches("(?s).*\\b" + b + "\\b.*")) {
                throw new IllegalArgumentException("SQL 包含禁止的操作: " + b);
            }
        }
        // v6.1 安全：敏感表黑名单——AI 查询不得触碰用户凭证/密钥/薪酬/总账等（绕过字段级脱敏的拖库通道）
        // v6.1.3：归一化文本中任意位置出现敏感表名即拒（此前要求紧跟 FROM/JOIN，
        // 逗号连接 FROM a, sys_user 的第二表名绕过检查）
        for (String t : SENSITIVE_TABLES) {
            if (trimmed.matches("(?s).*\\b" + t + "\\b.*")) {
                throw new IllegalArgumentException("禁止查询敏感表: " + t + "（含用户凭证/密钥/薪酬/总账数据）");
            }
        }
        // v6.1.2：操作日志按月分表（operation_log_YYYYMM）整体入黑名单——listTables 已隐藏，查询侧同口径封堵
        if (trimmed.matches("(?s).*\\bOPERATION_LOG\\w*\\b.*")) {
            throw new IllegalArgumentException("禁止查询敏感表: operation_log_*（含登录凭证与操作明细）");
        }
        // v6.1.6：系统表字典（可读全部表名与建表 DDL）同样封堵
        if (trimmed.matches("(?s).*\\b(SQLITE_MASTER|SQLITE_SCHEMA|SQLITE_TEMP_MASTER)\\b.*")) {
            throw new IllegalArgumentException("禁止查询系统表字典");
        }
    }

    /** v6.1 AI 敏感表黑名单（listTables 枚举同样过滤） */
    private static final String[] SENSITIVE_TABLES = {
            "sys_user", "ai_config", "salary_sheet", "salary_item", "employee",
            "voucher", "voucher_entry", "account_subject", "account_period", "account_mapping",
            "asset", "asset_depreciation", "backup_meta"
    };


    private void ensureReady(Map<String, String> cfg) {
        if (!"true".equalsIgnoreCase(cfg.get("enabled"))) throw new IllegalArgumentException("AI 助手未启用，请先在 AI 设置中开启");
        if (cfg.get("baseUrl").isBlank() || cfg.get("apiKey").isBlank() || cfg.get("model").isBlank()) {
            throw new IllegalArgumentException("AI 未配置完整（地址/Key/模型），请先到 系统设置 → AI 设置 填写");
        }
    }

    private String str(Object v) { return v == null ? "" : String.valueOf(v); }
}
