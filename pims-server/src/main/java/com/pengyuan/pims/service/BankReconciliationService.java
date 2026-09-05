package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.BankAccount;
import com.pengyuan.pims.entity.BankStatement;
import com.pengyuan.pims.repository.AccountsPayableRepository;
import com.pengyuan.pims.repository.PaymentReceiptRepository;
import com.pengyuan.pims.repository.PaymentDisbursementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.poi.ss.usermodel.*;

/**
 * 出纳银行对账（v6.3）：
 * - 账户档案（期初余额）→ 日记账（系统收付款单按账户/日期联查+滚动余额）
 * - 银行流水 Excel 导入（标准模板全量校验单事务）
 * - 自动勾对：同账户 + 金额相等 + 日期 ±3 天容差（对方户名含客户/供应商名加分）
 * - 余额调节表：账面（期初+收-付）vs 银行（对账单期末余额），差异=两边未勾对项
 */
@Service
public class BankReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BankReconciliationService.class);
    private static final DateTimeFormatter D1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbc;
    private final WriteQueue writeQueue;
    private final PaymentReceiptRepository receiptRepo;
    private final PaymentDisbursementRepository disbursementRepo;

    public BankReconciliationService(JdbcTemplate jdbc, WriteQueue writeQueue,
                                     PaymentReceiptRepository receiptRepo,
                                     PaymentDisbursementRepository disbursementRepo) {
        this.jdbc = jdbc;
        this.writeQueue = writeQueue;
        this.receiptRepo = receiptRepo;
        this.disbursementRepo = disbursementRepo;
    }

    // ===== 账户 =====

    public List<Map<String, Object>> listAccounts() {
        return jdbc.queryForList("SELECT * FROM bank_account ORDER BY id");
    }

    public Map<String, Object> saveAccount(Map<String, Object> body) {
        return writeQueue.executeTx(() -> {
            String name = str(body.get("name"));
            if (name == null) throw new IllegalArgumentException("账户名称不能为空");
            Object id = body.get("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                jdbc.update("UPDATE bank_account SET name=?, account_no=?, bank_name=?, opening_balance=?, enabled=? WHERE id=?",
                        name, str(body.get("accountNo")), str(body.get("bankName")),
                        bd(body.get("openingBalance")), strOr(body.get("enabled"), "1"), Long.valueOf(String.valueOf(id)));
            } else {
                if (!jdbc.queryForList("SELECT id FROM bank_account WHERE name = ?", name).isEmpty()) {
                    throw new IllegalArgumentException("账户名称已存在：" + name);
                }
                jdbc.update("INSERT INTO bank_account (name, account_no, bank_name, opening_balance, enabled) VALUES (?,?,?,?,?)",
                        name, str(body.get("accountNo")), str(body.get("bankName")),
                        bd(body.get("openingBalance")), strOr(body.get("enabled"), "1"));
            }
            return jdbc.queryForMap("SELECT * FROM bank_account WHERE name = ?", name);
        });
    }

    // ===== 日记账（系统收付款单） =====

    /**
     * 出纳日记账：指定账户期间内收款(+)/付款(−)联查，期初=账户期初+期间前净额，滚动余额。
     */
    public Map<String, Object> journal(Long accountId, String from, String to) {
        String accountName = accountName(accountId);
        long fromMs = dayStart(from), toMs = dayEnd(to);
        List<Map<String, Object>> rows = new ArrayList<>();
        // 收款（金额+）
        for (var r : jdbc.queryForList(
                "SELECT id AS jid, doc_no AS docNo, strftime('%Y-%m-%d', receipt_date/1000, 'unixepoch', 'localtime') AS d, amount, customer_name AS party, ar_doc_no AS refNo, bank_account AS acc " +
                "FROM payment_receipt WHERE bank_account = ? AND receipt_date >= ? AND receipt_date < ? ORDER BY receipt_date, id",
                accountName, fromMs, toMs)) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("side", "RECEIPT");
            row.put("signed", toBd(r.get("amount")));
            rows.add(row);
        }
        // 付款（金额−）
        for (var r : jdbc.queryForList(
                "SELECT id AS jid, doc_no AS docNo, strftime('%Y-%m-%d', pay_date/1000, 'unixepoch', 'localtime') AS d, amount, supplier_name AS party, ap_doc_no AS refNo, bank_account AS acc " +
                "FROM payment_disbursement WHERE bank_account = ? AND pay_date >= ? AND pay_date < ? ORDER BY pay_date, id",
                accountName, fromMs, toMs)) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("side", "DISBURSEMENT");
            row.put("signed", toBd(r.get("amount")).negate());
            rows.add(row);
        }
        rows.sort((a, b) -> String.valueOf(a.get("d")).compareTo(String.valueOf(b.get("d"))));
        // 期初 = 账户期初 + 期间前净额
        BigDecimal opening = toBd(jdbc.queryForMap("SELECT opening_balance AS ob FROM bank_account WHERE id = ?", accountId).get("ob"));
        BigDecimal beforeIn = toBd(jdbc.queryForMap(
                "SELECT COALESCE(SUM(amount),0) AS v FROM payment_receipt WHERE bank_account = ? AND receipt_date < ?",
                accountName, fromMs).get("v"));
        BigDecimal beforeOut = toBd(jdbc.queryForMap(
                "SELECT COALESCE(SUM(amount),0) AS v FROM payment_disbursement WHERE bank_account = ? AND pay_date < ?",
                accountName, fromMs).get("v"));
        // 期初 = 账户期初 + 期间前净额（receipt_date/pay_date 为毫秒时间戳列，与 fromMs 数值直比）
        opening = opening.add(beforeIn).subtract(beforeOut);
        // 滚动余额 + 勾对状态（按单号+账户+金额符号联流水）
        BigDecimal bal = opening;
        Set<String> matchedKeys = matchedKeys(accountId);
        for (Map<String, Object> row : rows) {
            bal = bal.add(toBd(row.get("signed")));
            row.put("balance", bal.setScale(2, java.math.RoundingMode.HALF_UP));
            row.put("matched", matchedKeys.contains(keyOf(row)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", accountId);
        result.put("accountName", accountName);
        result.put("from", from);
        result.put("to", to);
        result.put("opening", opening.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("closing", bal.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("rows", rows);
        return result;
    }

    // ===== 流水导入（标准模板：日期|摘要|对方户名|收入|支出|余额） =====

    public Map<String, Object> importStatements(Long accountId, MultipartFile file, String operator) {
        String accountName = accountName(accountId);
        List<Map<String, Object>> parsed = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            boolean header = true;
            for (Row row : sheet) {
                if (header) { header = false; continue; }
                String date = cellStr(row.getCell(0));
                String summary = cellStr(row.getCell(1));
                String party = cellStr(row.getCell(2));
                BigDecimal in = cellDec(row.getCell(3));
                BigDecimal out = cellDec(row.getCell(4));
                BigDecimal balance = cellDec(row.getCell(5));
                if (date == null || date.isBlank()) continue;
                LocalDate txDate = parseDate(date);
                if (txDate == null) throw new IllegalArgumentException("第 " + (row.getRowNum() + 1) + " 行日期无法识别：" + date);
                BigDecimal amount = in.subtract(out);
                if (amount.compareTo(BigDecimal.ZERO) == 0) throw new IllegalArgumentException("第 " + (row.getRowNum() + 1) + " 行收/支金额不能同时为空或相等");
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("txDate", txDate);
                r.put("summary", summary);
                r.put("party", party);
                r.put("amount", amount);
                r.put("balance", balance);
                parsed.add(r);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 解析失败：" + e.getMessage());
        }
        if (parsed.isEmpty()) throw new IllegalArgumentException("未解析到流水行（首行为表头，数据从第二行起）");
        return writeQueue.executeTx(() -> {
            String batch = "IMP" + System.currentTimeMillis();
            // 防重：同账户同日期同金额同摘要已存在则跳过（网银重复导出防呆）
            int inserted = 0, skipped = 0;
            for (Map<String, Object> r : parsed) {
                // v6.5：防重加余额比对——同日同额同摘要但余额不同=两笔真实流水（银行常见：多笔等额手续费），
                // 仅"四要素全同"才判重复导入跳过（余额是每笔唯一的强指纹）
                Integer dup = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bank_statement WHERE account_id = ? AND tx_date = ? AND amount = ? " +
                        "AND (summary IS ? OR summary = ?) AND ((balance IS ? AND ? IS NULL) OR balance = ?)",
                        Integer.class, accountId, r.get("txDate"), r.get("amount"),
                        r.get("summary"), r.get("summary"),
                        r.get("balance"), r.get("balance"), r.get("balance"));
                if (dup != null && dup > 0) { skipped++; continue; }
                jdbc.update("INSERT INTO bank_statement (account_id, tx_date, amount, balance, summary, counterparty, status, import_batch) VALUES (?,?,?,?,?,?, 'UNMATCHED', ?)",
                        accountId, r.get("txDate"), r.get("amount"), r.get("balance"), r.get("summary"), r.get("party"), batch);
                inserted++;
            }
            log.info("银行流水导入: 账户={} 新增 {} 条，防重跳过 {} 条（{}）", accountName, inserted, skipped, operator);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("inserted", inserted);
            result.put("skipped", skipped);
            return result;
        });
    }

    public List<Map<String, Object>> listStatements(Long accountId, String from, String to, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.* FROM bank_statement s WHERE s.account_id = ? ");
        List<Object> args = new ArrayList<>(List.of(accountId));
        if (from != null && !from.isBlank()) { sql.append(" AND s.tx_date >= ?"); args.add(from); }
        if (to != null && !to.isBlank()) { sql.append(" AND s.tx_date <= ?"); args.add(to); }
        if ("UNMATCHED".equals(status) || "MATCHED".equals(status)) { sql.append(" AND s.status = ?"); args.add(status); }
        sql.append(" ORDER BY s.tx_date, s.id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ===== 勾对 =====

    /** 自动勾对：金额相等 + 日期 ±3 天；对方户名包含往来单位名时优先 */
    public Map<String, Object> autoMatch(Long accountId) {
        return writeQueue.executeTx(() -> {
            List<Map<String, Object>> statements = jdbc.queryForList(
                    "SELECT * FROM bank_statement WHERE account_id = ? AND status = 'UNMATCHED' ORDER BY tx_date, id", accountId);
            // 收/付款单未勾对池（按单号判断已勾）
            Set<String> matched = matchedKeys(accountId);
            List<Map<String, Object>> receipts = jdbc.queryForList(
                    "SELECT id AS jid, 'RECEIPT' AS side, doc_no AS docNo, strftime('%Y-%m-%d', receipt_date/1000, 'unixepoch', 'localtime') AS d, amount, customer_name AS party FROM payment_receipt WHERE bank_account = ? ORDER BY receipt_date", accountName(accountId));
            List<Map<String, Object>> disbs = jdbc.queryForList(
                    "SELECT id AS jid, 'DISBURSEMENT' AS side, doc_no AS docNo, strftime('%Y-%m-%d', pay_date/1000, 'unixepoch', 'localtime') AS d, amount, supplier_name AS party FROM payment_disbursement WHERE bank_account = ? ORDER BY pay_date", accountName(accountId));
            int auto = 0;
            for (Map<String, Object> st : statements) {
                BigDecimal amt = toBd(st.get("amount"));
                String party = str(st.get("counterparty"));
                LocalDate txDate = LocalDate.parse(String.valueOf(st.get("tx_date")).substring(0, 10));
                List<Map<String, Object>> pool = amt.compareTo(BigDecimal.ZERO) > 0 ? receipts : disbs;
                Map<String, Object> best = null;
                int bestScore = -1;
                for (Map<String, Object> p : pool) {
                    if (matched.contains(keyOf(p))) continue;
                    BigDecimal pAmt = toBd(p.get("amount"));
                    if (amt.compareTo(BigDecimal.ZERO) > 0 ? pAmt.compareTo(amt) != 0 : pAmt.negate().compareTo(amt) != 0) continue;
                    LocalDate pd = LocalDate.parse(String.valueOf(p.get("d")).substring(0, 10));
                    long days = Math.abs(pd.toEpochDay() - txDate.toEpochDay());
                    if (days > 3) continue;
                    int score = 10 - (int) days;
                    if (party != null && str(p.get("party")) != null && (party.contains(str(p.get("party"))) || str(p.get("party")).contains(party))) score += 5;
                    if (score > bestScore) { bestScore = score; best = p; }
                }
                if (best != null) {
                    String refType = amt.compareTo(BigDecimal.ZERO) > 0 ? "RECEIPT" : "DISBURSEMENT";
                    jdbc.update("UPDATE bank_statement SET status='MATCHED', ref_type=?, ref_id=? WHERE id=?",
                            refType, best.get("jid"), st.get("id"));
                    matched.add(keyOf(best));   // side:jid 与 matchedKeys/refId 同键体系
                    auto++;
                }
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("matched", auto);
            r.put("remaining", statements.size() - auto);
            log.info("自动勾对: 账户={} 勾对 {} 笔，剩余 {} 笔未匹配", accountId, auto, statements.size() - auto);
            return r;
        });
    }

    /** 手工勾对：指定流水绑定指定收/付款单 */
    public void bind(Long statementId, String refType, Long refId) {
        if (!"RECEIPT".equals(refType) && !"DISBURSEMENT".equals(refType)) throw new IllegalArgumentException("refType 必须是 RECEIPT/DISBURSEMENT");
        writeQueue.executeTx(() -> {
            jdbc.update("UPDATE bank_statement SET status='MATCHED', ref_type=?, ref_id=? WHERE id=?", refType, refId, statementId);
            return null;
        });
    }

    public void unbind(Long statementId) {
        writeQueue.executeTx(() -> {
            jdbc.update("UPDATE bank_statement SET status='UNMATCHED', ref_type=NULL, ref_id=NULL WHERE id=?", statementId);
            return null;
        });
    }

    // ===== 余额调节表 =====

    /**
     * 余额调节表（期间末时点）：
     * 账面余额 = 账户期初 + 期间前净额 + 期间收款 − 期间付款（全部，含未勾对）
     * 银行余额 = 期间内最后一笔带余额的流水（无则手工传 bankEnding）
     * 差异：企业已收/付银行未记 = 期间日记账未勾对收/付；银行已收/付企业未记 = 期间流水未勾对收/支
     * 调节后两边应相等（差额=仍需人工认定的错账）。
     */
    public Map<String, Object> reconcileReport(Long accountId, String from, String to, BigDecimal bankEnding) {
        String accountName = accountName(accountId);
        long fromMs = dayStart(from), toMs = dayEnd(to);
        var journal = journal(accountId, from, to);
        BigDecimal bookClosing = toBd(journal.get("closing"));
        // 银行期末：优先手工传入，否则取期间内最后一笔余额
        BigDecimal bankEnd = bankEnding;
        if (bankEnd == null) {
            var last = jdbc.queryForList(
                    "SELECT balance FROM bank_statement WHERE account_id = ? AND balance IS NOT NULL AND tx_date >= ? AND tx_date <= ? ORDER BY tx_date DESC, id DESC LIMIT 1",
                    accountId, from, to);
            if (!last.isEmpty()) bankEnd = toBd(last.get(0).get("balance"));
        }
        Set<String> matched = matchedKeys(accountId);
        // v6.5 B2（会计口径修正）：未达账项 = 截至对账日(to)的全部未勾对，含期初之前的——
        // 原只统计 [from,to] 期间，上期遗留未达漏算，跨期调节表两侧必不相等。
        // 企业侧：该账户全部收付款单（日期 <= to）中未勾对者；银行侧：流水 status=UNMATCHED 且 tx_date <= to。
        BigDecimal firmIn = BigDecimal.ZERO, firmOut = BigDecimal.ZERO;   // 企业已记银行未记
        for (var row : jdbc.queryForList(
                "SELECT id, amount FROM payment_receipt WHERE bank_account = ? AND receipt_date < ?", accountName, toMs)) {
            if (matched.contains("RECEIPT:" + row.get("id"))) continue;
            firmIn = firmIn.add(toBd(row.get("amount")));
        }
        for (var row : jdbc.queryForList(
                "SELECT id, amount FROM payment_disbursement WHERE bank_account = ? AND pay_date < ?", accountName, toMs)) {
            if (matched.contains("DISBURSEMENT:" + row.get("id"))) continue;
            firmOut = firmOut.add(toBd(row.get("amount")));
        }
        BigDecimal bankIn = BigDecimal.ZERO, bankOut = BigDecimal.ZERO;   // 银行已记企业未记
        List<Map<String, Object>> unmatchedStmts = new ArrayList<>();
        for (var st : jdbc.queryForList(
                "SELECT * FROM bank_statement WHERE account_id = ? AND status = 'UNMATCHED' AND tx_date <= ? ORDER BY tx_date", accountId, to)) {
            BigDecimal amt = toBd(st.get("amount"));
            if (amt.compareTo(BigDecimal.ZERO) > 0) bankIn = bankIn.add(amt);
            else bankOut = bankOut.add(amt.negate());
            Map<String, Object> u = new LinkedHashMap<>(st);
            unmatchedStmts.add(u);
        }
        // v6.3.1 修正：双侧调节（原公式混侧）——
        // 银行侧调节后 = 银行余额 + 企业已收银行未记 − 企业已付银行未记
        // 账面侧调节后 = 账面余额 + 银行已收企业未记 − 银行已付企业未记；两侧应相等
        BigDecimal adjustedBank = bankEnd == null ? null : bankEnd.add(firmIn).subtract(firmOut);
        BigDecimal adjustedBook = bookClosing.add(bankIn).subtract(bankOut);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("accountId", accountId);
        r.put("accountName", accountName);
        r.put("from", from);
        r.put("to", to);
        r.put("bookClosing", bookClosing);
        r.put("bankEnding", bankEnd);
        r.put("firmInNotInBank", firmIn.setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("firmOutNotInBank", firmOut.setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("bankInNotInFirm", bankIn.setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("bankOutNotInFirm", bankOut.setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("adjustedBank", adjustedBank == null ? null : adjustedBank.setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("adjustedBook", adjustedBook.setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("diff", adjustedBank == null ? null : adjustedBank.subtract(adjustedBook).setScale(2, java.math.RoundingMode.HALF_UP));
        r.put("unmatchedStatements", unmatchedStmts);
        return r;
    }

    // ===== 内部 =====

    private String accountName(Long accountId) {
        var rows = jdbc.queryForList("SELECT name FROM bank_account WHERE id = ?", accountId);
        if (rows.isEmpty()) throw new IllegalArgumentException("银行账户不存在");
        return String.valueOf(rows.get(0).get("name"));
    }

    /** 已勾对的日记账单号键（流水 ref 关联反查） */
    private Set<String> matchedKeys(Long accountId) {
        Set<String> keys = new HashSet<>();
        for (var st : jdbc.queryForList(
                "SELECT ref_type, ref_id FROM bank_statement WHERE account_id = ? AND status = 'MATCHED' AND ref_type IS NOT NULL", accountId)) {
            keys.add(st.get("ref_type") + ":" + st.get("ref_id"));
        }
        return keys;
    }

    private String keyOf(Map<String, Object> journalRow) {
        return journalRow.get("side") + ":" + journalRow.get("jid");
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o).trim(); }
    private static String strOr(Object o, String d) { String s = str(o); return s == null || s.isBlank() ? d : s; }
    private static BigDecimal bd(Object o) { return o == null ? BigDecimal.ZERO : toBd(o); }

    private static BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(String.valueOf(v));
    }

    private long dayStart(String day) {
        return LocalDate.parse(day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long dayEnd(String day) {
        return LocalDate.parse(day).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String cellStr(Cell c) {
        if (c == null) return null;
        try {
            return switch (c.getCellType()) {
                case STRING -> c.getStringCellValue().trim();
                case NUMERIC -> DateUtil.isCellDateFormatted(c)
                        ? c.getLocalDateTimeCellValue().toLocalDate().format(D1)
                        : String.valueOf((long) c.getNumericCellValue());
                default -> null;
            };
        } catch (Exception e) { return null; }
    }

    private static BigDecimal cellDec(Cell c) {
        if (c == null) return BigDecimal.ZERO;
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue());
                case STRING -> new BigDecimal(c.getStringCellValue().trim().replace(",", ""));
                default -> BigDecimal.ZERO;
            };
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static LocalDate parseDate(String s) {
        for (String p : List.of("yyyy-MM-dd", "yyyy/M/d", "yyyy.MM.dd", "yyyyMMdd")) {
            try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)); } catch (Exception ignore) {}
        }
        try { return LocalDate.parse(s); } catch (Exception ignore) {}
        return null;
    }
}
