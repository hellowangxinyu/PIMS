package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.CustomerComplaint;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.service.ComplaintService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** v5.53 客户投诉/质量反馈（登记→原因→处理→关闭，批次可追溯） */
@RestController
@RequestMapping("/api/complaint")
public class ComplaintController {

    private final ComplaintService service;

    public ComplaintController(ComplaintService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission(value = "complaint:read")
    public Result<List<CustomerComplaint>> list(@RequestParam(required = false) Long customerId,
                                                @RequestParam(required = false) String status) {
        return Result.ok(service.list(customerId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "complaint:read")
    public Result<CustomerComplaint> detail(@PathVariable Long id) { return Result.ok(service.getById(id)); }

    @PostMapping
    @SaCheckPermission(value = "complaint:write")
    public Result<CustomerComplaint> create(@RequestBody CustomerComplaint c) { return Result.ok(service.create(c)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "complaint:write")
    public Result<CustomerComplaint> update(@PathVariable Long id, @RequestBody CustomerComplaint c) {
        return Result.ok(service.update(id, c));
    }

    /** 标记已处理（填原因分析+处理措施） */
    @PostMapping("/{id}/resolve")
    @SaCheckPermission(value = "complaint:write")
    public Result<CustomerComplaint> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LocalDate resolveDate = body.get("resolveDate") != null && !body.get("resolveDate").toString().isBlank()
                ? LocalDate.parse(body.get("resolveDate").toString().substring(0, 10)) : null;
        return Result.ok(service.resolve(id, (String) body.get("cause"), (String) body.get("action"),
                (String) body.get("handler"), resolveDate));
    }

    /** 客户确认关闭 */
    @PostMapping("/{id}/close")
    @SaCheckPermission(value = "complaint:write")
    public Result<CustomerComplaint> close(@PathVariable Long id) { return Result.ok(service.close(id)); }

    /** 批次追溯：该投诉物料+批号的全部出入库流水 */
    @GetMapping("/{id}/trace")
    @SaCheckPermission(value = "complaint:read")
    public Result<List<InventoryMovement>> trace(@PathVariable Long id) { return Result.ok(service.trace(id)); }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "complaint:write")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
}
