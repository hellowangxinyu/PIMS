package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.StockCheck;
import com.pengyuan.pims.service.StockCheckService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stock-check")
public class StockCheckController {
    private final com.pengyuan.pims.service.UserService userService;
    // v6.1.7：operator 参数已删除（原可被客户端伪造审计人），一律登录态取

    private final StockCheckService service;
    public StockCheckController(StockCheckService service, com.pengyuan.pims.service.UserService userService) { this.service = service;         this.userService = userService;
    }

    /** 查询全部盘库单据（可选按类型筛选） */
    @GetMapping
    @SaCheckPermission(value = "inventory:read")
    public Result<List<StockCheck>> list(@RequestParam(required = false) String docType) {
        if (docType != null && !docType.isBlank()) {
            return Result.ok(service.queryByType(docType));
        }
        return Result.ok(service.queryAll());
    }

    /** 盘盈 */
    @PostMapping("/gain")
    @SaCheckPermission(value = "inventory:write")
    public Result<StockCheck> gain(@RequestParam String materialCode,
                                   @RequestParam(required = false) String materialName,
                                   @RequestParam(required = false) String batchNo,
                                   @RequestParam String warehouseId,
                                   @RequestParam(required = false) String locationId,
                                   @RequestParam double actualQty,
                                                                      @RequestParam(required = false) String remark) {
        return Result.ok(service.stockGain(materialCode, materialName, batchNo,
                warehouseId, locationId,
                BigDecimal.valueOf(actualQty),
                userService.currentOperatorName(), remark));
    }

    /** 盘亏 */
    @PostMapping("/loss")
    @SaCheckPermission(value = "inventory:write")
    public Result<StockCheck> loss(@RequestParam String materialCode,
                                   @RequestParam(required = false) String materialName,
                                   @RequestParam(required = false) String batchNo,
                                   @RequestParam String warehouseId,
                                   @RequestParam(required = false) String locationId,
                                   @RequestParam double actualQty,
                                                                      @RequestParam(required = false) String remark) {
        return Result.ok(service.stockLoss(materialCode, materialName, batchNo,
                warehouseId, locationId,
                BigDecimal.valueOf(actualQty),
                userService.currentOperatorName(), remark));
    }

    /** 库位调整（toWarehouseId 为空=同仓内调库位；非空=跨仓库调整） */
    @PostMapping("/location-adjust")
    @SaCheckPermission(value = "inventory:write")
    public Result<StockCheck> locationAdjust(@RequestParam String materialCode,
                                             @RequestParam(required = false) String materialName,
                                             @RequestParam(required = false) String batchNo,
                                             @RequestParam String warehouseId,
                                             @RequestParam(required = false) String toWarehouseId,
                                             @RequestParam(required = false) String fromLocationId,
                                             @RequestParam String toLocationId,
                                             @RequestParam double qty,
                                                                                          @RequestParam(required = false) String remark) {
        return Result.ok(service.locationAdjust(materialCode, materialName, batchNo,
                warehouseId, toWarehouseId, fromLocationId, toLocationId,
                BigDecimal.valueOf(qty), userService.currentOperatorName(), remark));
    }
}
