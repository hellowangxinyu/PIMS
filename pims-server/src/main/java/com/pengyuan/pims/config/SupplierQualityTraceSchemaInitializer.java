package com.pengyuan.pims.config;

import com.pengyuan.pims.entity.LossLetterTemplate;
import com.pengyuan.pims.repository.LossLetterTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.59 供应商质量追溯建表：
 * 1) supplier_quality_trace 质量追溯单（批号驱动，品控发起→采购处理→处理完毕留档）
 * 2) loss_letter_template 损失沟通函模板（多模板可维护，正文段落+占位符）
 * 种子模板只从无到有——用户改过的模板不会被覆盖。
 */
@Component
@Order(3)
public class SupplierQualityTraceSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SupplierQualityTraceSchemaInitializer.class);
    private final JdbcTemplate jdbc;
    private final LossLetterTemplateRepository templateRepo;

    public SupplierQualityTraceSchemaInitializer(JdbcTemplate jdbc, LossLetterTemplateRepository templateRepo) {
        this.jdbc = jdbc;
        this.templateRepo = templateRepo;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS supplier_quality_trace (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trace_no VARCHAR(20) NOT NULL UNIQUE,
                    supplier_id BIGINT,
                    supplier_name VARCHAR(100) NOT NULL,
                    purchase_order_no VARCHAR(30),
                    order_category VARCHAR(20),
                    material_code VARCHAR(30),
                    material_name VARCHAR(100),
                    batch_no VARCHAR(30) NOT NULL,
                    arrival_date DATE,
                    purchase_qty DECIMAL(14,3),
                    purchase_unit_price DECIMAL(14,2),
                    purchase_amount DECIMAL(14,2),
                    qc_inspection_no VARCHAR(20),
                    qc_status VARCHAR(20),
                    issue_date DATE,
                    category VARCHAR(20),
                    description VARCHAR(2000) NOT NULL,
                    loss_amount DECIMAL(14,2),
                    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                    result_type VARCHAR(30),
                    compensation_amount DECIMAL(14,2),
                    result_remark VARCHAR(1000),
                    handler VARCHAR(50),
                    resolve_date DATE,
                    print_count INTEGER NOT NULL DEFAULT 0,
                    created_by VARCHAR(50),
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sqt_supplier ON supplier_quality_trace(supplier_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_sqt_status ON supplier_quality_trace(status)");
            // v5.59.2 委外反查链：存量表补 order_category 列（新表建表 SQL 已含）
            try {
                boolean hasCol = jdbc.queryForList("PRAGMA table_info(supplier_quality_trace)").stream()
                        .anyMatch(c -> "order_category".equalsIgnoreCase(String.valueOf(c.get("name"))));
                if (!hasCol) jdbc.execute("ALTER TABLE supplier_quality_trace ADD COLUMN order_category VARCHAR(20)");
            } catch (Exception ex) { log.warn("order_category 补列失败: {}", ex.getMessage()); }
            // v5.60 制单人：存量表补 created_by 列
            try {
                boolean hasBy = jdbc.queryForList("PRAGMA table_info(supplier_quality_trace)").stream()
                        .anyMatch(c -> "created_by".equalsIgnoreCase(String.valueOf(c.get("name"))));
                if (!hasBy) jdbc.execute("ALTER TABLE supplier_quality_trace ADD COLUMN created_by VARCHAR(50)");
            } catch (Exception ex) { log.warn("created_by 补列失败: {}", ex.getMessage()); }
            log.info("供应商质量追溯表 supplier_quality_trace 就绪");
        } catch (Exception e) { log.warn("supplier_quality_trace 建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS loss_letter_template (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(50) NOT NULL,
                    opening_text VARCHAR(1000),
                    body_text VARCHAR(2000) NOT NULL,
                    require_text VARCHAR(1000),
                    closing_text VARCHAR(1000),
                    is_default BOOLEAN NOT NULL DEFAULT 0,
                    enabled BOOLEAN NOT NULL DEFAULT 1,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("损失沟通函模板表 loss_letter_template 就绪");
        } catch (Exception e) { log.warn("loss_letter_template 建表失败: {}", e.getMessage()); }

        seedTemplates();
        seedStraceDicts();
    }

    /**
     * v5.59.1 问题类型/处理结果类型字典种子（系统设置→数据字典可维护）。
     * 按 type 整组幂等：该类型已有任何数据（含用户增删过的）即不再动；value=label=中文，
     * 与追溯单 category/resultType 存中文的既有口径一致。
     */
    private void seedStraceDicts() {
        try {
            seedDictGroup("strace_category", new String[]{
                    "色差", "性能不达标", "结块沉淀", "包装破损", "杂质超标", "批次不稳", "其他"});
            seedDictGroup("strace_result_type", new String[]{
                    "协商折让", "赔款", "补货", "换货", "供应商拒绝赔付", "免赔", "其他"});
        } catch (Exception e) { log.warn("质量追溯字典种子插入失败: {}", e.getMessage()); }
    }

    private void seedDictGroup(String type, String[] values) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dict_item WHERE type = ?", Integer.class, type);
        if (count != null && count > 0) return;
        for (int i = 0; i < values.length; i++) {
            jdbc.update("INSERT INTO dict_item (type, value, label, sort_order, enabled, create_time) VALUES (?, ?, ?, ?, 1, ?)",
                    type, values[i], values[i], i + 1, System.currentTimeMillis());
        }
        log.info("种子数据：已初始化字典「{}」{} 项", type, values.length);
    }

    /** 种子模板只从无到有（表空才插），用户修改/新增的不会被覆盖 */
    private void seedTemplates() {
        try {
            if (templateRepo.count() > 0) return;

            LossLetterTemplate std = new LossLetterTemplate();
            std.name = "标准损失沟通函";
            std.openingText = "{{supplierName}}：\n贵司供应的以下物料批次，经我司品控检验发现质量问题。现将有关情况通报如下，请贵司核实并回复处理意见。";
            std.bodyText = "物料：{{materialCode}} {{materialName}}（批号 {{batchNo}}）\n采购单号：{{purchaseOrderNo}}    到货日期：{{arrivalDate}}    入库数量：{{purchaseQty}}\n问题类型：「{{category}}」，具体情况：{{description}}\n该批次质量问题给我司造成损失合计 ¥{{lossAmount}}。";
            std.requireText = "请贵司于收函后 7 个工作日内书面回复处理意见，并就上述损失协商赔偿事宜。如有异议，请提供该批次出厂检验报告及相关质量证明材料。";
            std.closingText = "望贵司高度重视供货质量，妥善处理本次事宜，避免类似问题再次发生。";
            std.isDefault = true;
            std.enabled = true;
            templateRepo.save(std);

            LossLetterTemplate severe = new LossLetterTemplate();
            severe.name = "严重质量问题函（严正）";
            severe.openingText = "{{supplierName}}：\n贵司供应的物料批次发生严重质量问题，我司依约向贵司发出本损失沟通函，请贵司正式核实并限期答复。";
            severe.bodyText = "物料：{{materialCode}} {{materialName}}（批号 {{batchNo}}）\n采购单号：{{purchaseOrderNo}}    到货日期：{{arrivalDate}}    入库数量：{{purchaseQty}}    涉及金额：¥{{purchaseAmount}}\n问题类型：「{{category}}」，质检单号：{{qcInspectionNo}}\n情况描述：{{description}}\n本次质量问题造成损失合计 ¥{{lossAmount}}，相关证据材料（检验记录、现场照片）可随时提供核验。";
            severe.requireText = "请贵司于收函后 5 个工作日内书面回复；逾期未回复或拒绝协商的，我司将按合同约定从应付货款中扣除相应损失金额，并保留进一步追偿的权利。";
            severe.closingText = "请贵司即刻整改供货质量管控。我司将在后续合作中加大该批次来源物料的抽检力度。";
            severe.isDefault = false;
            severe.enabled = true;
            templateRepo.save(severe);

            log.info("种子数据：已初始化 2 套损失沟通函模板（标准版/严正版）");
        } catch (Exception e) { log.warn("损失沟通函种子模板插入失败: {}", e.getMessage()); }
    }
}
