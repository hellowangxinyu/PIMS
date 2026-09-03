package com.pengyuan.pims.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.Warehouse;
import com.pengyuan.pims.entity.WarehouseZone;
import com.pengyuan.pims.entity.WarehouseLocation;
import com.pengyuan.pims.service.WarehouseService;
import com.pengyuan.pims.service.WarehouseZoneService;
import com.pengyuan.pims.service.WarehouseLocationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse")
public class WarehouseController {

    private final WarehouseService service;
    private final WarehouseZoneService zoneService;
    private final WarehouseLocationService locationService;

    public WarehouseController(WarehouseService service,
                               WarehouseZoneService zoneService,
                               WarehouseLocationService locationService) {
        this.service = service;
        this.zoneService = zoneService;
        this.locationService = locationService;
    }

    // ==================== 仓库 ====================

    @GetMapping
    @SaCheckPermission(value = "warehouse:read")
    public Result<List<Warehouse>> list(@RequestParam(required = false) String processorId) {
        if (processorId != null && !processorId.isBlank()) return Result.ok(service.listByProcessor(processorId));
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = "warehouse:read")
    public Result<?> get(@PathVariable Long id) {
        return service.getById(id).map(Result::ok).orElse(Result.fail(500, "仓库不存在"));
    }

    @PostMapping
    @SaCheckPermission(value = "warehouse:write")
    public Result<Warehouse> create(@RequestBody Warehouse w) { return Result.ok(service.create(w)); }

    @PutMapping("/{id}")
    @SaCheckPermission(value = "warehouse:write")
    public Result<Warehouse> update(@PathVariable Long id, @RequestBody Warehouse w) {
        return Result.ok(service.update(id, w));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "warehouse:delete")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }

    /** 禁用/启用仓库（有出入库记录的仓库不允许删除，但可禁用） */
    @PutMapping("/{id}/enabled")
    @SaCheckPermission(value = "warehouse:write")
    public Result<Warehouse> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(service.toggleEnabled(id, enabled));
    }

    // ==================== 分库 ====================

    @GetMapping("/{warehouseId}/zone")
    @SaCheckPermission(value = "warehouse:read")
    public Result<List<WarehouseZone>> listZones(@PathVariable Long warehouseId) {
        return Result.ok(zoneService.listByWarehouse(warehouseId));
    }

    @PostMapping("/{warehouseId}/zone")
    @SaCheckPermission(value = "warehouse:write")
    public Result<WarehouseZone> createZone(@PathVariable Long warehouseId, @RequestBody WarehouseZone zone) {
        zone.warehouseId = warehouseId;
        return Result.ok(zoneService.create(zone));
    }

    @PutMapping("/zone/{id}")
    @SaCheckPermission(value = "warehouse:write")
    public Result<WarehouseZone> updateZone(@PathVariable Long id, @RequestBody WarehouseZone zone) {
        return Result.ok(zoneService.update(id, zone));
    }

    @DeleteMapping("/zone/{id}")
    @SaCheckPermission(value = "warehouse:write")
    public Result<Void> deleteZone(@PathVariable Long id) {
        zoneService.delete(id);
        return Result.ok();
    }

    // ==================== 库位 ====================

    @GetMapping("/{warehouseId}/locations")
    @SaCheckPermission(value = "warehouse:read")
    public Result<List<WarehouseLocation>> listLocationsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "false") boolean includeIsolated) {
        // v5.38：默认排除隔离分库（隔离区/油尾区）库位；includeIsolated=true 供隔离货管理场景（质检判定选位）
        return Result.ok(locationService.listByWarehouse(warehouseId, includeIsolated));
    }

    /** v5.38：按类型取隔离分库（不合格品库×3/油尾库×2）的库位（质检判定选位等隔离货管理场景专用）；
     *  v5.38.1 warehouseId 可选——每个一级仓都有自己的隔离分库，指定仓取该仓的（不传取第一个）；
     *  v5.38.3 type 支持 UNQUALIFIED_RAW/UNQUALIFIED_SEMI/UNQUALIFIED_FIN/TAILING/TAILING_FC（传 UNQUALIFIED 兼容映射为原材料库） */
    @GetMapping("/isolated-locations")
    @SaCheckPermission(value = "warehouse:read")
    public Result<List<WarehouseLocation>> isolatedLocations(@RequestParam String type,
                                                             @RequestParam(required = false) Long warehouseId) {
        final String zType = "UNQUALIFIED".equals(type) ? "UNQUALIFIED_RAW" : type;
        var zones = zoneService.listAll().stream()
                .filter(z -> zType.equals(z.zoneType) && Boolean.TRUE.equals(z.enabled))
                .toList();
        var zone = zones.stream()
                .filter(z -> warehouseId == null || warehouseId.equals(z.warehouseId))
                .findFirst().orElse(zones.isEmpty() ? null : zones.get(0));
        return Result.ok(zone == null ? List.of() : locationService.listByZone(zone.id));
    }

    @GetMapping("/zone/{zoneId}/location")
    @SaCheckPermission(value = "warehouse:read")
    public Result<List<WarehouseLocation>> listLocations(@PathVariable Long zoneId) {
        return Result.ok(locationService.listByZone(zoneId));
    }

    @PostMapping("/zone/{zoneId}/location")
    @SaCheckPermission(value = "warehouse:write")
    public Result<WarehouseLocation> createLocation(@PathVariable Long zoneId, @RequestBody WarehouseLocation loc) {
        loc.zoneId = zoneId;
        return Result.ok(locationService.create(loc));
    }

    @PutMapping("/location/{id}")
    @SaCheckPermission(value = "warehouse:write")
    public Result<WarehouseLocation> updateLocation(@PathVariable Long id, @RequestBody WarehouseLocation loc) {
        return Result.ok(locationService.update(id, loc));
    }

    @DeleteMapping("/location/{id}")
    @SaCheckPermission(value = "warehouse:write")
    public Result<Void> deleteLocation(@PathVariable Long id) {
        locationService.delete(id);
        return Result.ok();
    }
}
