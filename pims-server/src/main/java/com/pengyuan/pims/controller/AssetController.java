package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.ExcelUtil;
import com.pengyuan.pims.common.FieldFilter;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Asset;
import com.pengyuan.pims.entity.AssetDepreciation;
import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.service.AssetService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 固定资产（v5.62）：卡片 + 平均年限法月度折旧 + 计提凭证 */
@RestController
@RequestMapping("/api/asset")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) { this.service = service; }

    @GetMapping
    @SaCheckPermission("finance:read")
    public Result<?> list() {
        List<Map<String, Object>> list = service.list();
        if (!FieldFilter.hasAmountPerm("asset")) {
            list.forEach(row -> List.of("originalValue", "monthlyDep", "accumulatedDep", "netValue")
                    .forEach(row::remove));
        }
        return Result.ok(list);
    }

    @PostMapping
    @SaCheckPermission("finance:write")
    public Result<Asset> create(@RequestBody Asset a) {
        return Result.ok(service.create(a));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("finance:write")
    public Result<Asset> update(@PathVariable Long id, @RequestBody Asset a) {
        return Result.ok(service.update(id, a));
    }

    /** 报废（报废当月仍计提折旧，次月停） */
    @PutMapping("/{id}/scrap")
    @SaCheckPermission("finance:write")
    public Result<Asset> scrap(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        LocalDate date = body == null || body.get("scrapDate") == null ? null : LocalDate.parse(body.get("scrapDate"));
        return Result.ok(service.scrap(id, date));
    }

    /** 期间折旧记录 */
    @GetMapping("/depreciation")
    @SaCheckPermission("finance:read")
    public Result<List<AssetDepreciation>> depRecords(@RequestParam String period) {
        return Result.ok(service.depRecords(period));
    }

    /** 单卡折旧历史 */
    @GetMapping("/{id}/history")
    @SaCheckPermission("finance:read")
    public Result<List<AssetDepreciation>> history(@PathVariable Long id) {
        return Result.ok(service.history(id));
    }

    /** 月度计提：逐卡算折旧 + 生成计提凭证（DRAFT） */
    @PostMapping("/depreciate")
    @SaCheckPermission("finance:write")
    public Result<Map<String, Object>> depreciate(@RequestBody Map<String, String> body) {
        return Result.ok(service.depreciate(body.get("period")));
    }

    @GetMapping("/export")
    @SaCheckPermission("finance:read")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        boolean amt = FieldFilter.hasAmountPerm("asset");
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Map<String, Object> a : service.list()) {
            rows.add(new Object[]{ a.get("docNo"), a.get("name"), categoryLabel(String.valueOf(a.get("category"))),
                    a.get("purchaseDate"), amt ? a.get("originalValue") : "", a.get("usefulLifeMonths"),
                    a.get("residualRate"), amt ? a.get("monthlyDep") : "", amt ? a.get("accumulatedDep") : "",
                    amt ? a.get("netValue") : "", a.get("expenseSubject"), "IN_USE".equals(a.get("status")) ? "在用" : "已报废",
                    a.get("location"), a.get("keeper"), a.get("remark") });
        }
        ExcelUtil.export(response, "固定资产-" + LocalDate.now(), "固定资产",
                new String[]{"编号","名称","类别","购入日期","原值","年限(月)","残值率%","月折旧","累计折旧","净值","折旧科目","状态","存放位置","责任人","备注"}, rows);
    }

    private String categoryLabel(String c) {
        return switch (c) {
            case "BUILDING" -> "房屋建筑";
            case "MACHINE" -> "机器设备";
            case "VEHICLE" -> "运输工具";
            case "ELECTRONIC" -> "电子设备";
            default -> "其他";
        };
    }
}
