package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 原料采购 + 成品采购 + 到货管理
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final RawMaterialPurchaseRepository rawRepo;
    private final FinishedProductPurchaseRepository finishedRepo;
    private final PurchaseArrivalRepository arrivalRepo;
    private final SupplierRepository supplierRepo;
    private final InventoryService inventoryService;
    private final QualityInspectionService qcService;
    private final FinanceService financeService;
    private final AccountsPayableRepository apRepo;
    private final MaterialRepository materialRepo;
    // v5.2：到货明细库位名称反查
    private final WarehouseLocationRepository locationRepo;
    private final WarehouseZoneRepository zoneRepo;
    // v5.24：全局写锁（合同号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;   // v6.5 B3
    private final MaterialService materialService;
    // v5.60：制单人写入
    private final UserService userService;
    private final com.pengyuan.pims.repository.QualityInspectionRepository qcRepo;   // v6.1 反审核删质检单
    // v5.71.6：审核校验收货仓库档案
    private final WarehouseRepository warehouseRepo;

    public PurchaseService(RawMaterialPurchaseRepository rawRepo,
                           FinishedProductPurchaseRepository finishedRepo,
                           PurchaseArrivalRepository arrivalRepo,
                           SupplierRepository supplierRepo,
                           InventoryService inventoryService,
                           QualityInspectionService qcService,
                           FinanceService financeService,
                           AccountsPayableRepository apRepo,
                           MaterialRepository materialRepo,
                           WarehouseLocationRepository locationRepo,
                           WarehouseZoneRepository zoneRepo,
                           WriteQueue writeQueue,
                           MaterialService materialService,
                           UserService userService,
                           WarehouseRepository warehouseRepo, com.pengyuan.pims.repository.QualityInspectionRepository qcRepo,
                       org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.rawRepo = rawRepo;
        this.finishedRepo = finishedRepo;
        this.arrivalRepo = arrivalRepo;
        this.supplierRepo = supplierRepo;
        this.inventoryService = inventoryService;
        this.qcService = qcService;
        this.financeService = financeService;
        this.apRepo = apRepo;
        this.materialRepo = materialRepo;
        this.locationRepo = locationRepo;
        this.zoneRepo = zoneRepo;
        this.writeQueue = writeQueue;
        this.qcRepo = qcRepo;
        this.materialService = materialService;
        this.userService = userService;
        this.warehouseRepo = warehouseRepo;
        this.jdbc = jdbc;
    }

    // ==================== 原料采购 ====================

    public List<RawMaterialPurchase> listRaw() { return rawRepo.findAll(); }
    public Optional<RawMaterialPurchase> getRaw(Long id) { return rawRepo.findById(id); }

    /**
     * 原料采购分页查询（支持状态过滤 + 多条件 + 按采购日期倒序）
     * @param status       状态过滤：null=全部，DRAFT=开立，APPROVED=已审核
     * @param orderNo      合同号模糊匹配（可空）
     * @param supplierName 供应商模糊匹配（可空）
     * @param materialCode 物料编码模糊匹配（可空）
     * @param materialName 物料品名模糊匹配（可空）
     * @param startDate    采购开始日期（可空，含）
     * @param endDate      采购结束日期（可空，含）
     * @param page         页码（0 起）
     * @param size         每页条数
     */
    public Page<RawMaterialPurchase> searchRaw(String status, String orderNo, String supplierName,
                                               String materialCode, String materialName,
                                               LocalDate startDate, LocalDate endDate,
                                               int page, int size) {
        Specification<RawMaterialPurchase> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (orderNo != null && !orderNo.isBlank()) ps.add(cb.like(root.get("orderNo"), "%" + orderNo + "%"));
            if (supplierName != null && !supplierName.isBlank()) ps.add(cb.like(root.get("supplierName"), "%" + supplierName + "%"));
            if (materialCode != null && !materialCode.isBlank()) ps.add(cb.like(root.get("materialCode"), "%" + materialCode + "%"));
            if (materialName != null && !materialName.isBlank()) ps.add(cb.like(root.get("materialName"), "%" + materialName + "%"));
            if (startDate != null) ps.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), startDate));
            if (endDate != null) ps.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), endDate));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return rawRepo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "purchaseDate")));
    }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public RawMaterialPurchase createRaw(RawMaterialPurchase rp) {
        // v5.70.1 防呆：采购核心字段必填（不允许空物料空数量的采购单）
        if (rp.supplierId == null && (rp.supplierName == null || rp.supplierName.isBlank()))
            throw new IllegalArgumentException("供应商不能为空");
        if (rp.materialCode == null || rp.materialCode.isBlank())
            throw new IllegalArgumentException("物料编码不能为空");
        if (rp.materialName == null || rp.materialName.isBlank())
            throw new IllegalArgumentException("物料名称不能为空");
        if (rp.qty == null || rp.qty.doubleValue() <= 0)
            throw new IllegalArgumentException("数量必须大于 0");
        materialService.assertUsable(rp.materialCode, "采购");  // v5.43.1 禁用物料不可购买
        if (rp.createdBy == null || rp.createdBy.isBlank()) rp.createdBy = userService.currentOperatorName();  // v5.60 制单人（batch 复用此方法一并覆盖）
        // v5.24：合同号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // 合同号：供应商简称首字母 + 日期 + 序号
            if (rp.orderNo == null || rp.orderNo.isBlank()) {
                rp.orderNo = generateContractNo(rp.supplierId, "RAW");
            }
            if (rp.purchaseDate == null) rp.purchaseDate = LocalDate.now();
    
            // 赠送：单价清零
            if (rp.isFree != null && rp.isFree) {
                rp.unitPrice = BigDecimal.ZERO;
            }
    
            // 自动计算金额
            calcAmount(rp);
    
            // 增幅/增值
            if (rp.lastUnitPrice != null && rp.lastUnitPrice.compareTo(BigDecimal.ZERO) > 0
                    && rp.unitPrice != null) {
                rp.increaseAmount = rp.unitPrice.subtract(rp.lastUnitPrice);
                rp.increaseRate = rp.increaseAmount.divide(rp.lastUnitPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
    
            // 默认开立状态
            return rawRepo.save(rp);
        });
    }

    @Transactional
    public RawMaterialPurchase updateRaw(Long id, RawMaterialPurchase rp) {
        RawMaterialPurchase exist = rawRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("原料采购单不存在"));
        if (!"DRAFT".equals(exist.status))
            throw new IllegalArgumentException("只有开立状态的单据可编辑");

        exist.purchaseDate = rp.purchaseDate;
        exist.supplierId = rp.supplierId;
        exist.supplierName = rp.supplierName;
        exist.warehouseId = rp.warehouseId;   // v5.71.3 收货仓库可编辑
        exist.category = rp.category;
        exist.subCategory = rp.subCategory;
        exist.materialCode = rp.materialCode;
        exist.materialName = rp.materialName;
        exist.brand = rp.brand;
        exist.qty = rp.qty;
        exist.unitPrice = rp.unitPrice;
        exist.lastUnitPrice = rp.lastUnitPrice;
        exist.isFree = rp.isFree;
        exist.remark = rp.remark;
        exist.updateTime = java.time.LocalDateTime.now();
        calcAmount(exist);
        return rawRepo.save(exist);
    }

    /** 审核原料采购：开立→已审核 */
    /** v5.71.6 审核防呆：采购单必须已选收货仓库且仓库档案已维护收货地址/联系人（v6.1.6 清死注解：private 方法 @Transactional 无效） */
    private void assertReceiveInfo(String orderNo, String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank())
            throw new IllegalArgumentException("采购单 " + orderNo + " 未选择收货仓库，请编辑补选后再审核");
        warehouseRepo.findById(Long.valueOf(warehouseId.trim())).ifPresentOrElse(w -> {
            if (w.address == null || w.address.isBlank())
                throw new IllegalArgumentException("收货仓库「" + w.name + "」未维护收货地址，请先在 基础数据→仓库 维护后再审核");
            if (w.contactPerson == null || w.contactPerson.isBlank())
                throw new IllegalArgumentException("收货仓库「" + w.name + "」未维护收货联系人，请先在 基础数据→仓库 维护后再审核");
        }, () -> {
            throw new IllegalArgumentException("收货仓库不存在（" + warehouseId + "），请重新选择后再审核");
        });
    }

    public RawMaterialPurchase auditRaw(Long id) {
        RawMaterialPurchase rp = rawRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("采购单不存在"));
        if (!"DRAFT".equals(rp.status))
            throw new IllegalArgumentException("只有开立状态的单据可审核");
        // v5.70.1 防呆：审核前校验完整性
        if (rp.materialCode == null || rp.materialCode.isBlank())
            throw new IllegalArgumentException("采购单 " + rp.orderNo + " 未填写物料，不能审核");
        if (rp.qty == null || rp.qty.doubleValue() <= 0)
            throw new IllegalArgumentException("采购单 " + rp.orderNo + " 数量无效，不能审核");
        // v5.71.6：收货仓库与收货地址必须齐备（打印/收货依据）
        assertReceiveInfo(rp.orderNo, rp.warehouseId);
        rp.status = "APPROVED";
        rp.updateTime = java.time.LocalDateTime.now();
        return rawRepo.save(rp);
    }

    /** 反审核原料采购：已审核→开立 */
    @Transactional
    public RawMaterialPurchase reverseAuditRaw(Long id) {
        RawMaterialPurchase rp = rawRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("采购单不存在"));
        if (!"APPROVED".equals(rp.status))
            throw new IllegalArgumentException("只有已审核状态的单据可反审核");
        rp.status = "DRAFT";
        rp.updateTime = java.time.LocalDateTime.now();
        return rawRepo.save(rp);
    }

    /** 品名输入后查上次采购记录自动回填 */
    public RawMaterialPurchase getLastPurchase(String materialName) {
        return rawRepo.findTopByMaterialNameOrderByCreateTimeDesc(materialName).orElse(null);
    }

    /**
     * v5.0：原料采购批量创建——一张单据（同一合同号）一个供应商多个物料。
     * 生成一个 orderNo，逐行走 createRaw 相同校验/计算逻辑（赠送清单价、金额、增幅），同一事务原子提交。
     * @param body {supplierId, supplierName, purchaseDate?, remark?, items:[{materialCode, materialName, category, subCategory, brand, qty, unitPrice, isFree}]}
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public List<RawMaterialPurchase> batchCreateRaw(Map<String, Object> body) {
        // v5.24：合同号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (body.get("supplierId") == null) {
                throw new IllegalArgumentException("请选择供应商");
            }
            Long supplierId = Long.valueOf(body.get("supplierId").toString());
            String supplierName = (String) body.get("supplierName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("采购明细不能为空，请至少添加一个物料");
            }
            // 同一单据共享一个合同号
            String orderNo = generateContractNo(supplierId, "RAW");
            LocalDate purchaseDate = body.get("purchaseDate") != null
                    ? LocalDate.parse(body.get("purchaseDate").toString()) : LocalDate.now();
            String remark = (String) body.get("remark");
            // v5.71.3 收货仓库：下单时选定，打印请购单按此带出收货地址/联系人
            String warehouseId = body.get("warehouseId") != null ? body.get("warehouseId").toString() : null;
            // v5.75 税率%（默认13，可按单据修改）
            java.math.BigDecimal taxRate = body.get("taxRate") != null
                    ? new java.math.BigDecimal(body.get("taxRate").toString()) : java.math.BigDecimal.valueOf(13);
    
            List<RawMaterialPurchase> created = new ArrayList<>();
            for (Map<String, Object> it : items) {
                RawMaterialPurchase rp = new RawMaterialPurchase();
                rp.orderNo = orderNo;
                rp.supplierId = supplierId;
                rp.supplierName = supplierName;
                rp.warehouseId = warehouseId;
                rp.taxRate = taxRate;
                rp.purchaseDate = purchaseDate;
                rp.category = (String) it.get("category");
                rp.subCategory = (String) it.get("subCategory");
                rp.materialCode = (String) it.get("materialCode");
                materialService.assertUsable(rp.materialCode, "采购");  // v5.43.1 禁用物料不可购买
                rp.materialName = (String) it.get("materialName");
                rp.brand = (String) it.get("brand");
                rp.qty = new BigDecimal(it.get("qty").toString());
                rp.unitPrice = it.get("unitPrice") != null
                        ? new BigDecimal(it.get("unitPrice").toString()) : BigDecimal.ZERO;
                rp.isFree = Boolean.TRUE.equals(it.get("isFree"));
                rp.remark = it.get("remark") != null ? it.get("remark").toString() : remark;
                created.add(createRaw(rp));
            }
            log.info("原料采购批量创建: 合同号={} 供应商={} 共{}项", orderNo, supplierName, items.size());
            return created;
        });
    }

    /** 上传合同PDF */
    @Transactional
    public void uploadContract(Long id, MultipartFile file) {
        RawMaterialPurchase rp = rawRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("采购单不存在"));
        try {
            String supplierCode = getSupplierShortCode(rp.supplierId);
            Path dir = Paths.get("data", "contracts", supplierCode);
            Files.createDirectories(dir);
            String fileName = rp.orderNo + ".pdf";
            Path target = dir.resolve(fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            rp.filePath = target.toString().replace("\\", "/");
            rawRepo.save(rp);
            log.info("合同已上传: {}", rp.filePath);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    // ==================== 成品采购 ====================

    public List<FinishedProductPurchase> listFinished() { return finishedRepo.findAll(); }
    public Optional<FinishedProductPurchase> getFinished(Long id) { return finishedRepo.findById(id); }

    /**
     * 成品采购分页查询（支持状态过滤 + 多条件 + 按采购日期倒序）
     * 参数说明同 searchRaw
     */
    public Page<FinishedProductPurchase> searchFinished(String status, String orderNo, String supplierName,
                                                       String materialCode, String materialName,
                                                       LocalDate startDate, LocalDate endDate,
                                                       int page, int size) {
        Specification<FinishedProductPurchase> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (orderNo != null && !orderNo.isBlank()) ps.add(cb.like(root.get("orderNo"), "%" + orderNo + "%"));
            if (supplierName != null && !supplierName.isBlank()) ps.add(cb.like(root.get("supplierName"), "%" + supplierName + "%"));
            if (materialCode != null && !materialCode.isBlank()) ps.add(cb.like(root.get("materialCode"), "%" + materialCode + "%"));
            if (materialName != null && !materialName.isBlank()) ps.add(cb.like(root.get("materialName"), "%" + materialName + "%"));
            if (startDate != null) ps.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), startDate));
            if (endDate != null) ps.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), endDate));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return finishedRepo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "purchaseDate")));
    }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public FinishedProductPurchase createFinished(FinishedProductPurchase fp) {
        // v5.70.1 防呆：成品采购核心字段必填
        if (fp.supplierId == null && (fp.supplierName == null || fp.supplierName.isBlank()))
            throw new IllegalArgumentException("供应商不能为空");
        if (fp.materialCode == null || fp.materialCode.isBlank())
            throw new IllegalArgumentException("物料编码不能为空");
        if (fp.qty == null || fp.qty.doubleValue() <= 0)
            throw new IllegalArgumentException("数量必须大于 0");
        materialService.assertUsable(fp.materialCode, "采购");  // v5.43.1 禁用物料不可购买
        if (fp.createdBy == null || fp.createdBy.isBlank()) fp.createdBy = userService.currentOperatorName();  // v5.60 制单人（batch 复用此方法一并覆盖）
        // v5.24：合同号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (fp.orderNo == null || fp.orderNo.isBlank()) {
                fp.orderNo = generateContractNo(fp.supplierId, "FG");
            }
            if (fp.purchaseDate == null) fp.purchaseDate = LocalDate.now();
            if (fp.isFree != null && fp.isFree) fp.unitPrice = BigDecimal.ZERO;
            if (fp.qty != null && fp.unitPrice != null) {
                fp.totalAmount = fp.qty.multiply(fp.unitPrice);
            }
            // v4.5：校验供应商与物料品牌归属一致
            if (fp.materialCode != null && !fp.materialCode.isBlank()) {
                Optional<Material> matOpt = materialRepo.findByCode(fp.materialCode);
                if (matOpt.isPresent()) {
                    Material mat = matOpt.get();
                    // 物料必须为成品
                    if (!"C".equals(mat.category)) {
                        throw new IllegalArgumentException("成品采购仅可选择成品物料（大类C），当前物料大类为 " + mat.category);
                    }
                    // 校验品牌归属：供应商名称必须等于物料的 brandOwner
                    if (mat.brandOwner != null && !mat.brandOwner.isBlank()
                            && fp.supplierName != null && !fp.supplierName.isBlank()
                            && !mat.brandOwner.equals(fp.supplierName)) {
                        throw new IllegalArgumentException(
                                "供应商与物料品牌归属不一致：物料「" + mat.name + "」品牌归属为「"
                                        + mat.brandOwner + "」，当前供应商为「" + fp.supplierName + "」");
                    }
                }
            }
            // 默认开立状态
            return finishedRepo.save(fp);
        });
    }

    @Transactional
    public FinishedProductPurchase updateFinished(Long id, FinishedProductPurchase fp) {
        FinishedProductPurchase exist = finishedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("成品采购单不存在"));
        if (!"DRAFT".equals(exist.status))
            throw new IllegalArgumentException("只有开立状态的单据可编辑");
        exist.purchaseDate = fp.purchaseDate;
        exist.supplierId = fp.supplierId;
        exist.supplierName = fp.supplierName;
        exist.warehouseId = fp.warehouseId;   // v5.71.3 收货仓库可编辑
        exist.materialCode = fp.materialCode;
        exist.materialName = fp.materialName;
        exist.brand = fp.brand;
        exist.qty = fp.qty;
        exist.unitPrice = fp.unitPrice;
        exist.isFree = fp.isFree;
        if (fp.isFree != null && fp.isFree) exist.unitPrice = BigDecimal.ZERO;
        exist.remark = fp.remark;
        exist.updateTime = java.time.LocalDateTime.now();
        if (exist.qty != null && exist.unitPrice != null)
            exist.totalAmount = exist.qty.multiply(exist.unitPrice);
        // v4.5：校验供应商与物料品牌归属一致
        if (exist.materialCode != null && !exist.materialCode.isBlank()) {
            Optional<Material> matOpt = materialRepo.findByCode(exist.materialCode);
            if (matOpt.isPresent()) {
                Material mat = matOpt.get();
                if (!"C".equals(mat.category)) {
                    throw new IllegalArgumentException("成品采购仅可选择成品物料（大类C），当前物料大类为 " + mat.category);
                }
                if (mat.brandOwner != null && !mat.brandOwner.isBlank()
                        && exist.supplierName != null && !exist.supplierName.isBlank()
                        && !mat.brandOwner.equals(exist.supplierName)) {
                    throw new IllegalArgumentException(
                            "供应商与物料品牌归属不一致：物料「" + mat.name + "」品牌归属为「"
                                    + mat.brandOwner + "」，当前供应商为「" + exist.supplierName + "」");
                }
            }
        }
        return finishedRepo.save(exist);
    }

    /**
     * v5.0：成品采购批量创建——一张单据（同一合同号）一个供应商多个成品。
     * 逐行走 createFinished 校验（物料必须为成品、品牌归属一致），同一事务原子提交。
     * @param body {supplierId, supplierName, purchaseDate?, remark?, items:[{materialCode, materialName, brand, qty, unitPrice, isFree}]}
     */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public List<FinishedProductPurchase> batchCreateFinished(Map<String, Object> body) {
        // v5.24：合同号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (body.get("supplierId") == null) {
                throw new IllegalArgumentException("请选择供应商");
            }
            Long supplierId = Long.valueOf(body.get("supplierId").toString());
            String supplierName = (String) body.get("supplierName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("采购明细不能为空，请至少添加一个物料");
            }
            String orderNo = generateContractNo(supplierId, "FG");
            LocalDate purchaseDate = body.get("purchaseDate") != null
                    ? LocalDate.parse(body.get("purchaseDate").toString()) : LocalDate.now();
            
            // v5.71.3 收货仓库：下单时选定
            String finWarehouseId = body.get("warehouseId") != null ? body.get("warehouseId").toString() : null;
            java.math.BigDecimal finTaxRate = body.get("taxRate") != null
                    ? new java.math.BigDecimal(body.get("taxRate").toString()) : java.math.BigDecimal.valueOf(13);String remark = (String) body.get("remark");
    
            List<FinishedProductPurchase> created = new ArrayList<>();
            for (Map<String, Object> it : items) {
                FinishedProductPurchase fp = new FinishedProductPurchase();
                fp.orderNo = orderNo;
                fp.supplierId = supplierId;
                fp.supplierName = supplierName;
                fp.warehouseId = finWarehouseId;
                fp.taxRate = finTaxRate;
                fp.purchaseDate = purchaseDate;
                fp.materialCode = (String) it.get("materialCode");
                fp.materialName = (String) it.get("materialName");
                fp.brand = (String) it.get("brand");
                fp.qty = new BigDecimal(it.get("qty").toString());
                fp.unitPrice = it.get("unitPrice") != null
                        ? new BigDecimal(it.get("unitPrice").toString()) : BigDecimal.ZERO;
                fp.isFree = Boolean.TRUE.equals(it.get("isFree"));
                fp.remark = it.get("remark") != null ? it.get("remark").toString() : remark;
                created.add(createFinished(fp)); // 内含成品物料与品牌归属校验
            }
            log.info("成品采购批量创建: 合同号={} 供应商={} 共{}项", orderNo, supplierName, items.size());
            return created;
        });
    }

    @Transactional
    public FinishedProductPurchase auditFinished(Long id) {
        FinishedProductPurchase fp = finishedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("成品采购单不存在"));
        if (!"DRAFT".equals(fp.status))
            throw new IllegalArgumentException("只有开立状态的单据可审核");
        // v5.70.1 防呆：审核前校验完整性
        if (fp.materialCode == null || fp.materialCode.isBlank())
            throw new IllegalArgumentException("采购单 " + fp.orderNo + " 未填写物料，不能审核");
        if (fp.qty == null || fp.qty.doubleValue() <= 0)
            throw new IllegalArgumentException("采购单 " + fp.orderNo + " 数量无效，不能审核");
        // v5.71.6：收货仓库与收货地址必须齐备（打印/收货依据）
        assertReceiveInfo(fp.orderNo, fp.warehouseId);
        fp.status = "APPROVED";
        fp.updateTime = java.time.LocalDateTime.now();
        return finishedRepo.save(fp);
    }

    @Transactional
    public FinishedProductPurchase reverseAuditFinished(Long id) {
        FinishedProductPurchase fp = finishedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("成品采购单不存在"));
        if (!"APPROVED".equals(fp.status))
            throw new IllegalArgumentException("只有已审核状态的单据可反审核");
        fp.status = "DRAFT";
        fp.updateTime = java.time.LocalDateTime.now();
        return finishedRepo.save(fp);
    }

    // ==================== 到货管理 ====================

    public List<PurchaseArrival> listArrivals() { return arrivalRepo.findAll(); }
    public List<PurchaseArrival> listArrivalsByType(String type) { return arrivalRepo.findByTypeOrderByCreateTimeDesc(type); }

    /** v5.9：到货列表关键字分页搜索（带类型时关键字同样生效） */
    public org.springframework.data.domain.Page<PurchaseArrival> searchArrivals(String keyword, String type, org.springframework.data.domain.Pageable pageable) {
        String kw = keyword == null ? "" : keyword.trim();
        org.springframework.data.domain.Page<PurchaseArrival> page = (type != null && !type.isBlank())
                ? arrivalRepo.searchByTypeAndKeyword(type, kw, pageable)
                : arrivalRepo.searchByKeyword(kw, pageable);
        // v5.27：到货明细补合格入库批号（打印标签用）；v5.32：到货时已录批号的不再覆盖
        for (PurchaseArrival a : page.getContent()) {
            if (a.batchNo == null || a.batchNo.isBlank()) {
                a.batchNo = qcService.resolveBatchNo(a.refOrderNo, a.materialCode, a.qty);
            }
        }
        return page;
    }

    /**
     * v5.11 修复：工作台「最新采购订单」改查新采购表（raw_material_purchase + finished_product_purchase 合并）。
     * 此前 Dashboard 注入旧版 PurchaseOrderService 查询已废弃的 purchase_order 表（0 行），永远无数据。
     */
    public List<java.util.Map<String, Object>> listRecent(int limit) {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (var rp : rawRepo.findAllByOrderByCreateTimeDesc()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("orderNo", rp.orderNo);
            m.put("supplierId", rp.supplierId);
            m.put("supplierName", rp.supplierName);
            m.put("materialName", rp.materialName);
            m.put("totalAmount", rp.totalAmount);
            m.put("status", rp.status);
            m.put("createTime", rp.createTime);
            rows.add(m);
        }
        for (var fp : finishedRepo.findAllByOrderByCreateTimeDesc()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("orderNo", fp.orderNo);
            m.put("supplierId", fp.supplierId);
            m.put("supplierName", fp.supplierName);
            m.put("materialName", fp.materialName);
            m.put("totalAmount", fp.totalAmount);
            m.put("status", fp.status);
            m.put("createTime", fp.createTime);
            rows.add(m);
        }
        rows.sort((a, b) -> {
            var ta = (java.time.LocalDateTime) a.get("createTime");
            var tb = (java.time.LocalDateTime) b.get("createTime");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        if (rows.size() > limit) rows = rows.subList(0, limit);
        return rows;
    }

    /** 获取未完全到货的订单列表 */
    public List<?> getIncompleteOrders(String type) {
        if ("RAW".equals(type)) {
            return rawRepo.findIncompleteOrders();
        } else {
            return finishedRepo.findIncompleteOrders();
        }
    }

    /** 录入到货：更新已到货数量，创建来料质检单（待检），QC合格后才入库；v5.2 同时落库到货明细；
     *  v5.35：批号不再手工干预——到货时立即自动生成（B+日期+序号，全局唯一），质检单带上、判定合格入库时台账沿用 */
    @Transactional
    public void recordArrival(Long id, String type, BigDecimal arrivalQty, String warehouseId, String locationId,
                              String batchNo, String operator) {
        // v6.1.6：到货量校验（原无 >0 校验、不拦超收——负数到货直减 receivedQty、超收扭曲 RECEIVED 判定）
        if (arrivalQty == null || arrivalQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("到货数量必须大于 0");
        }
        String arrivalBatch = (batchNo == null || batchNo.isBlank()) ? inventoryService.nextBatchNo() : batchNo.trim();
        if ("RAW".equals(type)) {
            RawMaterialPurchase rp = rawRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("原料采购单不存在"));
            if (!"APPROVED".equals(rp.status))
                throw new IllegalArgumentException("只有已审核状态的单据可录入到货");

            // 更新已到货数量（v6.1.6：超订单到货拦截）
            BigDecimal already = rp.receivedQty == null ? BigDecimal.ZERO : rp.receivedQty;
            if (already.add(arrivalQty).compareTo(rp.qty == null ? BigDecimal.ZERO : rp.qty) > 0) {
                throw new IllegalArgumentException(String.format(
                        "到货数量超订单：已到 %.3f + 本次 %.3f > 订量 %.3f", already, arrivalQty, rp.qty));
            }
            rp.receivedQty = (rp.receivedQty == null ? BigDecimal.ZERO : rp.receivedQty).add(arrivalQty);
            rp.warehouseId = warehouseId;

            // 如果已完全到货，自动更新状态
            if (rp.receivedQty.compareTo(rp.qty) >= 0) {
                rp.status = "RECEIVED";
            }
            rp.updateTime = java.time.LocalDateTime.now();
            rawRepo.save(rp);

            // 创建来料质检单（待检状态，QC合格后才触发入库；v5.32 批号随到货带入）
            qcService.createIncoming(rp.orderNo,
                    rp.materialCode != null ? rp.materialCode : rp.materialName,
                    rp.materialName, arrivalBatch, arrivalQty, null,
                    warehouseId, locationId,
                    rp.isFree ? BigDecimal.ZERO : rp.unitPrice, null, operator);

            // v5.2：落库到货明细
            saveArrivalDetail("RAW", rp.orderNo, rp.supplierId, rp.supplierName,
                    rp.materialCode, rp.materialName, arrivalQty, null,
                    warehouseId, locationId, operator, null, arrivalBatch);

            log.info("原料到货录入(待检): {} -> {} 库位{}, 数量: {}, 批号: {}", rp.orderNo, warehouseId, locationId, arrivalQty, arrivalBatch);
        } else {
            FinishedProductPurchase fp = finishedRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("成品采购单不存在"));
            if (!"APPROVED".equals(fp.status))
                throw new IllegalArgumentException("只有已审核状态的单据可录入到货");

            // v6.1.6：超订单到货拦截（同原料分支）
            BigDecimal alreadyF = fp.receivedQty == null ? BigDecimal.ZERO : fp.receivedQty;
            if (alreadyF.add(arrivalQty).compareTo(fp.qty == null ? BigDecimal.ZERO : fp.qty) > 0) {
                throw new IllegalArgumentException(String.format(
                        "到货数量超订单：已到 %.3f + 本次 %.3f > 订量 %.3f", alreadyF, arrivalQty, fp.qty));
            }

            fp.receivedQty = (fp.receivedQty == null ? BigDecimal.ZERO : fp.receivedQty).add(arrivalQty);
            fp.warehouseId = warehouseId;

            if (fp.receivedQty.compareTo(fp.qty) >= 0) {
                fp.status = "RECEIVED";
            }
            fp.updateTime = java.time.LocalDateTime.now();
            finishedRepo.save(fp);

            // 创建来料质检单（待检状态；v5.32 批号随到货带入）
            qcService.createIncoming(fp.orderNo,
                    fp.materialCode != null ? fp.materialCode : fp.materialName,
                    fp.materialName, arrivalBatch, arrivalQty, null,
                    warehouseId, locationId,
                    fp.isFree ? BigDecimal.ZERO : fp.unitPrice, null, operator);

            // v5.2：落库到货明细
            saveArrivalDetail("FINISHED", fp.orderNo, fp.supplierId, fp.supplierName,
                    fp.materialCode, fp.materialName, arrivalQty, null,
                    warehouseId, locationId, operator, null, arrivalBatch);

            log.info("成品到货录入(待检): {} -> {} 库位{}, 数量: {}, 批号: {}", fp.orderNo, warehouseId, locationId, arrivalQty, arrivalBatch);
        }
    }

    /** v5.2：落库一条采购到货明细（到货明细页数据源，冗余分库/库位名称便于展示）；v5.32 增批号 */
    private void saveArrivalDetail(String type, String orderNo, Long supplierId, String supplierName,
                                   String materialCode, String materialName, BigDecimal qty, String unit,
                                   String warehouseId, String locationId, String operator, String remark,
                                   String batchNo) {
        PurchaseArrival pa = new PurchaseArrival();
        pa.type = type;
        pa.refOrderNo = orderNo;
        pa.supplierId = supplierId;
        pa.supplierName = supplierName;
        pa.materialCode = materialCode;
        pa.materialName = materialName;
        pa.qty = qty;
        pa.unit = unit;
        pa.warehouseId = warehouseId;
        pa.locationId = locationId;
        // 反查分库/库位名称冗余存储
        if (locationId != null) {
            locationRepo.findById(Long.valueOf(locationId)).ifPresent(loc -> {
                pa.locationName = loc.name;
                zoneRepo.findById(loc.zoneId).ifPresent(z -> pa.zoneName = z.name);
            });
        }
        pa.arrivalDate = LocalDate.now();
        pa.operator = operator;
        pa.remark = remark;
        pa.batchNo = batchNo; // v5.32：到货批号（供应商批号/到货批号）
        pa.status = "APPROVED"; // 到货即生效
        arrivalRepo.save(pa);
        // v6.1.6：直达路径（到货录入即 APPROVED）同样立应付——原仅 auditArrival 立账，走直达的到货永不生 AP
        generateAPForArrival(pa);
    }

    /**
     * 手动结束采购订单（即使未完全到货）。
     * v6.8 短量完结正规化：① 状态守卫——RECEIVED 已到齐无需关闭、DRAFT 请删除或先审核，防手滑误关；
     * ② 短量（已到 < 订量）时 reason 必填；③ 原因与到货快照追加进 remark，事后可查"为什么 100 只到了 95"。
     */
    @Transactional
    public void closeOrder(Long id, String type, String reason) {
        if ("RAW".equals(type)) {
            RawMaterialPurchase rp = rawRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("原料采购单不存在"));
            if ("RECEIVED".equals(rp.status)) throw new IllegalArgumentException("订单已全额到货（RECEIVED），无需关闭");
            if ("DRAFT".equals(rp.status)) throw new IllegalArgumentException("草稿单请直接删除或先审核，不支持关闭");
            if ("CLOSED".equals(rp.status)) throw new IllegalArgumentException("订单已关闭");
            java.math.BigDecimal received = rp.receivedQty == null ? java.math.BigDecimal.ZERO : rp.receivedQty;
            boolean shortQty = received.compareTo(rp.qty == null ? java.math.BigDecimal.ZERO : rp.qty) < 0;
            if (shortQty && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException(String.format(
                        "短量关闭必须填写原因：到货 %s / 订量 %s，尚差 %s", received.stripTrailingZeros().toPlainString(),
                        rp.qty.stripTrailingZeros().toPlainString(),
                        rp.qty.subtract(received).stripTrailingZeros().toPlainString()));
            }
            rp.status = "CLOSED";
            if (shortQty) {
                rp.remark = (rp.remark == null || rp.remark.isBlank() ? "" : rp.remark + "；")
                        + String.format("短量关闭：到货 %s/%s，原因：%s", received.stripTrailingZeros().toPlainString(),
                        rp.qty.stripTrailingZeros().toPlainString(), reason.trim());
            }
            rp.updateTime = java.time.LocalDateTime.now();
            rawRepo.save(rp);
            log.info("原料采购单手动关闭: {} 短量={} 原因={}", rp.orderNo, shortQty, reason);
        } else {
            FinishedProductPurchase fp = finishedRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("成品采购单不存在"));
            if ("RECEIVED".equals(fp.status)) throw new IllegalArgumentException("订单已全额到货（RECEIVED），无需关闭");
            if ("DRAFT".equals(fp.status)) throw new IllegalArgumentException("草稿单请直接删除或先审核，不支持关闭");
            if ("CLOSED".equals(fp.status)) throw new IllegalArgumentException("订单已关闭");
            java.math.BigDecimal received = fp.receivedQty == null ? java.math.BigDecimal.ZERO : fp.receivedQty;
            boolean shortQty = received.compareTo(fp.qty == null ? java.math.BigDecimal.ZERO : fp.qty) < 0;
            if (shortQty && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException(String.format(
                        "短量关闭必须填写原因：到货 %s / 订量 %s，尚差 %s", received.stripTrailingZeros().toPlainString(),
                        fp.qty.stripTrailingZeros().toPlainString(),
                        fp.qty.subtract(received).stripTrailingZeros().toPlainString()));
            }
            fp.status = "CLOSED";
            if (shortQty) {
                fp.remark = (fp.remark == null || fp.remark.isBlank() ? "" : fp.remark + "；")
                        + String.format("短量关闭：到货 %s/%s，原因：%s", received.stripTrailingZeros().toPlainString(),
                        fp.qty.stripTrailingZeros().toPlainString(), reason.trim());
            }
            fp.updateTime = java.time.LocalDateTime.now();
            finishedRepo.save(fp);
            log.info("成品采购单手动关闭: {} 短量={} 原因={}", fp.orderNo, shortQty, reason);
        }
    }

    /** 创建到货记录（开立状态，不触发入库） */
    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public PurchaseArrival createArrival(PurchaseArrival pa) {
        // v5.70.1 防呆：到货核心字段必填
        if (pa.materialCode == null || pa.materialCode.isBlank())
            throw new IllegalArgumentException("到货物料编码不能为空");
        if (pa.qty == null || pa.qty.doubleValue() <= 0)
            throw new IllegalArgumentException("到货数量必须大于 0");
        // v5.70 P1 防呆：到货数量为 0 或负数拦截
        if (pa.qty == null || pa.qty.doubleValue() <= 0) {
            throw new IllegalArgumentException("到货数量必须大于 0");
        }
        // v5.73 含税单价固化：未显式传价时从采购单（合同号+物料）带出
        if (pa.unitPrice == null && pa.refOrderNo != null && pa.materialCode != null) {
            pa.unitPrice = "RAW".equals(pa.type)
                    ? rawRepo.findFirstByOrderNoAndMaterialCodeAndUnitPriceIsNotNull(pa.refOrderNo, pa.materialCode)
                        .map(rp -> rp.unitPrice).orElse(null)
                    : finishedRepo.findFirstByOrderNoAndMaterialCodeAndUnitPriceIsNotNull(pa.refOrderNo, pa.materialCode)
                        .map(fp -> fp.unitPrice).orElse(null);
        }
        // v5.75 税率带出：未显式传时从采购单带（默认13）
        if (pa.refOrderNo != null && pa.materialCode != null) {
            java.math.BigDecimal tr = "RAW".equals(pa.type)
                    ? rawRepo.findFirstByOrderNoAndMaterialCodeAndUnitPriceIsNotNull(pa.refOrderNo, pa.materialCode)
                        .map(rp -> rp.taxRate).orElse(null)
                    : finishedRepo.findFirstByOrderNoAndMaterialCodeAndUnitPriceIsNotNull(pa.refOrderNo, pa.materialCode)
                        .map(fp -> fp.taxRate).orElse(null);
            if (tr != null) pa.taxRate = tr;
        }
        // v5.71.7：到货库位不允许选隔离分库（不合格品/油尾）——隔离库只收质检不合格或过期隔离货物，
        // 误选会导致质检合格判定时被入库防线拦截（"不合格品库不接受正常入库"）
        if (pa.locationId != null && !pa.locationId.isBlank()) {
            locationRepo.findById(Long.valueOf(pa.locationId.trim())).ifPresent(loc -> {
                zoneRepo.findById(loc.zoneId).ifPresent(z -> {
                    String zt = z.zoneType == null ? "" : z.zoneType;
                    if (zt.startsWith("UNQUALIFIED") || zt.startsWith("TAILING")) {
                        throw new IllegalArgumentException("到货库位不能选隔离分库「" + z.name
                                + "」：不合格品库仅接收质检不合格/过期自动隔离的货物，请选择正常分库库位");
                    }
                });
            });
        }
        pa.arrivalDate = pa.arrivalDate != null ? pa.arrivalDate : LocalDate.now();
        // v6.1（高#12）：取号+保存锁内包事务（此前裸跑无锁，并发到货撞 ARR 号）
        return writeQueue.executeTx(() -> {
            // v5.96 到货单号：ARR-YYYYMMDD-NNNN 按天流水（手工传入优先）
            if (pa.docNo == null || pa.docNo.isBlank()) {
                String day = LocalDate.now().toString().replace("-", "");
                Integer max = arrivalRepo.findMaxArrivalSeq("ARR-" + day + "-%");
                pa.docNo = String.format("ARR-%s-%04d", day, (max == null ? 0 : max) + 1);
            }
            return arrivalRepo.save(pa);
        });
    }

    /** 审核到货：DRAFT→APPROVED，创建来料质检单（待检），同时生成应付账款(AP) */
    @Transactional
    public PurchaseArrival auditArrival(Long id) {
        PurchaseArrival pa = arrivalRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("到货记录不存在"));
        if (!"DRAFT".equals(pa.status))
            throw new IllegalArgumentException("只有开立状态的到货单可审核");
        final String operator = pa.operator;
        // v5.70.2 修复：质检单带上到货单的仓库/库位（此前硬编码 WH-OWN-PY+null，
        // QC 合格判定入库时因无库位被"精确到库位铁律"拦截，到货→质检→入库链路走不通）
        final String qcWh = pa.warehouseId != null && !pa.warehouseId.isBlank() ? pa.warehouseId : "WH-OWN-PY";
        final String qcLoc = pa.locationId;

        // v5.95 修复：批量采购单同单号多行——按 单号+到货物料 精确取行（原 findByOrderNo 对多行单 non-unique）
        if ("RAW".equals(pa.type)) {
            rawRepo.findFirstByOrderNoAndMaterialCode(pa.refOrderNo, pa.materialCode)
                    .or(() -> rawRepo.findByOrderNo(pa.refOrderNo))
                    .ifPresent(rp -> {
                qcService.createIncoming(rp.orderNo,
                        rp.materialCode != null ? rp.materialCode : rp.materialName,
                        rp.materialName, (pa.batchNo == null || pa.batchNo.isBlank()) ? null : pa.batchNo.trim(), pa.qty, null,  // v5.27：按实际到货数量建质检单（到货≠订单量）；v5.32：带到货批号
                        qcWh, qcLoc,
                        rp.isFree ? java.math.BigDecimal.ZERO : rp.unitPrice, null, operator, pa.id);   // v6.1.2 到货关联
            });
        } else {
            finishedRepo.findFirstByOrderNoAndMaterialCode(pa.refOrderNo, pa.materialCode)
                    .or(() -> finishedRepo.findByOrderNo(pa.refOrderNo))
                    .ifPresent(fp -> {
                qcService.createIncoming(fp.orderNo,
                        fp.materialCode != null ? fp.materialCode : fp.materialName,
                        fp.materialName, (pa.batchNo == null || pa.batchNo.isBlank()) ? null : pa.batchNo.trim(), pa.qty, null,  // v5.27：按实际到货数量建质检单（到货≠订单量）；v5.32：带到货批号
                        qcWh, qcLoc,
                        fp.isFree ? java.math.BigDecimal.ZERO : fp.unitPrice, null, operator, pa.id);   // v6.1.2 到货关联
            });
        }
        pa.status = "APPROVED";
        // v5.95.1：到货审核回写采购行已到货量（与旧到货录入路径同口径），到齐自动置 RECEIVED
        backfillReceivedQty(pa);
        // 到货审核即产生应付义务（货到即负债），自动生成应付账款
        generateAPForArrival(pa);
        log.info("到货审核(待检): {} -> {}", pa.type, pa.refOrderNo);
        return arrivalRepo.save(pa);
    }

    /**
     * 到货审核时生成应付账款（v5.27：按到货单立账）
     * 金额 = 本到货单数量 × 采购单价（非采购订单整单金额；未到货不负债）
     * 幂等：同一到货单仅生成一次 AP（arrivalId 唯一）；赠送(isFree=true)不生成。
     */
    private void generateAPForArrival(PurchaseArrival pa) {
        String orderNo = pa.refOrderNo;
        // 幂等：同一到货单只立一张 AP
        if (!apRepo.findByArrivalId(pa.id).isEmpty()) {
            return;
        }
        BigDecimal amount = BigDecimal.ZERO;
        Long supplierId = pa.supplierId;
        String supplierName = pa.supplierName;

        // 取采购单价（按到货单物料精确匹配，多明细订单不串价；赠送明细单价为 0，金额自然为 0 跳过）
        BigDecimal unitPrice = BigDecimal.ZERO;
        if ("RAW".equals(pa.type)) {
            for (RawMaterialPurchase r : rawRepo.findAllByOrderNo(orderNo)) {
                if (pa.materialCode == null || pa.materialCode.equals(r.materialCode)) {
                    unitPrice = r.unitPrice != null ? r.unitPrice : BigDecimal.ZERO;
                    if (supplierId == null) supplierId = r.supplierId;
                    if (supplierName == null) supplierName = r.supplierName;
                    break;
                }
            }
        } else {
            for (FinishedProductPurchase f : finishedRepo.findAllByOrderNo(orderNo)) {
                if (pa.materialCode == null || pa.materialCode.equals(f.materialCode)) {
                    unitPrice = f.unitPrice != null ? f.unitPrice : BigDecimal.ZERO;
                    if (supplierId == null) supplierId = f.supplierId;
                    if (supplierName == null) supplierName = f.supplierName;
                    break;
                }
            }
        }
        amount = pa.qty != null ? pa.qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || supplierId == null) return;

        AccountsPayable ap = new AccountsPayable();
        ap.supplierId = supplierId;
        ap.purchaseOrderNo = orderNo;
        ap.arrivalId = pa.id;
        ap.payableType = "PURCHASE";
        ap.amount = amount;
        // v6.1.4：账期从到货日起算（补录历史到货不再按今天推 30 天）；无到货日回退今天
        ap.dueDate = financeService.calcApDueDate(supplierId, LocalDate.now().plusDays(30),
                pa.arrivalDate != null ? pa.arrivalDate : LocalDate.now()); // v5.6：按供应商付款条件（货到付款=当天）
        ap.status = "UNPAID";
        ap.remark = "采购到货自动生成 " + orderNo + "（到货单#" + pa.id + "）";
        financeService.createAP(ap);
        log.info("到货审核生成AP(按到货单): 到货单#{} 订单={} 供应商={} 金额={}", pa.id, orderNo, supplierName, amount);
    }

    /** v5.95.1 到货审核回写采购行 receivedQty（单号+物料匹配），到齐置 RECEIVED */
    private void backfillReceivedQty(PurchaseArrival pa) {
        try {
            arrivalRepo.flush();   // v6.1.1：聚合走 DB，先 flush 保证读到本事务最新状态（反审核置 DRAFT 后汇总须不含本单）
            java.math.BigDecimal arrived0 = arrivalRepo.sumApprovedQtyByOrderNoAndMaterial(pa.refOrderNo, pa.materialCode);
            final java.math.BigDecimal arrived = arrived0 == null ? java.math.BigDecimal.ZERO : arrived0;
            if ("RAW".equals(pa.type)) {
                rawRepo.findFirstByOrderNoAndMaterialCode(pa.refOrderNo, pa.materialCode).ifPresent(rp -> {
                    rp.receivedQty = arrived;
                    if (arrived.compareTo(rp.qty != null ? rp.qty : java.math.BigDecimal.ZERO) >= 0) {
                        if (!"RECEIVED".equals(rp.status)) rp.status = "RECEIVED";
                    } else if ("RECEIVED".equals(rp.status)) {
                        rp.status = "APPROVED";   // v6.1.1：反审核后到货量低于订量时回退 RECEIVED→APPROVED（原实现只升不降）
                    }
                    rawRepo.save(rp);
                });
            } else {
                finishedRepo.findFirstByOrderNoAndMaterialCode(pa.refOrderNo, pa.materialCode).ifPresent(fp -> {
                    fp.receivedQty = arrived;
                    if (arrived.compareTo(fp.qty != null ? fp.qty : java.math.BigDecimal.ZERO) >= 0) {
                        if (!"RECEIVED".equals(fp.status)) fp.status = "RECEIVED";
                    } else if ("RECEIVED".equals(fp.status)) {
                        fp.status = "APPROVED";
                    }
                    finishedRepo.save(fp);
                });
            }
        } catch (Exception e) {
            log.warn("回写采购已到货量失败（不影响到货）: {} {}", pa.refOrderNo, e.getMessage());
        }
    }

    /** 反审核到货：APPROVED→DRAFT，冲正库存 */
    @Transactional
    public PurchaseArrival reverseAuditArrival(Long id) {
        PurchaseArrival pa = arrivalRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("到货记录不存在"));
        if (!"APPROVED".equals(pa.status))
            throw new IllegalArgumentException("只有已审核状态的到货单可反审核");
        final String operator = pa.operator;

        // v6.1 重写反审核（v5.80 审查高#5：原实现批号 null 撞铁律必失败、按整单量冲、硬编码仓、不冲AP不删QC）
        // ① 已判定的质检单不可反审核（库存可能已动/退货单可能已生成）
        // v6.1.2：按 arrivalId 精确隔离——同订单同物料分批到货时只看本到货单的质检单，
        // 不再误拦/误删他批（历史单无 arrivalId 回退 订单号+物料 旧条件）
        for (var qc : qcRepo.findByRefDocNoAndType(pa.refOrderNo, "INCOMING")) {
            boolean mine = qc.arrivalId != null ? qc.arrivalId.equals(pa.id)
                    : (pa.materialCode != null && pa.materialCode.equals(qc.materialCode));
            if (mine && !"PENDING".equals(qc.status)) {
                throw new IllegalArgumentException("该到货的质检单已判定（" + qc.inspectionNo + "），不能反审核；请先处理质检");
            }
        }
        // ② 反审核只回滚"审核动作"：立 AP、PENDING QC、已到货量。
        // v6.1.1 修复：到货审核本身不入库（质检合格才入库），此前误调 reverseInbound 冲库存——
        // 台账无此批次行（或行在库位上对不上），100% 抛"库存不足无法冲正"，反审核整体不可用。
        // ①已拦截非 PENDING 质检单 ⇒ 走到这里必然尚未入库，无库存可冲。
        // ③ 删该到货单立的 AP（arrivalId 幂等立账，精准冲）
        apRepo.findByArrivalId(pa.id).forEach(apRepo::delete);
        // ④ 删该到货生成的 PENDING 质检单（v6.1.2：按 arrivalId 精确隔离，同上）
        for (var qc : qcRepo.findByRefDocNoAndType(pa.refOrderNo, "INCOMING")) {
            boolean mine = qc.arrivalId != null ? qc.arrivalId.equals(pa.id)
                    : (pa.materialCode != null && pa.materialCode.equals(qc.materialCode));
            if (mine && "PENDING".equals(qc.status)) {
                qcRepo.delete(qc);
            }
        }
        // ⑤ 回写采购行已到货量：先置 DRAFT 落库再重算（v6.1.1 修复时序——
        // 此前 backfill 在置 DRAFT 前调用，approved 汇总仍含本单，已到货量减不回去）
        pa.status = "DRAFT";
        arrivalRepo.save(pa);
        backfillReceivedQty(pa);
        log.info("到货反审核: {} -> {}（删AP + 删PENDING QC + 回写已到货量，PENDING 阶段无库存动作）",
                pa.type, pa.refOrderNo);
        return pa;
    }

    // ==================== v6.5 B3：请购单转采购支撑 ====================

    public record SupRef(Long id, String name) {}
    public record MatRef(String code, String name, String category) {}

    public SupRef findSupplier(Long supplierId) {
        var rows = jdbc.queryForList("SELECT id, name FROM supplier WHERE id = ?", supplierId);
        if (rows.isEmpty()) throw new IllegalArgumentException("供应商不存在：" + supplierId);
        return new SupRef(((Number) rows.get(0).get("id")).longValue(), String.valueOf(rows.get(0).get("name")));
    }

    public MatRef findMaterialCategory(String code) {
        var rows = jdbc.queryForList("SELECT code, name, category FROM material WHERE code = ?", code);
        if (rows.isEmpty()) return new MatRef(null, null, null);
        var r = rows.get(0);
        return new MatRef(String.valueOf(r.get("code")), String.valueOf(r.get("name")),
                r.get("category") == null ? "" : String.valueOf(r.get("category")));
    }

    /** 请购转采购：生成 APPROVED 状态采购单（原料/成品同构，供应商+仓库+单价承接请购），返回合同号 */
    public String createPurchaseFromRequisition(Long supplierId, String supplierName, String materialCode, String materialName,
                                                java.math.BigDecimal qty, java.math.BigDecimal unitPrice,
                                                String warehouseId, String operator) {
        if (qty == null || qty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("物料 " + materialCode + " 数量必须大于 0");
        }
        // 判类：C 走成品采购，其余走原料采购（口径与转委外/转生产一致，按档案 category 不按首字符）
        var mat = findMaterialCategory(materialCode);
        if ("C".equals(mat.category())) {
            FinishedProductPurchase fp = new FinishedProductPurchase();
            fp.supplierId = supplierId;
            fp.supplierName = supplierName;
            fp.materialCode = materialCode;
            fp.materialName = materialName;
            fp.qty = qty;
            fp.unitPrice = unitPrice != null ? unitPrice : java.math.BigDecimal.ZERO;
            fp.warehouseId = warehouseId != null && !warehouseId.isBlank() ? warehouseId : "1";
            fp.purchaseDate = java.time.LocalDate.now();
            fp.createdBy = operator;
            fp.remark = "请购单转采购";
            FinishedProductPurchase saved = createFinished(fp);
            saved.status = "APPROVED";
            saved.updateTime = java.time.LocalDateTime.now();
            return finishedRepo.save(saved).orderNo;
        } else {
            RawMaterialPurchase rp = new RawMaterialPurchase();
            rp.supplierId = supplierId;
            rp.supplierName = supplierName;
            rp.materialCode = materialCode;
            rp.materialName = materialName;
            rp.qty = qty;
            rp.unitPrice = unitPrice != null ? unitPrice : java.math.BigDecimal.ZERO;
            rp.warehouseId = warehouseId != null && !warehouseId.isBlank() ? warehouseId : "1";
            rp.purchaseDate = java.time.LocalDate.now();
            rp.createdBy = operator;
            rp.remark = "请购单转采购";
            RawMaterialPurchase saved = createRaw(rp);
            saved.status = "APPROVED";
            saved.updateTime = java.time.LocalDateTime.now();
            return rawRepo.save(saved).orderNo;
        }
    }

    // ==================== 内部方法 ====================

    /** 生成合同号：供应商简称首字母 + YYYYMMDD + 序号（v5.24：跨两表取最大序号，删除不错位且并发防重） */
    private String generateContractNo(Long supplierId, String prefix) {
        String shortCode = getSupplierShortCode(supplierId);
        String datePart = LocalDate.now().toString().replace("-", "");
        String basePrefix = shortCode + datePart;
        // v5.9：序号查询必须带单据前缀（单号 = 前缀 + 供应商简称 + 日期 + 序号），
        // 否则 LIKE 'EEJS20260806%' 永远匹配不到 'RAWEEJS20260806-01'，count 恒为 0
        String fullPrefix = prefix + basePrefix;
        // v5.70.2 修复：findMaxSeq 的 LIKE 不自动补 %，裸前缀=精确匹配永远查不到 → 同供应商同日第二单必撞号
        Integer rawMax = rawRepo.findMaxSeq(fullPrefix + "%");
        Integer finMax = finishedRepo.findMaxSeq(fullPrefix + "%");
        int next = Math.max(rawMax == null ? 0 : rawMax, finMax == null ? 0 : finMax) + 1;
        return String.format("%s%s-%02d", prefix, basePrefix, next);
    }

    /** 供应商简称：取中文拼音首字母，英文取首字母 */
    private String getSupplierShortCode(Long supplierId) {
        if (supplierId == null) return "XX";
        return supplierRepo.findById(supplierId)
                .map(s -> toShortCode(s.name))
                .orElse("XX");
    }

    private String toShortCode(String name) {
        if (name == null || name.isBlank()) return "XX";
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') sb.append(Character.toUpperCase(c));
            else if (c >= 0x4e00 && c <= 0x9fff) sb.append(getPinyinInitial(c));
        }
        String result = sb.toString().replaceAll("[^A-Z]", "");
        return result.length() >= 2 ? result.substring(0, Math.min(4, result.length())) : name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private char getPinyinInitial(char c) {
        // 简化拼音首字母映射
        String[] map = {"A","B","C","D","E","F","G","H","J","K","L","M","N","O","P","Q","R","S","T","W","X","Y","Z"};
        int[] range = {0xB0A1,0xB0C5,0xB2C1,0xB4EE,0xB6EA,0xB7A2,0xB8C1,0xB9FE,0xBBF7,0xBFA6,0xC0AC,0xC2E8,0xC4C3,0xC5B6,0xC5BE,0xC6DA,0xC8BB,0xC8F6,0xCBFA,0xCDDA,0xCEF4,0xD1B9,0xD4D1};
        int code = (c << 8) | 0; // simplified
        // Fallback: use a simple heuristic
        String py = "ABCDEFGHJKLMNOPQRSTWXYZ";
        int idx = (c - 0x4E00) * py.length() / (0x9FA5 - 0x4E00);
        if (idx >= 0 && idx < py.length()) return py.charAt(idx);
        return 'X';
    }

    private void calcAmount(RawMaterialPurchase rp) {
        if (rp.qty != null && rp.unitPrice != null) {
            rp.totalAmount = rp.qty.multiply(rp.unitPrice);
        }
    }
}
