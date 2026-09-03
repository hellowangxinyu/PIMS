package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.entity.LossLetterTemplate;
import com.pengyuan.pims.entity.SupplierQualityTrace;
import com.pengyuan.pims.service.SupplierQualityTraceService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** v5.59 供应商质量追溯（批号驱动 + 损失沟通函多模板 + 采购处理结果留档） */
@RestController
@RequestMapping("/api/quality-trace")
public class SupplierQualityTraceController {

    private final SupplierQualityTraceService service;

    public SupplierQualityTraceController(SupplierQualityTraceService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "strace:read")
    public Result<List<SupplierQualityTrace>> list(@RequestParam(required = false) Long supplierId,
                                                   @RequestParam(required = false) String status) {
        return Result.ok(service.list(supplierId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "strace:read")
    public Result<SupplierQualityTrace> detail(@PathVariable Long id) { return Result.ok(service.getById(id)); }

    /** 品控选批次：关键字搜台账批次（含不合格/油尾批次），按物料+批号聚合 */
    @GetMapping("/batches")
    @SaCheckPermission(value = "strace:read")
    public Result<List<Map<String, Object>>> batches(@RequestParam(defaultValue = "") String keyword) {
        return Result.ok(service.batchOptions(keyword));
    }

    /** 按物料+批号自动带出原始采购入库信息（品控选批次后调用，不手填） */
    @GetMapping("/purchase-info")
    @SaCheckPermission(value = "strace:read")
    public Result<Map<String, Object>> purchaseInfo(@RequestParam String materialCode, @RequestParam String batchNo) {
        return Result.ok(service.purchaseInfo(materialCode, batchNo));
    }

    @PostMapping
    @SaCheckPermission(value = "strace:write")
    public Result<SupplierQualityTrace> create(@RequestBody SupplierQualityTrace t) { return Result.ok(service.create(t)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "strace:write")
    public Result<SupplierQualityTrace> update(@PathVariable Long id, @RequestBody SupplierQualityTrace t) {
        return Result.ok(service.update(id, t));
    }

    /** 处理完毕：必须有处理结果（结果类型+处理说明必填，赔款时赔付金额必填） */
    @PostMapping("/{id}/resolve")
    @SaCheckPermission(value = "strace:write")
    public Result<SupplierQualityTrace> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LocalDate resolveDate = body.get("resolveDate") != null && !body.get("resolveDate").toString().isBlank()
                ? LocalDate.parse(body.get("resolveDate").toString().substring(0, 10)) : null;
        BigDecimal compensationAmount = null;
        Object amt = body.get("compensationAmount");
        if (amt != null && !amt.toString().isBlank()) {
            try { compensationAmount = new BigDecimal(amt.toString()); } catch (NumberFormatException ignored) { }
        }
        return Result.ok(service.resolve(id, (String) body.get("resultType"), (String) body.get("resultRemark"),
                compensationAmount, (String) body.get("handler"), resolveDate));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "strace:write")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }

    /** 批次追溯：该批次全部出入库流水（主表+归档表） */
    @GetMapping("/{id}/trace")
    @SaCheckPermission(value = "strace:read")
    public Result<List<InventoryMovement>> trace(@PathVariable Long id) { return Result.ok(service.trace(id)); }

    // ==================== 损失沟通函模板 ====================

    @GetMapping("/templates")
    @SaCheckPermission(value = "strace:read")
    public Result<List<LossLetterTemplate>> listTemplates() { return Result.ok(service.listTemplates()); }

    @PostMapping("/templates")
    @SaCheckPermission(value = "strace:write")
    public Result<LossLetterTemplate> createTemplate(@RequestBody LossLetterTemplate t) {
        return Result.ok(service.createTemplate(t));
    }

    @PutMapping("/templates/{id}")
    @SaCheckPermission(value = "strace:write")
    public Result<LossLetterTemplate> updateTemplate(@PathVariable Long id, @RequestBody LossLetterTemplate t) {
        return Result.ok(service.updateTemplate(id, t));
    }

    @PutMapping("/templates/{id}/default")
    @SaCheckPermission(value = "strace:write")
    public Result<LossLetterTemplate> setDefault(@PathVariable Long id) { return Result.ok(service.setDefault(id)); }

    @DeleteMapping("/templates/{id}")
    @SaCheckPermission(value = "strace:write")
    public Result<Void> deleteTemplate(@PathVariable Long id) { service.deleteTemplate(id); return Result.ok(); }
}
