package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.Warehouse;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import com.pengyuan.pims.repository.InventoryMovementRepository;
import com.pengyuan.pims.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WarehouseService {

    private final WarehouseRepository repo;
    private final InventoryLedgerRepository ledgerRepo;
    private final InventoryMovementRepository movementRepo;
    private final IsolatedZoneService isolatedZoneService;
    public WarehouseService(WarehouseRepository repo, InventoryLedgerRepository ledgerRepo,
                            InventoryMovementRepository movementRepo, IsolatedZoneService isolatedZoneService) {
        this.repo = repo;
        this.ledgerRepo = ledgerRepo;
        this.movementRepo = movementRepo;
        this.isolatedZoneService = isolatedZoneService;
    }

    public List<Warehouse> listAll() { return repo.findAll(); }
    public List<Warehouse> listByProcessor(String processorId) { return repo.findByProcessorId(processorId); }
    public Optional<Warehouse> getById(Long id) { return repo.findById(id); }

    @Transactional
    public Warehouse create(Warehouse w) {
        Warehouse saved = repo.save(w);
        // v5.58：新增仓库当场补建 5 个隔离分库（原需等下次重启，空窗期内过期隔离静默降级、质检不合格跨仓兜底、油尾退回报错）
        isolatedZoneService.ensureForWarehouse(saved);
        return saved;
    }

    @Transactional
    public Warehouse update(Long id, Warehouse w) {
        Warehouse exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
        // v5.30/5.35：不合格品库/油尾库为系统隔离仓，禁止修改类型（改类型会破坏自动入仓与报表隔离逻辑）
        if (isProtectedType(exist.warehouseType) && !isProtectedType(w.warehouseType)) {
            throw new IllegalArgumentException("该仓库为系统隔离仓，不允许修改仓库类型");
        }
        exist.name = w.name;
        exist.warehouseType = w.warehouseType;
        exist.processorId = w.processorId;
        exist.processorName = w.processorName;
        exist.address = w.address;
        exist.contactPerson = w.contactPerson;
        exist.contactPhone = w.contactPhone;
        exist.remark = w.remark;
        exist.updateTime = java.time.LocalDateTime.now();
        return repo.save(exist);
    }

    /** v5.30/5.35：系统隔离仓（不合格品库/油尾库），禁止改类型/删除/禁用 */
    private boolean isProtectedType(String type) {
        return "OWN_UNQUALIFIED".equals(type) || "OWN_TAILING".equals(type);
    }

    /** 该仓库是否已发生出入库（库存台账有行 或 异动表有记录） */
    private boolean hasUsage(String warehouseId) {
        if (warehouseId == null) return false;
        return !ledgerRepo.findByWarehouseId(warehouseId).isEmpty()
                || !movementRepo.findByWarehouseIdOrderByCreateTimeDesc(warehouseId).isEmpty();
    }

    /** 该仓库当前是否有库存（台账存在 qty>0 的行） */
    private boolean hasStock(String warehouseId) {
        if (warehouseId == null) return false;
        return ledgerRepo.findByWarehouseId(warehouseId).stream()
                .anyMatch(l -> l.qty != null && l.qty.compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    /**
     * 删除（软删）：一旦仓库有出入库记录则不允许删除（保留数据完整性），可改用禁用。
     */
    @Transactional
    public void delete(Long id) {
        Warehouse w = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
        // v5.30/5.35：不合格品库/油尾库为系统隔离仓，禁止删除
        if (isProtectedType(w.warehouseType)) {
            throw new IllegalArgumentException("该仓库为系统隔离仓，不允许删除");
        }
        if (hasUsage(String.valueOf(id))) {
            throw new IllegalArgumentException("该仓库已有出入库记录，不允许删除（如需停用请使用「禁用」）");
        }
        w.enabled = false;
        repo.save(w);
    }

    /** 禁用/启用仓库（库里有库存的仓库不允许禁用，须先清空库存） */
    @Transactional
    public Warehouse toggleEnabled(Long id, boolean enabled) {
        Warehouse w = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
        // v5.30/5.35：不合格品库/油尾库为系统隔离仓，禁止禁用
        if (isProtectedType(w.warehouseType) && !enabled) {
            throw new IllegalArgumentException("该仓库为系统隔离仓，不允许禁用");
        }
        if (!enabled && hasStock(String.valueOf(id))) {
            throw new IllegalArgumentException("该仓库尚有库存，不允许禁用（请先清空该仓库库存）");
        }
        w.enabled = enabled;
        w.updateTime = java.time.LocalDateTime.now();
        Warehouse saved = repo.save(w);
        // v5.58：启用仓库时幂等补建隔离分库（覆盖「建仓时为禁用状态、后来才启用」的场景）
        if (enabled) isolatedZoneService.ensureForWarehouse(saved);
        return saved;
    }
}
