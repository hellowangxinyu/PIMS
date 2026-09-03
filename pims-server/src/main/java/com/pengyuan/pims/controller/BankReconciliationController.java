package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.BankReconciliationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 出纳银行对账（v6.3）：账户档案 / 出纳日记账 / 银行流水导入 / 自动勾对 / 余额调节表。
 * 权限挂财务域（finance:read 查、finance:write 操作）。
 */
@RestController
@RequestMapping("/api/bank")
public class BankReconciliationController {

    private final BankReconciliationService service;
    private final com.pengyuan.pims.service.UserService userService;

    public BankReconciliationController(BankReconciliationService service,
                                        com.pengyuan.pims.service.UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    // ===== 账户 =====

    @GetMapping("/account")
    @SaCheckPermission(value = "finance:read")
    public Result<List<Map<String, Object>>> accounts() {
        return Result.ok(service.listAccounts());
    }

    @PostMapping("/account")
    @SaCheckPermission(value = "finance:write")
    public Result<Map<String, Object>> saveAccount(@RequestBody Map<String, Object> body) {
        return Result.ok(service.saveAccount(body));
    }

    // ===== 日记账 =====

    @GetMapping("/journal")
    @SaCheckPermission(value = "finance:read")
    public Result<Map<String, Object>> journal(@RequestParam Long accountId,
                                               @RequestParam String from,
                                               @RequestParam String to) {
        return Result.ok(service.journal(accountId, from, to));
    }

    // ===== 流水 =====

    /** 标准模板下载：日期|摘要|对方户名|收入|支出|余额 */
    @GetMapping("/statement/template")
    @SaCheckPermission(value = "finance:read")
    public void template(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=bank-statement-template.xlsx");
        try (var out = response.getOutputStream();
             var wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(50)) {
            var sh = wb.createSheet("银行流水");
            var head = sh.createRow(0);
            String[] cols = {"日期(2026-01-31)", "摘要", "对方户名", "收入", "支出", "余额"};
            for (int i = 0; i < cols.length; i++) head.createCell(i).setCellValue(cols[i]);
            var demo = sh.createRow(1);
            demo.createCell(0).setCellValue("2026-01-05");
            demo.createCell(1).setCellValue("货款");
            demo.createCell(2).setCellValue("某公司");
            demo.createCell(3).setCellValue(50000);
            demo.createCell(4).setCellValue(0);
            demo.createCell(5).setCellValue(150000);
            for (int i = 0; i < cols.length; i++) sh.setColumnWidth(i, 16 * 256);
            wb.write(out);
            wb.dispose();
        }
    }

    @PostMapping("/statement/import")
    @SaCheckPermission(value = "finance:write")
    public Result<Map<String, Object>> importStatements(@RequestParam Long accountId,
                                                        @RequestParam("file") MultipartFile file) {
        return Result.ok(service.importStatements(accountId, file, operator()));
    }

    @GetMapping("/statement")
    @SaCheckPermission(value = "finance:read")
    public Result<List<Map<String, Object>>> statements(@RequestParam Long accountId,
                                                        @RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to,
                                                        @RequestParam(required = false) String status) {
        return Result.ok(service.listStatements(accountId, from, to, status));
    }

    // ===== 勾对 =====

    @PostMapping("/reconcile/auto")
    @SaCheckPermission(value = "finance:write")
    public Result<Map<String, Object>> autoMatch(@RequestParam Long accountId) {
        return Result.ok(service.autoMatch(accountId));
    }

    @PostMapping("/reconcile/{statementId}/bind")
    @SaCheckPermission(value = "finance:write")
    public Result<?> bind(@PathVariable Long statementId,
                          @RequestParam String refType,
                          @RequestParam Long refId) {
        service.bind(statementId, refType, refId);
        return Result.ok("已勾对");
    }

    @PostMapping("/reconcile/{statementId}/unbind")
    @SaCheckPermission(value = "finance:write")
    public Result<?> unbind(@PathVariable Long statementId) {
        service.unbind(statementId);
        return Result.ok("已取消勾对");
    }

    /** 余额调节表：bankEnding 可空（取期间最后一笔流水余额） */
    @GetMapping("/reconcile/report")
    @SaCheckPermission(value = "finance:read")
    public Result<Map<String, Object>> report(@RequestParam Long accountId,
                                              @RequestParam String from,
                                              @RequestParam String to,
                                              @RequestParam(required = false) BigDecimal bankEnding) {
        return Result.ok(service.reconcileReport(accountId, from, to, bankEnding));
    }

    private String operator() {
        try { return userService.currentOperatorName(); } catch (Exception e) { return "系统"; }
    }
}
