package com.pengyuan.pims.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务报表（v5.36）：月度利润试算 + 客户对账单
 *
 * 利润试算口径（管理口径，非会计准则）：
 *   营业收入 = 当月销售出库立账应收合计（accounts_receivable.amount，按立账时间归月）
 *   营业成本 = 当月已确认销售出库的库存成本合计（sales_outbound.cost）
 *   期间费用 = 当月费用单支出合计（expense，按登记时间归月）；其他收入 = 费用单收入方向合计
 *   利润净额 = 收入 − 成本 + 其他收入 − 费用
 *   参考区（资金占用，不进损益公式）：当月生产领料 / 委外加工费立账 / 采购应付立账
 *
 * 对账单口径：客户欠款 = Σ应收立账 − Σ收款 − Σ销售退货冲减（退货按 FIFO 累加 receivedAmount，与 AR 表余额恒等闭环）
 */
@Service
public class FinanceReportService {

    private final JdbcTemplate jdbc;

    public FinanceReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ===== 月度利润试算 =====

    public Map<String, Object> profitTrial(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException("月份格式应为 YYYY-MM");

        BigDecimal revenue = sumOrZero("SELECT SUM(amount) FROM accounts_receivable WHERE " + monthOf("create_time"), month, month);
        BigDecimal cogs = sumOrZero("SELECT SUM(cost) FROM sales_outbound WHERE status = 'CONFIRMED' AND " + monthOf("create_time"), month, month);
        BigDecimal grossProfit = revenue.subtract(cogs);
        BigDecimal grossRate = revenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP) : null;

        BigDecimal expenseTotal = sumOrZero("SELECT SUM(amount) FROM expense WHERE direction = 'EXPENSE' AND " + monthOf("create_time"), month, month);
        // v5.66 物流运费（公司承担）进管理口径费用——shipping_log 独立于 expense 表，不与 FREIGHT 费用单双算
        BigDecimal freight = sumOrZero("SELECT SUM(freight) FROM shipping_log WHERE borne = 'COMPANY' AND strftime('%Y-%m', ship_date) = ?", month);
        expenseTotal = expenseTotal.add(freight);
        BigDecimal otherIncome = sumOrZero("SELECT SUM(amount) FROM expense WHERE direction = 'INCOME' AND " + monthOf("create_time"), month, month);
        BigDecimal netProfit = grossProfit.add(otherIncome).subtract(expenseTotal);

        List<Map<String, Object>> expenseDetail = labelList(
                "SELECT expense_type AS type, SUM(amount) AS amount FROM expense WHERE direction = 'EXPENSE' AND "
                        + monthOf("create_time") + " GROUP BY expense_type ORDER BY SUM(amount) DESC", month, month);
        List<Map<String, Object>> incomeDetail = labelList(
                "SELECT expense_type AS type, SUM(amount) AS amount FROM expense WHERE direction = 'INCOME' AND "
                        + monthOf("create_time") + " GROUP BY expense_type ORDER BY SUM(amount) DESC", month, month);

        // 资金占用参考（当月投入，不进损益公式）
        BigDecimal materialUsed = sumOrZero("SELECT SUM(cost) FROM production_outbound WHERE status = 'CONFIRMED' AND " + monthOf("create_time"), month, month);
        BigDecimal outsourceFee = sumOrZero("SELECT SUM(amount) FROM accounts_payable WHERE payable_type = 'OUTSOURCE' AND " + monthOf("create_time"), month, month);
        BigDecimal purchaseAp = sumOrZero("SELECT SUM(amount) FROM accounts_payable WHERE payable_type = 'PURCHASE' AND " + monthOf("create_time"), month, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("revenue", revenue);
        result.put("cogs", cogs);
        result.put("grossProfit", grossProfit);
        result.put("grossRate", grossRate);
        result.put("expenseTotal", expenseTotal);
        result.put("otherIncome", otherIncome);
        result.put("netProfit", netProfit);
        result.put("expenseDetail", expenseDetail);
        result.put("incomeDetail", incomeDetail);
        result.put("materialUsed", materialUsed);
        result.put("outsourceFee", outsourceFee);
        result.put("purchaseAp", purchaseAp);
        result.put("trend", trend());
        return result;
    }

    /** 近 12 个月收入/成本/净利走势 */
    private List<Map<String, Object>> trend() {
        // 近 11 个月首月零点毫秒（常量表达式，裸列比较可走索引）
        String sinceMs = "1000 * (CAST(strftime('%s', strftime('%Y-%m','now','+8 hours','-11 months') || '-01') AS INTEGER) - 28800)";
        Map<String, Object> rev = monthSum("SELECT " + tsMonth("create_time") + " AS m, SUM(amount) AS v FROM accounts_receivable WHERE create_time >= " + sinceMs + " GROUP BY m");
        Map<String, Object> cogsMap = monthSum("SELECT " + tsMonth("create_time") + " AS m, SUM(cost) AS v FROM sales_outbound WHERE status='CONFIRMED' AND create_time >= " + sinceMs + " GROUP BY m");
        Map<String, Object> expMap = monthSum("SELECT m, SUM(v) AS v FROM (SELECT " + tsMonth("create_time") + " AS m, amount AS v FROM expense WHERE direction='EXPENSE' AND create_time >= " + sinceMs +
                " UNION ALL SELECT " + tsMonth("create_time") + " AS m, -amount AS v FROM expense WHERE direction='INCOME' AND create_time >= " + sinceMs + ") GROUP BY m");

        java.util.TreeSet<String> months = new java.util.TreeSet<>();
        months.addAll(rev.keySet()); months.addAll(cogsMap.keySet()); months.addAll(expMap.keySet());
        List<Map<String, Object>> list = new ArrayList<>();
        for (String m : months) {
            BigDecimal rv = toBd(rev.get(m)), cg = toBd(cogsMap.get(m)), ex = toBd(expMap.get(m));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m);
            row.put("revenue", rv);
            row.put("cogs", cg);
            row.put("net", rv.subtract(cg).subtract(ex));
            list.add(row);
        }
        return list;
    }

    // ===== 客户对账单 =====

    /**
     * 客户期间对账单：期初余额 + 期间往来明细（应收立账+/收款−/销售退货−）+ 期末余额。
     * 期末余额 = 客户当前欠款（与 AR 表 amount − receivedAmount 恒等）。
     * 退货行按「customer_id 或 订单号关联 AR」匹配——与 applySalesReturn 按订单号 FIFO 冲减的口径对齐
     * （历史退货单可能未回填 customer_id，但其冲减落在订单对应客户的 AR 上）。
     */
    public Map<String, Object> statement(Long customerId, String from, String to) {
        List<Map<String, Object>> cust = jdbc.queryForList(
                "SELECT id, code, name, tax_no AS taxNo FROM customer WHERE id = ?", customerId);
        if (cust.isEmpty()) throw new IllegalArgumentException("客户不存在");

        String returnMatch = "(customer_id = ? OR EXISTS (SELECT 1 FROM accounts_receivable ar WHERE ar.sales_order_no = return_order.sales_order_no AND ar.customer_id = ?))";

        BigDecimal openAr = sumOrZero("SELECT SUM(amount) FROM accounts_receivable WHERE customer_id = ? AND " + dayBefore("create_time"), customerId, from);
        BigDecimal openRc = sumOrZero("SELECT SUM(amount) FROM payment_receipt WHERE customer_id = ? AND " + dayBefore("receipt_date"), customerId, from);
        BigDecimal openRt = sumOrZero("SELECT SUM(return_amount) FROM return_order WHERE type = 'SALES_RETURN' AND status = 'DONE' AND " + returnMatch + " AND " + dayBefore("COALESCE(update_time, create_time)"),
                customerId, customerId, from);
        BigDecimal opening = openAr.subtract(openRc).subtract(openRt);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT " + tsDay("create_time") + " AS d, doc_no AS docNo, sales_order_no AS refNo, amount FROM accounts_receivable " +
                "WHERE customer_id = ? AND " + dayStart("create_time") + " AND " + dayThrough("create_time") + " ORDER BY create_time", customerId, from, to)) {
            lines.add(line(r, "销售立账", r.get("refNo") == null ? "" : "订单 " + r.get("refNo"), toBd(r.get("amount")), null));
        }
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT " + tsDay("receipt_date") + " AS d, doc_no AS docNo, ar_doc_no AS refNo, amount FROM payment_receipt " +
                "WHERE customer_id = ? AND " + dayStart("receipt_date") + " AND " + dayThrough("receipt_date") + " ORDER BY receipt_date", customerId, from, to)) {
            lines.add(line(r, "收款", r.get("refNo") == null ? "" : "核销 " + r.get("refNo"), null, toBd(r.get("amount"))));
        }
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT " + tsDay("COALESCE(update_time, create_time)") + " AS d, doc_no AS docNo, material_name AS refNo, return_amount AS amount FROM return_order " +
                "WHERE type = 'SALES_RETURN' AND status = 'DONE' AND " + returnMatch + " AND " + dayStart("COALESCE(update_time, create_time)") + " AND " + dayThrough("COALESCE(update_time, create_time)"),
                customerId, customerId, from, to)) {
            lines.add(line(r, "销售退货", r.get("refNo") == null ? "" : String.valueOf(r.get("refNo")), null, toBd(r.get("amount"))));
        }

        lines.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));
        BigDecimal debit = lines.stream().map(l -> toBd(l.get("debit"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(l -> toBd(l.get("credit"))).reduce(BigDecimal.ZERO, BigDecimal::add);

        // v6.1 期末改纯时点口径：期初 + 期间借 − 期间贷 = 期末，三流全部按各自业务日期截断，
        // 期间之后的收款/退货不再冲减历史对账单（原实现锚定 AR 台账的 received_amount 当前累计值，
        // 收到上月货款后上月对账单期末会变小，且靠"冲减调整"行抹平，客户对不上账）。
        BigDecimal closing = opening.add(debit).subtract(credit);
        // 与 AR 台账当前余额的差额作为参考信息返回（不再改写期末），供财务核对历史口径差
        BigDecimal ledgerBalance = sumOrZero("SELECT SUM(amount) - SUM(received_amount) FROM accounts_receivable WHERE customer_id = ?", customerId);
        BigDecimal ledgerDiff = ledgerBalance.subtract(closing);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", cust.get(0));
        result.put("from", from);
        result.put("to", to);
        result.put("opening", opening);
        result.put("lines", lines);
        result.put("debit", debit);
        result.put("credit", credit);
        result.put("closing", closing);
        result.put("ledgerBalance", ledgerBalance);
        result.put("ledgerDiff", ledgerDiff);
        return result;
    }

    // ===== 供应商期间对账单（v6.3：与客户版同款纯时点三流口径） =====

    /**
     * 供应商期间对账单：期初余额 + 期间往来明细（应付立账+/付款−/采购退货冲减−）+ 期末余额。
     * 期末 = 期初 + 期间应付 − 期间付款 − 期间退货冲减（三流各按业务日期截断）。
     * 退货冲减口径：系统内退货视同已付款（FIFO 累计 paidAmount），故对账单以"退货冲减"单列体现；
     * 退货行按「supplier_id 或 采购单号关联 AP」匹配（与 applyPurchaseReturn/BySupplier 的冲减落点对齐）。
     */
    public Map<String, Object> supplierStatement(Long supplierId, String from, String to) {
        List<Map<String, Object>> sup = jdbc.queryForList(
                "SELECT id, code, name FROM supplier WHERE id = ?", supplierId);
        if (sup.isEmpty()) throw new IllegalArgumentException("供应商不存在");

        String returnMatch = "(supplier_id = ? OR EXISTS (SELECT 1 FROM accounts_payable ap WHERE ap.purchase_order_no = return_order.purchase_order_no AND ap.supplier_id = ?))";

        BigDecimal openAp = sumOrZero("SELECT SUM(amount) FROM accounts_payable WHERE supplier_id = ? AND " + dayBefore("create_time"), supplierId, from);
        BigDecimal openPay = sumOrZero("SELECT SUM(amount) FROM payment_disbursement WHERE supplier_id = ? AND " + dayBefore("pay_date"), supplierId, from);
        BigDecimal openRet = sumOrZero("SELECT SUM(return_amount) FROM return_order WHERE type = 'PURCHASE_RETURN' AND status = 'DONE' AND " + returnMatch + " AND " + dayBefore("COALESCE(update_time, create_time)"),
                supplierId, supplierId, from);
        BigDecimal opening = openAp.subtract(openPay).subtract(openRet);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT " + tsDay("create_time") + " AS d, doc_no AS docNo, purchase_order_no AS refNo, amount FROM accounts_payable " +
                "WHERE supplier_id = ? AND " + dayStart("create_time") + " AND " + dayThrough("create_time") + " ORDER BY create_time", supplierId, from, to)) {
            lines.add(line(r, "应付立账", r.get("refNo") == null ? "" : "采购 " + r.get("refNo"), toBd(r.get("amount")), null));
        }
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT " + tsDay("pay_date") + " AS d, doc_no AS docNo, ap_doc_no AS refNo, amount FROM payment_disbursement " +
                "WHERE supplier_id = ? AND " + dayStart("pay_date") + " AND " + dayThrough("pay_date") + " ORDER BY pay_date", supplierId, from, to)) {
            lines.add(line(r, "付款", r.get("refNo") == null ? "" : "核销 " + r.get("refNo"), null, toBd(r.get("amount"))));
        }
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT " + tsDay("COALESCE(update_time, create_time)") + " AS d, doc_no AS docNo, material_name AS refNo, return_amount AS amount FROM return_order " +
                "WHERE type = 'PURCHASE_RETURN' AND status = 'DONE' AND " + returnMatch + " AND " + dayStart("COALESCE(update_time, create_time)") + " AND " + dayThrough("COALESCE(update_time, create_time)"),
                supplierId, supplierId, from, to)) {
            lines.add(line(r, "退货冲减", r.get("refNo") == null ? "" : String.valueOf(r.get("refNo")), null, toBd(r.get("amount"))));
        }

        lines.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));
        BigDecimal debit = lines.stream().map(l -> toBd(l.get("debit"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(l -> toBd(l.get("credit"))).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 期末纯时点口径（与客户版 v6.1.2 一致）；AP 台账当前余额（Σ立账−Σpaid，退货已累计在 paid 内）作参考列
        BigDecimal closing = opening.add(debit).subtract(credit);
        BigDecimal ledgerBalance = sumOrZero("SELECT SUM(amount) - SUM(COALESCE(paid_amount,0)) FROM accounts_payable WHERE supplier_id = ?", supplierId);
        BigDecimal ledgerDiff = ledgerBalance.subtract(closing);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplier", sup.get(0));
        result.put("from", from);
        result.put("to", to);
        result.put("opening", opening);
        result.put("lines", lines);
        result.put("debit", debit);
        result.put("credit", credit);
        result.put("closing", closing);
        result.put("ledgerBalance", ledgerBalance);
        result.put("ledgerDiff", ledgerDiff);
        return result;
    }

    private static Map<String, Object> line(Map<String, Object> r, String type, String note, BigDecimal debit, BigDecimal credit) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("date", r.get("d"));
        l.put("docNo", r.get("docNo"));
        l.put("type", type);
        l.put("note", note);
        l.put("debit", debit);    // 应收增加（借方）
        l.put("credit", credit);  // 收款/退货冲减（贷方）
        return l;
    }

    // ===== SQLite 时间工具（v5.55 起时间列统一毫秒整数：WHERE 一律裸列范围比较走 create_time 索引；格式化仅用于 SELECT 展示） =====

    /** 展示用：毫秒列 → 'YYYY-MM'（东八区） */
    private static String tsMonth(String col) {
        return "strftime('%Y-%m', " + col + "/1000, 'unixepoch', '+8 hours')";
    }

    /** 展示用：毫秒列 → 'YYYY-MM-DD'（东八区） */
    private static String tsDay(String col) {
        return "strftime('%Y-%m-%d', " + col + "/1000, 'unixepoch', '+8 hours')";
    }

    /** ?=yyyy-MM-dd：列 >= 东八区当日零点毫秒（strftime 对绑定参数常量折叠，可走索引） */
    private static String dayStart(String col) {
        return col + " >= 1000 * (CAST(strftime('%s', ?) AS INTEGER) - 28800)";
    }

    /** ?=yyyy-MM-dd：列 < 东八区当日零点毫秒（期初口径） */
    private static String dayBefore(String col) {
        return col + " < 1000 * (CAST(strftime('%s', ?) AS INTEGER) - 28800)";
    }

    /** ?=yyyy-MM-dd（期间末天，含当天）：列 < 次日零点毫秒 */
    private static String dayThrough(String col) {
        return col + " < 1000 * (CAST(strftime('%s', ?) AS INTEGER) + 86400 - 28800)";
    }

    /** 两个 ? 均为 yyyy-MM：列在当月 [月初, 次月初) 毫秒范围内 */
    private static String monthOf(String col) {
        return col + " >= 1000 * (CAST(strftime('%s', ? || '-01') AS INTEGER) - 28800)" +
                " AND " + col + " < 1000 * (CAST(strftime('%s', ? || '-01', '+1 month') AS INTEGER) - 28800)";
    }

    private Map<String, Object> monthSum(String sql) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(sql)) m.put(String.valueOf(r.get("m")), toBd(r.get("v")));
        return m;
    }

    private BigDecimal sumOrZero(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) return BigDecimal.ZERO;
        Object v = rows.get(0).values().iterator().next();
        return toBd(v);
    }

    private List<Map<String, Object>> labelList(String sql, Object... args) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, args)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.get("type"));
            m.put("amount", toBd(r.get("amount")));
            out.add(m);
        }
        return out;
    }

    /** SQLite 聚合值可能是 Integer/Double/BigDecimal，统一兼容（历史 ClassCast 坑） */
    private static BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
