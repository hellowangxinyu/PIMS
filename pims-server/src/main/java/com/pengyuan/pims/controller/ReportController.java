package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报表中心 API
 * 提供采购/库存/财务/生产四大报表的聚合统计数据
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 采购报表：月度金额趋势 + 供应商TOP10 + 分类占比 */
    @GetMapping("/purchase")
    @SaCheckPermission("purchase:read")
    public Result<Map<String, Object>> purchase(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.purchaseReport(months));
    }

    /** 库存报表：月度出入库趋势 + 物料吞吐TOP10 + 仓库分布 */
    @GetMapping("/inventory")
    @SaCheckPermission("inventory:read")
    public Result<Map<String, Object>> inventory(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.inventoryReport(months));
    }

    /** v6.3 库存周转率：近 N 天出库/当前库存年化，大类汇总 + 物料明细（升序=最呆滞在前） */
    @GetMapping("/turnover")
    @SaCheckPermission(value = "inventory:read")
    public Result<Map<String, Object>> turnover(@RequestParam(defaultValue = "90") int days) {
        return Result.ok(reportService.turnoverReport(days));
    }

    /** 财务报表：月度应收应付趋势 + 回款率 + 收支对比 */
    @GetMapping("/finance")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> finance(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.financeReport(months));
    }

    /** 生产报表：月度订单趋势 + 完工率 + 产量对比 */
    @GetMapping("/production")
    @SaCheckPermission("inventory:read")
    public Result<Map<String, Object>> production(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.productionReport(months));
    }

    /** 生产进度表：各订单完成进度（进度条展示） */
    @GetMapping("/production-progress")
    @SaCheckPermission("inventory:read")
    public Result<Map<String, Object>> productionProgress() {
        return Result.ok(reportService.productionProgress());
    }

    /** 低库存预警：原材料按历史实际用量测算月均/日均用量、安全库存与可用天数，可用天数<15天预警 */
    @GetMapping("/low-stock")
    @SaCheckPermission("inventory:read")
    public Result<Map<String, Object>> lowStock() {
        return Result.ok(reportService.lowStockReport());
    }

    /** 批次过期预警（v5.23）：在库批次过期日期预警（已过期 + 30天内到期），inventory:read */
    @GetMapping("/expiry")
    @SaCheckPermission("inventory:read")
    public Result<Map<String, Object>> expiry() {
        return Result.ok(reportService.expiryReport());
    }

    /** 批次过期预警导出（v5.23）：与报表同口径，xlsx */
    @GetMapping("/expiry/export")
    @SaCheckPermission("inventory:read")
    public void expiryExport(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows =
                (java.util.List<java.util.Map<String, Object>>) reportService.expiryReport().get("rows");
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> m : rows) {
            out.add(new Object[]{
                    m.get("materialCode"), m.get("materialName"), m.get("batchNo"), m.get("qty"), m.get("unit"),
                    m.get("expiryDate"), m.get("remainDays"),
                    "EXPIRED".equals(m.get("level")) ? "已过期" : "即将到期",
                    m.get("amount"), m.get("warehouses")
            });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "批次过期预警-" + java.time.LocalDate.now(), "过期预警",
                new String[]{"物料编码", "品名", "批号", "库存量", "单位", "过期日期", "剩余天数", "预警级别", "金额", "仓库"},
                out);
    }

    /** 销售报表（v5.8）：月度收入/成本/数量 + 客户/产品/制单人排行 + 订单状态分布 */
    @GetMapping("/sales")
    @SaCheckPermission("sales:read")
    public Result<Map<String, Object>> sales(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.salesReport(months));
    }

    /** 毛利分析（v5.8）：月度收入/成本/毛利 + 产品/客户毛利TOP10 + 毛利率 */
    @GetMapping("/margin")
    @SaCheckPermission("sales:read")
    public Result<Map<String, Object>> margin(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.marginReport(months));
    }

    /** 销售订单执行率（v5.8）：明细级发货完成率 + 未发货/部分发货清单 */
    @GetMapping("/order-exec")
    @SaCheckPermission("sales:read")
    public Result<Map<String, Object>> orderExec() {
        return Result.ok(reportService.orderExecReport());
    }

    /** 领料差异分析（v5.64）：实际净领料 vs 配方计划用量——按配方/原料聚合 + 明细，持续偏差=配方问题信号 */
    @GetMapping("/material-variance")
    @SaCheckPermission("production:read")
    public Result<Map<String, Object>> materialVariance() {
        return Result.ok(reportService.materialVarianceReport());
    }

    @GetMapping("/material-variance/export")
    @SaCheckPermission("production:read")
    public void exportMaterialVariance(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = com.pengyuan.pims.common.FieldFilter.hasPerm("finance:amount");
        List<Object[]> out = new java.util.ArrayList<>();
        Map<String, Object> d = reportService.materialVarianceReport();
        out.add(new Object[]{"【按配方聚合】", "", "", "", "", "", "", ""});
        out.add(new Object[]{"产品", "订单数", "领料行数", "多领行", "少领行", "相符行", amt ? "加权差异率" : "", amt ? "差异金额" : ""});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProduct = (List<Map<String, Object>>) d.get("byProduct");
        for (Map<String, Object> m : byProduct) {
            out.add(new Object[]{ m.get("name"), m.get("orderCount"), m.get("lineCount"), m.get("overCount"),
                    m.get("underCount"), m.get("matchCount"),
                    amt ? String.format("%.1f%%", toD(m.get("weightedRate")) * 100) : "",
                    amt ? toD(m.get("diffAmount")) : "" });
        }
        out.add(new Object[]{"", "", "", "", "", "", "", ""});
        out.add(new Object[]{"【按原料聚合】", "", "", "", "", "", "", ""});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byMaterial = (List<Map<String, Object>>) d.get("byMaterial");
        for (Map<String, Object> m : byMaterial) {
            out.add(new Object[]{ m.get("key") + " " + m.get("name"), m.get("orderCount"), m.get("lineCount"),
                    m.get("overCount"), m.get("underCount"), m.get("matchCount"),
                    amt ? String.format("%.1f%%", toD(m.get("weightedRate")) * 100) : "",
                    amt ? toD(m.get("diffAmount")) : "" });
        }
        out.add(new Object[]{"", "", "", "", "", "", "", ""});
        out.add(new Object[]{"【订单×物料明细】", "", "", "", "", "", "", ""});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) d.get("rows");
        for (Map<String, Object> r : rows) {
            out.add(new Object[]{ r.get("orderNo"), r.get("productName"), r.get("materialCode") + " " + r.get("materialName"),
                    r.get("plannedQty"), r.get("actualQty"), r.get("unit"),
                    String.format("%.1f%%", toD(r.get("diffRate")) * 100),
                    amt ? toD(r.get("diffAmount")) : "" });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "领料差异分析-" + java.time.LocalDate.now(), "领料差异",
                new String[]{"项目1", "项目2", "项目3", "数量1", "数量2", "单位", "差异率", "差异金额"}, out);
    }

    private double toD(Object v) {
        return v == null ? 0 : Double.parseDouble(String.valueOf(v));
    }

    /** 质检报表（v5.8）：月度合格率 + 判定分布 + 不合格物料TOP10 + 明细 */
    @GetMapping("/qc")
    @SaCheckPermission("qc:read")
    public Result<Map<String, Object>> qc(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.qcReport(months));
    }

    /** 账龄分析（v5.8）：应收/应付未结清按到期日分层 */
    @GetMapping("/aging")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> aging() {
        return Result.ok(reportService.agingReport());
    }

    /** 库存分析（v5.8）：批次库龄 + 呆滞排行 + 大类金额 + 领用vs标准 */
    @GetMapping("/stock-analysis")
    @SaCheckPermission("inventory:read")
    public Result<Map<String, Object>> stockAnalysis() {
        return Result.ok(reportService.stockAnalysisReport());
    }

    /** 采购分析（v5.8）：同物料比价 + 到货完成率 + 未到齐明细 */
    @GetMapping("/purchase-analysis")
    @SaCheckPermission("purchase:read")
    public Result<Map<String, Object>> purchaseAnalysis() {
        return Result.ok(reportService.purchaseAnalysisReport());
    }

    /** 委外报表（v5.8）：月度加工费 + 代工厂排行 + 代工厂得率 */
    @GetMapping("/outsource")
    @SaCheckPermission("outsource:read")
    public Result<Map<String, Object>> outsource(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.outsourceReport(months));
    }

    /** 经营看板（v5.8）：当月KPI + 月度销售/采购/加工费/收付款对比 */
    @GetMapping("/overview")
    @SaCheckPermission("finance:read")
    public Result<Map<String, Object>> overview(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(reportService.overviewReport(months));
    }
}
