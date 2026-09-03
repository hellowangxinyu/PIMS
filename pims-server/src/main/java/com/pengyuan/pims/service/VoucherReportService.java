package com.pengyuan.pims.service;

import com.pengyuan.pims.repository.AccountMappingRepository;
import com.pengyuan.pims.repository.AccountSubjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 总账报表（v5.61）：科目余额表 / 明细账 / 资产负债表 / 利润表 / 现金流量表
 *
 * 统一口径：
 *  - 一切账簿余额 = 科目期初建账数 + 年初至该期 POSTED 凭证净发生额（含 TRANSFER 结转凭证——正是它把损益转平进 3104）
 *  - 利润表取数排除 TRANSFER 凭证（结转分录会把损益发生额对冲为零，排除后才能反映真实损益）
 *  - 资产负债表「未分配利润」= 3104+3105 余额 + 未结转损益（PL 科目余额）——结转前后恒等
 *  - 现金流量表简化直接法：现金类科目（1001/1002/1012）分录按同凭证对方科目比例分摊，经 cf: 映射归集
 */
@Service
public class VoucherReportService {

    /** 现金类科目（现金流量表锚点） */
    private static final Set<String> CASH_SUBJECTS = Set.of("1001", "1002", "1012");

    private final JdbcTemplate jdbc;
    private final AccountSubjectRepository subjectRepo;
    private final AccountMappingRepository mappingRepo;

    public VoucherReportService(JdbcTemplate jdbc, AccountSubjectRepository subjectRepo,
                                AccountMappingRepository mappingRepo) {
        this.jdbc = jdbc;
        this.subjectRepo = subjectRepo;
        this.mappingRepo = mappingRepo;
    }

    // ===== 科目余额表 =====

    /**
     * @param period 期间 YYYY-MM（含当期）
     * @param level TOP=仅一级科目 / ALL=一级+明细
     * 返回行：{code, name, category, direction, top(是否一级), beginDr/beginCr(期初借/贷), debit, credit, endDr/endCr(期末)}
     */
    public List<Map<String, Object>> accountBalance(String period, String level) {
        checkPeriod(period);
        String yearStart = period.substring(0, 4) + "-01";

        // 年初至当期累计发生额 / 当期发生额
        Map<String, BigDecimal[]> cum = occurrence(yearStart, period);
        Map<String, BigDecimal[]> cur = occurrence(period, period);

        List<Map<String, Object>> result = new ArrayList<>();
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            BigDecimal[] c = cum.getOrDefault(s.code, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] u = cur.getOrDefault(s.code, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            boolean top = s.parentCode == null || s.parentCode.isBlank();

            // 带方向余额（正=direction 方向，负=反方向）
            BigDecimal opening = signed(s.openingBalance, s.openingDirection != null ? s.openingDirection : s.direction);
            BigDecimal end = opening.add(net(c));
            BigDecimal begin = end.subtract(net(u));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", s.code);
            row.put("name", s.name);
            row.put("category", s.category);
            row.put("direction", s.direction);
            row.put("top", top);
            putBalances(row, "begin", begin);
            row.put("debit", u[0]);
            row.put("credit", u[1]);
            putBalances(row, "end", end);
            result.add(row);
        }
        // 一级行汇总 = 自身 + 子科目（发生与余额相加，余额按符号累加后仍带方向）
        // v6.1：TOP 视图同样先上卷再过滤输出——此前 TOP 直接跳过子科目，一级行缺明细科目发生额，
        // 与 ALL 视图同科目对不上（凭证记在明细科目时 TOP 行发生额为 0）
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        for (Map<String, Object> r : result) byCode.put((String) r.get("code"), r);
        for (Map<String, Object> r : result) {
            if (Boolean.TRUE.equals(r.get("top"))) continue;
            Map<String, Object> parent = byCode.get(parentOf((String) r.get("code")));
            if (parent == null) continue;
            for (String k : List.of("debit", "credit")) parent.put(k, toBd(parent.get(k)).add(toBd(r.get(k))));
            addSigned(parent, "beginDr", "beginCr", r);
            addSigned(parent, "endDr", "endCr", r);
        }
        if ("TOP".equals(level)) {
            return result.stream().filter(r -> Boolean.TRUE.equals(r.get("top"))).toList();
        }
        return result;
    }

    // ===== 明细账 =====

    /**
     * @param subjectCode 科目编码（一级或明细均可）
     * @param from/to 期间范围 YYYY-MM
     * @param auxName 辅助核算名称筛选（可空）
     */
    public Map<String, Object> accountDetail(String subjectCode, String from, String to, String auxName) {
        checkPeriod(from);
        checkPeriod(to);
        if (to.compareTo(from) < 0) throw new IllegalArgumentException("截止期间不能早于起始期间");
        var sOpt = subjectRepo.findAllByOrderByCodeAsc().stream()
                .filter(x -> x.code.equals(subjectCode)).findFirst();
        if (sOpt.isEmpty()) throw new IllegalArgumentException("科目 " + subjectCode + " 不存在");
        var s = sOpt.get();
        String yearStart = from.substring(0, 4) + "-01";
        String prevEnd = prevPeriod(from);

        // 期初 = opening + 年初至起始期前一月累计
        Map<String, BigDecimal[]> before = prevEnd.compareTo(yearStart) < 0
                ? Map.of() : occurrence(yearStart, prevEnd);
        BigDecimal[] b = before.getOrDefault(subjectCode, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        String dir = s.openingDirection != null ? s.openingDirection : s.direction;
        BigDecimal begin = signed(s.openingBalance, dir).add(net(b));

        // 期间流水
        StringBuilder sql = new StringBuilder("""
                SELECT v.id, v.doc_no, v.voucher_date, ve.digest, ve.debit, ve.credit, ve.aux_name
                FROM voucher_entry ve JOIN voucher v ON ve.voucher_id = v.id
                WHERE v.status = 'POSTED' AND v.period >= ? AND v.period <= ? AND ve.subject_code = ?
                """);
        List<Object> args = new ArrayList<>(List.of(from, to, subjectCode));
        if (auxName != null && !auxName.isBlank()) {
            sql.append(" AND ve.aux_name LIKE ?");
            args.add("%" + auxName + "%");
        }
        sql.append(" ORDER BY v.voucher_date, v.id, ve.line_no");
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal running = begin;
        BigDecimal sumDebit = BigDecimal.ZERO, sumCredit = BigDecimal.ZERO;
        for (Map<String, Object> row : jdbc.queryForList(sql.toString(), args.toArray())) {
            BigDecimal d = toBd(row.get("debit")), c = toBd(row.get("credit"));
            running = running.add(d).subtract(c);
            sumDebit = sumDebit.add(d);
            sumCredit = sumCredit.add(c);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("voucherId", row.get("id"));
            line.put("docNo", row.get("doc_no"));
            line.put("voucherDate", row.get("voucher_date"));
            line.put("digest", row.get("digest"));
            line.put("debit", d);
            line.put("credit", c);
            line.put("auxName", row.get("aux_name"));
            putBalances(line, "balance", running);
            lines.add(line);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", Map.of("code", s.code, "name", s.name, "direction", s.direction, "category", s.category));
        result.put("from", from);
        result.put("to", to);
        result.put("beginDr", begin.compareTo(BigDecimal.ZERO) > 0 ? begin : BigDecimal.ZERO);
        result.put("beginCr", begin.compareTo(BigDecimal.ZERO) < 0 ? begin.abs() : BigDecimal.ZERO);
        result.put("lines", lines);
        result.put("sumDebit", sumDebit);
        result.put("sumCredit", sumCredit);
        BigDecimal end = begin.add(sumDebit).subtract(sumCredit);
        result.put("endDr", end.compareTo(BigDecimal.ZERO) > 0 ? end : BigDecimal.ZERO);
        result.put("endCr", end.compareTo(BigDecimal.ZERO) < 0 ? end.abs() : BigDecimal.ZERO);
        return result;
    }

    // ===== 资产负债表 =====

    public Map<String, Object> balanceSheet(String period) {
        checkPeriod(period);
        String yearStart = period.substring(0, 4) + "-01";

        // 期末余额（带符号：DR 正、CR 负→按科目余额语义：正=direction 方向）
        Map<String, BigDecimal> endBal = balancesWithOpening(yearStart, period);
        // 年初余额 = 期初建账数（PL 年初必为 0）
        Map<String, BigDecimal> beginBal = new HashMap<>();
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            beginBal.put(s.code, signed(s.openingBalance, s.openingDirection != null ? s.openingDirection : s.direction));
        }
        // 归集到一级（明细并入父级）
        Map<String, BigDecimal> endTop = rollup(endBal);
        Map<String, BigDecimal> beginTop = rollup(beginBal);

        // 未结转损益（PL 余额，贷方净额为利润）
        BigDecimal plEnd = BigDecimal.ZERO;
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            if ("PL".equals(s.category)) plEnd = plEnd.add(endBal.getOrDefault(s.code, BigDecimal.ZERO));
        }

        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liabilities = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        Set<String> used = new HashSet<>();

        bsItem(assets, "货币资金", beginTop, endTop, used, false, "1001", "1002", "1012");
        bsItem(assets, "应收票据", beginTop, endTop, used, false, "1121");
        bsItem(assets, "应收账款", beginTop, endTop, used, false, "1122");
        bsItem(assets, "预付款项", beginTop, endTop, used, false, "1123");
        bsItem(assets, "其他应收款", beginTop, endTop, used, false, "1131");
        bsItem(assets, "存货", beginTop, endTop, used, false, "1403", "1405", "1408", "1411", "5001", "5101");
        bsItem(assets, "固定资产", beginTop, endTop, used, false, "1501", "1602");
        bsItem(assets, "无形资产", beginTop, endTop, used, false, "1701");
        bsItem(assets, "长期待摊费用", beginTop, endTop, used, false, "1801");
        Map<String, BigDecimal> otherAsset = residual("ASSET", used, beginTop, endTop, false);
        if (otherAsset.get("end").compareTo(BigDecimal.ZERO) != 0 || otherAsset.get("begin").compareTo(BigDecimal.ZERO) != 0) {
            assets.add(bsRow("其他资产", otherAsset.get("begin"), otherAsset.get("end")));
        }
        bsItem(liabilities, "短期借款", beginTop, endTop, used, true, "2001");
        bsItem(liabilities, "应付票据", beginTop, endTop, used, true, "2201");
        bsItem(liabilities, "应付账款", beginTop, endTop, used, true, "2202");
        bsItem(liabilities, "预收款项", beginTop, endTop, used, true, "2203");
        bsItem(liabilities, "应付职工薪酬", beginTop, endTop, used, true, "2211");
        bsItem(liabilities, "应交税费", beginTop, endTop, used, true, "2221");
        bsItem(liabilities, "其他应付款", beginTop, endTop, used, true, "2231", "2232");
        bsItem(liabilities, "长期借款", beginTop, endTop, used, true, "2501");
        Map<String, BigDecimal> otherLiab = residual("LIABILITY", used, beginTop, endTop, true);
        if (otherLiab.get("end").compareTo(BigDecimal.ZERO) != 0 || otherLiab.get("begin").compareTo(BigDecimal.ZERO) != 0) {
            liabilities.add(bsRow("其他负债", otherLiab.get("begin"), otherLiab.get("end")));
        }
        bsItem(equity, "实收资本", beginTop, endTop, used, true, "3001");
        bsItem(equity, "资本公积", beginTop, endTop, used, true, "3101");
        used.add("3104"); used.add("3105");
        // 未分配利润 = 3104+3105+未结转损益（PL 余额，正为利润）+其他自建权益科目；负债/权益侧余额取反（内部 DR正/CR负 → 报表正值）
        Map<String, BigDecimal> otherEq = residual("EQUITY", used, beginTop, endTop, true);
        BigDecimal retainedBegin = beginTop.getOrDefault("3104", BigDecimal.ZERO).add(beginTop.getOrDefault("3105", BigDecimal.ZERO)).negate()
                .add(otherEq.get("begin"));
        BigDecimal retainedEnd = endTop.getOrDefault("3104", BigDecimal.ZERO).add(endTop.getOrDefault("3105", BigDecimal.ZERO)).add(plEnd).negate()
                .add(otherEq.get("end"));
        if (retainedBegin.compareTo(BigDecimal.ZERO) != 0 || retainedEnd.compareTo(BigDecimal.ZERO) != 0) {
            Map<String, Object> retained = new LinkedHashMap<>();
            retained.put("item", "未分配利润"); retained.put("yearBegin", retainedBegin); retained.put("periodEnd", retainedEnd);
            equity.add(retained);
        }

        BigDecimal assetTotal = assets.stream().map(r -> toBd(r.get("periodEnd"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal assetBeginTotal = assets.stream().map(r -> toBd(r.get("yearBegin"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabTotal = liabilities.stream().map(r -> toBd(r.get("periodEnd"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabBeginTotal = liabilities.stream().map(r -> toBd(r.get("yearBegin"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal eqTotal = equity.stream().map(r -> toBd(r.get("periodEnd"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal eqBeginTotal = equity.stream().map(r -> toBd(r.get("yearBegin"))).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("assets", assets);
        result.put("liabilities", liabilities);
        result.put("equity", equity);
        result.put("assetTotal", assetTotal); result.put("assetBeginTotal", assetBeginTotal);
        result.put("liabTotal", liabTotal); result.put("liabBeginTotal", liabBeginTotal);
        result.put("eqTotal", eqTotal); result.put("eqBeginTotal", eqBeginTotal);
        result.put("diff", assetTotal.subtract(liabTotal).subtract(eqTotal));   // 应为 0
        return result;
    }

    /**
     * 归集科目余额到报表项目。
     * flip=false 资产侧（DR 正=正值）；flip=true 负债/权益侧（内部 CR 为负 → 取反为正）。
     */
    private void bsItem(List<Map<String, Object>> list, String item, Map<String, BigDecimal> begin, Map<String, BigDecimal> end,
                        Set<String> used, boolean flip, String... codes) {
        BigDecimal b = BigDecimal.ZERO, e = BigDecimal.ZERO;
        for (String code : codes) {
            b = b.add(begin.getOrDefault(code, BigDecimal.ZERO));
            e = e.add(end.getOrDefault(code, BigDecimal.ZERO));
            used.add(code);
        }
        if (flip) { b = b.negate(); e = e.negate(); }
        if (b.compareTo(BigDecimal.ZERO) == 0 && e.compareTo(BigDecimal.ZERO) == 0) return;   // 两栏全零不显示
        list.add(bsRow(item, b, e));
    }

    private Map<String, Object> bsRow(String item, BigDecimal begin, BigDecimal end) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("item", item);
        r.put("yearBegin", begin);
        r.put("periodEnd", end);
        return r;
    }

    /** 归集后剩余的科目余额兜底（其他资产/其他负债） */
    private Map<String, BigDecimal> residual(String category, Set<String> used, Map<String, BigDecimal> beginTop, Map<String, BigDecimal> endTop, boolean flip) {
        BigDecimal b = BigDecimal.ZERO, e = BigDecimal.ZERO;
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            if (!category.equals(s.category) || used.contains(s.code) || s.parentCode != null && !s.parentCode.isBlank()) continue;
            b = b.add(beginTop.getOrDefault(s.code, BigDecimal.ZERO));
            e = e.add(endTop.getOrDefault(s.code, BigDecimal.ZERO));
        }
        if (flip) { b = b.negate(); e = e.negate(); }
        return Map.of("begin", b, "end", e);
    }

    // ===== 利润表 =====

    public Map<String, Object> incomeStatement(String period) {
        checkPeriod(period);
        String yearStart = period.substring(0, 4) + "-01";

        // 排除 TRANSFER 结转凭证（其发生额会把损益对冲为零）
        Map<String, BigDecimal[]> month = plOccurrence(period, period);
        Map<String, BigDecimal[]> year = plOccurrence(yearStart, period);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal[] rev = plItem(rows, "一、营业收入", month, year, true, "6001", "6051");
        BigDecimal[] cogs = plItem(rows, "减：营业成本", month, year, false, "6401", "6402");
        BigDecimal[] tax = plItem(rows, "减：税金及附加", month, year, false, "6403");
        BigDecimal[] sell = plItem(rows, "减：销售费用", month, year, false, "6601");
        BigDecimal[] admin = plItem(rows, "减：管理费用", month, year, false, "6602");
        BigDecimal[] fin = plItem(rows, "减：财务费用", month, year, false, "6603");
        BigDecimal opM = rev[0].subtract(cogs[0]).subtract(tax[0]).subtract(sell[0]).subtract(admin[0]).subtract(fin[0]);
        BigDecimal opY = rev[1].subtract(cogs[1]).subtract(tax[1]).subtract(sell[1]).subtract(admin[1]).subtract(fin[1]);
        rows.add(row("二、营业利润", opM, opY));
        BigDecimal[] nonopIn = plItem(rows, "加：营业外收入", month, year, true, "6301");
        BigDecimal[] nonopOut = plItem(rows, "减：营业外支出", month, year, false, "6711");
        BigDecimal totalM = opM.add(nonopIn[0]).subtract(nonopOut[0]);
        BigDecimal totalY = opY.add(nonopIn[1]).subtract(nonopOut[1]);
        rows.add(row("三、利润总额", totalM, totalY));
        BigDecimal[] incomeTax = plItem(rows, "减：所得税费用", month, year, false, "6801");
        rows.add(row("四、净利润", totalM.subtract(incomeTax[0]), totalY.subtract(incomeTax[1])));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("rows", rows);
        return result;
    }

    /** 损益项目取数：返回 {本月净额, 本年累计净额} */
    private BigDecimal[] plItem(List<Map<String, Object>> rows, String item, Map<String, BigDecimal[]> month, Map<String, BigDecimal[]> year,
                                boolean creditSide, String... codes) {
        BigDecimal m = BigDecimal.ZERO, y = BigDecimal.ZERO;
        for (String code : codes) {
            for (String c : topAndChildren(code)) {
                BigDecimal[] mm = month.getOrDefault(c, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] yy = year.getOrDefault(c, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                m = m.add(creditSide ? netCredit(mm) : netDebit(mm));
                y = y.add(creditSide ? netCredit(yy) : netDebit(yy));
            }
        }
        rows.add(row(item, m, y));
        return new BigDecimal[]{m, y};
    }

    private Map<String, Object> row(String item, BigDecimal month, BigDecimal yearCum) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("item", item);
        r.put("month", month);
        r.put("yearCum", yearCum);
        return r;
    }

    // ===== 现金流量表 =====

    public Map<String, Object> cashFlow(String period) {
        checkPeriod(period);
        String yearStart = period.substring(0, 4) + "-01";

        // 期间涉及现金科目的凭证（含分录），排除 TRANSFER（结转不涉现金，保险起见）
        List<Map<String, Object>> cashVouchers = jdbc.queryForList("""
                SELECT DISTINCT v.id FROM voucher v
                WHERE v.status = 'POSTED' AND v.period >= ? AND v.period <= ?
                  AND EXISTS (SELECT 1 FROM voucher_entry ve WHERE ve.voucher_id = v.id AND ve.subject_code IN ('1001','1002','1012'))
                """, yearStart, period);
        if (cashVouchers.isEmpty()) return emptyCashFlow(period);

        List<Long> ids = cashVouchers.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT voucher_id, subject_code, subject_name, debit, credit FROM voucher_entry WHERE voucher_id IN (" + placeholders + ") ORDER BY voucher_id, line_no",
                ids.toArray());

        Map<Long, List<Map<String, Object>>> byVoucher = new LinkedHashMap<>();
        for (Map<String, Object> e : entries) {
            byVoucher.computeIfAbsent(((Number) e.get("voucher_id")).longValue(), k -> new ArrayList<>()).add(e);
        }

        Map<String, Map<String, BigDecimal>> agg = new LinkedHashMap<>();   // cfItem -> {month, yearCum}
        for (var row : jdbc.queryForList("SELECT period, id FROM voucher WHERE id IN (" + placeholders + ")", ids.toArray())) {
            long vid = ((Number) row.get("id")).longValue();
            boolean inMonth = period.equals(String.valueOf(row.get("period")));
            List<Map<String, Object>> ves = byVoucher.get(vid);
            List<Map<String, Object>> nonCash = ves.stream()
                    .filter(e -> !CASH_SUBJECTS.contains(String.valueOf(e.get("subject_code")))).toList();
            if (nonCash.isEmpty()) continue;   // 现金科目内部转账（提现/互转）不产生现金流量

            for (Map<String, Object> cash : ves) {
                if (!CASH_SUBJECTS.contains(String.valueOf(cash.get("subject_code")))) continue;   // 只处理现金类分录
                boolean inflow = toBd(cash.get("debit")).compareTo(BigDecimal.ZERO) != 0;
                BigDecimal amount = inflow ? toBd(cash.get("debit")) : toBd(cash.get("credit"));
                if (amount.compareTo(BigDecimal.ZERO) == 0) continue;
                BigDecimal nonCashAbs = nonCash.stream().map(e -> toBd(e.get("debit")).add(toBd(e.get("credit"))).abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (nonCashAbs.compareTo(BigDecimal.ZERO) == 0) continue;
                // v6.1.6：尾差挂末行——逐行 HALF_UP 的累计与总额差 1~2 分，末行用「总额−前面行合计」收口
                BigDecimal allocated = BigDecimal.ZERO;
                java.util.List<Map<String, Object>> ncList = nonCash;
                for (int i = 0; i < ncList.size(); i++) {
                    Map<String, Object> nc = ncList.get(i);
                    String code = String.valueOf(nc.get("subject_code"));
                    BigDecimal share;
                    if (i == ncList.size() - 1) {
                        share = amount.abs().subtract(allocated);
                    } else {
                        share = toBd(nc.get("debit")).add(toBd(nc.get("credit"))).abs()
                                .multiply(amount.abs()).divide(nonCashAbs, 2, java.math.RoundingMode.HALF_UP);
                        allocated = allocated.add(share);
                    }
                    if (amount.compareTo(BigDecimal.ZERO) < 0) share = share.negate();   // 红字冲减
                    String cfItem = resolveCf(code, inflow);
                    Map<String, BigDecimal> cell = agg.computeIfAbsent(cfItem, k -> new HashMap<>());
                    cell.merge("yearCum", share, BigDecimal::add);
                    if (inMonth) cell.merge("month", share, BigDecimal::add);
                }
            }
        }
        return buildCashFlow(period, agg);
    }

    /** cf 映射：先精确 cf:in:{code}，再父级；兜底 CF03(流入其他)/CF07(流出其他) */
    private String resolveCf(String subjectCode, boolean inflow) {
        String prefix = inflow ? "cf:in:" : "cf:out:";
        Map<String, String> maps = new HashMap<>();
        for (var m : mappingRepo.findAllByOrderByMapKeyAsc()) maps.put(m.mapKey, m.subjectCode);
        String hit = maps.get(prefix + subjectCode);
        if (hit == null) {
            String parent = parentOf(subjectCode);
            if (parent != null) hit = maps.get(prefix + parent);
        }
        return hit != null ? hit : (inflow ? "CF03" : "CF07");
    }

    private Map<String, Object> emptyCashFlow(String period) { return buildCashFlow(period, Map.of()); }

    private Map<String, Object> buildCashFlow(String period, Map<String, Map<String, BigDecimal>> agg) {
        // {段, 编码, 项目, 是否流入}——流出项列示正数，净额 = 流入 − 流出
        Object[][] defs = {
            {"经营", "CF01", "销售商品、提供劳务收到的现金", true},
            {"经营", "CF02", "收到的税费返还", true},
            {"经营", "CF03", "收到其他与经营活动有关的现金", true},
            {"经营", "CF04", "购买商品、接受劳务支付的现金", false},
            {"经营", "CF05", "支付给职工以及为职工支付的现金", false},
            {"经营", "CF06", "支付的各项税费", false},
            {"经营", "CF07", "支付其他与经营活动有关的现金", false},
            {"投资", "CF10", "处置固定资产、无形资产和其他长期资产收回的现金净额", true},
            {"投资", "CF11", "购建固定资产、无形资产和其他长期资产支付的现金", false},
            {"投资", "CF12", "投资支付的现金", false},
            {"筹资", "CF13", "吸收投资收到的现金", true},
            {"筹资", "CF14", "取得借款收到的现金", true},
            {"筹资", "CF15", "偿还债务支付的现金", false},
            {"筹资", "CF16", "分配股利、利润或偿付利息支付的现金", false},
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> segMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> segYear = new LinkedHashMap<>();
        String lastSeg = null;
        for (Object[] d : defs) {
            if (lastSeg != null && !lastSeg.equals(d[0])) addSegSubtotal(rows, lastSeg, segMonth, segYear);
            lastSeg = (String) d[0];
            Map<String, BigDecimal> cell = agg.getOrDefault((String) d[1], Map.of());
            BigDecimal m = cell.getOrDefault("month", BigDecimal.ZERO);
            BigDecimal y = cell.getOrDefault("yearCum", BigDecimal.ZERO);
            if (m.compareTo(BigDecimal.ZERO) == 0 && y.compareTo(BigDecimal.ZERO) == 0) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("segment", d[0]); r.put("code", d[1]); r.put("item", d[2]);
            r.put("month", m); r.put("yearCum", y);
            rows.add(r);
            int sign = Boolean.TRUE.equals(d[3]) ? 1 : -1;
            segMonth.merge((String) d[0], m.multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
            segYear.merge((String) d[0], y.multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
        }
        if (lastSeg != null) addSegSubtotal(rows, lastSeg, segMonth, segYear);

        BigDecimal netM = segMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netY = segYear.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> net = new LinkedHashMap<>();
        net.put("segment", "净额"); net.put("code", "NET"); net.put("item", "现金及现金等价物净增加额");
        net.put("month", netM); net.put("yearCum", netY);
        rows.add(net);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("rows", rows);
        return result;
    }

    private void addSegSubtotal(List<Map<String, Object>> rows, String seg, Map<String, BigDecimal> segMonth, Map<String, BigDecimal> segYear) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("segment", seg); r.put("code", "SUB"); r.put("item", seg + "活动现金流量净额");
        r.put("month", segMonth.getOrDefault(seg, BigDecimal.ZERO));
        r.put("yearCum", segYear.getOrDefault(seg, BigDecimal.ZERO));
        rows.add(r);
    }

    // ===== 公共取数 =====

    /** 期间范围（含端点）各科目 POSTED 借贷发生额 {code -> [debitSum, creditSum]} */
    private Map<String, BigDecimal[]> occurrence(String from, String to) {
        Map<String, BigDecimal[]> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT ve.subject_code AS code, SUM(ve.debit) AS d, SUM(ve.credit) AS c
                FROM voucher_entry ve JOIN voucher v ON ve.voucher_id = v.id
                WHERE v.status = 'POSTED' AND v.period >= ? AND v.period <= ?
                GROUP BY ve.subject_code
                """, from, to)) {
            result.put(String.valueOf(row.get("code")), new BigDecimal[]{toBd(row.get("d")), toBd(row.get("c"))});
        }
        return result;
    }

    /** 损益类科目发生额（排除 TRANSFER，一级含明细） */
    private Map<String, BigDecimal[]> plOccurrence(String from, String to) {
        Map<String, BigDecimal[]> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT ve.subject_code AS code, SUM(ve.debit) AS d, SUM(ve.credit) AS c
                FROM voucher_entry ve JOIN voucher v ON ve.voucher_id = v.id
                JOIN account_subject s ON s.code = ve.subject_code
                WHERE v.status = 'POSTED' AND v.period >= ? AND v.period <= ? AND s.category = 'PL'
                  AND COALESCE(v.source, 'MANUAL') != 'TRANSFER'
                GROUP BY ve.subject_code
                """, from, to)) {
            result.put(String.valueOf(row.get("code")), new BigDecimal[]{toBd(row.get("d")), toBd(row.get("c"))});
        }
        return result;
    }

    /** 各科目带符号期末余额（正=direction 方向），含期初建账数 */
    private Map<String, BigDecimal> balancesWithOpening(String yearStart, String period) {
        Map<String, BigDecimal[]> occ = occurrence(yearStart, period);
        Map<String, BigDecimal> result = new HashMap<>();
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            BigDecimal[] o = occ.getOrDefault(s.code, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            String dir = s.openingDirection != null ? s.openingDirection : s.direction;
            result.put(s.code, signed(s.openingBalance, dir).add(net(o)));
        }
        return result;
    }

    /** 明细科目余额上卷到一级 */
    private Map<String, BigDecimal> rollup(Map<String, BigDecimal> balances) {
        Map<String, BigDecimal> result = new HashMap<>(balances);
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            if (s.parentCode == null || s.parentCode.isBlank()) continue;
            result.merge(s.parentCode, balances.getOrDefault(s.code, BigDecimal.ZERO), BigDecimal::add);
        }
        return result;
    }

    /** 一级科目 + 其全部明细科目编码 */
    private List<String> topAndChildren(String topCode) {
        List<String> codes = new ArrayList<>();
        codes.add(topCode);
        for (var s : subjectRepo.findAllByOrderByCodeAsc()) {
            if (topCode.equals(s.parentCode)) codes.add(s.code);
        }
        return codes;
    }

    private void putBalances(Map<String, Object> row, String prefix, BigDecimal signedBal) {
        row.put(prefix + "Dr", signedBal.compareTo(BigDecimal.ZERO) > 0 ? signedBal : BigDecimal.ZERO);
        row.put(prefix + "Cr", signedBal.compareTo(BigDecimal.ZERO) < 0 ? signedBal.abs() : BigDecimal.ZERO);
    }

    private void addSigned(Map<String, Object> parent, String drKey, String crKey, Map<String, Object> child) {
        BigDecimal p = toBd(parent.get(drKey)).subtract(toBd(parent.get(crKey)));
        BigDecimal c = toBd(child.get(drKey)).subtract(toBd(child.get(crKey)));
        BigDecimal sum = p.add(c);
        parent.put(drKey, sum.compareTo(BigDecimal.ZERO) > 0 ? sum : BigDecimal.ZERO);
        parent.put(crKey, sum.compareTo(BigDecimal.ZERO) < 0 ? sum.abs() : BigDecimal.ZERO);
    }

    private String parentOf(String code) {
        int dot = code.indexOf('.');
        return dot > 0 ? code.substring(0, dot) : null;
    }

    private String prevPeriod(String period) {
        int y = Integer.parseInt(period.substring(0, 4));
        int m = Integer.parseInt(period.substring(5, 7));
        if (m == 1) return (y - 1) + "-12";
        return String.format("%d-%02d", y, m - 1);
    }

    /** 带方向符号：DR 为正、CR 为负 */
    private BigDecimal signed(BigDecimal bal, String direction) {
        if (bal == null || bal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return "CR".equals(direction) ? bal.negate() : bal;
    }

    /** 借-贷（带符号净额） */
    private BigDecimal net(BigDecimal[] dc) { return dc[0].subtract(dc[1]); }

    /** 贷-借净额（收入类取数） */
    private BigDecimal netCredit(BigDecimal[] dc) { return dc[1].subtract(dc[0]); }

    /** 借-贷净额（费用类取数） */
    private BigDecimal netDebit(BigDecimal[] dc) { return dc[0].subtract(dc[1]); }

    private void checkPeriod(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException("期间格式应为 YYYY-MM");
    }

    /** sqlite-jdbc 聚合返回 Integer/Long/Double/ByteArray，统一转 BigDecimal（踩坑记录） */
    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Double d) return BigDecimal.valueOf(d).setScale(2, java.math.RoundingMode.HALF_UP);
        if (v instanceof byte[] bytes) return new BigDecimal(new String(bytes));
        return new BigDecimal(String.valueOf(v));
    }
}
