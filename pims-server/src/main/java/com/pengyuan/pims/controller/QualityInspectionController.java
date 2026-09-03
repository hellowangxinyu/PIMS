package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.QualityInspection;
import com.pengyuan.pims.service.QualityInspectionService;
import com.pengyuan.pims.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 质检管理接口
 * 来料质检 / 出厂质检
 */
@RestController
@RequestMapping("/api/qc")
public class QualityInspectionController {

    private final QualityInspectionService service;
    private final UserService userService;

    public QualityInspectionController(QualityInspectionService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    /** 查询全部质检单 */
    @GetMapping
    @SaCheckPermission(value = "qc:read")
    public Result<List<QualityInspection>> list(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return Result.ok(service.listByType(type));
        }
        return Result.ok(service.listAll());
    }

    /** 查询待检列表 */
    @GetMapping("/pending")
    @SaCheckPermission(value = "qc:read")
    public Result<List<QualityInspection>> listPending(@RequestParam String type) {
        return Result.ok(service.listPending(type));
    }

    /**
     * 质检单分页查询（支持类型 + 状态 + 多条件）
     * 默认每页 20 条，按创建时间倒序
     */
    @GetMapping("/search")
    @SaCheckPermission(value = "qc:read")
    public Result<?> search(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String inspectionNo,
                            @RequestParam(required = false) String refDocNo,
                            @RequestParam(required = false) String materialCode,
                            @RequestParam(required = false) String materialName,
                            @RequestParam(required = false) String batchNo,
                            @RequestParam(required = false) String inspector,
                            @RequestParam(required = false) LocalDate startDate,
                            @RequestParam(required = false) LocalDate endDate,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        var result = service.search(type, status, category, inspectionNo, refDocNo,
                materialCode, materialName, batchNo, inspector,
                startDate, endDate, page, size);
        return Result.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        ));
    }

    /** QC判定（v5.32：body 增加可选 items=检测项实测值数组、batchNo=补填批号） */
    @PostMapping("/{id}/judge")
    @SaCheckPermission(value = "qc:write")
    public Result<QualityInspection> judge(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String result = body.get("result") == null ? null : body.get("result").toString();
        String resultRemark = body.get("resultRemark") == null ? null : body.get("resultRemark").toString();
        // v5.31：判定不合格时的存放库位（不合格品库内，可空=默认库位）
        Long unqualifiedLocationId = null;
        Object locStr = body.get("unqualifiedLocationId");
        if (locStr != null && !locStr.toString().isBlank()) {
            try { unqualifiedLocationId = Long.valueOf(locStr.toString().trim()); } catch (NumberFormatException ignored) { }
        }
        // v5.32：检测项实测值 [{id, measuredValue, itemResult}]，可选
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = body.get("items") instanceof List<?> l
                ? (List<Map<String, Object>>) l : null;
        // v5.32：批号补填（到货未带批号时检验员按实物包装录入，入库台账沿用）
        String batchNo = body.get("batchNo") == null ? null : body.get("batchNo").toString();
        // v5.37：复检后有效期（复检单 PASS/CONCESSION 时必填，更新台账过期日期即放行出库）
        LocalDate reexpiryDate = null;
        Object rex = body.get("reexpiryDate");
        if (rex != null && !rex.toString().isBlank()) {
            reexpiryDate = LocalDate.parse(rex.toString().trim());
        }
        String inspector = userService.currentOperatorName();
        return Result.ok(service.judge(id, result, inspector, resultRemark, unqualifiedLocationId, items, batchNo, reexpiryDate));
    }

    /** v5.37：对过期批次发起复检评估（生成 PENDING 复检质检单，refDocType=REINSPECTION） */
    @PostMapping("/reinspection")
    @SaCheckPermission(value = "qc:write")
    public Result<QualityInspection> createReinspection(@RequestBody Map<String, String> body) {
        return Result.ok(service.createReinspection(body.get("materialCode"), body.get("batchNo"),
                userService.currentOperatorName()));
    }

    /** v5.32：质检单检测项（判定弹窗/打印加载；存量待检单无快照时按默认模板补建） */
    @GetMapping("/{id}/items")
    @SaCheckPermission(value = "qc:read")
    public Result<List<com.pengyuan.pims.entity.QualityInspectionItem>> items(@PathVariable Long id) {
        return Result.ok(service.getItems(id));
    }

    /** v5.32：按质检单号取检测项（库存页展开查看批次检测明细；权限随库存页，无质检权限的库存用户也可看） */
    @GetMapping("/items-by-no/{inspectionNo}")
    @SaCheckPermission(value = "inventory:read")
    public Result<List<com.pengyuan.pims.entity.QualityInspectionItem>> itemsByNo(@PathVariable String inspectionNo) {
        return Result.ok(service.getItemsByNo(inspectionNo));
    }

    /** v5.32：按质检单号取质检单完整信息+检测项（库存页点击检测结果弹窗查看质检报告） */
    @GetMapping("/by-no/{inspectionNo}")
    @SaCheckPermission(value = "inventory:read")
    public Result<Map<String, Object>> byNo(@PathVariable String inspectionNo) {
        return Result.ok(service.getByNo(inspectionNo));
    }

    // ==================== 导出（v5.23） ====================

    /** 质检单导出：当前筛选条件全量（与已质检列表搜索参数一致） */
    @GetMapping("/export")
    @SaCheckPermission(value = "qc:read")
    public void export(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String inspectionNo,
                       @RequestParam(required = false) String refDocNo,
                       @RequestParam(required = false) String materialCode,
                       @RequestParam(required = false) String materialName,
                       @RequestParam(required = false) String batchNo,
                       @RequestParam(required = false) String inspector,
                       @RequestParam(required = false) LocalDate startDate,
                       @RequestParam(required = false) LocalDate endDate,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var result = service.search(type, status, category, inspectionNo, refDocNo,
                materialCode, materialName, batchNo, inspector, startDate, endDate, 0, 100000);
        // v5.32：批量取检测项明细，拼"外观:透明；细度:25μm"文本列
        var itemMap = service.getItemsIn(result.getContent().stream().map(q -> q.id).toList());
        List<Object[]> rows = new java.util.ArrayList<>();
        for (QualityInspection q : result.getContent()) {
            String st = switch (q.status == null ? "" : q.status) {
                case "PENDING" -> "待检";
                case "PASS" -> "合格";
                case "CONCESSION" -> "让步接收";
                case "REJECT" -> "退货";
                default -> q.status;
            };
            String refType = switch (q.refDocType == null ? "" : q.refDocType) {
                case "PURCHASE" -> "采购";
                case "PRODUCTION_INBOUND" -> "生产入库";
                case "OUTSOURCE_INBOUND" -> "委外入库";
                case "OTHER_INBOUND" -> "其他入库";
                default -> q.refDocType;
            };
            String detail = itemMap.getOrDefault(q.id, List.of()).stream()
                    .filter(i -> i.measuredValue != null && !i.measuredValue.isBlank())
                    .map(i -> i.name + ":" + i.measuredValue + (i.unit == null || i.unit.isBlank() ? "" : i.unit))
                    .reduce((a, b) -> a + "；" + b).orElse("");
            rows.add(new Object[]{
                    q.inspectionNo, q.materialCategory, q.refDocNo, refType, q.materialCode, q.materialName,
                    q.batchNo, q.qty, q.unit, st, q.inspector, q.inspectDate, q.resultRemark, detail, q.createdBy
            });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response, "质检记录-" + LocalDate.now(), "质检记录",
                new String[]{"质检单号", "物料类型", "关联单号", "关联类型", "物料编码", "品名", "批号", "数量", "单位",
                        "状态", "检验员", "检验日期", "判定说明", "检测明细", "制单人"},
                rows);
    }
}
