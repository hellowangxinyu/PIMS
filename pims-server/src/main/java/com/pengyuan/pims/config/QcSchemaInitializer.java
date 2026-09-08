package com.pengyuan.pims.config;

import com.pengyuan.pims.service.QualityInspectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 质检模块表结构初始化（ddl-auto=none 后手动维护）
 * 仅在表/列不存在时创建，幂等安全
 */
@Component
@Order(2)
public class QcSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QcSchemaInitializer.class);
    private final JdbcTemplate jdbc;
    private final QualityInspectionService qcService;

    public QcSchemaInitializer(JdbcTemplate jdbc, QualityInspectionService qcService) {
        this.jdbc = jdbc;
        this.qcService = qcService;
    }

    @Override
    public void run(String... args) {
        // 创建质检单表
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS quality_inspection (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inspection_no VARCHAR(30) NOT NULL UNIQUE,
                type VARCHAR(20) NOT NULL,
                ref_doc_no VARCHAR(30),
                ref_doc_type VARCHAR(30),
                material_code VARCHAR(30),
                material_name VARCHAR(100),
                batch_no VARCHAR(30),
                qty DECIMAL(14,3),
                unit VARCHAR(10),
                warehouse_id VARCHAR(20),
                location_id VARCHAR(20),
                unit_price DECIMAL(14,2),
                produce_date DATE,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                inspector VARCHAR(50),
                inspect_date DATE,
                result_remark VARCHAR(500),
                created_by VARCHAR(50),
                remark VARCHAR(500),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);

        // material 表新增 shelf_life_days 列
        var materialCols = jdbc.queryForList("PRAGMA table_info(material)");
        boolean hasShelfLife = materialCols.stream().anyMatch(m -> "shelf_life_days".equals(m.get("name")));
        if (!hasShelfLife) {
            jdbc.execute("ALTER TABLE material ADD COLUMN shelf_life_days INTEGER");
            log.info("质检表结构：material 新增 shelf_life_days 列");
        }

        // material 表新增 brand_owner 列（v4.5：成品物料品牌归属，自产默认"芃远"，外购为成品供应商名称）
        boolean hasBrandOwner = materialCols.stream().anyMatch(m -> "brand_owner".equals(m.get("name")));
        if (!hasBrandOwner) {
            jdbc.execute("ALTER TABLE material ADD COLUMN brand_owner VARCHAR(100)");
            // 已有成品物料默认填"芃远"
            jdbc.execute("UPDATE material SET brand_owner = '芃远' WHERE category = 'C' AND (brand_owner IS NULL OR brand_owner = '')");
            log.info("表结构：material 新增 brand_owner 列，已有成品物料默认填「芃远」");
        }

        // material 表新增 alternative_codes 列（v5.1：平替物料编码，逗号分隔，仅原材料；配方树中可直接切换）
        boolean hasAlternativeCodes = materialCols.stream().anyMatch(m -> "alternative_codes".equals(m.get("name")));
        if (!hasAlternativeCodes) {
            jdbc.execute("ALTER TABLE material ADD COLUMN alternative_codes VARCHAR(500)");
            log.info("表结构：material 新增 alternative_codes 列（平替物料）");
        }

        // inventory_ledger 表新增 produce_date / expiry_date / inbound_date 列
        var ledgerCols = jdbc.queryForList("PRAGMA table_info(inventory_ledger)");
        boolean hasProduceDate = ledgerCols.stream().anyMatch(m -> "produce_date".equals(m.get("name")));
        if (!hasProduceDate) {
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN produce_date DATE");
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN expiry_date DATE");
            log.info("质检表结构：inventory_ledger 新增 produce_date / expiry_date 列");
        }
        boolean hasInboundDate = ledgerCols.stream().anyMatch(m -> "inbound_date".equals(m.get("name")));
        if (!hasInboundDate) {
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN inbound_date DATE");
            log.info("质检表结构：inventory_ledger 新增 inbound_date 列");
        }

        // inventory_ledger 表新增质检信息持久化列（qc_status / qc_inspection_no / qc_result / qc_inspector / qc_date）
        boolean hasQcStatus = ledgerCols.stream().anyMatch(m -> "qc_status".equals(m.get("name")));
        if (!hasQcStatus) {
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN qc_status VARCHAR(20)");
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN qc_inspection_no VARCHAR(30)");
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN qc_result VARCHAR(500)");
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN qc_inspector VARCHAR(50)");
            jdbc.execute("ALTER TABLE inventory_ledger ADD COLUMN qc_date DATE");
            log.info("质检表结构：inventory_ledger 新增 qc_status / qc_inspection_no / qc_result / qc_inspector / qc_date 列");
        }

        // v6.1.2：quality_inspection 新增 arrival_id 列——质检单与到货单精确关联
        // （同订单同物料分批到货各生成一张质检单，反审核按 arrivalId 隔离，不误删他批待检单）
        boolean hasArrivalId = jdbc.queryForList("PRAGMA table_info(quality_inspection)")
                .stream().anyMatch(m -> "arrival_id".equals(m.get("name")));
        if (!hasArrivalId) {
            jdbc.execute("ALTER TABLE quality_inspection ADD COLUMN arrival_id BIGINT");
            // 历史回填：INCOMING 单按 订单号+物料 关联最新一张已审核到货单（近似关联，好于 NULL）
            jdbc.update("""
                UPDATE quality_inspection SET arrival_id =
                    (SELECT MAX(pa.id) FROM purchase_arrival pa
                     WHERE pa.ref_order_no = quality_inspection.ref_doc_no
                       AND pa.material_code = quality_inspection.material_code
                       AND pa.status = 'APPROVED')
                WHERE arrival_id IS NULL AND type = 'INCOMING'
                """);
            log.info("质检表结构：quality_inspection 新增 arrival_id 列（到货关联）");
        }

        // quality_inspection 新增 material_category 列（v5.0：材料/半成品/成品分类 Tab 用）
        var qcCols = jdbc.queryForList("PRAGMA table_info(quality_inspection)");
        boolean hasMaterialCategory = qcCols.stream().anyMatch(m -> "material_category".equals(m.get("name")));
        if (!hasMaterialCategory) {
            jdbc.execute("ALTER TABLE quality_inspection ADD COLUMN material_category VARCHAR(5)");
            // 历史回填：优先按物料主档匹配；匹配不到按编码首字符推断（编码规则：首字符即大类 A/P/F/R/S/B/C）
            jdbc.update("""
                UPDATE quality_inspection SET material_category =
                    (SELECT category FROM material WHERE material.code = quality_inspection.material_code)
                WHERE material_category IS NULL
                  AND material_code IS NOT NULL AND material_code != ''
                  AND EXISTS (SELECT 1 FROM material WHERE material.code = quality_inspection.material_code)
                """);
            jdbc.update("""
                UPDATE quality_inspection SET material_category = CASE
                    WHEN substr(material_code, 1, 1) IN ('A','P','F','R','S') THEN substr(material_code, 1, 1)
                    WHEN substr(material_code, 1, 1) = 'B' THEN 'B'
                    WHEN substr(material_code, 1, 1) = 'C' THEN 'C'
                    ELSE 'A'
                END
                WHERE material_category IS NULL
                  AND material_code IS NOT NULL AND material_code != ''
                """);
            log.info("质检表结构：quality_inspection 新增 material_category 列并回填历史分类");
        }

        log.info("质检表结构：quality_inspection 就绪");

        // 历史数据迁移：出厂质检(OUTGOING)统一改为来料质检(INCOMING)
        // 业务模型修正——所有质检均为入库前质检，不存在出厂质检
        int outdated = jdbc.update("UPDATE quality_inspection SET type='INCOMING' WHERE type='OUTGOING'");
        if (outdated > 0) {
            log.info("历史数据迁移：{} 条 OUTGOING 质检单已转为 INCOMING", outdated);
        }

        // 历史数据迁移：入库单据状态对齐新模型
        // CONFIRMED 且无待检质检单 → 已入库(DONE)
        // 覆盖：改造前直接入库的老单据(无质检单) + 质检已合格但未回写的单据
        String[] inboundTables = {
            "production_inbound:PRODUCTION_INBOUND",
            "outsource_finish_inbound:OUTSOURCE_INBOUND"
        };
        for (String entry : inboundTables) {
            String[] parts = entry.split(":");
            String table = parts[0];
            String refType = parts[1];
            int updated = jdbc.update("UPDATE " + table + " SET status='DONE' WHERE status='CONFIRMED' " +
                    "AND doc_no NOT IN (SELECT ref_doc_no FROM quality_inspection " +
                    "WHERE ref_doc_type='" + refType + "' AND status='PENDING')");
            if (updated > 0) {
                log.info("历史数据迁移：{} 表 {} 条 CONFIRMED 单据已转为 DONE(已入库)", table, updated);
            }
        }
        // 其他入库：历史 CONFIRMED(已确认/已入库) 统一为 DONE
        int otherInUpdated = jdbc.update("UPDATE other_inbound SET status='DONE' WHERE status='CONFIRMED'");
        if (otherInUpdated > 0) {
            log.info("历史数据迁移：other_inbound 表 {} 条 CONFIRMED 单据已转为 DONE(已入库)", otherInUpdated);
        }

        // ===== 财务模块表结构迁移 =====
        migrateFinanceSchema();

        // 历史数据补偿：已审核到货记录回填应付账款(AP)
        // 采购到货审核即应产生AP，历史已审核但未生成AP的单据在此补录
        backfillAccountsPayable();
    }

    /**
     * 财务模块表结构迁移：幂等添加新列、创建新表
     */
    private void migrateFinanceSchema() {
        // 1. outsource_order 表新增 supplier_id / processing_fee 列
        try {
            var ooCols = jdbc.queryForList("PRAGMA table_info(outsource_order)");
            if (!ooCols.stream().anyMatch(c -> "supplier_id".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE outsource_order ADD COLUMN supplier_id BIGINT");
                log.info("财务表结构：outsource_order 新增 supplier_id 列");
            }
            if (!ooCols.stream().anyMatch(c -> "processing_fee".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE outsource_order ADD COLUMN processing_fee DECIMAL(12,2)");
                log.info("财务表结构：outsource_order 新增 processing_fee 列");
            }
        } catch (Exception e) {
            log.warn("迁移 outsource_order 列失败: {}", e.getMessage());
        }

        // 2. accounts_payable 表新增 outsource_order_no 列
        try {
            var apCols = jdbc.queryForList("PRAGMA table_info(accounts_payable)");
            if (!apCols.stream().anyMatch(c -> "outsource_order_no".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE accounts_payable ADD COLUMN outsource_order_no VARCHAR(20)");
                log.info("财务表结构：accounts_payable 新增 outsource_order_no 列");
            }
        } catch (Exception e) {
            log.warn("迁移 accounts_payable 列失败: {}", e.getMessage());
        }

        // 3. 创建 payment_receipt 表（收款单）
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS payment_receipt (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    ar_id BIGINT,
                    ar_doc_no VARCHAR(20),
                    customer_id BIGINT,
                    customer_name VARCHAR(50),
                    amount DECIMAL(14,2) NOT NULL,
                    method VARCHAR(20) NOT NULL DEFAULT 'BANK',
                    bank_account VARCHAR(50),
                    receipt_date DATE NOT NULL,
                    operator VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP
                )
            """);
        } catch (Exception e) {
            log.warn("创建 payment_receipt 表失败: {}", e.getMessage());
        }

        // 4. 创建 payment_disbursement 表（付款单）
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS payment_disbursement (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    ap_id BIGINT,
                    ap_doc_no VARCHAR(20),
                    supplier_id BIGINT NOT NULL,
                    supplier_name VARCHAR(50),
                    amount DECIMAL(14,2) NOT NULL,
                    method VARCHAR(20) NOT NULL DEFAULT 'BANK',
                    bank_account VARCHAR(50),
                    pay_date DATE NOT NULL,
                    operator VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP
                )
            """);
        } catch (Exception e) {
            log.warn("创建 payment_disbursement 表失败: {}", e.getMessage());
        }

        // 5. other_inbound 表新增财务字段
        try {
            var oiCols = jdbc.queryForList("PRAGMA table_info(other_inbound)");
            if (!oiCols.stream().anyMatch(c -> "gen_finance".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE other_inbound ADD COLUMN gen_finance BOOLEAN DEFAULT 0");
                jdbc.execute("ALTER TABLE other_inbound ADD COLUMN finance_amount DECIMAL(14,2)");
                jdbc.execute("ALTER TABLE other_inbound ADD COLUMN finance_partner_id BIGINT");
                jdbc.execute("ALTER TABLE other_inbound ADD COLUMN finance_partner_name VARCHAR(50)");
                jdbc.execute("ALTER TABLE other_inbound ADD COLUMN finance_doc_no VARCHAR(20)");
                log.info("财务表结构：other_inbound 新增财务字段");
            }
        } catch (Exception e) {
            log.warn("迁移 other_inbound 财务列失败: {}", e.getMessage());
        }

        // 6. other_outbound 表新增财务字段
        try {
            var ooCols = jdbc.queryForList("PRAGMA table_info(other_outbound)");
            if (!ooCols.stream().anyMatch(c -> "gen_finance".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE other_outbound ADD COLUMN gen_finance BOOLEAN DEFAULT 0");
                jdbc.execute("ALTER TABLE other_outbound ADD COLUMN finance_amount DECIMAL(14,2)");
                jdbc.execute("ALTER TABLE other_outbound ADD COLUMN finance_partner_id BIGINT");
                jdbc.execute("ALTER TABLE other_outbound ADD COLUMN finance_partner_name VARCHAR(50)");
                jdbc.execute("ALTER TABLE other_outbound ADD COLUMN finance_doc_no VARCHAR(20)");
                log.info("财务表结构：other_outbound 新增财务字段");
            }
        } catch (Exception e) {
            log.warn("迁移 other_outbound 财务列失败: {}", e.getMessage());
        }
    }

    /**
     * 历史已审核到货记录回填应付账款
     * 遍历 APPROVED 状态的到货记录，若对应采购订单尚未生成AP则补录
     */
    private void backfillAccountsPayable() {
        // 检查 accounts_payable 表是否存在
        try {
            jdbc.queryForList("SELECT 1 FROM accounts_payable LIMIT 1");
        } catch (Exception e) {
            return; // 表不存在，跳过（JPA会自动建表，下次启动再补）
        }

        // 查所有已审核到货记录
        var arrivals = jdbc.queryForList(
                "SELECT type, ref_order_no, supplier_id, supplier_name FROM purchase_arrival WHERE status='APPROVED'");
        int count = 0;
        for (var arr : arrivals) {
            String type = (String) arr.get("type");
            String orderNo = (String) arr.get("ref_order_no");
            if (orderNo == null) continue;

            // 检查该订单是否已有AP
            var existing = jdbc.queryForList(
                    "SELECT id FROM accounts_payable WHERE purchase_order_no = ?", orderNo);
            if (!existing.isEmpty()) continue;

            // 查采购订单金额
            BigDecimal amount = null;
            Long supplierId = asLong(arr.get("supplier_id"));
            String supplierName = (String) arr.get("supplier_name");
            if ("RAW".equals(type)) {
                try {
                    var rows = jdbc.queryForList(
                            "SELECT total_amount, supplier_id, supplier_name, is_free FROM raw_material_purchase WHERE order_no = ?", orderNo);
                    for (var r : rows) {
                        Boolean isFree = (Boolean) r.get("is_free");
                        if (Boolean.TRUE.equals(isFree)) continue;
                        BigDecimal ta = (BigDecimal) r.get("total_amount");
                        if (ta != null) {
                            amount = (amount == null) ? ta : amount.add(ta);
                        }
                        if (supplierId == null) supplierId = asLong(r.get("supplier_id"));
                        if (supplierName == null) supplierName = (String) r.get("supplier_name");
                    }
                } catch (Exception ignored) {}
            } else {
                try {
                    var rows = jdbc.queryForList(
                            "SELECT total_amount, supplier_id, supplier_name, is_free FROM finished_product_purchase WHERE order_no = ?", orderNo);
                    for (var r : rows) {
                        Boolean isFree = (Boolean) r.get("is_free");
                        if (Boolean.TRUE.equals(isFree)) continue;
                        BigDecimal ta = (BigDecimal) r.get("total_amount");
                        if (ta != null) amount = ta;
                        if (supplierId == null) supplierId = asLong(r.get("supplier_id"));
                        if (supplierName == null) supplierName = (String) r.get("supplier_name");
                    }
                } catch (Exception ignored) {}
            }

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || supplierId == null) continue;

            // 生成AP记录
            // v8.4（D5）：COUNT+1 改 MAX+1——删行后必然重号（与 ApArrivalSchemaInitializer 统一）
            Integer apMax = jdbc.queryForObject(
                    "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM accounts_payable WHERE doc_no LIKE ?",
                    Integer.class, "AP-" + java.time.LocalDate.now().getYear() + "-%");
            String docNo = String.format("AP-%d-%04d", java.time.LocalDate.now().getYear(),
                    (apMax == null ? 0 : apMax) + 1);
            jdbc.update("INSERT INTO accounts_payable (doc_no, supplier_id, purchase_order_no, payable_type, amount, paid_amount, due_date, status, remark, create_time) " +
                            "VALUES (?, ?, ?, 'PURCHASE', ?, 0, ?, 'UNPAID', ?, ?)",
                    docNo, supplierId, orderNo, amount,
                    java.time.LocalDate.now().plusDays(30),
                    "历史到货补录 " + orderNo,
                    java.time.LocalDateTime.now());
            count++;
        }

        // 补偿二：recordArrival 路径（无到货审核记录，仅有质检单）
        // 先清理历史补偿插入的日期格式错误记录（JdbcTemplate 时间格式与 JPA 不一致）
        try {
            // v8.4（D4）：只删无付款的（原无条件物理删——若已被付款引用，重启即断链）
            int cleaned = jdbc.update(
                    "DELETE FROM accounts_payable WHERE remark LIKE '历史质检合格补录%' " +
                    "AND COALESCE(paid_amount, 0) = 0 AND status != 'PAID'");
            if (cleaned > 0) {
                log.info("历史数据补偿：清理 {} 条格式异常的应付账款记录，将用 JPA 重新生成", cleaned);
            }
        } catch (Exception ignored) {}
        // 使用 JPA Service 补生成（确保日期字段存储格式与 JPA 一致）
        try {
            count += qcService.backfillPurchaseAPFromQc();
        } catch (Exception e) {
            log.warn("质检单AP补偿异常: {}", e.getMessage());
        }
        // v5.27：入库单批号回填（生产/委外/其他入库此前未回写批号）
        try {
            qcService.backfillInboundBatchNo();
        } catch (Exception e) {
            log.warn("入库单批号回填异常: {}", e.getMessage());
        }

        if (count > 0) {
            log.info("历史数据补偿：回填 {} 条应付账款记录", count);
        }
    }

    /** SQLite INTEGER 列由 JDBC 返回 Integer/Long 不定，统一转 Long（防止 ClassCastException） */
    private Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }
}
