package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.FinanceReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 财务报表（v5.36）：月度利润试算 + 客户对账单取数（打印由前端拼 HTML）；v5.61 总账账簿与三大报表 */
@RestController
@RequestMapping("/api/finance-report")
public class FinanceReportController {

    private final FinanceReportService service;
    private final com.pengyuan.pims.service.VoucherReportService voucherReport;
    public FinanceReportController(FinanceReportService service, com.pengyuan.pims.service.VoucherReportService voucherReport) {
        this.service = service;
        this.voucherReport = voucherReport;
    }

    /** 月度利润试算：收入/成本/毛利/费用/净利 + 资金占用参考 + 近12月走势 */
    @GetMapping("/profit-trial")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> profitTrial(@RequestParam String month) {
        return Result.ok(service.profitTrial(month));
    }

    /** 月度利润试算导出 Excel */
    @GetMapping("/profit-trial/export")
    @SaCheckPermission("finance:read")
    public void exportProfitTrial(@RequestParam String month, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        boolean amtPerm = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance-report");
        Map<String, Object> d = service.profitTrial(month);
        List<Object[]> rows = new java.util.ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expenseDetail = (List<Map<String, Object>>) d.get("expenseDetail");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> incomeDetail = (List<Map<String, Object>>) d.get("incomeDetail");
        if (amtPerm) {
            rows.add(new Object[]{"一、营业收入", num(d.get("revenue"))});
            rows.add(new Object[]{"减：营业成本", num(d.get("cogs"))});
            rows.add(new Object[]{"二、毛利润", num(d.get("grossProfit"))});
            rows.add(new Object[]{"加：其他收入", num(d.get("otherIncome"))});
            rows.add(new Object[]{"减：期间费用合计", num(d.get("expenseTotal"))});
            for (Map<String, Object> e : expenseDetail) rows.add(new Object[]{"　　" + e.get("type"), num(e.get("amount"))});
            rows.add(new Object[]{"三、利润净额", num(d.get("netProfit"))});
            rows.add(new Object[]{"", ""});
            rows.add(new Object[]{"资金占用参考", ""});
            rows.add(new Object[]{"生产领料成本", num(d.get("materialUsed"))});
            rows.add(new Object[]{"委外加工费立账", num(d.get("outsourceFee"))});
            rows.add(new Object[]{"采购应付立账", num(d.get("purchaseAp"))});
            if (incomeDetail != null) for (Map<String, Object> e : incomeDetail) rows.add(new Object[]{"其他收入·" + e.get("type"), num(e.get("amount"))});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "利润试算表-" + month, "利润试算",
                new String[]{"项目", "金额"}, rows);
    }

    private Object num(Object v) {
        return v == null ? 0 : v;
    }

    /** 客户对账单：期初 + 期间往来明细 + 期末 */
    @GetMapping("/statement")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> statement(@RequestParam Long customerId,
                                                 @RequestParam String from,
                                                 @RequestParam String to) {
        return Result.ok(service.statement(customerId, from, to));
    }

    // ===== v5.61 总账账簿与三大报表 =====

    /** 科目余额表（level: TOP 仅一级 / ALL 含明细） */
    @GetMapping("/account-balance")
    @SaCheckPermission("finance:read")
    public Result<List<Map<String, Object>>> accountBalance(@RequestParam String period,
                                                            @RequestParam(defaultValue = "ALL") String level) {
        return Result.ok(voucherReport.accountBalance(period, level));
    }

    @GetMapping("/account-balance/export")
    @SaCheckPermission("finance:read")
    public void exportAccountBalance(@RequestParam String period, @RequestParam(defaultValue = "ALL") String level,
                                     jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance-report");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Map<String, Object> r : voucherReport.accountBalance(period, level)) {
            if (amt) {
                rows.add(new Object[]{ r.get("code"), r.get("name"),
                        r.get("beginDr"), r.get("beginCr"), r.get("debit"), r.get("credit"),
                        r.get("endDr"), r.get("endCr") });
            } else {
                rows.add(new Object[]{ r.get("code"), r.get("name") });
            }
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "科目余额表-" + period, "科目余额表",
                amt ? new String[]{"科目编码","科目名称","期初借方","期初贷方","本期借方","本期贷方","期末借方","期末贷方"}
                    : new String[]{"科目编码","科目名称"}, rows);
    }

    /** 明细账 */
    @GetMapping("/account-detail")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> accountDetail(@RequestParam String subjectCode,
                                                     @RequestParam String from,
                                                     @RequestParam String to,
                                                     @RequestParam(required = false) String auxName) {
        return Result.ok(voucherReport.accountDetail(subjectCode, from, to, auxName));
    }

    @GetMapping("/account-detail/export")
    @SaCheckPermission("finance:read")
    public void exportAccountDetail(@RequestParam String subjectCode, @RequestParam String from, @RequestParam String to,
                                    @RequestParam(required = false) String auxName,
                                    jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance-report");
        Map<String, Object> d = voucherReport.accountDetail(subjectCode, from, to, auxName);
        List<Object[]> rows = new java.util.ArrayList<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> subject = (Map<String, Object>) d.get("subject");
        if (amt) {
            rows.add(new Object[]{"期初", "", "", d.get("beginDr"), d.get("beginCr"), ""});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) d.get("lines");
            for (Map<String, Object> l : lines) {
                rows.add(new Object[]{ l.get("voucherDate"), l.get("docNo"), l.get("digest"),
                        l.get("debit"), l.get("credit"),
                        "借方 " + l.get("balanceDr") + " / 贷方 " + l.get("balanceCr") });
            }
            rows.add(new Object[]{"合计", "", "", d.get("sumDebit"), d.get("sumCredit"), ""});
            rows.add(new Object[]{"期末", "", "", d.get("endDr"), d.get("endCr"), ""});
        }
        com.pengyuan.pims.common.ExcelUtil.export(response,
                "明细账-" + subject.get("code") + " " + subject.get("name") + "-" + from, "明细账",
                amt ? new String[]{"日期","凭证号","摘要","借方","贷方","余额"}
                    : new String[]{"日期","凭证号","摘要"}, rows);
    }

    /** 资产负债表 */
    @GetMapping("/balance-sheet")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> balanceSheet(@RequestParam String period) {
        return Result.ok(voucherReport.balanceSheet(period));
    }

    @GetMapping("/balance-sheet/export")
    @SaCheckPermission("finance:read")
    public void exportBalanceSheet(@RequestParam String period,
                                   jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance-report");
        Map<String, Object> d = voucherReport.balanceSheet(period);
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{"资产", "", ""});
        appendBs(rows, d, "assets", "资产总计", "assetTotal", "assetBeginTotal", amt);
        rows.add(new Object[]{"负债", "", ""});
        appendBs(rows, d, "liabilities", "负债合计", "liabTotal", "liabBeginTotal", amt);
        rows.add(new Object[]{"所有者权益", "", ""});
        appendBs(rows, d, "equity", "所有者权益合计", "eqTotal", "eqBeginTotal", amt);
        com.pengyuan.pims.common.ExcelUtil.export(response, "资产负债表-" + period, "资产负债表",
                new String[]{"项目", "年初余额", "期末余额"}, rows);
    }

    @SuppressWarnings("unchecked")
    private void appendBs(List<Object[]> rows, Map<String, Object> d, String key, String totalLabel,
                          String totalKey, String beginKey, boolean amt) {
        for (Map<String, Object> r : (List<Map<String, Object>>) d.get(key)) {
            rows.add(new Object[]{ r.get("item"), amt ? r.get("yearBegin") : "", amt ? r.get("periodEnd") : "" });
        }
        rows.add(new Object[]{ totalLabel, amt ? d.get(beginKey) : "", amt ? d.get(totalKey) : "" });
    }

    /** 利润表 */
    @GetMapping("/income-statement")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> incomeStatement(@RequestParam String period) {
        return Result.ok(voucherReport.incomeStatement(period));
    }

    @GetMapping("/income-statement/export")
    @SaCheckPermission("finance:read")
    public void exportIncomeStatement(@RequestParam String period,
                                      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance-report");
        List<Object[]> rows = new java.util.ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> isRows = (List<Map<String, Object>>) voucherReport.incomeStatement(period).get("rows");
        for (Map<String, Object> r : isRows) {
            rows.add(new Object[]{ r.get("item"), amt ? r.get("month") : "", amt ? r.get("yearCum") : "" });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "利润表-" + period, "利润表",
                new String[]{"项目", "本月金额", "本年累计"}, rows);
    }

    /** 现金流量表 */
    @GetMapping("/cash-flow")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> cashFlow(@RequestParam String period) {
        return Result.ok(voucherReport.cashFlow(period));
    }

    @GetMapping("/cash-flow/export")
    @SaCheckPermission("finance:read")
    public void exportCashFlow(@RequestParam String period,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance-report");
        List<Object[]> rows = new java.util.ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cfRows = (List<Map<String, Object>>) voucherReport.cashFlow(period).get("rows");
        for (Map<String, Object> r : cfRows) {
            rows.add(new Object[]{ r.get("segment"), r.get("item"), amt ? r.get("month") : "", amt ? r.get("yearCum") : "" });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "现金流量表-" + period, "现金流量表",
                new String[]{"类别", "项目", "本月金额", "本年累计"}, rows);
    }
}
