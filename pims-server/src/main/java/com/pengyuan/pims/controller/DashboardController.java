package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.config.SummarySchemaInitializer;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import com.pengyuan.pims.service.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 首页仪表盘聚合接口
 * 统计数据从汇总表读取（触发器自动维护），避免全表扫描
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PurchaseService purchaseService;
    private final SalesOrderService salesService;
    private final OutsourceOrderService outsourceService;
    private final InventoryService inventoryService;
    private final StatOrderMonthlyRepository orderMonthlyRepo;
    private final StatFinanceSummaryRepository financeSummaryRepo;
    private final SummarySchemaInitializer summaryInitializer;
    private final WarehouseRepository warehouseRepo;
    private final MaterialRepository materialRepo;
    private final SupplierRepository supplierRepo;
    private final CustomerRepository customerRepo;
    private final com.pengyuan.pims.repository.QualityInspectionRepository qcRepo;
    private final com.pengyuan.pims.repository.ReturnOrderRepository returnRepo;
    private final com.pengyuan.pims.repository.AccountsReceivableRepository arRepo;
    private final com.pengyuan.pims.repository.WeeklyTopicRepository topicRepo;
    private final com.pengyuan.pims.service.BackupService backupService;
    private final com.pengyuan.pims.service.CrmService crmService;
    private final com.pengyuan.pims.service.TaskService taskService;
    private final com.pengyuan.pims.service.UserService userService;
    private final com.pengyuan.pims.repository.SampleRequestRepository sampleRepo;

    private final ReportService reportService;
    private final InventoryLedgerRepository ledgerRepo;

    public DashboardController(PurchaseService purchaseService,
                               ReportService reportService,
                               InventoryLedgerRepository ledgerRepo,
                               SalesOrderService salesService,
                               OutsourceOrderService outsourceService,
                               InventoryService inventoryService,
                               StatOrderMonthlyRepository orderMonthlyRepo,
                               StatFinanceSummaryRepository financeSummaryRepo,
                               SummarySchemaInitializer summaryInitializer,
                               WarehouseRepository warehouseRepo,
                               MaterialRepository materialRepo,
                               SupplierRepository supplierRepo,
                               CustomerRepository customerRepo,
                               com.pengyuan.pims.repository.QualityInspectionRepository qcRepo,
                               com.pengyuan.pims.repository.ReturnOrderRepository returnRepo,
                               com.pengyuan.pims.repository.AccountsReceivableRepository arRepo,
                               com.pengyuan.pims.repository.WeeklyTopicRepository topicRepo,
                               com.pengyuan.pims.service.BackupService backupService,
                               com.pengyuan.pims.service.CrmService crmService,
                               com.pengyuan.pims.service.TaskService taskService,
                               com.pengyuan.pims.service.UserService userService,
                               com.pengyuan.pims.repository.SampleRequestRepository sampleRepo) {
        this.purchaseService = purchaseService;
        this.reportService = reportService;
        this.ledgerRepo = ledgerRepo;
        this.salesService = salesService;
        this.outsourceService = outsourceService;
        this.inventoryService = inventoryService;
        this.orderMonthlyRepo = orderMonthlyRepo;
        this.financeSummaryRepo = financeSummaryRepo;
        this.summaryInitializer = summaryInitializer;
        this.warehouseRepo = warehouseRepo;
        this.materialRepo = materialRepo;
        this.supplierRepo = supplierRepo;
        this.customerRepo = customerRepo;
        this.qcRepo = qcRepo;
        this.returnRepo = returnRepo;
        this.arRepo = arRepo;
        this.topicRepo = topicRepo;
        this.backupService = backupService;
        this.crmService = crmService;
        this.taskService = taskService;
        this.userService = userService;
        this.sampleRepo = sampleRepo;
    }

    @GetMapping
    public Result<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 基础数据统计
        data.put("warehouseCount", warehouseRepo.countByEnabledTrue());  // v5.43.2 只数启用（排除禁用/测试）
        data.put("materialCount", materialRepo.countByEnabledTrue());  // v5.43.2
        data.put("materialCategoryCount", materialRepo.countDistinctCategory());

        // v5.47 待办聚合（管理层一眼看全：待判定/待审核/到期应收/逾期议题）
        java.util.Map<String, Object> todos = new java.util.LinkedHashMap<>();
        todos.put("pendingQc", qcRepo.countByStatus("PENDING"));
        todos.put("pendingReinspect", qcRepo.countByStatusAndRefDocType("PENDING", "REINSPECTION"));
        todos.put("pendingReturn", returnRepo.countByTypeAndStatus("PURCHASE_RETURN", "DRAFT")
                + returnRepo.countByTypeAndStatus("SALES_RETURN", "DRAFT"));
        todos.put("pendingTailing", returnRepo.countByTypeAndStatus("TAILING_RETURN", "DRAFT"));
        java.time.LocalDate today = java.time.LocalDate.now();
        // 到期应收：两条聚合查询替代拉全量未结清单内存累加
        java.math.BigDecimal dueAmount = arRepo.sumDueNotSettled(today);
        int dueCount = (int) arRepo.countByStatusNotAndDueDateLessThanEqual("PAID", today);
        todos.put("dueArCount", dueCount);
        todos.put("dueArAmount", dueAmount);
        todos.put("overdueTopics", topicRepo.countByClosedDateIsNullAndPlanDateBefore(today));
        todos.put("dueFollowUp", crmService.dueFollowUpCount());  // v5.50 今日该跟进
        // v7.7 打样任务：派发给我且待接收的数量（打样员工作台提醒，接收后消失）
        try {
            todos.put("sampleToAccept", sampleRepo.countByAssigneeAndStatus(userService.currentUsername(), "ASSIGNED"));
        } catch (Exception ignored) { }
        // v5.67 任务督办：我的待办任务 + 逾期任务
        try {
            var myTasks = taskService.myTaskCount(userService.currentUsername());
            todos.put("myOpenTasks", myTasks.get("myOpenTasks"));
            todos.put("overdueTasks", myTasks.get("overdueTasks"));
        } catch (Exception ignored) { }
        // v5.48 备份健康：上次备份超 25 小时或从未备份则提醒（备份服务异常可被发现）
        try {
            var last = backupService.lastBackupTime();
            todos.put("lastBackup", last == null ? null : last.toString());
            todos.put("backupStale", last == null || last.isBefore(java.time.LocalDateTime.now().minusHours(25)));
        } catch (Exception e) {
            todos.put("backupStale", false);
        }
        data.put("todos", todos);
        data.put("supplierCount", supplierRepo.countByEnabledTrue());  // v5.43.2
        data.put("customerCount", customerRepo.countByEnabledTrue());  // v5.43.2

        // 最近5条采购订单（走索引 create_time DESC，不全表加载）
        data.put("recentPurchases", purchaseService.listRecent(5));

        // 最近5条销售订单
        data.put("recentSales", salesService.listRecent(5));

        // 最近5条委外工单
        data.put("recentOutsource", outsourceService.listRecent(5));

        // 低库存预警（v5.12 与报表口径统一：可用天数<15天 + 库存0无用量物料）
        data.put("lowStock", lowStockSummary());

        // 应收应付汇总（从汇总表单行读取，O(1)）
        // v6.1（中#14）：金额字段按 finance:amount 权限过滤——无金额权限的角色看到 0，不泄漏财务数据
        StatFinanceSummary fin = financeSummaryRepo.findById(1L).orElse(null);
        boolean canSeeAmount = com.pengyuan.pims.common.FieldFilter.hasAmountPerm("finance");
        if (fin != null && canSeeAmount) {
            data.put("arTotal", fin.arTotal);
            data.put("arReceived", fin.arReceived);
            data.put("arPending", fin.arTotal.subtract(fin.arReceived));
            data.put("apTotal", fin.apTotal);
            data.put("apPaid", fin.apPaid);
            data.put("apPending", fin.apTotal.subtract(fin.apPaid));
        } else {
            data.put("arTotal", BigDecimal.ZERO);
            data.put("arReceived", BigDecimal.ZERO);
            data.put("arPending", BigDecimal.ZERO);
            data.put("apTotal", BigDecimal.ZERO);
            data.put("apPaid", BigDecimal.ZERO);
            data.put("apPending", BigDecimal.ZERO);
        }

        // 当月订单统计（从月度汇总表读取）
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<StatOrderMonthly> monthStats = orderMonthlyRepo.findSince(currentMonth);
        data.put("monthlyStats", monthStats);

        return Result.ok(data);
    }

    /** v5.12：工作台低库存预警 = 报表口径（可用天数<15天，物料级）+ 库存为0且无用量记录的原材料（补RED） */
    private List<Map<String, Object>> lowStockSummary() {
        Map<String, Object> report = reportService.lowStockReport();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = new java.util.ArrayList<>((List<Map<String, Object>>) report.get("rows"));
        java.util.Set<String> covered = new java.util.HashSet<>();
        for (Map<String, Object> r : rows) covered.add((String) r.get("materialCode"));
        // 物料大类一次性建映射（此前逐行 findAll 物料表是 行数×物料量 的嵌套全表扫描）
        Map<String, String> categoryByCode = new java.util.HashMap<>();
        for (var m : materialRepo.findAll()) categoryByCode.put(m.code, m.category);
        for (var l : ledgerRepo.findByQtyLessThanEqualOrQtyIsNull(java.math.BigDecimal.ZERO)) {
            if (l.qty != null && l.qty.compareTo(java.math.BigDecimal.ZERO) > 0) continue;
            if (l.materialCode == null || covered.contains(l.materialCode)) continue;
            String cat = categoryByCode.get(l.materialCode);
            if (cat == null || !"APFRS".contains(cat)) continue;
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("materialCode", l.materialCode);
            item.put("materialName", l.materialName);
            item.put("currentQty", java.math.BigDecimal.ZERO);
            item.put("availableDays", 0);
            item.put("level", "RED");
            item.put("unit", l.unit);
            item.put("warehouseId", "");
            item.put("warehouseName", "全仓"); // v5.34：无用量记录的 0 库存补充行按物料级显示
            rows.add(item);
            covered.add(l.materialCode);
        }
        rows.sort(java.util.Comparator.comparing(r -> ((Number) r.get("availableDays")).intValue()));
        if (rows.size() > 20) rows = rows.subList(0, 20);
        return rows;
    }

    /** 手动重建汇总数据（管理员维护用） */
    /** v5.48 手动备份（运维通道：升级前/重要操作前即时备份） */
    @PostMapping("/backup")
    @SaCheckPermission(value = "user:write")
    public Result<String> backupNow() {
        return Result.ok("备份完成: " + backupService.backupNow());
    }

    @PostMapping("/rebuild-stats")
    @SaCheckPermission(value = "user:write")
    public Result<?> rebuildStats() {
        summaryInitializer.backfillHistory();
        return Result.ok("汇总数据重建完成");
    }
}
