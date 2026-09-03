package com.pengyuan.pims.config;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengyuan.pims.entity.User;
import com.pengyuan.pims.repository.UserRepository;
import com.pengyuan.pims.service.OperationLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 操作日志过滤器：记录所有写操作（POST/PUT/DELETE，含登录成功/失败）
 * - 请求参数与响应体经 ContentCaching 包装捕获（截断 + 敏感字段脱敏）
 * - 日志入内存队列异步落库，不阻塞业务
 */
@Component
public class OperationLogFilter extends OncePerRequestFilter {

    private static final int PARAMS_MAX = 800;
    private static final int RESP_MAX = 400;

    private final OperationLogService logService;
    private final UserRepository userRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public OperationLogFilter(OperationLogService logService, UserRepository userRepo) {
        this.logService = logService;
        this.userRepo = userRepo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) return true; // 只记录 API
        String method = request.getMethod();
        // 仅写操作（POST/PUT/DELETE）；GET 查询类不记录，控制数据量
        return !("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedReq = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        boolean failed = false;
        try {
            chain.doFilter(wrappedReq, wrappedRes);
        } catch (Exception e) {
            failed = true;
            // 异常未被全局处理器捕获时兜底记录（正常业务异常已被 GlobalExceptionHandler 转为 JSON 响应）
            logService.record(buildEntry(request, wrappedReq, 500, "异常: " + e.getMessage(), null, start));
            throw e;
        } finally {
            if (!failed) { // 已正常处理的响应（含业务错误 4xx/401）记录；500 已在 catch 中记录避免重复
                String respBody = new String(wrappedRes.getContentAsByteArray(), StandardCharsets.UTF_8);
                // v6.1 安全：响应体 token 脱敏（登录响应含 pims-token，明文入库可被 log:read 者冒用身份）
                // v6.1.1 修复：源码 "\s" 编译为空格字符而非正则空白类，改 \\s（对紧凑/带换行 JSON 均生效）
                respBody = respBody.replaceAll("(?i)(\"(?:token|pims-token)\"\\s*:\\s*\")[^\"\\s]+(\"|)", "$1***$2");
                logService.record(buildEntry(request, wrappedReq, wrappedRes.getStatus(),
                        truncate(respBody, RESP_MAX), respBody, start));
            }
            wrappedRes.copyBodyToResponse();
        }
    }

    private OperationLogService.LogEntry buildEntry(HttpServletRequest request, ContentCachingRequestWrapper wrappedReq,
                                                    int status, String resultMsg, String fullRespBody, long start) {
        OperationLogService.LogEntry entry = new OperationLogService.LogEntry();
        entry.method = request.getMethod();
        entry.path = request.getRequestURI();
        entry.module = OperationLogService.resolveModule(request.getRequestURI());
        entry.action = OperationLogService.resolveAction(request.getMethod(), request.getRequestURI());
        entry.params = extractParams(request, wrappedReq);
        entry.bizNo = extractBizNo(entry.params, request.getRequestURI(), fullRespBody);
        entry.detail = buildDetail(entry.params);
        entry.resultCode = status;
        entry.resultMsg = resultMsg;
        entry.durationMs = System.currentTimeMillis() - start;
        entry.ip = clientIp(request);

        // 当前登录用户（登录接口在登录前，从请求体取 username）
        String username = entry.params != null && entry.params.contains("\"username\"")
                ? extractJsonField(entry.params, "username") : null;
        if (username == null || username.isBlank()) {
            try {
                long uid = StpUtil.getLoginIdAsLong();
                User u = userRepo.findById(uid).orElse(null);
                if (u != null) {
                    entry.username = u.username;
                    entry.realName = u.realName;
                }
            } catch (Exception ignored) { /* 未登录 */ }
        } else {
            entry.username = username;
        }
        return entry;
    }

    /** 提取请求体（截断 + 敏感字段脱敏）；multipart 上传不解析 body */
    private String extractParams(HttpServletRequest request, ContentCachingRequestWrapper wrappedReq) {
        String ct = request.getContentType();
        if (ct != null && ct.toLowerCase().contains("multipart")) return "[文件上传]";
        byte[] body = wrappedReq.getContentAsByteArray();
        if (body == null || body.length == 0) return null;
        String params = new String(body, StandardCharsets.UTF_8);
        // 敏感字段脱敏（password/apiKey 等）
        params = params.replaceAll("(?i)(\"(?:password|apiKey|oldPassword|newPassword|confirmPassword|pwd)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        return truncate(params, PARAMS_MAX);
    }

    /**
     * v5.61 提取业务单号：① 请求体单号字段（编辑/删除场景）② 响应 data 里的单号字段（新增场景——
     * 单号由后端生成在响应里，此前只查请求体导致新增单据恒无单号）③ 路径中的数字 id。
     */
    private String extractBizNo(String params, String path, String respBody) {
        if (params != null) {
            try {
                String no = findBizNoField(mapper.readTree(params));
                if (no != null) return no;
            } catch (Exception ignored) { /* 非 JSON 参数跳过 */ }
        }
        if (respBody != null && !respBody.isBlank()) {
            try {
                var node = mapper.readTree(respBody);
                var data = node.get("data");
                if (data != null && data.isArray() && !data.isEmpty()) data = data.get(0);
                String no = findBizNoField(data);
                if (no != null) return no;
            } catch (Exception ignored) { /* 非 JSON 响应跳过 */ }
        }
        String[] seg = path.split("/");
        for (int i = seg.length - 1; i >= 3; i--) {
            if (seg[i].matches("\\d+")) return "id:" + seg[i];
        }
        return null;
    }

    /** 单号字段清单（覆盖系统全部单据类型），返回第一个非空值 */
    private String findBizNoField(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isObject()) return null;
        for (String f : new String[]{"traceNo", "docNo", "orderNo", "inspectionNo", "quotationNo",
                "invoiceNo", "complaintNo", "sampleNo", "purchaseOrderNo", "documentNo", "doc_no", "docCode", "code"}) {
            var v = node.get(f);
            if (v != null && !v.asText("").isBlank()) return v.asText();
        }
        return null;
    }

    /** v5.61 业务摘要：从请求体提取关键要素拼一句人话（客户/供应商/物料/数量/金额/批号/类型），截断 200 */
    private String buildDetail(String params) {
        if (params == null || params.isBlank()) return null;
        try {
            var node = mapper.readTree(params);
            if (!node.isObject()) return null;
            StringBuilder sb = new StringBuilder();
            appendField(sb, node, "客户", "customerName", "partnerName");
            appendField(sb, node, "供应商", "supplierName", "processor");
            appendField(sb, node, "物料", "materialName", "productName");
            appendField(sb, node, "数量", "qty", "batchQty");
            appendField(sb, node, "金额", "amount", "totalAmount", "lossAmount", "compensationAmount", "returnAmount");
            appendField(sb, node, "批号", "batchNo");
            appendField(sb, node, "类型", "category", "resultType", "expenseType", "direction");
            String s = sb.toString();
            return s.isBlank() ? null : truncate(s, 200);
        } catch (Exception e) {
            return null;
        }
    }

    private void appendField(StringBuilder sb, com.fasterxml.jackson.databind.JsonNode node, String label, String... fields) {
        for (String f : fields) {
            var v = node.get(f);
            if (v != null && !v.isNull() && !v.asText("").isBlank()) {
                if (sb.length() > 0) sb.append('，');
                sb.append(label).append('：').append(v.asText());
                return;
            }
        }
    }

    private String extractJsonField(String json, String field) {
        try {
            var node = mapper.readTree(json).get(field);
            return node == null ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        // v6.1.6：内网直连部署无反向代理，X-Forwarded-For 可被客户端任意伪造（刷假 IP 进审计日志）——
        // 以 TCP 对端地址为准；日后上反代时在网关层覆写 XFF 并改回信任链
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
