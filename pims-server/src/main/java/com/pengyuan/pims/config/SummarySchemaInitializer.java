package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 汇总表触发器 & 索引初始化器
 * 应用启动时自动：
 * 1. 创建 SQLite 触发器（业务表写入时自动维护汇总数据）
 * 2. 创建查询加速索引
 * 3. 回填历史数据到汇总表（幂等，已有数据不重复插入）
 * 4. 确保财务汇总行存在
 */
@Component
@Order(100) // 在 DataInitializer 之后执行，确保表已创建
public class SummarySchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SummarySchemaInitializer.class);
    private final JdbcTemplate jdbc;
    /** v5.34：本次启动是否给 stat_material_usage 补了 warehouse_id 列（需要重建用量汇总） */
    private boolean usageWhColAdded = false;

    public SummarySchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // v4.8：幂等建 stat_* 汇总表（早期版本依赖手工建表，新环境部署缺表会导致触发器失败）
        ensureStatTables();
        // v5.55：时间戳归一化（须在触发器重建/汇总校验之前——此后触发器与报表查询统一按毫秒整数处理）
        normalizeTimestampColumns();
        createTriggers();
        createIndexes();
        ensureFinanceRow();
        // v4.8：物料用量汇总初始化（表为空时回填）
        ensureMaterialUsageStats();

        // 清理历史触发器写入的 period=NULL 脏行（create_time 毫秒兼容修复前遗留）
        jdbc.update("DELETE FROM stat_order_monthly WHERE period IS NULL");
        // 首次运行（汇总表为空）→ 全量回填；否则 → 轻量校验，偏差时才重建
        // 注意：回填/重建必须在归档之前执行，保证统计基于全量异动（含将归档的历史行）
        Integer orderStatCount = jdbc.queryForObject("SELECT COUNT(*) FROM stat_order_monthly", Integer.class);
        if (orderStatCount == null || orderStatCount == 0) {
            backfillHistory();
            log.info("汇总表初始化完成：触发器、索引、历史回填就绪");
        } else {
            verifyAndRepair();
            log.info("汇总表校验完成：触发器、索引就绪");
        }

        // v4.8：归档 2 年前的库存异动（追溯查询会兼容归档表）
        archiveOldMovements();
        // v4.8：WAL checkpoint 收缩 + 月度 VACUUM 碎片回收
        maintenance();

        // v5.6：幂等补录"货到付款"付款类型（DataInitializer 仅在字典为空时初始化，存量库需补录）
        ensurePaymentTermsDict();
    }

    /**
     * v5.55 时间戳格式归一化：历史库 create_time/update_time 混存「毫秒整数 / 字符串(yyyy-MM-dd HH:mm:ss)」
     * （旧版 JPA 写字符串、现版写毫秒），报表查询被迫把列包进 date() 做双格式兼容，索引全部失效。
     * 通用扫描所有业务表的时间列，字符串行按北京时间墙钟折算为毫秒。幂等：整数/NULL 不动、不可解析跳过并告警。
     * operation_log* 月表 create_time 是刻意的文本设计，排除。
     */
    private void normalizeTimestampColumns() {
        var tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'operation_log%' AND name NOT LIKE 'sqlite_%'");
        int totalFixed = 0;
        for (var t : tables) {
            String table = String.valueOf(t.get("name"));
            for (var c : jdbc.queryForList("PRAGMA table_info(" + table + ")")) {
                String col = String.valueOf(c.get("name"));
                if (!"create_time".equals(col) && !"update_time".equals(col)) continue;
                int updated = jdbc.update("UPDATE " + table + " SET " + col +
                        " = 1000 * (CAST(strftime('%s', " + col + ") AS INTEGER) - 28800)" +
                        " WHERE typeof(" + col + ") = 'text' AND strftime('%s', " + col + ") IS NOT NULL");
                if (updated > 0) {
                    log.info("时间戳归一化: {}.{} {} 行 text → 毫秒", table, col, updated);
                    totalFixed += updated;
                }
                Integer bad = jdbc.queryForObject("SELECT COUNT(*) FROM " + table +
                        " WHERE typeof(" + col + ") = 'text'", Integer.class);
                if (bad != null && bad > 0) {
                    log.warn("时间戳归一化: {}.{} 有 {} 行不可解析的 text 时间值未转换，请人工核查", table, col, bad);
                }
            }
        }
        if (totalFixed > 0) {
            log.info("时间戳归一化完成: 共 {} 行转为毫秒整数（此后报表查询不再函数包列，可走索引）", totalFixed);
        }
    }

    /**
     * 幂等补录字典：payment_terms 货到付款(COD)
     * 货到付款：货到（收货）才付款——应收/应付到期日按收货/发货当日计算
     */
    private void ensurePaymentTermsDict() {
        var exists = jdbc.queryForList(
                "SELECT id FROM dict_item WHERE type='payment_terms' AND value='COD'");
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO dict_item (type, label, value, sort_order, enabled, create_time) " +
                    "VALUES ('payment_terms', '货到付款', 'COD', 8, 1, ?)", System.currentTimeMillis());
            log.info("字典补录: payment_terms 货到付款(COD)");
        }
    }

    // ==================== v4.8：汇总表结构 & 运维 ====================

    /**
     * 幂等创建全部 stat_* 汇总表（表已存在则跳过，字段由实体/触发器按列名访问）。
     * 修复早期版本汇总表仅靠手工建表、新环境部署即失败的问题。
     */
    private void ensureStatTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS stat_order_monthly (
                id integer PRIMARY KEY AUTOINCREMENT,
                period varchar(7) not null,
                order_type varchar(20) not null,
                order_count integer not null,
                total_amount numeric(16,2) not null
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS stat_inventory_daily (
                id integer PRIMARY KEY AUTOINCREMENT,
                stat_date varchar(10) not null,
                material_code varchar(30) not null,
                warehouse_id varchar(20) not null,
                in_qty numeric(14,3) not null,
                out_qty numeric(14,3) not null,
                in_amount numeric(16,2) not null
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS stat_finance_summary (
                id bigint not null primary key,
                ar_total numeric(16,2) not null,
                ar_received numeric(16,2) not null,
                ap_total numeric(16,2) not null,
                ap_paid numeric(16,2) not null,
                update_time timestamp
            )
        """);
        // 物料用量月度汇总（低库存预警数据源，触发器维护，避免报表全扫 inventory_movement）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS stat_material_usage (
                id integer PRIMARY KEY AUTOINCREMENT,
                material_code varchar(30) not null,
                period varchar(7) not null,
                out_qty numeric(14,3) not null,
                usage_days integer not null
            )
        """);
        // v5.34：用量增加仓库维度（低库存预警按仓库计算）——旧库补列 + 迁移唯一索引
        var usageCols = jdbc.queryForList("PRAGMA table_info(stat_material_usage)");
        boolean usageHasWh = usageCols.stream().anyMatch(c -> "warehouse_id".equals(c.get("name")));
        if (!usageHasWh) {
            jdbc.execute("ALTER TABLE stat_material_usage ADD COLUMN warehouse_id varchar(20)");
            usageWhColAdded = true;
        }
        // 仅当存在旧定义索引（不含 warehouse_id）时才 DROP 重建（v5.34 迁移的一次性动作，不再每次启动都做 DDL）
        var oldIdx = jdbc.queryForList(
                "SELECT sql FROM sqlite_master WHERE type='index' AND name='uk_stat_material_usage'");
        if (!oldIdx.isEmpty() && !String.valueOf(oldIdx.get(0).get("sql")).contains("warehouse_id")) {
            jdbc.execute("DROP INDEX IF EXISTS uk_stat_material_usage");
        }
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_stat_material_usage ON stat_material_usage(material_code, period, warehouse_id)");
        // 运维标记表（VACUUM 周期等）
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_maintenance (key_name TEXT PRIMARY KEY, value_text TEXT)");
        // 库存异动归档表（结构与 inventory_movement 一致，供追溯查询 UNION 兼容）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS inventory_movement_archive (
                id integer PRIMARY KEY,
                doc_type varchar(20) not null,
                doc_no varchar(30) not null,
                material_code varchar(30) not null,
                batch_no varchar(30),
                warehouse_id varchar(20) not null,
                direction varchar(5) not null,
                qty numeric(14,3) not null,
                qty_before numeric(14,3),
                qty_after numeric(14,3),
                ownership_type varchar(20) not null,
                operator varchar(50),
                remark varchar(500),
                create_time timestamp
            )
        """);
        log.info("stat_* 汇总表结构就绪");
    }

    /**
     * 归档 1 年前的库存异动到 inventory_movement_archive（幂等；v5.9 由 2 年缩短为 1 年——异动表只增不减，
     * 保留在线一年足够批次追溯，stat_* 汇总已固化不受影响，缩短后在线表体积更小查询更快）。
     * 追溯查询（批号溯源）通过 UNION 兼容归档数据，功能不受影响。
     */
    private void archiveOldMovements() {
        String cutoff = LocalDate.now().minusYears(1).toString();
        // v8.0（P0-12）：SELECT * 改显式列名——两表列序已实测不一致（movement 第2列 batch_no、archive 第2列 doc_type），
        // SELECT * 首次触发（2027-08）即全列错位写入且源行被删，是确定性数据事故。列名对齐 InventoryMovementArchiveService。
        int moved = jdbc.update("""
            INSERT OR REPLACE INTO inventory_movement_archive (id, doc_type, doc_no, material_code, batch_no,
                warehouse_id, location_id, direction, qty, qty_before, qty_after, ownership_type, operator, remark, create_time)
            SELECT id, doc_type, doc_no, material_code, batch_no,
                warehouse_id, location_id, direction, qty, qty_before, qty_after, ownership_type, operator, remark, create_time
            FROM inventory_movement
            WHERE date(CAST(create_time AS INTEGER) / 1000, 'unixepoch') < ?
            """, cutoff);
        if (moved > 0) {
            jdbc.update("""
                DELETE FROM inventory_movement
                WHERE date(CAST(create_time AS INTEGER) / 1000, 'unixepoch') < ?
                """, cutoff);
            log.info("历史库存异动归档: {} 行移至 inventory_movement_archive（早于 {}）", moved, cutoff);
        }
    }

    /**
     * 启动维护：WAL checkpoint 截断（防止 WAL 无限膨胀）+ 月度 VACUUM（回收删除产生的碎片页）。
     * VACUUM 条件：库文件 ≥ 50MB 且距上次执行超过 30 天（库小时自动跳过，避免每次启动全量重写文件）。
     */
    private void maintenance() {
        try {
            jdbc.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (Exception e) {
            log.warn("WAL checkpoint 失败: {}", e.getMessage());
        }
        try {
            Long pages = jdbc.queryForObject("SELECT page_count FROM pragma_page_count", Long.class);
            Long pageSize = jdbc.queryForObject("SELECT page_size FROM pragma_page_size", Long.class);
            long sizeMB = (pages == null ? 0 : pages) * (pageSize == null ? 4096 : pageSize) / 1024 / 1024;
            String lastVacuum = null;
            try {
                lastVacuum = jdbc.queryForObject("SELECT value_text FROM sys_maintenance WHERE key_name = 'last_vacuum'", String.class);
            } catch (EmptyResultDataAccessException ignored) { /* 首次运行无记录 */ }
            if (sizeMB >= 50 && (lastVacuum == null || lastVacuum.compareTo(LocalDate.now().minusMonths(1).toString()) < 0)) {
                jdbc.execute("VACUUM");
                jdbc.update("INSERT INTO sys_maintenance (key_name, value_text) VALUES (?, ?) " +
                                "ON CONFLICT(key_name) DO UPDATE SET value_text = excluded.value_text",
                        "last_vacuum", LocalDate.now().toString());
                log.info("VACUUM 完成: 库文件 {} MB", sizeMB);
            }
        } catch (Exception e) {
            log.warn("VACUUM 检查跳过: {}", e.getMessage());
        }
    }

    /**
     * 轻量一致性校验：对比财务汇总值与源表实际值
     * 偏差超过 0.01 则自动重建（覆盖新模块遗漏触发器的场景）
     */
    private void verifyAndRepair() {
        try {
            Double summaryAr = jdbc.queryForObject(
                    "SELECT ar_total FROM stat_finance_summary WHERE id = 1", Double.class);
            Double actualAr = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM accounts_receivable", Double.class);
            Double summaryAp = jdbc.queryForObject(
                    "SELECT ap_total FROM stat_finance_summary WHERE id = 1", Double.class);
            Double actualAp = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM accounts_payable", Double.class);

            double arDrift = Math.abs((summaryAr != null ? summaryAr : 0) - (actualAr != null ? actualAr : 0));
            double apDrift = Math.abs((summaryAp != null ? summaryAp : 0) - (actualAp != null ? actualAp : 0));

            if (arDrift > 0.01 || apDrift > 0.01) {
                log.warn("汇总数据偏差超阈值(AR偏差={}, AP偏差={})，自动重建", arDrift, apDrift);
                backfillHistory();
            }
        } catch (Exception e) {
            log.warn("汇总校验异常，执行全量重建: {}", e.getMessage());
            backfillHistory();
        }
    }

    // ==================== 触发器 ====================

    private void createTriggers() {
        // 汇总表唯一索引（ON CONFLICT 依赖）
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_stat_order_monthly ON stat_order_monthly(period, order_type)");

        // ---------- 采购订单 ----------
        jdbc.execute("DROP TRIGGER IF EXISTS trg_po_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_po_insert AFTER INSERT ON purchase_order
            WHEN NEW.status != 'DRAFT'
            BEGIN
                INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
                VALUES (strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours'), 'PURCHASE', 1, COALESCE(NEW.total_amount, 0))
                ON CONFLICT(period, order_type) DO UPDATE SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.total_amount, 0);
            END
        """);

        jdbc.execute("DROP TRIGGER IF EXISTS trg_po_update");
        jdbc.execute("""
            CREATE TRIGGER trg_po_update AFTER UPDATE ON purchase_order
            BEGIN
                -- 状态从DRAFT变为有效：计数+1，金额加入
                UPDATE stat_order_monthly SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.total_amount, 0)
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'PURCHASE'
                AND OLD.status = 'DRAFT' AND NEW.status != 'DRAFT';

                -- 状态从有效变为DRAFT：计数-1，金额减去
                UPDATE stat_order_monthly SET
                    order_count = order_count - 1,
                    total_amount = total_amount - COALESCE(OLD.total_amount, 0)
                WHERE period = strftime('%Y-%m', CAST(OLD.create_time AS INTEGER)/1000, 'unixepoch') AND order_type = 'PURCHASE'
                AND OLD.status != 'DRAFT' AND NEW.status = 'DRAFT';

                -- 金额变化（状态未变且非DRAFT）
                UPDATE stat_order_monthly SET
                    total_amount = total_amount + (COALESCE(NEW.total_amount, 0) - COALESCE(OLD.total_amount, 0))
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'PURCHASE'
                AND OLD.status = NEW.status AND NEW.status != 'DRAFT'
                AND COALESCE(NEW.total_amount, 0) != COALESCE(OLD.total_amount, 0);
            END
        """);

        // ---------- 销售订单 ----------
        jdbc.execute("DROP TRIGGER IF EXISTS trg_so_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_so_insert AFTER INSERT ON sales_order
            WHEN NEW.status != 'DRAFT'
            BEGIN
                INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
                VALUES (strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours'), 'SALES', 1, COALESCE(NEW.total_amount, 0))
                ON CONFLICT(period, order_type) DO UPDATE SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.total_amount, 0);
            END
        """);

        jdbc.execute("DROP TRIGGER IF EXISTS trg_so_update");
        jdbc.execute("""
            CREATE TRIGGER trg_so_update AFTER UPDATE ON sales_order
            BEGIN
                UPDATE stat_order_monthly SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.total_amount, 0)
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'SALES'
                AND OLD.status = 'DRAFT' AND NEW.status != 'DRAFT';

                UPDATE stat_order_monthly SET
                    order_count = order_count - 1,
                    total_amount = total_amount - COALESCE(OLD.total_amount, 0)
                WHERE period = strftime('%Y-%m', CAST(OLD.create_time AS INTEGER)/1000, 'unixepoch') AND order_type = 'SALES'
                AND OLD.status != 'DRAFT' AND NEW.status = 'DRAFT';

                UPDATE stat_order_monthly SET
                    total_amount = total_amount + (COALESCE(NEW.total_amount, 0) - COALESCE(OLD.total_amount, 0))
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'SALES'
                AND OLD.status = NEW.status AND NEW.status != 'DRAFT'
                AND COALESCE(NEW.total_amount, 0) != COALESCE(OLD.total_amount, 0);
            END
        """);

        // ---------- 委外工单（金额字段为 processing_fee） ----------
        jdbc.execute("DROP TRIGGER IF EXISTS trg_oo_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_oo_insert AFTER INSERT ON outsource_order
            WHEN NEW.status != 'DRAFT'
            BEGIN
                INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
                VALUES (strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours'), 'OUTSOURCE', 1, COALESCE(NEW.processing_fee, 0))
                ON CONFLICT(period, order_type) DO UPDATE SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.processing_fee, 0);
            END
        """);

        jdbc.execute("DROP TRIGGER IF EXISTS trg_oo_update");
        jdbc.execute("""
            CREATE TRIGGER trg_oo_update AFTER UPDATE ON outsource_order
            BEGIN
                UPDATE stat_order_monthly SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.processing_fee, 0)
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'OUTSOURCE'
                AND OLD.status = 'DRAFT' AND NEW.status != 'DRAFT';

                UPDATE stat_order_monthly SET
                    order_count = order_count - 1,
                    total_amount = total_amount - COALESCE(OLD.processing_fee, 0)
                WHERE period = strftime('%Y-%m', CAST(OLD.create_time AS INTEGER)/1000, 'unixepoch') AND order_type = 'OUTSOURCE'
                AND OLD.status != 'DRAFT' AND NEW.status = 'DRAFT';

                UPDATE stat_order_monthly SET
                    total_amount = total_amount + (COALESCE(NEW.processing_fee, 0) - COALESCE(OLD.processing_fee, 0))
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'OUTSOURCE'
                AND OLD.status = NEW.status AND NEW.status != 'DRAFT'
                AND COALESCE(NEW.processing_fee, 0) != COALESCE(OLD.processing_fee, 0);
            END
        """);

        // ---------- 财务：应收 ----------
        jdbc.execute("DROP TRIGGER IF EXISTS trg_ar_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_ar_insert AFTER INSERT ON accounts_receivable
            BEGIN
                UPDATE stat_finance_summary SET
                    ar_total = ar_total + COALESCE(NEW.amount, 0),
                    ar_received = ar_received + COALESCE(NEW.received_amount, 0),
                    update_time = datetime('now', '+8 hours')
                WHERE id = 1;
            END
        """);

        jdbc.execute("DROP TRIGGER IF EXISTS trg_ar_update");
        jdbc.execute("""
            CREATE TRIGGER trg_ar_update AFTER UPDATE ON accounts_receivable
            BEGIN
                UPDATE stat_finance_summary SET
                    ar_total = ar_total + (COALESCE(NEW.amount, 0) - COALESCE(OLD.amount, 0)),
                    ar_received = ar_received + (COALESCE(NEW.received_amount, 0) - COALESCE(OLD.received_amount, 0)),
                    update_time = datetime('now', '+8 hours')
                WHERE id = 1;
            END
        """);

        // ---------- 财务：应付 ----------
        jdbc.execute("DROP TRIGGER IF EXISTS trg_ap_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_ap_insert AFTER INSERT ON accounts_payable
            BEGIN
                UPDATE stat_finance_summary SET
                    ap_total = ap_total + COALESCE(NEW.amount, 0),
                    ap_paid = ap_paid + COALESCE(NEW.paid_amount, 0),
                    update_time = datetime('now', '+8 hours')
                WHERE id = 1;
            END
        """);

        jdbc.execute("DROP TRIGGER IF EXISTS trg_ap_update");
        jdbc.execute("""
            CREATE TRIGGER trg_ap_update AFTER UPDATE ON accounts_payable
            BEGIN
                UPDATE stat_finance_summary SET
                    ap_total = ap_total + (COALESCE(NEW.amount, 0) - COALESCE(OLD.amount, 0)),
                    ap_paid = ap_paid + (COALESCE(NEW.paid_amount, 0) - COALESCE(OLD.paid_amount, 0)),
                    update_time = datetime('now', '+8 hours')
                WHERE id = 1;
            END
        """);

        // ---------- 生产订单 ----------
        jdbc.execute("DROP TRIGGER IF EXISTS trg_mo_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_mo_insert AFTER INSERT ON production_order
            WHEN NEW.status != 'DRAFT'
            BEGIN
                INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
                VALUES (strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours'), 'PRODUCTION', 1, COALESCE(NEW.batch_qty, 0))
                ON CONFLICT(period, order_type) DO UPDATE SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.batch_qty, 0);
            END
        """);

        jdbc.execute("DROP TRIGGER IF EXISTS trg_mo_update");
        jdbc.execute("""
            CREATE TRIGGER trg_mo_update AFTER UPDATE ON production_order
            BEGIN
                UPDATE stat_order_monthly SET
                    order_count = order_count + 1,
                    total_amount = total_amount + COALESCE(NEW.batch_qty, 0)
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'PRODUCTION'
                AND OLD.status = 'DRAFT' AND NEW.status != 'DRAFT';

                UPDATE stat_order_monthly SET
                    order_count = order_count - 1,
                    total_amount = total_amount - COALESCE(OLD.batch_qty, 0)
                WHERE period = strftime('%Y-%m', CAST(OLD.create_time AS INTEGER)/1000, 'unixepoch') AND order_type = 'PRODUCTION'
                AND OLD.status != 'DRAFT' AND NEW.status = 'DRAFT';

                UPDATE stat_order_monthly SET
                    total_amount = total_amount + (COALESCE(NEW.batch_qty, 0) - COALESCE(OLD.batch_qty, 0))
                WHERE period = strftime('%Y-%m', CAST(NEW.create_time AS INTEGER)/1000, 'unixepoch', '+8 hours') AND order_type = 'PRODUCTION'
                AND OLD.status = NEW.status AND NEW.status != 'DRAFT'
                AND COALESCE(NEW.batch_qty, 0) != COALESCE(OLD.batch_qty, 0);
            END
        """);

        // ---------- 库存异动 ----------
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_stat_inv_daily ON stat_inventory_daily(stat_date, material_code, warehouse_id)");
        jdbc.execute("DROP TRIGGER IF EXISTS trg_movement_insert");
        jdbc.execute("""
            CREATE TRIGGER trg_movement_insert AFTER INSERT ON inventory_movement
            BEGIN
                INSERT INTO stat_inventory_daily (stat_date, material_code, warehouse_id, in_qty, out_qty, in_amount)
                VALUES (
                    COALESCE(date(CAST(NEW.create_time AS INTEGER) / 1000, 'unixepoch'), date('now', '+8 hours')),
                    NEW.material_code,
                    NEW.warehouse_id,
                    CASE WHEN NEW.direction = 'IN' THEN NEW.qty ELSE 0 END,
                    CASE WHEN NEW.direction = 'OUT' THEN ABS(NEW.qty) ELSE 0 END,
                    0
                )
                ON CONFLICT(stat_date, material_code, warehouse_id) DO UPDATE SET
                    in_qty = in_qty + CASE WHEN NEW.direction = 'IN' THEN NEW.qty ELSE 0 END,
                    out_qty = out_qty + CASE WHEN NEW.direction = 'OUT' THEN ABS(NEW.qty) ELSE 0 END;
            END
        """);

        // ---------- v4.8 物料用量月度汇总（低库存预警数据源） ----------
        // 口径与低库存预警报表一致：出库用量 = PRODUCTION_OUT/OUTSOURCE_OUT/OTHER_OUT/SALES_OUT（调拨、盘盈亏不计入）
        // v6.8 口径补充：REWORK_OUT 返工出库不计入——已耗料再利用（消耗已在首次领料计入），隔离库存不计可用，分子分母一致
        // v5.34：增加仓库维度（material_code, period, warehouse_id），低库存预警按仓库计算
        // usage_days 按「该物料该仓库该月有出库记录的去重天数」维护：同月同日同仓第二条记录不再 +1
        jdbc.execute("DROP TRIGGER IF EXISTS trg_movement_usage");
        jdbc.execute("""
            CREATE TRIGGER trg_movement_usage AFTER INSERT ON inventory_movement
            WHEN NEW.direction = 'OUT' AND NEW.doc_type IN ('PRODUCTION_OUT','OUTSOURCE_OUT','OTHER_OUT','SALES_OUT')
            BEGIN
                INSERT INTO stat_material_usage (material_code, period, warehouse_id, out_qty, usage_days)
                VALUES (
                    NEW.material_code,
                    COALESCE(strftime('%Y-%m', CAST(NEW.create_time AS INTEGER) / 1000, 'unixepoch'), strftime('%Y-%m', 'now', '+8 hours')),
                    COALESCE(NEW.warehouse_id, ''),
                    ABS(NEW.qty),
                    1
                )
                ON CONFLICT(material_code, period, warehouse_id) DO UPDATE SET
                    out_qty = out_qty + ABS(NEW.qty),
                    usage_days = usage_days + CASE WHEN EXISTS (
                        SELECT 1 FROM inventory_movement m2
                        WHERE m2.material_code = NEW.material_code
                          AND COALESCE(m2.warehouse_id, '') = COALESCE(NEW.warehouse_id, '')
                          AND m2.direction = 'OUT'
                          AND m2.doc_type IN ('PRODUCTION_OUT','OUTSOURCE_OUT','OTHER_OUT','SALES_OUT')
                          AND date(CAST(m2.create_time AS INTEGER) / 1000, 'unixepoch')
                              = date(CAST(NEW.create_time AS INTEGER) / 1000, 'unixepoch')
                          AND m2.rowid != NEW.rowid
                    ) THEN 0 ELSE 1 END;
            END
        """);

        log.info("已创建 14 个 SQLite 触发器（订单×8 + 财务×4 + 库存×2）");
    }

    // ==================== 索引 ====================

    private void createIndexes() {
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_po_create_time ON purchase_order(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_po_status ON purchase_order(status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_so_create_time ON sales_order(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_so_status ON sales_order(status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_oo_create_time ON outsource_order(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_material ON inventory_movement(material_code)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_warehouse ON inventory_movement(warehouse_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_create_time ON inventory_movement(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stat_inv_material ON stat_inventory_daily(material_code)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stat_inv_date ON stat_inventory_daily(stat_date)");

        // ===== v4.8 性能优化新增索引（复合索引覆盖热路径查询） =====
        // 库存台账 4 元组定位（每次出入库 getOrCreateLedger 的核心查询）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ledger_lookup ON inventory_ledger(material_code, batch_no, warehouse_id, location_id)");
        // 批号追溯（溯源/批次查询）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_batch ON inventory_movement(material_code, batch_no, create_time)");
        // 用量聚合（低库存预警/报表，按 docType 过滤出库）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_usage ON inventory_movement(material_code, doc_type, direction, create_time)");
        // 参照订单过滤 / 订单状态流转（findByProductionOrderNoAndStatus 等）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_prod_out_order ON production_outbound(production_order_no, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_prod_in_order ON production_inbound(production_order_no, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_os_out_order ON outsource_material_outbound(outsource_order_no, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_os_in_order ON outsource_finish_inbound(outsource_order_no, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sales_out_order ON sales_outbound(sales_order_no, status)");

        // ===== v5.9 报表性能索引（报表 SQL 按日期过滤 + 状态分组，缺索引时全表扫） =====
        // 质检报表：月度单数/判定分布（按月过滤 + status 分组）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_qc_status_time ON quality_inspection(status, create_time)");
        // 采购报表/比价：按月过滤采购记录
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_raw_pur_date ON raw_material_purchase(purchase_date, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fin_pur_date ON finished_product_purchase(purchase_date, status)");
        // 账龄/趋势报表：未结清过滤 + 按月分组
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ar_time_status ON accounts_receivable(create_time, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ap_time_status ON accounts_payable(create_time, status)");
        // 销售报表：出库单按月过滤（join 订单明细）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sales_out_time ON sales_outbound(create_time, status)");
        // 订单执行 join：sales_outbound.sales_order_no → sales_order.order_no → sales_order_item.order_id
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_so_item_order ON sales_order_item(order_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_po_item_order ON production_order_item(order_id)");
        // 收付款月度统计
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_pay_recv_time ON payment_receipt(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_pay_disb_time ON payment_disbursement(create_time)");
        // 库龄分层/呆滞排行（按入库日期过滤）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ledger_inbound ON inventory_ledger(inbound_date)");
        // 到货明细查询（按采购单号反查）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_arrival_ref ON purchase_arrival(ref_order_no)");
        // 其他出入库单按月过滤
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_other_in_time ON other_inbound(create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_other_out_time ON other_outbound(create_time)");

        // ===== v5.54 性能摸排补齐（客户对账单/发票/明细装载等此前全表扫的高频查询） =====
        // 客户维度：对账单/客户360°/应收总表（此前 accounts_receivable.customer_id 无索引全表扫）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ar_customer ON accounts_receivable(customer_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ap_supplier ON accounts_payable(supplier_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_receipt_customer ON payment_receipt(customer_id, receipt_date)");
        // 退货关联：对账单退货匹配子查询逐行探测 AR
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_return_type_status ON return_order(type, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_return_so ON return_order(sales_order_no)");
        // 订单客户列：客户360°/最近成交价
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_so_customer ON sales_order(customer_id)");
        // 生产订单状态过滤（其余订单表都有，唯生产订单漏建；车间看板高频）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_prod_order_status_time ON production_order(status, create_time)");
        // 发票模块（此前零二级索引）：红冲防悬挂反查 + 按往来方/日期过滤
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_invoice_flush ON invoice(flush_doc_no)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_invoice_partner ON invoice(partner_type, partner_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_invoice_date ON invoice(invoice_date)");
        // 单据号反查异动（出入库详情页热路径）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_doc ON inventory_movement(doc_no)");
        // 明细表外键（质检单带明细/采购、委外订单明细装载）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_qc_item_order ON quality_inspection_item(inspection_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_pur_item_order ON purchase_order_item(order_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_os_item_order ON outsource_order_item(order_id)");
        log.info("已创建查询加速索引");
    }

    // ==================== 初始化 & 回填 ====================

    /** 确保财务汇总表有且仅有一行 */
    private void ensureFinanceRow() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stat_finance_summary", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO stat_finance_summary (id, ar_total, ar_received, ap_total, ap_paid, update_time) " +
                    "VALUES (1, 0, 0, 0, 0, datetime('now','+8 hours'))");
        }
    }

    /**
     * 物料用量汇总初始化：表为空时从历史异动回填（非空说明已有触发器持续维护，跳过）
     * v5.34：新增仓库维度——补列或存在无仓库维度旧行时全量重建
     */
    private void ensureMaterialUsageStats() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stat_material_usage", Integer.class);
        Integer nullWh = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stat_material_usage WHERE warehouse_id IS NULL", Integer.class);
        boolean empty = count == null || count == 0;
        if (empty || usageWhColAdded || (nullWh != null && nullWh > 0)) {
            jdbc.update("DELETE FROM stat_material_usage");
            backfillMaterialUsage();
        }
    }

    /** 物料用量月度汇总回填（口径与触发器一致：仅统计四类出库异动；主表+归档表合并统计，归档后重建不丢历史用量；v5.34 按仓库分组） */
    private void backfillMaterialUsage() {
        int n = jdbc.update("""
            INSERT INTO stat_material_usage (material_code, period, warehouse_id, out_qty, usage_days)
            SELECT material_code,
                   COALESCE(
                       strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch'),
                       strftime('%Y-%m', CAST(create_time AS INTEGER) / 1000, 'unixepoch'),
                       strftime('%Y-%m', 'now', '+8 hours')
                   ),
                   COALESCE(warehouse_id, ''),
                   COALESCE(SUM(ABS(qty)), 0),
                   COUNT(DISTINCT date(CAST(create_time AS INTEGER) / 1000, 'unixepoch'))
            FROM (
                SELECT * FROM inventory_movement
                UNION ALL
                SELECT * FROM inventory_movement_archive
            )
            WHERE direction = 'OUT' AND doc_type IN ('PRODUCTION_OUT','OUTSOURCE_OUT','OTHER_OUT','SALES_OUT')
            GROUP BY material_code, COALESCE(warehouse_id, ''),
                     COALESCE(
                         strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch'),
                         strftime('%Y-%m', CAST(create_time AS INTEGER) / 1000, 'unixepoch'),
                         strftime('%Y-%m', 'now', '+8 hours')
                     )
        """);
        log.info("物料用量汇总回填完成（含仓库维度）: {} 条", n);
    }

    /** 回填历史数据（幂等：先清空再重建） */
    public void backfillHistory() {
        // 清空汇总表
        jdbc.update("DELETE FROM stat_order_monthly");
        jdbc.update("DELETE FROM stat_inventory_daily");
        jdbc.update("DELETE FROM stat_material_usage");

        // 回填订单月度统计
        jdbc.update("""
            INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
            SELECT strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch'), 'PURCHASE', COUNT(*), COALESCE(SUM(total_amount), 0)
            FROM purchase_order WHERE status != 'DRAFT' AND strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch') IS NOT NULL
            GROUP BY strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch')
        """);
        jdbc.update("""
            INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
            SELECT strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch'), 'SALES', COUNT(*), COALESCE(SUM(total_amount), 0)
            FROM sales_order WHERE status != 'DRAFT' AND strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch') IS NOT NULL
            GROUP BY strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch')
        """);
        jdbc.update("""
            INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
            SELECT strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch'), 'OUTSOURCE', COUNT(*), COALESCE(SUM(processing_fee), 0)
            FROM outsource_order WHERE status != 'DRAFT' AND strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch') IS NOT NULL
            GROUP BY strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch')
        """);
        jdbc.update("""
            INSERT INTO stat_order_monthly (period, order_type, order_count, total_amount)
            SELECT strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch'), 'PRODUCTION', COUNT(*), COALESCE(SUM(batch_qty), 0)
            FROM production_order WHERE status != 'DRAFT' AND strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch') IS NOT NULL
            GROUP BY strftime('%Y-%m', CAST(create_time AS INTEGER)/1000, 'unixepoch')
        """);

        // 回填库存日汇总
        jdbc.update("""
            INSERT INTO stat_inventory_daily (stat_date, material_code, warehouse_id, in_qty, out_qty, in_amount)
            SELECT COALESCE(
                       date(create_time),
                       date(CAST(create_time AS INTEGER) / 1000, 'unixepoch'),
                       date('now', '+8 hours')
                   ),
                   material_code, warehouse_id,
                   COALESCE(SUM(CASE WHEN direction = 'IN' THEN qty ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN direction = 'OUT' THEN ABS(qty) ELSE 0 END), 0),
                   0
            FROM inventory_movement
            GROUP BY COALESCE(
                       date(create_time),
                       date(CAST(create_time AS INTEGER) / 1000, 'unixepoch'),
                       date('now', '+8 hours')
                     ), material_code, warehouse_id
        """);

        // 回填物料用量月度汇总
        backfillMaterialUsage();

        // 回填财务汇总
        jdbc.update("""
            UPDATE stat_finance_summary SET
                ar_total = (SELECT COALESCE(SUM(amount), 0) FROM accounts_receivable),
                ar_received = (SELECT COALESCE(SUM(received_amount), 0) FROM accounts_receivable),
                ap_total = (SELECT COALESCE(SUM(amount), 0) FROM accounts_payable),
                ap_paid = (SELECT COALESCE(SUM(paid_amount), 0) FROM accounts_payable),
                update_time = datetime('now', '+8 hours')
            WHERE id = 1
        """);

        log.info("历史数据回填完成");
    }
}
