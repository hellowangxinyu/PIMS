package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * v5.73 采购含税口径：
 * 1) tax_rate 字典种子（默认 13%，数据字典页可维护）——独立幂等（存量库 initDictItems 因已有数据被跳过）；
 * 2) purchase_arrival 加含税单价列（到货录入时从采购单带出固化，入库视图展示含税/不含税/税额三口径）。
 * 必须最先执行：其他 Initializer（如 RecipeSchemaInitializer 迁移）启动期即用 JPA 查 purchase_arrival，加列必须先于一切 JPA 查询。
 */
@Configuration
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class TaxSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TaxSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public TaxSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        // 1) 税率字典种子
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dict_item WHERE type = 'tax_rate'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbc.execute("INSERT INTO dict_item (type, label, value, sort_order, enabled, create_time) "
                        + "VALUES ('tax_rate', '13%', '13', 1, 1, 1)");
                log.info("税率字典：已初始化 tax_rate=13%（数据字典页可维护）");
            }
        } catch (Exception e) {
            log.warn("税率字典初始化跳过: {}", e.getMessage());
        }

        // 2) 到货表加含税单价列
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(purchase_arrival)");
            boolean has = cols.stream().anyMatch(c -> "unit_price".equals(c.get("name")));
            if (!has) {
                jdbc.execute("ALTER TABLE purchase_arrival ADD COLUMN unit_price DECIMAL(14,4)");
                log.info("到货表结构：purchase_arrival 新增 unit_price（含税单价）列");
            }
        } catch (Exception e) {
            log.warn("到货表加列跳过: {}", e.getMessage());
        }

        // 2.5) v5.75 税率列：采购（原料/成品）、到货、销售订单——默认 13%
        for (String t : new String[]{"raw_material_purchase", "finished_product_purchase", "purchase_arrival", "sales_order", "other_inbound"}) {
            try {
                var cols = jdbc.queryForList("PRAGMA table_info(" + t + ")");
                boolean has = cols.stream().anyMatch(c -> "tax_rate".equals(c.get("name")));
                if (!has) {
                    jdbc.execute("ALTER TABLE " + t + " ADD COLUMN tax_rate DECIMAL(5,2) DEFAULT 13");
                    jdbc.update("UPDATE " + t + " SET tax_rate = 13 WHERE tax_rate IS NULL");
                    log.info("税率列：{} 新增 tax_rate（默认13%）", t);
                }
            } catch (Exception e) {
                log.warn("税率列 {} 加列跳过: {}", t, e.getMessage());
            }
        }

        // 2.9) v5.96 到货单号列 + 存量回填
        try {
            var acols = jdbc.queryForList("PRAGMA table_info(purchase_arrival)");
            boolean hasDocNo = acols.stream().anyMatch(c -> "doc_no".equals(c.get("name")));
            if (!hasDocNo) {
                jdbc.execute("ALTER TABLE purchase_arrival ADD COLUMN doc_no VARCHAR(30)");
                log.info("到货表结构：purchase_arrival 新增 doc_no（到货单号）列");
            }
            // 存量回填：按 id 顺序补 ARR-20260901-0001 式编号（同天分组流水）
            var rows = jdbc.queryForList(
                    "SELECT id, DATE(arrival_date/1000, 'unixepoch', '+8 hours') AS d FROM purchase_arrival WHERE doc_no IS NULL OR doc_no = '' ORDER BY id");
            java.util.Map<String, Integer> seq = new java.util.HashMap<>();
            for (var r : rows) {
                String day = String.valueOf(r.get("d"));
                int n = seq.merge(day, 1, Integer::sum);
                jdbc.update("UPDATE purchase_arrival SET doc_no = ? WHERE id = ?",
                        String.format("ARR-%s-%04d", day.replace("-", ""), n), r.get("id"));
            }
            if (!rows.isEmpty()) log.info("到货单号存量回填 {} 条", rows.size());
        } catch (Exception e) {
            log.warn("到货单号初始化跳过: {}", e.getMessage());
        }

        // 3) 存量到货单回填含税单价（从采购单按 合同号+物料 反查；查不到留空）
        try {
            int n1 = jdbc.update("""
                    UPDATE purchase_arrival SET unit_price = (
                        SELECT p.unit_price FROM raw_material_purchase p
                        WHERE p.order_no = purchase_arrival.ref_order_no
                          AND p.material_code = purchase_arrival.material_code
                          AND p.unit_price IS NOT NULL LIMIT 1)
                    WHERE unit_price IS NULL AND type = 'RAW'""");
            int n2 = jdbc.update("""
                    UPDATE purchase_arrival SET unit_price = (
                        SELECT p.unit_price FROM finished_product_purchase p
                        WHERE p.order_no = purchase_arrival.ref_order_no
                          AND p.material_code = purchase_arrival.material_code
                          AND p.unit_price IS NOT NULL LIMIT 1)
                    WHERE unit_price IS NULL AND type = 'FINISHED'""");
            if (n1 + n2 > 0) log.info("到货单含税单价回填：原材料 {} 条、成品 {} 条", n1, n2);
        } catch (Exception e) {
            log.warn("到货单价回填跳过: {}", e.getMessage());
        }

        // 3.5) 到货税率回填（从采购单带出）
        try {
            int n = jdbc.update("UPDATE purchase_arrival SET tax_rate = (SELECT p.tax_rate FROM raw_material_purchase p WHERE p.order_no = purchase_arrival.ref_order_no AND p.material_code = purchase_arrival.material_code LIMIT 1) WHERE type = 'RAW'");
            n += jdbc.update("UPDATE purchase_arrival SET tax_rate = (SELECT p.tax_rate FROM finished_product_purchase p WHERE p.order_no = purchase_arrival.ref_order_no AND p.material_code = purchase_arrival.material_code LIMIT 1) WHERE type = 'FINISHED'");
            if (n > 0) log.info("到货税率回填 {} 条", n);
        } catch (Exception e) {
            log.warn("到货税率回填跳过: {}", e.getMessage());
        }
    }
}
