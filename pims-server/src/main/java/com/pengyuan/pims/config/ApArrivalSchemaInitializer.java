package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 应付按到货单立账表结构初始化（v5.27，ddl-auto=none 后手动维护）
 * 1. accounts_payable 新增 arrival_id 列（立账依据的到货单 ID）
 * 2. 存量迁移：历史 AP 为「按采购订单整单」立账，拆分为「按到货单」多张 AP
 *    （金额 = 到货数量 × 采购单价；已付金额按比例分摊；整单与到货合计一致时总额不变）
 * 幂等：仅处理 arrival_id IS NULL 的采购应付（拆分完成后全部非空，重启不再执行）
 * 必须最先执行（@Order(1)）：JPA 实体已带 arrivalId 字段
 */
@Component
@Order(1)
public class ApArrivalSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApArrivalSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public ApArrivalSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 1. 加列
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(accounts_payable)");
            boolean hasArrivalId = cols.stream().anyMatch(c -> "arrival_id".equals(c.get("name")));
            if (!hasArrivalId) {
                jdbc.execute("ALTER TABLE accounts_payable ADD COLUMN arrival_id BIGINT");
                log.info("应付表结构：accounts_payable 新增 arrival_id 列");
            }
        } catch (Exception e) {
            log.warn("应付表结构：accounts_payable 加列失败（表可能不存在，跳过）: {}", e.getMessage());
        }

        // 2. 存量迁移：整单 AP → 按到货单拆分（幂等）
        migrateLegacyAp();
        log.info("应付按到货单立账初始化完成");
    }

    /** 存量整单 AP 拆分为按到货单的 AP（金额 = 到货量 × 单价；已付按比例分摊） */
    private void migrateLegacyAp() {
        try {
            List<Map<String, Object>> legacy = jdbc.queryForList(
                    "SELECT id, doc_no, purchase_order_no, amount, paid_amount, status " +
                    "FROM accounts_payable WHERE arrival_id IS NULL AND purchase_order_no IS NOT NULL " +
                    "AND purchase_order_no != '' AND payable_type = 'PURCHASE' ORDER BY id");
            if (legacy.isEmpty()) return;

            int splitCount = 0;
            for (Map<String, Object> ap : legacy) {
                Long apId = ((Number) ap.get("id")).longValue();
                String orderNo = (String) ap.get("purchase_order_no");
                BigDecimal apAmount = new BigDecimal(ap.get("amount").toString());
                BigDecimal apPaid = new BigDecimal(ap.get("paid_amount") == null ? "0" : ap.get("paid_amount").toString());
                String oldDocNo = (String) ap.get("doc_no");

                // 该采购单的到货单（按创建时间升序，第一张复用原 AP 行）
                // v5.27：不筛状态——历史回填(BACKFILL)/旧路径(DRAFT)/新路径(APPROVED) 都是有效到货记录，缺一不可
                List<Map<String, Object>> arrivals = jdbc.queryForList(
                        "SELECT id, material_code, qty FROM purchase_arrival " +
                        "WHERE ref_order_no = ? ORDER BY id", orderNo);
                if (arrivals.isEmpty()) {
                    log.warn("应付迁移：{} 无到货单可关联（{}），跳过", oldDocNo, orderNo);
                    continue;
                }

                // 逐张到货单计算金额（数量 × 采购单价；取不到单价按原金额比例分摊）
                BigDecimal sumQty = BigDecimal.ZERO;
                for (Map<String, Object> a : arrivals) {
                    sumQty = sumQty.add(new BigDecimal(a.get("qty").toString()));
                }
                // 先算各到货单金额，分摊已付时以拆分后合计为分母（避免原整单含未到货部分导致已付缩水）
                BigDecimal[] amounts = new BigDecimal[arrivals.size()];
                for (int i = 0; i < arrivals.size(); i++) {
                    Map<String, Object> a = arrivals.get(i);
                    BigDecimal qty = new BigDecimal(a.get("qty").toString());
                    BigDecimal unitPrice = findUnitPrice(orderNo, (String) a.get("material_code"));
                    amounts[i] = unitPrice != null
                            ? qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
                            : (sumQty.compareTo(BigDecimal.ZERO) > 0
                                ? apAmount.multiply(qty).divide(sumQty, 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO);
                }
                BigDecimal splitTotal = BigDecimal.ZERO;
                for (BigDecimal amt : amounts) splitTotal = splitTotal.add(amt);
                BigDecimal paidLeft = apPaid;
                int idx = 0;
                for (Map<String, Object> a : arrivals) {
                    Long arrivalId = ((Number) a.get("id")).longValue();
                    BigDecimal amount = amounts[idx];
                    BigDecimal paid;
                    if (idx == arrivals.size() - 1) {
                        paid = paidLeft; // 最后一张承接剩余已付，保证已付总和守恒
                    } else if (splitTotal.compareTo(BigDecimal.ZERO) > 0 && apPaid.compareTo(BigDecimal.ZERO) > 0) {
                        paid = apPaid.multiply(amount).divide(splitTotal, 2, RoundingMode.HALF_UP);
                    } else {
                        paid = BigDecimal.ZERO;
                    }
                    paidLeft = paidLeft.subtract(paid);
                    String newStatus = paid.compareTo(amount) >= 0 ? "PAID"
                            : (paid.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL" : "UNPAID");

                    if (idx == 0) {
                        // 第一张到货单复用原 AP 行（保留 doc_no/付款历史）
                        jdbc.update("UPDATE accounts_payable SET arrival_id = ?, amount = ?, paid_amount = ?, status = ? WHERE id = ?",
                                arrivalId, amount, paid, newStatus, apId);
                    } else {
                        // 其余到货单新建 AP
                        String newDocNo = nextDocNo();
                        jdbc.update("INSERT INTO accounts_payable (doc_no, supplier_id, purchase_order_no, arrival_id, payable_type, amount, paid_amount, due_date, status, remark, create_time) " +
                                        "SELECT ?, supplier_id, purchase_order_no, ?, payable_type, ?, ?, due_date, ?, ?, create_time FROM accounts_payable WHERE id = ?",
                                newDocNo, arrivalId, amount, paid, newStatus,
                                oldDocNo + " 拆分为到货单#" + arrivalId, apId);
                    }
                    splitCount++;
                    idx++;
                }
                log.info("应付迁移：{} 拆分为 {} 张到货单应付（到货 {} 批）", oldDocNo, arrivals.size(), arrivals.size());
            }
            log.info("应付迁移：共拆分 {} 条存量应付为按到货单立账", splitCount);
        } catch (Exception e) {
            log.error("应付迁移失败（不影响启动，可下次重试）: {}", e.getMessage(), e);
        }
    }

    /** 从原料/成品采购表按单号+物料取采购单价（isFree 返回 0） */
    private BigDecimal findUnitPrice(String orderNo, String materialCode) {
        if (orderNo == null || materialCode == null) return null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT unit_price, is_free FROM raw_material_purchase WHERE order_no = ? AND material_code = ? LIMIT 1",
                    orderNo, materialCode);
            if (rows.isEmpty()) {
                rows = jdbc.queryForList(
                        "SELECT unit_price, is_free FROM finished_product_purchase WHERE order_no = ? AND material_code = ? LIMIT 1",
                        orderNo, materialCode);
            }
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                if (Boolean.TRUE.equals(r.get("is_free"))) return BigDecimal.ZERO;
                return r.get("unit_price") != null ? new BigDecimal(r.get("unit_price").toString()) : null;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String nextDocNo() {
        Integer maxSeq = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM accounts_payable WHERE doc_no LIKE 'AP-2026-%'",
                Integer.class);
        return String.format("AP-2026-%04d", (maxSeq == null ? 0 : maxSeq) + 1);
    }
}
