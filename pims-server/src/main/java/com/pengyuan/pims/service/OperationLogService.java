package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 操作日志服务
 * 设计要点（大数据量优化）：
 * 1. 按月分表 operation_log_YYYYMM —— 30 天数据天然分片，每张表始终很小
 * 2. 异步批量写入 —— 业务线程只入内存队列（零阻塞），后台每 2s 批量落库（SQLite 写经 WriteQueue 串行）
 * 3. 归档 = 表改名 —— 超过 30 天的月表 RENAME TO operation_log_archive_YYYYMM，秒级 DDL 零数据搬运
 * 4. 归档保留 12 个月，到期 DROP
 * 5. 查询强制时间范围（默认近 30 天、上限 93 天），按时间路由月表，避免全表扫描
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final int QUEUE_CAPACITY = 5000;
    private static final int BATCH_SIZE = 200;
    private static final int ARCHIVE_DAYS = 30;          // 热数据保留 30 天
    private static final int ARCHIVE_RETENTION_MONTHS = 12; // 归档保留 12 个月
    private static final int MAX_QUERY_DAYS = 93;        // 单次查询最大时间跨度（防全表扫描）

    private final JdbcTemplate jdbc;
    private final WriteQueue writeQueue;
    private final BlockingQueue<LogEntry> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    /** 本进程已 ensure 过的月表（flush 每 2s 热路径不再重复跑幂等 DDL，跨月/首见时才执行） */
    private final java.util.Set<String> ensuredTables = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public OperationLogService(JdbcTemplate jdbc, WriteQueue writeQueue) {
        this.jdbc = jdbc;
        this.writeQueue = writeQueue;
    }

    /** 启动时执行一次归档（幂等，@Scheduled 每日凌晨还会执行） */
    @PostConstruct
    public void init() {
        try {
            // v5.54：给存量月表/归档表补 module 索引（module 是前端筛选下拉的常用精确条件，此前只有 time/user 索引）
            // v5.61：热表统一补列（含 detail）——跨月 UNION 查询 SELECT 明确列名，要求全部热表列对齐
            for (String t : listHotTables()) {
                ensureColumns(t);
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + t + "_module ON " + t + "(module)");
            }
            for (String t : listArchiveTables()) {
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + t + "_module ON " + t + "(module)");
            }
            archiveExpiredTables();
        } catch (Exception e) {
            log.warn("启动归档检查失败: {}", e.getMessage());
        }
    }

    /** 日志条目 */
    public static class LogEntry {
        public String username;
        public String realName;
        public String method;
        public String path;
        public String module;   // 单据/模块（如 销售订单、物料）
        public String action;   // 动作（如 新增/修改/删除/审核）
        public String bizNo;    // 业务单号（如 SO-20260826-0001，或 id:12）
        public String detail;   // v5.61 业务摘要（客户/供应商/物料/数量/金额/批号/类型）
        public String params;
        public int resultCode;
        public String resultMsg;
        public long durationMs;
        public String ip;
        public LocalDateTime time = LocalDateTime.now();
    }

    /** 业务线程调用：入队（队列满则丢弃，不阻塞业务） */
    public void record(LogEntry entry) {
        if (!queue.offer(entry)) {
            long d = dropped.incrementAndGet();
            if (d % 1000 == 0) log.warn("操作日志队列已满，已丢弃 {} 条", d);
        }
    }

    // ============ 批量落库 ============

    /** 后台定时 flush：批量取出 → 按表分组 → 串行批量 INSERT */
    @Scheduled(fixedDelay = 2000)
    public void flush() {
        List<LogEntry> batch = new ArrayList<>();
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) return;

        // 按目标月表分组
        Map<String, List<LogEntry>> byTable = new LinkedHashMap<>();
        for (LogEntry e : batch) {
            byTable.computeIfAbsent("operation_log_" + e.time.format(MONTH_FMT), k -> new ArrayList<>()).add(e);
        }

        writeQueue.executeTx(() -> {
            for (var group : byTable.entrySet()) {
                String table = group.getKey();
                ensureTable(table);
                List<LogEntry> rows = group.getValue();
                StringBuilder sql = new StringBuilder(
                        "INSERT INTO " + table + " (username, real_name, method, path, module, action, biz_no, detail, params, result_code, result_msg, duration_ms, ip, create_time) VALUES ");
                List<Object> args = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                    LogEntry e = rows.get(i);
                    args.add(e.username);
                    args.add(e.realName);
                    args.add(e.method);
                    args.add(e.path);
                    args.add(e.module);
                    args.add(e.action);
                    args.add(e.bizNo);
                    args.add(e.detail);
                    args.add(e.params);
                    args.add(e.resultCode);
                    args.add(e.resultMsg);
                    args.add(e.durationMs);
                    args.add(e.ip);
                    args.add(e.time.format(TIME_FMT));
                }
                jdbc.update(sql.toString(), args.toArray());
            }
        });
    }

    /** 幂等建表 + 兼容旧表（PRAGMA 检查缺列则 ALTER ADD）；每张表本进程内只 ensure 一次（DDL 失败不入缓存，下次重试） */
    private void ensureTable(String table) {
        if (ensuredTables.contains(table)) return;
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT, real_name TEXT, method TEXT, path TEXT," +
                "module TEXT, action TEXT, biz_no TEXT, detail TEXT," +
                "params TEXT, result_code INTEGER, result_msg TEXT," +
                "duration_ms INTEGER, ip TEXT, create_time TEXT)");
        ensureColumns(table);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_time ON " + table + "(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_user ON " + table + "(username)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_module ON " + table + "(module)");
        ensuredTables.add(table);
    }

    /** v5.61 补列（module/action/biz_no/detail）：存量表缺哪列补哪列（跨月 UNION 查询要求列对齐，所有热表启动时统一补） */
    private void ensureColumns(String table) {
        List<String> cols = jdbc.queryForList("PRAGMA table_info(" + table + ")")
                .stream().map(c -> String.valueOf(c.get("name"))).toList();
        for (String col : List.of("module", "action", "biz_no", "detail")) {
            if (!cols.contains(col)) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " TEXT");
        }
    }

    // ============ 归档（30 天热数据 → 归档表改名） ============

    /** 每日凌晨 3 点归档；启动时也执行一次 */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void archiveTask() {
        archiveExpiredTables();
    }

    /** 归档过期月表 + 清理超期归档表（幂等，可随时调用） */
    public void archiveExpiredTables() {
        List<String> tables = listHotTables();
        for (String table : tables) {
            String month = table.substring("operation_log_".length());
            if (isMonthExpired(month, ARCHIVE_DAYS)) {
                String archiveTable = "operation_log_archive_" + month;
                writeQueue.executeTx(() -> {
                    jdbc.execute("ALTER TABLE " + table + " RENAME TO " + archiveTable);
                    log.info("操作日志归档: {} -> {}", table, archiveTable);
                });
            }
        }
        // 清理超过保留期的归档表
        List<String> archives = listArchiveTables();
        for (String table : archives) {
            String month = table.substring("operation_log_archive_".length());
            if (isMonthExpired(month, ARCHIVE_RETENTION_MONTHS * 31)) {
                writeQueue.executeTx(() -> {
                    jdbc.execute("DROP TABLE " + table);
                    log.info("操作日志归档清理: {} 已删除", table);
                });
            }
        }
    }

    /** 月份是否已过期：该月最后一天距今超过 days 天 */
    private boolean isMonthExpired(String yyyyMM, int days) {
        LocalDate monthEnd = LocalDate.parse(yyyyMM + "01", DateTimeFormatter.ofPattern("yyyyMMdd"))
                .plusMonths(1).minusDays(1);
        return ChronoUnit.DAYS.between(monthEnd, LocalDate.now()) > days;
    }

    /** 热数据月表（operation_log_YYYYMM，GLOB 精确匹配，避免把归档表当热表） */
    private List<String> listHotTables() {
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'operation_log_[0-9][0-9][0-9][0-9][0-9][0-9]'")
                .stream().map(m -> String.valueOf(m.get("name"))).sorted().toList();
    }

    private List<String> listArchiveTables() {
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'operation_log_archive_%'")
                .stream().map(m -> String.valueOf(m.get("name"))).sorted().toList();
    }

    // ============ 查询 ============

    /** 热数据查询：按时间路由月表（最多跨 4 个月），强制时间范围 */
    public Map<String, Object> search(String username, String method, String path, String module,
                                      String action, String bizNo,
                                      LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(ARCHIVE_DAYS);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        if (start.isAfter(end)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(start, end) > MAX_QUERY_DAYS) {
            throw new IllegalArgumentException("查询时间范围不能超过 " + MAX_QUERY_DAYS + " 天");
        }

        List<String> tables = monthTablesInRange(start, end);
        if (tables.isEmpty()) return Map.of("rows", List.of(), "total", 0L);

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        buildWhere(where, args, username, method, path, module, action, bizNo, start, end);

        String union = String.join(" UNION ALL ", tables.stream().map(t ->
                "SELECT id, username, real_name, method, path, module, action, biz_no, detail, params, result_code, result_msg, duration_ms, ip, create_time FROM " + t).toList());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + union + " WHERE 1=1" + where + ")",
                Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) (page - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM (" + union + " WHERE 1=1" + where + ") ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        return result;
    }

    /** 归档月份列表（倒序，最新在前） */
    public List<Map<String, Object>> archives() {        List<Map<String, Object>> result = new ArrayList<>();
        for (String table : listArchiveTables()) {
            String month = table.substring("operation_log_archive_".length());
            Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("count", cnt);
            result.add(m);
        }
        result.sort((a, b) -> String.valueOf(b.get("month")).compareTo(String.valueOf(a.get("month"))));
        return result;
    }

    /** 查询指定归档月 */
    public Map<String, Object> searchArchive(String month, String username, String method, String path, String module,
                                             String action, String bizNo, int page, int pageSize) {
        String table = "operation_log_archive_" + month;
        if (!listArchiveTables().contains(table)) {
            throw new IllegalArgumentException("归档月份不存在: " + month);
        }
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        buildWhere(where, args, username, method, path, module, action, bizNo, null, null);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE 1=1" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) (page - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE 1=1" + where + " ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        return result;
    }

    /** 时间范围覆盖到的月表（含边界月份，最多 4 张） */
    private List<String> monthTablesInRange(LocalDate start, LocalDate end) {
        List<String> tables = new ArrayList<>();
        LocalDate cursor = start.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            String table = "operation_log_" + cursor.format(MONTH_FMT);
            if (listHotTables().contains(table)) tables.add(table);
            cursor = cursor.plusMonths(1);
        }
        return tables;
    }

    private void buildWhere(StringBuilder where, List<Object> args, String username, String method,
                            String path, String module, String action, String bizNo, LocalDate start, LocalDate end) {
        if (username != null && !username.isBlank()) {
            where.append(" AND username LIKE ?");
            args.add("%" + username.trim() + "%");
        }
        if (method != null && !method.isBlank()) {
            where.append(" AND method = ?");
            args.add(method.trim().toUpperCase());
        }
        if (path != null && !path.isBlank()) {
            where.append(" AND path LIKE ?");
            args.add("%" + path.trim() + "%");
        }
        if (module != null && !module.isBlank()) {
            where.append(" AND module = ?");
            args.add(module.trim());
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ?");
            args.add(action.trim());
        }
        if (bizNo != null && !bizNo.isBlank()) {
            where.append(" AND biz_no LIKE ?");
            args.add("%" + bizNo.trim() + "%");
        }
        if (start != null) {
            where.append(" AND create_time >= ?");
            args.add(start.atStartOfDay().format(TIME_FMT));
        }
        if (end != null) {
            where.append(" AND create_time <= ?");
            args.add(end.plusDays(1).atStartOfDay().format(TIME_FMT));
        }
    }

    // ============ 单据模块映射（Filter 语义化记录用） ============

    /** /api/ 首段 → 单据模块中文名 */
    private static final Map<String, String> MODULES = new LinkedHashMap<>();
    static {
        MODULES.put("auth", "登录认证");
        MODULES.put("user", "用户");
        MODULES.put("role", "角色权限");
        MODULES.put("dict", "数据字典");
        MODULES.put("coding-rule", "编码规则");
        MODULES.put("supplier", "供应商");
        MODULES.put("customer", "客户");
        MODULES.put("material", "物料");
        MODULES.put("warehouse", "仓库");
        MODULES.put("inventory", "库存");
        MODULES.put("recipe", "配方");
        MODULES.put("production-order", "生产订单");
        MODULES.put("purchase-order", "采购订单");
        MODULES.put("raw-material-purchase", "原料采购");
        MODULES.put("finished-product-purchase", "成品采购");
        MODULES.put("purchase-arrival", "采购到货");
        MODULES.put("return-order", "采购退货");
        MODULES.put("sales-order", "销售订单");
        MODULES.put("sales-return", "销售退货");
        MODULES.put("outsource-order", "委外订单");
        MODULES.put("stock-check", "盘库");
        MODULES.put("qc", "质检");
        MODULES.put("ai", "AI 智能助手");
        MODULES.put("log", "操作日志");
        MODULES.put("finance", "财务");
        MODULES.put("meeting", "周度会议");
        MODULES.put("crm", "CRM 客户经营");
        MODULES.put("finance-report", "财务报表");
        MODULES.put("invoice", "发票");
        MODULES.put("expense", "费用");
        MODULES.put("advance", "预收预付");
        MODULES.put("cost", "成本核算");
        MODULES.put("outbound", "出库");
        MODULES.put("quality-trace", "质量追溯");
        MODULES.put("complaint", "客户投诉");
        MODULES.put("sample", "打样样品");
        MODULES.put("tailing-return", "油尾退回");
        MODULES.put("voucher", "会计凭证");        // v5.61
        MODULES.put("account-subject", "会计科目");     // v5.61
        MODULES.put("salary", "工资核算");          // v5.62
        MODULES.put("employee", "员工档案");        // v5.62
        MODULES.put("asset", "固定资产");          // v5.62
        MODULES.put("costing", "存货计价");        // v5.63
        MODULES.put("shipping", "物流运费");       // v5.66
        MODULES.put("task", "任务督办");       // v5.67
    }

    /** 从请求路径解析单据模块（含 outbound/finance 二级路径） */
    public static String resolveModule(String path) {
        String[] seg = path.split("/");
        if (seg.length < 3) return null;
        String first = seg[2];
        String second = seg.length > 3 ? seg[3] : "";
        if ("outbound".equals(first)) {
            return switch (second) {
                case "production" -> "生产出库";
                case "sales" -> "销售出库";
                case "outsource" -> "委外出库";
                case "other" -> "其他出库";
                case "production-inbound" -> "生产入库";
                case "outsource-inbound" -> "委外入库";
                case "other-inbound" -> "其他入库";
                default -> "出库";
            };
        }
        if ("finance".equals(first)) {
            return switch (second) {
                case "receipt" -> "收款单";
                case "disbursement" -> "付款单";
                case "ar" -> "应收款";
                case "ap" -> "应付款";
                default -> "财务";
            };
        }
        return MODULES.getOrDefault(first, first);
    }

    /** 从请求路径 + 方法解析动作（含审核/反审核/确认等业务动作） */
    public static String resolveAction(String method, String path) {
        String p = path.toLowerCase();
        if (p.endsWith("/login")) return "登录";
        if (p.endsWith("/logout")) return "登出";
        if (p.contains("reverse-audit")) return "反审核";
        if (p.contains("/audit")) return "审核";
        if (p.contains("/judge")) return "质检判定";
        if (p.contains("/confirm")) return "确认";
        if (p.contains("reset-password")) return "重置密码";
        if (p.contains("/close")) return "关闭";
        if (p.contains("/pay")) return "付款";
        if (p.contains("/receive")) return "收款";
        if (p.contains("/upload")) return "上传";
        if (p.contains("/generate")) return "生成";
        return switch (method) {
            case "POST" -> "新增";
            case "PUT" -> "修改";
            case "DELETE" -> "删除";
            default -> method;
        };
    }

    /** 可筛选的单据模块列表（前端下拉用） */
    public List<Map<String, String>> modules() {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(Map.of("value", "", "label", "全部"));
        for (var e : MODULES.entrySet()) {
            result.add(Map.of("value", e.getValue(), "label", e.getValue()));
        }
        result.add(Map.of("value", "生产出库", "label", "生产出库"));
        result.add(Map.of("value", "销售出库", "label", "销售出库"));
        result.add(Map.of("value", "委外出库", "label", "委外出库"));
        result.add(Map.of("value", "其他出库", "label", "其他出库"));
        result.add(Map.of("value", "生产入库", "label", "生产入库"));
        result.add(Map.of("value", "委外入库", "label", "委外入库"));
        result.add(Map.of("value", "其他入库", "label", "其他入库"));
        result.add(Map.of("value", "收款单", "label", "收款单"));
        result.add(Map.of("value", "付款单", "label", "付款单"));
        return result;
    }
}
