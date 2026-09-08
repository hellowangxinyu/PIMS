package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 记账凭证（v5.61 总账体系）：凭证 CRUD/记账 + 业务单据转凭证 + 结转损益 + 期间结账 */
@Service
public class VoucherService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VoucherService.class);

    private final VoucherRepository repo;
    private final VoucherEntryRepository entryRepo;
    private final AccountPeriodRepository periodRepo;
    private final AccountSubjectRepository subjectRepo;
    private final AccountSubjectService subjectService;
    private final PaymentReceiptRepository receiptRepo;
    private final PaymentDisbursementRepository disbursementRepo;
    private final ExpenseRepository expenseRepo;
    private final InvoiceRepository invoiceRepo;
    private final WriteQueue writeQueue;
    private final JdbcTemplate jdbc;
    private final CostingService costingService;   // v5.63 全月平均结账前置校验

    public VoucherService(VoucherRepository repo, VoucherEntryRepository entryRepo,
                          AccountPeriodRepository periodRepo, AccountSubjectRepository subjectRepo,
                          AccountSubjectService subjectService,
                          PaymentReceiptRepository receiptRepo, PaymentDisbursementRepository disbursementRepo,
                          ExpenseRepository expenseRepo, InvoiceRepository invoiceRepo,
                          WriteQueue writeQueue, JdbcTemplate jdbc, CostingService costingService) {
        this.repo = repo;
        this.entryRepo = entryRepo;
        this.periodRepo = periodRepo;
        this.subjectRepo = subjectRepo;
        this.subjectService = subjectService;
        this.receiptRepo = receiptRepo;
        this.disbursementRepo = disbursementRepo;
        this.expenseRepo = expenseRepo;
        this.invoiceRepo = invoiceRepo;
        this.writeQueue = writeQueue;
        this.jdbc = jdbc;
        this.costingService = costingService;
    }

    // ===== 查询 =====

    /** 全量凭证 + 批量预取分录（禁 N+1） */
    public List<Voucher> list() {
        List<Voucher> all = repo.findAllByOrderByVoucherDateDescIdDesc();
        if (all.isEmpty()) return all;
        List<Long> ids = all.stream().map(v -> v.id).toList();
        Map<Long, List<VoucherEntry>> byVoucher = new LinkedHashMap<>();
        for (VoucherEntry e : entryRepo.findByVoucherIdInOrderByVoucherIdAscLineNoAsc(ids)) {
            byVoucher.computeIfAbsent(e.voucherId, k -> new ArrayList<>()).add(e);
        }
        for (Voucher v : all) v.entries = byVoucher.getOrDefault(v.id, List.of());
        return all;
    }

    // ===== 凭证 CRUD =====

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public Voucher create(Voucher v) {
        normalizeAndValidate(v);
        checkPeriodOpen(v.period);
        if (v.source == null || v.source.isBlank()) v.source = "MANUAL";
        if (!"MANUAL".equals(v.source)) {
            if (v.refDocNo == null || v.refDocNo.isBlank()) throw new IllegalArgumentException("业务来源凭证必须关联来源单号");
        } else {
            v.refDocNo = null;
        }
        // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁
        return writeQueue.executeTx(() -> {
            // v6.1.4：来源查重挪进锁内（此前锁外先查后锁，并发双过 TOCTOU）
            if (!"MANUAL".equals(v.source) && repo.existsBySourceAndRefDocNo(v.source, v.refDocNo)) {
                throw new IllegalArgumentException("来源单据 " + v.refDocNo + " 已生成过凭证，不能重复生成");
            }
            // 会计准则：凭证按月连续编号（每月从0001起，审计查连续性），不按天
            String vchMonth = v.voucherDate.toString().substring(0, 7).replace("-", "");
            Integer maxSeq = repo.findMaxSeq("VCH-" + vchMonth + "-%");
            v.docNo = String.format("VCH-%s-%04d", vchMonth, (maxSeq == null ? 0 : maxSeq) + 1);
            Voucher saved = repo.save(v);
            saveEntries(saved);
            return assemble(saved);
        });
    }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public Voucher update(Long id, Voucher in) {
        Voucher v = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("凭证不存在"));
        if ("POSTED".equals(v.status)) throw new IllegalArgumentException("已记账凭证不能修改，请先反记账");
        String oldPeriod = v.period;
        v.voucherDate = in.voucherDate;
        v.period = periodOf(in.voucherDate);
        v.attachmentCount = in.attachmentCount;
        v.remark = in.remark;
        v.entries = in.entries;
        v.updateTime = LocalDateTime.now();
        normalizeAndValidate(v);
        checkPeriodOpen(v.period);
        // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁
        return writeQueue.executeTx(() -> {
            // 凭证按月连续编号：改期跨月时原编号仍属旧月份序列，须按新月重新取号（旧号留空不重排，审计可追溯）
            if (!v.period.equals(oldPeriod)) {
                String vchMonth = v.voucherDate.toString().substring(0, 7).replace("-", "");
                Integer maxSeq = repo.findMaxSeq("VCH-" + vchMonth + "-%");
                v.docNo = String.format("VCH-%s-%04d", vchMonth, (maxSeq == null ? 0 : maxSeq) + 1);
            }
            entryRepo.deleteByVoucherId(v.id);
            saveEntries(v);
            repo.save(v);
            return assemble(v);
        });
    }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public void delete(Long id) {
        Voucher v = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("凭证不存在"));
        if ("POSTED".equals(v.status)) throw new IllegalArgumentException("已记账凭证不能删除，请先反记账");
        // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁
        writeQueue.executeTx(() -> {
            entryRepo.deleteByVoucherId(id);
            repo.deleteById(id);
        });
    }

    /** 记账：DRAFT → POSTED */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public Voucher post(Long id, String operator) {
        Voucher v = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("凭证不存在"));
        if ("POSTED".equals(v.status)) throw new IllegalArgumentException("凭证已记账");
        checkPeriodOpen(v.period);
        // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁
        return writeQueue.executeTx(() -> {
            v.status = "POSTED";
            v.postedBy = operator;
            v.postedTime = LocalDateTime.now();
            v.updateTime = LocalDateTime.now();
            return repo.save(v);
        });
    }

    /** 反记账：POSTED → DRAFT */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public Voucher unpost(Long id) {
        Voucher v = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("凭证不存在"));
        if (!"POSTED".equals(v.status)) throw new IllegalArgumentException("凭证尚未记账");
        checkPeriodOpen(v.period);
        // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁
        return writeQueue.executeTx(() -> {
            v.status = "DRAFT";
            v.postedBy = null;
            v.postedTime = null;
            v.updateTime = LocalDateTime.now();
            return repo.save(v);
        });
    }

    // ===== 业务单据转凭证（生成预览，前端弹窗确认后调 create 落库） =====

    public Map<String, Object> generate(String sourceType, Long refId) {
        return switch (sourceType) {
            case "RECEIPT" -> genReceipt(refId);
            case "PAYMENT" -> genPayment(refId);
            case "EXPENSE" -> genExpense(refId);
            case "INVOICE" -> genInvoice(refId);
            default -> throw new IllegalArgumentException("不支持的来源类型 " + sourceType);
        };
    }

    private Map<String, Object> genReceipt(Long id) {
        PaymentReceipt r = receiptRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("收款单不存在"));
        checkNotGenerated("RECEIPT", r.docNo);
        String cashCode = methodSubject("RECEIPT", r.method);
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(cashCode, "收款-" + r.customerName, r.amount, null, null, null));
        entries.add(entry("1122", "收 " + r.customerName + " 货款", null, r.amount, "CUSTOMER", r.customerName));
        return preview(r.receiptDate, "RECEIPT", r.docNo, "收款 " + r.customerName, r.remark, entries);
    }

    private Map<String, Object> genPayment(Long id) {
        PaymentDisbursement p = disbursementRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("付款单不存在"));
        checkNotGenerated("PAYMENT", p.docNo);
        String cashCode = methodSubject("PAYMENT", p.method);
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry("2202", "付 " + p.supplierName + " 货款", p.amount, null, "SUPPLIER", p.supplierName));
        entries.add(entry(cashCode, "付款-" + p.supplierName, null, p.amount, null, null));
        return preview(p.payDate, "PAYMENT", p.docNo, "付款 " + p.supplierName, p.remark, entries);
    }

    private Map<String, Object> genExpense(Long id) {
        Expense e = expenseRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("费用单不存在"));
        checkNotGenerated("EXPENSE", e.docNo);
        String cashCode = methodSubject("PAYMENT", e.method);
        String typeKey = "INCOME".equals(e.direction) ? "biz:income:" : "biz:expense:";
        String feeCode = subjectService.mappedSubject(typeKey + e.expenseType);
        String digest = e.expenseType + (e.partner == null || e.partner.isBlank() ? "" : "-" + e.partner);
        List<Map<String, Object>> entries = new ArrayList<>();
        if ("INCOME".equals(e.direction)) {
            entries.add(entry(cashCode, digest, e.amount, null, null, null));
            entries.add(entry(feeCode, digest, null, e.amount, null, null));
        } else {
            entries.add(entry(feeCode, digest, e.amount, null, null, null));
            entries.add(entry(cashCode, digest, null, e.amount, null, null));
        }
        return preview(e.occurDate, "EXPENSE", e.docNo, digest, e.remark, entries);
    }

    private Map<String, Object> genInvoice(Long id) {
        Invoice iv = invoiceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("发票不存在"));
        checkNotGenerated("INVOICE", iv.docNo);
        // 红字发票（红冲生成的负数对冲单）按负数金额生成红字分录；已红冲的原单禁止生成
        boolean red = iv.totalAmount != null && iv.totalAmount.compareTo(BigDecimal.ZERO) < 0;
        if ("FLUSHED".equals(iv.status)) throw new IllegalArgumentException("发票 " + iv.docNo + " 已红冲，请对红字对冲发票生成凭证");
        List<Map<String, Object>> entries = new ArrayList<>();
        if ("OUTPUT".equals(iv.direction)) {
            String auxType = iv.partnerName == null ? null : "CUSTOMER";
            BigDecimal tax = nvl(iv.taxAmount), amt = nvl(iv.amount);
            entries.add(entry("1122", "开票 " + iv.partnerName, nvl(iv.totalAmount), null, auxType, iv.partnerName));
            entries.add(entry("6001", "开票 " + iv.partnerName, null, amt, auxType, iv.partnerName));
            if (tax.compareTo(BigDecimal.ZERO) != 0) {
                entries.add(entry("2221.01", "销项税额", null, tax, null, null));
            }
        } else {
            // 进项：借进项税+存货（默认原材料，弹窗可改），贷应付
            BigDecimal tax = nvl(iv.taxAmount), amt = nvl(iv.amount);
            String auxType = iv.partnerName == null ? null : "SUPPLIER";
            if (tax.compareTo(BigDecimal.ZERO) != 0) {   // v6.1 修复：零税额免税票不加税额行（否则被零金额校验拒绝），与销项侧同守卫
                entries.add(entry("2221.02", "进项税额", tax, null, null, null));
            }
            entries.add(entry("1403", "采购 " + iv.partnerName, amt, null, null, null));
            entries.add(entry("2202", "采购 " + iv.partnerName, null, nvl(iv.totalAmount), auxType, iv.partnerName));
        }
        Map<String, Object> p;
        if (red) {
            // v6.1.5（高A）：红字分录改为「借贷方向翻转、金额取正」的标准红字表达——
            // v6.1.4 起负数分录被 normalizeAndValidate 拦截，原按负数金额生成的红字分录必被拒（红字发票无法生成凭证）
            List<Map<String, Object>> flipped = new ArrayList<>();
            for (Map<String, Object> e : entries) {
                java.math.BigDecimal d = nvl((BigDecimal) e.get("debit")), c = nvl((BigDecimal) e.get("credit"));
                flipped.add(entry((String) e.get("subjectCode"), (String) e.get("digest"),
                        c.abs(), d.abs(), (String) e.get("auxType"), (String) e.get("auxName")));
            }
            entries = flipped;
        }
        p = preview(iv.invoiceDate, "INVOICE", iv.docNo,
                ("OUTPUT".equals(iv.direction) ? "销项" : "进项") + "发票 " + (red ? "(红字)" : ""), iv.remark, entries);
        if (red) p.put("red", true);
        return p;
    }

    private void checkNotGenerated(String source, String refDocNo) {
        if (repo.existsBySourceAndRefDocNo(source, refDocNo)) {
            throw new IllegalArgumentException("来源单据 " + refDocNo + " 已生成过凭证，不能重复生成");
        }
    }

    /** 收付款方式 → 资金科目（biz:method: 映射，未配置默认银行存款） */
    private String methodSubject(String direction, String method) {
        String code = subjectService.mappedSubject("biz:method:" + direction + ":" + (method == null ? "BANK" : method));
        return code != null ? code : "1002";
    }

    private Map<String, Object> entry(String subjectCode, String digest, BigDecimal debit, BigDecimal credit, String auxType, String auxName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subjectCode", subjectCode);
        m.put("subjectName", subjectName(subjectCode));
        m.put("digest", digest);
        m.put("debit", debit);
        m.put("credit", credit);
        m.put("auxType", auxType);
        m.put("auxName", auxName);
        return m;
    }

    private Map<String, Object> preview(LocalDate date, String source, String refDocNo, String digest, String remark, List<Map<String, Object>> entries) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("voucherDate", date);
        p.put("source", source);
        p.put("refDocNo", refDocNo);
        p.put("digest", digest);
        p.put("remark", remark);
        p.put("entries", entries);
        return p;
    }

    // ===== 结转损益 =====

    /**
     * 结转损益：按「年初至该期 PL 科目当前余额」生成结转凭证（DRAFT，人工审核后记账）。
     * 按余额而非当期发生额结转——天然幂等：之前月已结转的部分余额已为 0，不会重复结转。
     */
    // v6.1.5：选号/删草稿/余额统计/生成整体 executeTx 锁内包事务（原 @Transactional+选号在锁外，TOCTOU）
    public Map<String, Object> transferProfit(String period, String operator) {
        checkPeriod(period);
        checkPeriodOpen(period);
        String yearStart = period.substring(0, 4) + "-01";
        return writeQueue.executeTx(() -> {
        return transferProfitLocked(period, operator, yearStart);
        });
    }

    private Map<String, Object> transferProfitLocked(String period, String operator, String yearStart) {
        // v5.70.2 修复结账死锁：结转凭证支持重算——
        // 1) 该期存在未记账(DRAFT)的结转凭证 → 删除后按最新余额重新生成；
        // 2) 结转凭证已记账、其后又有新的损益凭证记账 → 原防重会永久拦住重新结转，
        //    改为生成"补结转"凭证（refDocNo 带 #N 序号）。余额 SQL 统计 POSTED 净额
        //    （已含旧结转转平部分），剩余余额即需补转部分，数学口径成立。
        String refDoc = period;
        for (int i = 2; i < 100; i++) {
            Optional<Voucher> exist = repo.findBySourceAndRefDocNo("TRANSFER", refDoc);
            if (exist.isEmpty()) break;
            Voucher old = exist.get();
            if ("DRAFT".equals(old.status)) {
                entryRepo.deleteByVoucherId(old.id);
                repo.deleteById(old.id);
                repo.flush();
                break;   // 删除旧草稿后沿用原 refDocNo 重新生成
            }
            refDoc = period + "#" + i;   // 已记账 → 生成补结转凭证
        }

        // PL 科目年初至该期 POSTED 发生额（含 TRANSFER 结转凭证——正是它把余额转平）
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ve.subject_code AS code, MAX(ve.subject_name) AS name, s.direction AS dir,
                       SUM(ve.debit) AS d, SUM(ve.credit) AS c
                FROM voucher_entry ve
                JOIN voucher v ON ve.voucher_id = v.id
                JOIN account_subject s ON s.code = ve.subject_code
                WHERE v.status = 'POSTED' AND v.period >= ? AND v.period <= ? AND s.category = 'PL'
                GROUP BY ve.subject_code
                """, yearStart, period);

        BigDecimal profit = BigDecimal.ZERO;
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String dir = String.valueOf(row.get("dir"));
            BigDecimal d = toBd(row.get("d")), c = toBd(row.get("c"));
            BigDecimal bal = "CR".equals(dir) ? c.subtract(d) : d.subtract(c);   // 余额：收入贷方净额 / 费用借方净额
            if (bal.compareTo(BigDecimal.ZERO) == 0) continue;
            String code = String.valueOf(row.get("code"));
            String name = String.valueOf(row.get("name"));
            if ("CR".equals(dir)) {
                // 收入类余额在贷方 → 结转分录借方冲平
                entries.add(entry(code, "结转 " + name, bal, null, null, null));
                profit = profit.add(bal);
            } else {
                // 费用类余额在借方 → 结转分录贷方冲平
                entries.add(entry(code, "结转 " + name, null, bal, null, null));
                profit = profit.subtract(bal);
            }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException(period + " 无需结转损益（损益类科目余额已为零）");
        if (profit.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry("3104", "结转本年利润", null, profit, null, null));
        } else if (profit.compareTo(BigDecimal.ZERO) < 0) {
            entries.add(entry("3104", "结转本年亏损", profit.abs(), null, null, null));
        }

        LocalDate lastDay = LocalDate.of(Integer.parseInt(period.substring(0, 4)),
                Integer.parseInt(period.substring(5, 7)), 1).plusMonths(1).minusDays(1);
        Voucher v = new Voucher();
        v.voucherDate = lastDay;
        v.source = "TRANSFER";
        v.refDocNo = refDoc;
        v.remark = period + " 期末结转损益";
        v.createdBy = operator;
        v.entries = entries.stream().map(this::toEntry).toList();
        Voucher saved = create(v);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("voucher", saved);
        result.put("profit", profit);
        return result;
    }

    // ===== 期间结账 =====

    /** 期间列表：有凭证或已结账的期间汇总（凭证数/草稿数/结账状态） */
    public List<Map<String, Object>> periodStatus() {
        List<Map<String, Object>> periods = jdbc.queryForList("""
                SELECT period, COUNT(*) AS total,
                       SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END) AS draft
                FROM voucher GROUP BY period ORDER BY period DESC
                """);
        Map<String, AccountPeriod> closed = new HashMap<>();
        for (AccountPeriod p : periodRepo.findAll()) closed.put(p.period, p);
        List<Map<String, Object>> result = new ArrayList<>();
        // 无凭证但已结账的期间也要展示
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : periods) {
            String period = String.valueOf(row.get("period"));
            seen.add(period);
            result.add(buildPeriodRow(period, row.get("total"), row.get("draft"), closed.get(period)));
        }
        for (AccountPeriod p : periodRepo.findAllByOrderByPeriodDesc()) {
            if (!seen.contains(p.period) && Boolean.TRUE.equals(p.closed)) {
                result.add(buildPeriodRow(p.period, 0, 0, p));
            }
        }
        result.sort((a, b) -> String.valueOf(b.get("period")).compareTo(String.valueOf(a.get("period"))));
        return result;
    }

    private Map<String, Object> buildPeriodRow(String period, Object total, Object draft, AccountPeriod p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", period);
        row.put("total", total == null ? 0 : total);
        row.put("draft", draft == null ? 0 : draft);
        row.put("posted", (total == null ? 0 : ((Number) total).intValue()) - (draft == null ? 0 : ((Number) draft).intValue()));
        row.put("closed", p != null && Boolean.TRUE.equals(p.closed));
        row.put("closedBy", p == null ? null : p.closedBy);
        row.put("closeTime", p == null ? null : p.closeTime);
        // 期初建账以来未结转损益提示（前端展示用）
        row.put("plBalance", plBalance(period));
        return row;
    }

    /** 年初至该期 PL 科目净余额（≠0 表示有未结转损益） */
    private BigDecimal plBalance(String period) {
        String yearStart = period.substring(0, 4) + "-01";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.direction AS dir, SUM(ve.debit) AS d, SUM(ve.credit) AS c
                FROM voucher_entry ve
                JOIN voucher v ON ve.voucher_id = v.id
                JOIN account_subject s ON s.code = ve.subject_code
                WHERE v.status = 'POSTED' AND v.period >= ? AND v.period <= ? AND s.category = 'PL'
                GROUP BY s.direction
                """, yearStart, period);
        BigDecimal balance = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal d = toBd(row.get("d")), c = toBd(row.get("c"));
            balance = "CR".equals(String.valueOf(row.get("dir"))) ? balance.add(c.subtract(d)) : balance.subtract(d.subtract(c));
        }
        return balance;
    }

    /** 结账：该期无草稿、损益已结转，锁定期间 */
    @Transactional
    public void closePeriod(String period, String operator) {
        checkPeriod(period);
        if (periodRepo.findByPeriod(period).filter(p -> Boolean.TRUE.equals(p.closed)).isPresent()) {
            throw new IllegalArgumentException(period + " 已结账");
        }
        if (repo.existsByPeriodAndStatus(period, "DRAFT")) {
            throw new IllegalArgumentException(period + " 存在未记账凭证，请先全部记账");
        }
        BigDecimal pl = plBalance(period);
        if (pl.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(period + " 损益类科目尚有余额 " + pl + "，请先生成结转凭证并记账");
        }
        // v5.63：全月平均模式下，该期存货成本未计算则拦截结账（保证出库成本已回填均价）
        if ("MONTHLY_AVG".equals(costingService.method()) && !costingService.monthlyDone(period)) {
            throw new IllegalArgumentException("当前计价方式为全月平均，" + period + " 尚未进行存货成本计算，请先在期末结账页执行「存货成本计算」");
        }
        writeQueue.execute(() -> {
            AccountPeriod p = periodRepo.findByPeriod(period).orElseGet(AccountPeriod::new);
            p.period = period;
            p.closed = true;
            p.closedBy = operator;
            p.closeTime = LocalDateTime.now();
            periodRepo.save(p);
        });
    }

    /**
     * v7.0 年结检查+执行（12 月月结时的年度收尾）：
     * 前置：① 12 月损益已全部结转（PL 余额零）② 1-11 月全部已结账 ③ 12 月无草稿凭证。
     * 执行：① 结平 3104 本年利润 → 3105 利润分配（未分配利润）转账凭证（有余额时）
     *      ② 12 月月结（复用 closePeriod）。
     * 次年开账无需任何操作——期初即各科目当前余额（continuity 口径），报表按年区间自然切分。
     */
    // v8.0（P0-1）：去外层 @Transactional（内部 create/post 各自锁内包事务）；年结凭证改走 create() 统一取号+校验
    public Map<String, Object> yearEndClose(String year, String operator) {
        if (year == null || !year.matches("\\d{4}")) throw new IllegalArgumentException("年份格式应为 YYYY");
        String dec = year + "-12";
        // ① 1-11 月全部结账
        for (int m = 1; m <= 11; m++) {
            String pm = String.format("%s-%02d", year, m);
            if (periodRepo.findAll().stream().noneMatch(x -> x.period.equals(pm) && Boolean.TRUE.equals(x.closed))) {
                // 无凭证的空月不算阻塞（没做账的月份跳过），有凭证未结才拦
                if (repo.existsByPeriodAndStatus(pm, "DRAFT") || repo.existsByPeriodAndStatus(pm, "POSTED")) {
                    throw new IllegalArgumentException(pm + " 存在凭证但未结账，请先完成 1-12 月全部月结");
                }
            }
        }
        // ② 12 月损益已结转
        if (plBalance(dec).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(dec + " 损益类科目尚有余额，请先生成结转凭证并记账");
        }
        // ③ 结平本年利润 → 未分配利润
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        var acc3104 = subjectRepo.findByCode("3104").orElse(null);
        if (acc3104 == null) throw new IllegalArgumentException("科目 3104 本年利润不存在");
        BigDecimal bal = signedBalanceOf("3104", year);
        Voucher transfer = null;
        if (bal.compareTo(BigDecimal.ZERO) != 0) {
            // v8.0（P0-1）：改走 create()+post()——旧实现手工 save 缺 docNo/period（两列 NOT NULL UNIQUE，
            // INSERT 必炸使年结不可用），且绕过借贷平衡/科目校验、无 createdBy；create() 的来源查重
            // （YEAR_END+年份）顺带获得年结幂等
            Voucher v = new Voucher();
            v.voucherDate = LocalDate.of(Integer.parseInt(year), 12, 31);
            v.source = "YEAR_END";
            v.refDocNo = year;
            v.remark = year + " 年结：本年利润结转未分配利润";
            v.createdBy = operator;
            List<VoucherEntry> entries = new java.util.ArrayList<>();
            VoucherEntry e1 = new VoucherEntry();
            e1.subjectCode = bal.compareTo(BigDecimal.ZERO) > 0 ? "3104" : "3105";
            e1.subjectName = subjectName(e1.subjectCode);
            e1.digest = "年结结转";
            if (bal.compareTo(BigDecimal.ZERO) > 0) { e1.debit = bal; e1.credit = BigDecimal.ZERO; }
            else { e1.debit = BigDecimal.ZERO; e1.credit = bal.negate(); }
            entries.add(e1);
            VoucherEntry e2 = new VoucherEntry();
            e2.subjectCode = bal.compareTo(BigDecimal.ZERO) > 0 ? "3105" : "3104";
            e2.subjectName = subjectName(e2.subjectCode);
            e2.digest = "年结结转";
            if (bal.compareTo(BigDecimal.ZERO) > 0) { e2.debit = BigDecimal.ZERO; e2.credit = bal; }
            else { e2.debit = bal.negate(); e2.credit = BigDecimal.ZERO; }
            entries.add(e2);
            v.entries = entries;
            Voucher saved = create(v);            // 统一取号（VCH-YYYYMM-NNNN）+ normalizeAndValidate + 来源幂等
            transfer = post(saved.id, operator);  // 记账（年结凭证无需人工审核——前置校验已保证合法性）
            result.put("transferVoucher", transfer.docNo);
            result.put("profitTransferred", bal);
        }
        // ④ 12 月月结
        closePeriod(dec, operator);
        result.put("year", year);
        result.put("decPeriod", dec);
        result.put("status", "已年结");
        log.info("年结完成: {} 转账凭证={} 本年利润结转={}", year, transfer != null ? transfer.docNo : "无", bal);
        return result;
    }

    /** 3104 科目年初至今带方向余额 */
    private BigDecimal signedBalanceOf(String code, String year) {
        var rows = jdbc.queryForList("""
                SELECT SUM(ve.debit) AS d, SUM(ve.credit) AS c
                FROM voucher_entry ve JOIN voucher v ON ve.voucher_id = v.id
                WHERE v.status = 'POSTED' AND ve.subject_code = ? AND v.period >= ? AND v.period <= ?
                """, code, year + "-01", year + "-12");
        BigDecimal d = rows.isEmpty() || rows.get(0).get("d") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(rows.get(0).get("d")));
        BigDecimal c = rows.isEmpty() || rows.get(0).get("c") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(rows.get(0).get("c")));
        return d.subtract(c);
    }

    /** 反结账：仅允许从最近已结期间逐月往前 */
    @Transactional
    public void reopenPeriod(String period) {
        checkPeriod(period);
        AccountPeriod p = periodRepo.findByPeriod(period)
                .filter(x -> Boolean.TRUE.equals(x.closed))
                .orElseThrow(() -> new IllegalArgumentException(period + " 未结账"));
        boolean hasLater = periodRepo.findAll().stream()
                .anyMatch(x -> Boolean.TRUE.equals(x.closed) && x.period.compareTo(period) > 0);
        if (hasLater) throw new IllegalArgumentException("存在更晚的已结账期间，请从最近期间逐月反结账");
        writeQueue.execute(() -> periodRepo.delete(p));
    }

    // ===== 内部方法 =====

    private void normalizeAndValidate(Voucher v) {
        if (v.voucherDate == null) throw new IllegalArgumentException("凭证日期不能为空");
        // v5.70 P1 防呆：凭证日期不允许选未来（仅手工凭证；系统凭证如工资计提/折旧/结转按月末出票属行业惯例，不拦）
        boolean manual = v.source == null || "MANUAL".equals(v.source);
        if (manual && v.voucherDate.isAfter(java.time.LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("凭证日期 " + v.voucherDate + " 不能是未来日期");
        }
        v.period = periodOf(v.voucherDate);
        if (v.entries == null || v.entries.size() < 2) throw new IllegalArgumentException("凭证至少需要两行分录");
        Map<String, AccountSubject> subjects = new HashMap<>();
        for (AccountSubject s : subjectRepo.findAll()) subjects.put(s.code, s);

        BigDecimal debitSum = BigDecimal.ZERO, creditSum = BigDecimal.ZERO;
        int line = 0;
        for (VoucherEntry e : v.entries) {
            line++;
            if (e.subjectCode == null || e.subjectCode.isBlank()) throw new IllegalArgumentException("第 " + line + " 行未选择科目");
            AccountSubject s = subjects.get(e.subjectCode);
            if (s == null) throw new IllegalArgumentException("科目 " + e.subjectCode + " 不存在");
            if (!"ENABLED".equals(s.status)) throw new IllegalArgumentException("科目 " + s.code + " " + s.name + " 已停用");
            e.subjectName = s.name;   // 快照
            e.debit = nvl(e.debit);
            e.credit = nvl(e.credit);
            if (e.debit.compareTo(BigDecimal.ZERO) != 0 && e.credit.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("第 " + line + " 行借方与贷方不能同时有金额");
            }
            // v6.1.4：手工分录禁负数（负借负贷可配平绕过平衡校验；红字冲销用反方向正常金额表达）
            if (e.debit.compareTo(BigDecimal.ZERO) < 0 || e.credit.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("第 " + line + " 行金额不能为负数（红字请以反方向金额录入）");
            }
            if (e.debit.compareTo(BigDecimal.ZERO) == 0 && e.credit.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("第 " + line + " 行金额不能为零");
            }
            debitSum = debitSum.add(e.debit);
            creditSum = creditSum.add(e.credit);
        }
        if (debitSum.compareTo(creditSum) != 0) {
            throw new IllegalArgumentException("借贷不平衡：借方合计 " + debitSum + " ≠ 贷方合计 " + creditSum);
        }
        if (debitSum.abs().compareTo(BigDecimal.ZERO) == 0) throw new IllegalArgumentException("凭证合计金额不能为零");
        v.totalDebit = debitSum;
        v.totalCredit = creditSum;
    }

    private void saveEntries(Voucher v) {
        int line = 0;
        for (VoucherEntry e : v.entries) {
            e.id = null;
            e.voucherId = v.id;
            e.lineNo = ++line;
            e.debit = nvl(e.debit);
            e.credit = nvl(e.credit);
            entryRepo.save(e);
        }
    }

    private Voucher assemble(Voucher v) {
        v.entries = entryRepo.findByVoucherIdOrderByLineNoAsc(v.id);
        return v;
    }

    private VoucherEntry toEntry(Map<String, Object> m) {
        VoucherEntry e = new VoucherEntry();
        e.subjectCode = String.valueOf(m.get("subjectCode"));
        e.subjectName = String.valueOf(m.get("subjectName"));
        e.digest = (String) m.get("digest");
        e.debit = m.get("debit") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(m.get("debit")));
        e.credit = m.get("credit") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(m.get("credit")));
        e.auxType = (String) m.get("auxType");
        e.auxName = (String) m.get("auxName");
        return e;
    }

    private String subjectName(String code) {
        if (code == null) return null;
        return subjectRepo.findByCode(code).map(s -> s.name).orElse(code);
    }

    private void checkPeriodOpen(String period) {
        if (periodRepo.findByPeriod(period).filter(p -> Boolean.TRUE.equals(p.closed)).isPresent()) {
            throw new IllegalArgumentException(period + " 已结账，该期间禁止凭证操作（如需调整请先反结账）");
        }
    }

    private void checkPeriod(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException("期间格式应为 YYYY-MM");
    }

    private String periodOf(LocalDate date) {
        return date.toString().substring(0, 7);
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** sqlite-jdbc 聚合返回 Integer/Long/Double，统一转 BigDecimal（踩坑记录） */
    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(String.valueOf(v));
    }
}
