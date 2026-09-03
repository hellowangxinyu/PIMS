package com.pengyuan.pims.config;

import com.pengyuan.pims.entity.FinishedProductPurchase;
import com.pengyuan.pims.entity.PurchaseArrival;
import com.pengyuan.pims.entity.RawMaterialPurchase;
import com.pengyuan.pims.repository.FinishedProductPurchaseRepository;
import com.pengyuan.pims.repository.PurchaseArrivalRepository;
import com.pengyuan.pims.repository.RawMaterialPurchaseRepository;
import com.pengyuan.pims.service.ReturnOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 配方管理表结构初始化（ddl-auto=none 后手动维护）
 * 仅在表不存在时创建，幂等安全
 */
@Component
@Order(1)
public class RecipeSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RecipeSchemaInitializer.class);
    private final JdbcTemplate jdbc;
    private final RawMaterialPurchaseRepository rawRepo;
    private final FinishedProductPurchaseRepository finishedRepo;
    private final PurchaseArrivalRepository arrivalRepo;
    private final ReturnOrderService returnOrderService;

    public RecipeSchemaInitializer(JdbcTemplate jdbc,
                                   RawMaterialPurchaseRepository rawRepo,
                                   FinishedProductPurchaseRepository finishedRepo,
                                   PurchaseArrivalRepository arrivalRepo,
                                   ReturnOrderService returnOrderService) {
        this.jdbc = jdbc;
        this.rawRepo = rawRepo;
        this.finishedRepo = finishedRepo;
        this.arrivalRepo = arrivalRepo;
        this.returnOrderService = returnOrderService;
    }

    @Override
    public void run(String... args) {
        // v5.32：purchase_arrival 补 batch_no 列（到货批号/供应商批号，实体已去 @Transient）——
        // 必须最先执行：本 Initializer 稍后会查询 PurchaseArrival（backfillArrivalDetails），列不存在直接启动失败
        var arrivalCols = jdbc.queryForList("PRAGMA table_info(purchase_arrival)");
        boolean hasArrivalBatch = arrivalCols.stream().anyMatch(m -> "batch_no".equals(m.get("name")));
        if (!hasArrivalBatch) {
            jdbc.execute("ALTER TABLE purchase_arrival ADD COLUMN batch_no VARCHAR(30)");
            log.info("表结构：purchase_arrival 新增 batch_no 列（到货批号）");
        }
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS recipe (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_no VARCHAR(30) NOT NULL UNIQUE,
                product_code VARCHAR(30),
                product_name VARCHAR(100) NOT NULL,
                category VARCHAR(50),
                description VARCHAR(500),
                enabled BOOLEAN DEFAULT 1,
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        // product_name 唯一索引（配方名称不允许重复）；若存在历史同名数据则跳过并告警，待清洗后重启自动建上
        try {
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_recipe_product_name ON recipe(product_name)");
            log.info("配方表：product_name 唯一索引就绪");
        } catch (Exception e) {
            log.warn("配方表：product_name 唯一索引未创建（可能存在历史同名配方，清洗后重启自动建）: {}", e.getMessage());
        }
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS recipe_version (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_id BIGINT NOT NULL,
                version_no VARCHAR(20) NOT NULL,
                batch_qty DECIMAL(14,3),
                unit VARCHAR(10),
                status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                released_by VARCHAR(50),
                released_time TIMESTAMP,
                created_by VARCHAR(50),
                remark VARCHAR(500),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS recipe_tree_node (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                version_id BIGINT NOT NULL,
                parent_node_id BIGINT,
                node_type VARCHAR(20) NOT NULL,
                material_code VARCHAR(30),
                material_name VARCHAR(100),
                spec VARCHAR(100),
                unit VARCHAR(10),
                qty DECIMAL(14,3) NOT NULL DEFAULT 0,
                ref_recipe_id BIGINT,
                sort_order INTEGER DEFAULT 0,
                remark VARCHAR(200)
            )
        """);
        // 确保 recipe 表有 recipe_type 列
        var recipeCols = jdbc.queryForList("PRAGMA table_info(recipe)");
        boolean hasType = recipeCols.stream().anyMatch(m -> "recipe_type".equals(m.get("name")));
        if (!hasType) {
            jdbc.execute("ALTER TABLE recipe ADD COLUMN recipe_type VARCHAR(20) NOT NULL DEFAULT 'TINTING'");
            log.info("配方表结构：recipe 新增 recipe_type 列");
        }
        // 确保 production_order 表有 recipe_version_id 列（兼容旧库）
        var columns = jdbc.queryForList("PRAGMA table_info(production_order)");
        boolean hasCol = columns.stream().anyMatch(m -> "recipe_version_id".equals(m.get("name")));
        if (!hasCol) {
            jdbc.execute("ALTER TABLE production_order ADD COLUMN recipe_version_id BIGINT");
            log.info("配方表结构：production_order 新增 recipe_version_id 列");
        }
        // 确保 outsource_order 表有 recipe_version_id 列
        var ooCols = jdbc.queryForList("PRAGMA table_info(outsource_order)");
        boolean ooHasCol = ooCols.stream().anyMatch(m -> "recipe_version_id".equals(m.get("name")));
        if (!ooHasCol) {
            jdbc.execute("ALTER TABLE outsource_order ADD COLUMN recipe_version_id BIGINT");
            log.info("配方表结构：outsource_order 新增 recipe_version_id 列");
        }
        // 移除 raw_material_purchase.order_no 上的唯一约束（同一合同号允许多行明细）
        // SQLite 内联 UNIQUE 约束产生的 sqlite_autoindex 无法 DROP INDEX，必须重建表
        try {
            // 先处理上次迁移失败的中间状态
            var tables = jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name='raw_material_purchase_old'");
            if (!tables.isEmpty()) {
                // 旧表还在，说明上次迁移未完成，删除新表并恢复旧表
                jdbc.execute("DROP TABLE IF EXISTS raw_material_purchase");
                jdbc.execute("ALTER TABLE raw_material_purchase_old RENAME TO raw_material_purchase");
                log.info("恢复 raw_material_purchase 表（上次迁移未完成）");
            }

            var indexes = jdbc.queryForList("PRAGMA index_list(raw_material_purchase)");
            boolean hasUniqueOnOrderNo = false;
            for (var idx : indexes) {
                String idxName = (String) idx.get("name");
                Object uniqueObj = idx.get("unique");
                boolean isUnique = uniqueObj != null && (uniqueObj.equals(1) || uniqueObj.equals(1L));
                if (!isUnique || idxName == null) continue;
                var cols = jdbc.queryForList("PRAGMA index_info(" + idxName + ")");
                if (cols.stream().anyMatch(c -> "order_no".equals(c.get("name")))) {
                    hasUniqueOnOrderNo = true;
                    break;
                }
            }
            if (hasUniqueOnOrderNo) {
                // 获取旧表所有列名
                var oldCols = jdbc.queryForList("PRAGMA table_info(raw_material_purchase)");
                List<String> colNames = oldCols.stream()
                        .map(c -> (String) c.get("name"))
                        .collect(java.util.stream.Collectors.toList());
                String colList = String.join(", ", colNames);

                jdbc.execute("ALTER TABLE raw_material_purchase RENAME TO raw_material_purchase_old");
                // 重建表：去掉 order_no 的 UNIQUE，保留所有列
                StringBuilder ddl = new StringBuilder("CREATE TABLE raw_material_purchase (");
                for (int i = 0; i < oldCols.size(); i++) {
                    var col = oldCols.get(i);
                    String name = (String) col.get("name");
                    String type = (String) col.get("type");
                    if (i > 0) ddl.append(", ");
                    ddl.append(name).append(" ").append(type != null ? type : "TEXT");
                    if ("id".equals(name)) ddl.append(" PRIMARY KEY AUTOINCREMENT");
                }
                ddl.append(")");
                jdbc.execute(ddl.toString());
                jdbc.execute("INSERT INTO raw_material_purchase (" + colList + ") SELECT " + colList + " FROM raw_material_purchase_old");
                jdbc.execute("DROP TABLE raw_material_purchase_old");
                log.info("已重建 raw_material_purchase 表，移除 order_no 唯一约束");
            }
        } catch (Exception e) {
            log.warn("检查/重建 raw_material_purchase 表失败: {}", e.getMessage());
        }
        log.info("配方表结构：recipe / recipe_version / recipe_tree_node 就绪");

        // 修复 outsource_order.processor_id NOT NULL 约束（实体已不使用该字段）
        try {
            var ooInfo = jdbc.queryForList("PRAGMA table_info(outsource_order)");
            boolean needFix = ooInfo.stream().anyMatch(c ->
                    ("processor_id".equals(c.get("name")) || "warehouse_id".equals(c.get("name")))
                    && Integer.valueOf(1).equals(c.get("notnull")));
            if (needFix) {
                List<String> colNames = ooInfo.stream().map(c -> (String) c.get("name")).toList();
                String colList = String.join(",", colNames);
                jdbc.execute("ALTER TABLE outsource_order RENAME TO outsource_order_old");
                StringBuilder ddl = new StringBuilder("CREATE TABLE outsource_order (");
                for (int i = 0; i < ooInfo.size(); i++) {
                    var col = ooInfo.get(i);
                    String name = (String) col.get("name");
                    String type = (String) col.get("type");
                    int pk = col.get("pk") != null ? ((Number) col.get("pk")).intValue() : 0;
                    int notnull = col.get("notnull") != null ? ((Number) col.get("notnull")).intValue() : 0;
                    if (i > 0) ddl.append(", ");
                    ddl.append(name).append(" ").append(type != null ? type : "TEXT");
                    if (pk == 1) ddl.append(" PRIMARY KEY AUTOINCREMENT");
                    else if (notnull == 1 && !"processor_id".equals(name) && !"warehouse_id".equals(name)) ddl.append(" NOT NULL");
                }
                ddl.append(")");
                jdbc.execute(ddl.toString());
                jdbc.execute("INSERT INTO outsource_order (" + colList + ") SELECT " + colList + " FROM outsource_order_old");
                jdbc.execute("DROP TABLE outsource_order_old");
                log.info("已重建 outsource_order 表，processor_id/warehouse_id 改为可空");
            }
        } catch (Exception e) {
            log.warn("检查/重建 outsource_order 表失败: {}", e.getMessage());
        }

        // 修复 outsource_finish_inbound.processor_id NOT NULL 约束
        try {
            var ofiCols = jdbc.queryForList("PRAGMA table_info(outsource_finish_inbound)");
            var nullableFixCols = Set.of("processor_id", "product_batch_no", "product_code");
            boolean ofiNeedFix = ofiCols.stream().anyMatch(c ->
                    nullableFixCols.contains(c.get("name"))
                    && Integer.valueOf(1).equals(c.get("notnull")));
            if (ofiNeedFix) {
                List<String> colNames = ofiCols.stream().map(c -> (String) c.get("name")).toList();
                String colList = String.join(",", colNames);
                jdbc.execute("ALTER TABLE outsource_finish_inbound RENAME TO outsource_finish_inbound_old");
                StringBuilder ddl = new StringBuilder("CREATE TABLE outsource_finish_inbound (");
                for (int i = 0; i < ofiCols.size(); i++) {
                    var col = ofiCols.get(i);
                    String name = (String) col.get("name");
                    String type = (String) col.get("type");
                    int pk = col.get("pk") != null ? ((Number) col.get("pk")).intValue() : 0;
                    int notnull = col.get("notnull") != null ? ((Number) col.get("notnull")).intValue() : 0;
                    if (i > 0) ddl.append(", ");
                    ddl.append(name).append(" ").append(type != null ? type : "TEXT");
                    if (pk == 1) ddl.append(" PRIMARY KEY AUTOINCREMENT");
                    else if (notnull == 1 && !nullableFixCols.contains(name)) ddl.append(" NOT NULL");
                }
                ddl.append(")");
                jdbc.execute(ddl.toString());
                jdbc.execute("INSERT INTO outsource_finish_inbound (" + colList + ") SELECT " + colList + " FROM outsource_finish_inbound_old");
                jdbc.execute("DROP TABLE outsource_finish_inbound_old");
                log.info("已重建 outsource_finish_inbound 表，processor_id/product_batch_no/product_code 改为可空");
            }
        } catch (Exception e) {
            log.warn("检查/重建 outsource_finish_inbound 表失败: {}", e.getMessage());
        }

        // 确保 recipe_tree_node 表有 category / sub_category 列
        var tnCols = jdbc.queryForList("PRAGMA table_info(recipe_tree_node)");
        boolean hasCat = tnCols.stream().anyMatch(m -> "category".equals(m.get("name")));
        if (!hasCat) {
            jdbc.execute("ALTER TABLE recipe_tree_node ADD COLUMN category VARCHAR(50)");
            jdbc.execute("ALTER TABLE recipe_tree_node ADD COLUMN sub_category VARCHAR(50)");
            log.info("配方表结构：recipe_tree_node 新增 category / sub_category 列");
        }

        // 统一配方标准批量为 100
        int updated = jdbc.update("UPDATE recipe_version SET batch_qty = 100 WHERE batch_qty IS NULL OR batch_qty != 100");
        if (updated > 0) {
            log.info("已将 {} 个配方版本的标准批量统一为 100", updated);
        }

        // 出库单据记录批号单价与实际成本（半成品/成品实际材料成本按批号直取）
        ensureColumn("production_outbound", "unit_price", "DECIMAL(14,2)");
        ensureColumn("production_outbound", "cost", "DECIMAL(14,2)");
        // 生产退料：单据类型列（ISSUE 领料 / RETURN 退料），历史行幂等补录为 ISSUE
        ensureColumn("production_outbound", "doc_type", "VARCHAR(20)");
        jdbc.update("UPDATE production_outbound SET doc_type = 'ISSUE' WHERE doc_type IS NULL");
        // v5.17：成品三维分类（角色=小类，主材/色系独立列）
        ensureColumn("material", "main_material", "VARCHAR(20)");
        ensureColumn("material", "color_series", "VARCHAR(20)");
        ensureColumn("outsource_material_outbound", "unit_price", "DECIMAL(14,2)");
        ensureColumn("outsource_material_outbound", "cost", "DECIMAL(14,2)");
        // v4.5：委外出库新增代工厂名称字段（冗余存储便于展示）
        ensureColumn("outsource_material_outbound", "processor_name", "VARCHAR(100)");
        // v4.5：生产出库/委外出库新增库位ID及分库/库位名称冗余字段（前端按库位分行选择批次，精确扣减对应库位库存）
        ensureColumn("production_outbound", "location_id", "VARCHAR(20)");
        ensureColumn("production_outbound", "zone_name", "VARCHAR(50)");
        ensureColumn("production_outbound", "location_name", "VARCHAR(50)");
        ensureColumn("outsource_material_outbound", "location_id", "VARCHAR(20)");
        ensureColumn("outsource_material_outbound", "zone_name", "VARCHAR(50)");
        ensureColumn("outsource_material_outbound", "location_name", "VARCHAR(50)");
        ensureColumn("other_outbound", "unit_price", "DECIMAL(14,2)");
        ensureColumn("other_outbound", "cost", "DECIMAL(14,2)");
        // 入库得率：实际产出 vs 理论产出（配方批量）
        ensureColumn("production_inbound", "theoretical_qty", "DECIMAL(14,3)");
        ensureColumn("production_inbound", "yield_rate", "DECIMAL(8,2)");
        // 生产出库自动 FIFO（全仓先进先出）：warehouse_id 放宽为可空（null=全仓扣减）
        relaxNotNullColumn("production_outbound", "warehouse_id");
        ensureColumn("outsource_finish_inbound", "theoretical_qty", "DECIMAL(14,3)");
        ensureColumn("outsource_finish_inbound", "yield_rate", "DECIMAL(8,2)");
        // 委外入库分库/库位名称冗余字段（与生产入库保持一致）
        ensureColumn("outsource_finish_inbound", "zone_name", "VARCHAR(50)");
        ensureColumn("outsource_finish_inbound", "location_name", "VARCHAR(50)");
        // 销售订单模块：客户名/品名冗余字段 + 销售出库成本字段
        ensureColumn("sales_order", "customer_name", "VARCHAR(100)");
        ensureColumn("sales_order_item", "material_name", "VARCHAR(100)");
        ensureColumn("sales_outbound", "unit_price", "DECIMAL(14,2)");
        ensureColumn("sales_outbound", "cost", "DECIMAL(14,2)");
        ensureColumn("sales_outbound", "location_id", "VARCHAR(20)");
        // 客户收款条件与收款方式（账期缓冲）
        ensureColumn("customer", "payment_terms", "VARCHAR(500)");
        ensureColumn("customer", "payment_method", "VARCHAR(20)");
        // 代工厂档案维护加工费（委外订单选代工厂自动带出）
        ensureColumn("supplier", "processing_fee", "DECIMAL(12,2)");

        // 其他入库-退货关联字段（保留：销售退货入库时仍可使用，当前未启用）
        ensureColumn("other_inbound", "return_ref_type", "VARCHAR(20)");
        ensureColumn("other_inbound", "return_ref_doc_no", "VARCHAR(30)");
        ensureColumn("other_inbound", "return_ref_id", "BIGINT");
        ensureColumn("other_inbound", "customer_id", "BIGINT");
        ensureColumn("other_inbound", "supplier_id", "BIGINT");
        ensureColumn("other_inbound", "return_amount", "DECIMAL(14,2)");
        ensureColumn("other_inbound", "return_offset_status", "VARCHAR(20)");

        // 其他出库-退货关联字段（采购退货出库时回填退货单号与库位）
        ensureColumn("other_outbound", "location_id", "VARCHAR(20)");
        ensureColumn("other_outbound", "return_order_id", "BIGINT");
        ensureColumn("other_outbound", "return_ref_doc_no", "VARCHAR(20)");

        // v5.2：采购到货明细字段（到货录入时落库，供「到货明细」页展示）
        ensureColumn("purchase_arrival", "material_code", "VARCHAR(30)");
        ensureColumn("purchase_arrival", "material_name", "VARCHAR(100)");
        ensureColumn("purchase_arrival", "qty", "DECIMAL(14,3)");
        ensureColumn("purchase_arrival", "unit", "VARCHAR(10)");
        ensureColumn("purchase_arrival", "warehouse_id", "VARCHAR(20)");
        ensureColumn("purchase_arrival", "location_id", "VARCHAR(20)");
        ensureColumn("purchase_arrival", "zone_name", "VARCHAR(50)");
        ensureColumn("purchase_arrival", "location_name", "VARCHAR(50)");

        // 退货单表（QC 判定退货 → 采购审核 → 仓管出库 → 冲减应付）
        createReturnOrderTable();

        // v5.4：销售退货字段（退货单共用表，type=SALES_RETURN 时使用；客户退货 → 审核 → 退货入库 → 冲减应收）
        ensureColumn("return_order", "customer_id", "BIGINT");
        ensureColumn("return_order", "customer_name", "VARCHAR(100)");
        ensureColumn("return_order", "sales_order_no", "VARCHAR(30)");
        ensureColumn("return_order", "ref_sales_outbound_no", "VARCHAR(30)");
        ensureColumn("return_order", "inbound_doc_no", "VARCHAR(20)");

        // v5.27：手工采购退货单选择库存批号退货（创建时即锁定批号，出库按该批号退）
        ensureColumn("return_order", "batch_no", "VARCHAR(50)");

        // v5.35：油尾退回单（复用退货单表，type=TAILING_RETURN）——结算方式 + 油尾库位
        ensureColumn("return_order", "settle_type", "VARCHAR(20)");
        ensureColumn("return_order", "location_id", "VARCHAR(20)");
        ensureColumn("return_order", "location_name", "VARCHAR(50)");

        // v4.5：委外出库 to_warehouse_id 改为可空（不再使用调入仓），修复历史表中 NOT NULL 约束
        relaxNotNullColumn("outsource_material_outbound", "to_warehouse_id");

        // v5.6：订单明细保留半成品行（常备库存半成品不展开原料）——明细表加节点类型与子配方引用列
        ensureColumn("production_order_item", "node_type", "VARCHAR(20)");
        ensureColumn("production_order_item", "ref_recipe_id", "BIGINT");
        ensureColumn("outsource_order_item", "node_type", "VARCHAR(20)");
        ensureColumn("outsource_order_item", "ref_recipe_id", "BIGINT");
        // 半成品节点物料编码可空（配方历史节点可能未绑定编码，订单保存时友好校验提示）
        relaxNotNullColumn("production_order_item", "material_code");
        relaxNotNullColumn("outsource_order_item", "material_code");

        // v5.4：回填历史到货明细（v5.3 功能上线前的到货无明细，从已到货采购单反推，启动幂等）
        backfillArrivalDetails();

        // v5.5：迁移旧流程遗留的 APPROVED 采购退货单（审核即完成，补单价并冲减应付）
        int migrated = returnOrderService.migrateApprovedPurchaseReturns();
        if (migrated > 0) {
            log.info("v5.5 采购退货单状态迁移完成: {} 张 APPROVED → DONE", migrated);
        }

        // 回填历史配方树节点的物料分类（早期前端添加节点未带 category/sub_category，按 material_code 关联 material 表补齐）
        try {
            int filled = jdbc.update("""
                UPDATE recipe_tree_node
                SET category = (SELECT m.category FROM material m WHERE m.code = recipe_tree_node.material_code),
                    sub_category = (SELECT m.sub_category FROM material m WHERE m.code = recipe_tree_node.material_code)
                WHERE (category IS NULL OR category = '')
                  AND material_code IS NOT NULL
                  AND EXISTS (SELECT 1 FROM material m WHERE m.code = recipe_tree_node.material_code)
                """);
            if (filled > 0) {
                log.info("已回填 {} 个配方树节点的物料分类（category/sub_category）", filled);
            }
            // 半成品节点（SUB_RECIPE）统一归为 B 大类（历史节点未带分类）
            int subFilled = jdbc.update("UPDATE recipe_tree_node SET category = 'B' WHERE node_type = 'SUB_RECIPE' AND (category IS NULL OR category = '')");
            if (subFilled > 0) {
                log.info("已回填 {} 个半成品节点的分类（category=B）", subFilled);
            }
        } catch (Exception e) {
            log.warn("回填配方树节点分类失败: {}", e.getMessage());
        }
    }

    /**
     * 回填历史到货明细：到货明细功能（v5.3）上线前的到货没有明细记录，
     * 从已到货（received_qty > 0）的原料/成品采购单反推生成。
     * 幂等：同一（单号+物料+数量）已有明细则跳过；不影响新到货的逐条记录。
     * 历史粒度：每张采购行累计到货一条（无法还原每次录入），操作人留空，日期取单据最后更新时间。
     */
    private void backfillArrivalDetails() {
        int raw = 0, fin = 0;
        for (RawMaterialPurchase rp : rawRepo.findAll()) {
            if (rp.receivedQty == null || rp.receivedQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (!arrivalExists(rp.orderNo, rp.materialCode, rp.receivedQty)) {
                PurchaseArrival pa = new PurchaseArrival();
                pa.type = "RAW";
                pa.refOrderNo = rp.orderNo;
                pa.supplierId = rp.supplierId;
                pa.supplierName = rp.supplierName;
                pa.materialCode = rp.materialCode;
                pa.materialName = rp.materialName;
                pa.qty = rp.receivedQty;
                pa.unit = "kg";
                pa.warehouseId = rp.warehouseId;
                pa.arrivalDate = toDate(rp.updateTime != null ? rp.updateTime : rp.createTime);
                // 状态 BACKFILL：标识历史回填（前端展示不依赖状态；同时避免被 QcSchemaInitializer 的 AP 补偿逻辑误当成"到货审核"补录应付）
                pa.status = "BACKFILL";
                pa.remark = "历史到货回填";
                pa.createTime = LocalDateTime.now();
                arrivalRepo.save(pa);
                raw++;
            }
        }
        for (FinishedProductPurchase fp : finishedRepo.findAll()) {
            if (fp.receivedQty == null || fp.receivedQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (!arrivalExists(fp.orderNo, fp.materialCode, fp.receivedQty)) {
                PurchaseArrival pa = new PurchaseArrival();
                pa.type = "FINISHED";
                pa.refOrderNo = fp.orderNo;
                pa.supplierId = fp.supplierId;
                pa.supplierName = fp.supplierName;
                pa.materialCode = fp.materialCode;
                pa.materialName = fp.materialName;
                pa.qty = fp.receivedQty;
                pa.unit = "kg";
                pa.warehouseId = fp.warehouseId;
                pa.arrivalDate = toDate(fp.updateTime != null ? fp.updateTime : fp.createTime);
                // 状态 BACKFILL：标识历史回填（避免被 AP 补偿逻辑误补应付，见原料分支注释）
                pa.status = "BACKFILL";
                pa.remark = "历史到货回填";
                pa.createTime = LocalDateTime.now();
                arrivalRepo.save(pa);
                fin++;
            }
        }
        if (raw > 0 || fin > 0) {
            log.info("到货明细历史回填完成: 原料 {} 条, 成品 {} 条", raw, fin);
        }
    }

    /** v5.95.3 幂等判断收紧：同（单号+物料）存在**任何**到货明细即视为已有（不看数量）——
     *  分批到货（25+25）永远凑不出整单量（50），按数量匹配会导致每次重启误补一张汇总 BACKFILL 单、删了复活 */
    private boolean arrivalExists(String orderNo, String materialCode, BigDecimal qty) {
        for (PurchaseArrival a : arrivalRepo.findByRefOrderNo(orderNo)) {
            boolean sameMaterial = (materialCode == null && a.materialCode == null)
                    || (materialCode != null && materialCode.equals(a.materialCode));
            if (sameMaterial) {
                return true;
            }
        }
        return false;
    }

    /** LocalDateTime → LocalDate（null 时取当天） */
    private LocalDate toDate(LocalDateTime dt) {
        return dt != null ? dt.toLocalDate() : LocalDate.now();
    }

    /**
     * 将指定列的 NOT NULL 约束放宽为可空（SQLite 不支持 ALTER COLUMN，需重建表）
     * 仅当该列当前为 NOT NULL 时执行，幂等安全
     */
    private void relaxNotNullColumn(String table, String column) {
        // 检查表是否存在
        var tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'");
        if (tables.isEmpty()) return;
        // 检查列是否为 NOT NULL（PRAGMA table_info 的 notnull 字段为 1 表示 NOT NULL）
        var cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        boolean needFix = false;
        for (var m : cols) {
            if (column.equals(m.get("name")) && Integer.valueOf(1).equals(m.get("notnull"))) {
                needFix = true;
                break;
            }
        }
        if (!needFix) return;
        // SQLite 不支持 ALTER COLUMN，使用临时表重建方式放宽约束
        String tmp = table + "_tmp_nullable";
        jdbc.execute("DROP TABLE IF EXISTS " + tmp);
        // 按当前列结构创建临时表（所有列均可空，主键与唯一约束保留）
        StringBuilder createSql = new StringBuilder("CREATE TABLE " + tmp + " (");
        var pkCols = new java.util.ArrayList<String>();
        for (int i = 0; i < cols.size(); i++) {
            var m = cols.get(i);
            String name = (String) m.get("name");
            String type = (String) m.get("type");
            int notnull = ((Number) m.get("notnull")).intValue();
            String dflt = (String) m.get("dflt_value");
            int pk = ((Number) m.get("pk")).intValue();
            if (i > 0) createSql.append(", ");
            createSql.append(name).append(" ").append(type != null && !type.isEmpty() ? type : "");
            // 目标列放宽为可空；其他列保留原 NOT NULL
            if (notnull == 1 && !column.equals(name)) createSql.append(" NOT NULL");
            if (dflt != null) createSql.append(" DEFAULT ").append(dflt);
            if (pk > 0) pkCols.add(name);
        }
        if (!pkCols.isEmpty()) {
            createSql.append(", PRIMARY KEY (");
            createSql.append(String.join(",", pkCols));
            createSql.append(")");
        }
        createSql.append(")");
        jdbc.execute(createSql.toString());
        // 复制全部数据
        String colNames = String.join(",", cols.stream().map(m -> (String) m.get("name")).toList());
        jdbc.execute("INSERT INTO " + tmp + " (" + colNames + ") SELECT " + colNames + " FROM " + table);
        // 替换原表
        jdbc.execute("DROP TABLE " + table);
        jdbc.execute("ALTER TABLE " + tmp + " RENAME TO " + table);
        log.info("表结构：{}.{} NOT NULL 约束已放宽为可空（通过表重建）", table, column);
    }

    /** 创建退货单表（幂等：存在则跳过） */
    private void createReturnOrderTable() {
        var tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='return_order'");
        if (!tables.isEmpty()) return;
        jdbc.execute(
                "CREATE TABLE return_order (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  doc_no VARCHAR(20) NOT NULL UNIQUE," +
                "  type VARCHAR(20) NOT NULL," +
                "  ref_arrival_id BIGINT," +
                "  purchase_order_no VARCHAR(30)," +
                "  qc_inspection_no VARCHAR(30)," +
                "  material_code VARCHAR(30) NOT NULL," +
                "  material_name VARCHAR(100)," +
                "  unit VARCHAR(10)," +
                "  qty DECIMAL(14,3) NOT NULL," +
                "  unit_price DECIMAL(14,2)," +
                "  return_amount DECIMAL(14,2)," +
                "  supplier_id BIGINT," +
                "  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'," +
                "  outbound_doc_no VARCHAR(20)," +
                "  approved_by VARCHAR(50)," +
                "  approve_time DATETIME," +
                "  created_by VARCHAR(50)," +
                "  remark VARCHAR(500)," +
                "  create_time DATETIME," +
                "  update_time DATETIME" +
                ")");
        log.info("退货单表 return_order 创建完成");
    }

    /** 幂等补列：表不存在该列时执行 ALTER TABLE ADD COLUMN */
    private void ensureColumn(String table, String column, String type) {
        var cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        boolean exists = cols.stream().anyMatch(m -> column.equals(m.get("name")));
        if (!exists) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("表结构：{} 新增 {} 列", table, column);
        }
    }
}
