package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.AccountSubject;
import com.pengyuan.pims.service.AccountSubjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 会计科目（v5.61 总账体系）：科目维护 + 期初建账 + 业务转凭证默认科目映射 */
@RestController
@RequestMapping("/api/account-subject")
public class AccountSubjectController {

    private final AccountSubjectService service;

    public AccountSubjectController(AccountSubjectService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<AccountSubject> list = service.list();
        if (!FieldFilter.hasAmountPerm("account-subject")) {
            return Result.ok(FieldFilter.filterListFields(list, "openingBalance"));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<AccountSubject> create(@RequestBody AccountSubject s) {
        return Result.ok(service.create(s));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<AccountSubject> update(@PathVariable Long id, @RequestBody AccountSubject s) {
        return Result.ok(service.update(id, s));
    }

    @PutMapping("/{id}/toggle")
    @SaCheckPermission("finance:write")
    public Result<AccountSubject> toggle(@PathVariable Long id) {
        return Result.ok(service.toggle(id));
    }

    /** 期初建账：全量覆盖，借贷必须平衡 */
    @PutMapping("/opening-balance")
    @SaCheckPermission("finance:write")
    public Result<?> saveOpeningBalance(@RequestBody List<Map<String, Object>> items) {
        return Result.ok(service.saveOpeningBalance(items));
    }

    @GetMapping("/mapping")
    @SaCheckPermission("finance:read")
    public Result<?> mappings() { return Result.ok(service.mappings()); }

    /** 保存单条业务映射（subjectCode 传空 = 清除映射） */
    @PutMapping("/mapping")
    @SaCheckPermission("finance:write")
    public Result<?> saveMapping(@RequestBody Map<String, String> body) {
        service.saveMapping(body.get("mapKey"), body.get("subjectCode"));
        return Result.ok("已保存");
    }
}
