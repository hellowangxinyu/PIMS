package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 质检管理服务
 * 统一为入库前质检：采购入库 / 生产入库 / 委外入库 / 其他入库 → 待检 → QC判定(合格/让步/退货)
 *          - 合格/让步 → 入库，回写单据状态为 DONE
 *          - 退货 → 采购入库自动生成采购退货单(DRAFT)，等待采购员审核；回写单据状态为 REJECTED
 */
@Service
public class QualityInspectionService {

    private static final Logger log = LoggerFactory.getLogger(QualityInspectionService.class);

    private final QualityInspectionRepository qcRepo;
    private final InventoryService inventoryService;
    private final MaterialRepository materialRepo;
    private final OtherInboundRepository otherInRepo;
    private final OtherOutboundRepository otherOutRepo;
    private final ProductionInboundRepository prodInRepo;
    private final OutsourceFinishInboundRepository outsourceInRepo;
    private final OutsourceOrderRepository outsourceOrderRepo;
    private final RawMaterialPurchaseRepository rawPurchaseRepo;
    private final FinishedProductPurchaseRepository finishedPurchaseRepo;
    private final PurchaseArrivalRepository arrivalRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;   // v5.63 订单归集成本
    private final AccountsPayableRepository apRepo;
    private final FinanceService financeService;
    // 不合格品库（v5.30：质检不合格隔离仓）
    private final WarehouseRepository warehouseRepo;
    // 库位服务（v5.31：不合格品精确到库位）
    private final WarehouseLocationService locationService;
    // 退货单生成（@Lazy 避免循环依赖）
    private final ReturnOrderService returnOrderService;
    // v5.24：全局写锁（质检单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    // 生产订单服务：入库合格自动完工（@Lazy 避免潜在循环依赖）
    private final ProductionOrderService productionOrderService;
    // v5.32：质检模板（按物料大类快照检测项）
    private final QcTemplateService qcTemplateService;
    private final QualityInspectionItemRepository inspectionItemRepo;
    // v5.38：隔离区分库查找（隔离仓降级为分库）
    private final WarehouseZoneRepository zoneRepo;
    // v5.38.2：复检合格移回普通库位（过期原材料已自动移入不合格品库）
    private final ExpiryQuarantineService expiryQuarantineService;
    private final com.pengyuan.pims.repository.RecipeRepository recipeRepo;   // v5.81 配方绑定质检模板

    public QualityInspectionService(QualityInspectionRepository qcRepo,
                                    InventoryService inventoryService,
                                    MaterialRepository materialRepo,
                                    OtherInboundRepository otherInRepo,
                                    OtherOutboundRepository otherOutRepo,
                                    ProductionInboundRepository prodInRepo,
                                    OutsourceFinishInboundRepository outsourceInRepo,
                                    OutsourceOrderRepository outsourceOrderRepo,
                                    RawMaterialPurchaseRepository rawPurchaseRepo,
                                    FinishedProductPurchaseRepository finishedPurchaseRepo,
                                    PurchaseArrivalRepository arrivalRepo,
                                    InventoryLedgerRepository ledgerRepo,
                                    AccountsPayableRepository apRepo,
                                    FinanceService financeService,
                                    WarehouseRepository warehouseRepo,
                                    WarehouseLocationService locationService,
                                    @Lazy ReturnOrderService returnOrderService,
                                    WriteQueue writeQueue,
                                    @Lazy ProductionOrderService productionOrderService,
                                    QcTemplateService qcTemplateService,
                                    QualityInspectionItemRepository inspectionItemRepo,
                                    WarehouseZoneRepository zoneRepo,
                                    ExpiryQuarantineService expiryQuarantineService,
                                    org.springframework.jdbc.core.JdbcTemplate jdbc, com.pengyuan.pims.repository.RecipeRepository recipeRepo) {
        this.qcRepo = qcRepo;
        this.inventoryService = inventoryService;
        this.materialRepo = materialRepo;
        this.otherInRepo = otherInRepo;
        this.otherOutRepo = otherOutRepo;
        this.prodInRepo = prodInRepo;
        this.outsourceInRepo = outsourceInRepo;
        this.outsourceOrderRepo = outsourceOrderRepo;
        this.rawPurchaseRepo = rawPurchaseRepo;
        this.finishedPurchaseRepo = finishedPurchaseRepo;
        this.arrivalRepo = arrivalRepo;
        this.ledgerRepo = ledgerRepo;
        this.apRepo = apRepo;
        this.financeService = financeService;
        this.warehouseRepo = warehouseRepo;
        this.locationService = locationService;
        this.returnOrderService = returnOrderService;
        this.writeQueue = writeQueue;
        this.productionOrderService = productionOrderService;
        this.qcTemplateService = qcTemplateService;
        this.inspectionItemRepo = inspectionItemRepo;
        this.zoneRepo = zoneRepo;
        this.expiryQuarantineService = expiryQuarantineService;
        this.recipeRepo = recipeRepo;
        this.jdbc = jdbc;   // v5.63 成品入库归集成本聚合查询
    }

    // ==================== 查询 ====================

    public List<QualityInspection> listAll() {
        return qcRepo.findByOrderByCreateTimeDesc();
    }

    public List<QualityInspection> listByType(String type) {
        return qcRepo.findByTypeOrderByCreateTimeDesc(type);
    }

    public List<QualityInspection> listPending(String type) {
        return qcRepo.findByTypeAndStatusOrderByCreateTimeDesc(type, "PENDING");
    }

    /**
     * 质检单分页查询（支持类型 + 状态 + 多条件 + 按创建时间倒序）
     * @param type         质检类型：INCOMING/OUTGOING（可空=全部）
     * @param status       状态：PENDING/PASS/CONCESSION/REJECT（可空=全部）
     * @param category     物料大类过滤（v5.0，逗号分隔集合，如 "A,P,F,R,S"=材料 / "B"=半成品 / "C"=成品；可空=全部）
     * @param inspectionNo 质检单号模糊匹配（可空）
     * @param refDocNo     关联单号模糊匹配（可空）
     * @param materialCode 物料编码模糊匹配（可空）
     * @param materialName 物料品名模糊匹配（可空）
     * @param batchNo      批次模糊匹配（可空）
     * @param inspector     检验员模糊匹配（可空）
     * @param startDate    检验开始日期（可空，含）
     * @param endDate      检验结束日期（可空，含）
     * @param page         页码（0 起）
     * @param size         每页条数
     */
    public Page<QualityInspection> search(String type, String status, String category,
                                          String inspectionNo, String refDocNo,
                                          String materialCode, String materialName,
                                          String batchNo, String inspector,
                                          LocalDate startDate, LocalDate endDate,
                                          int page, int size) {
        Specification<QualityInspection> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (type != null && !type.isBlank()) ps.add(cb.equal(root.get("type"), type));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (category != null && !category.isBlank()) {
                ps.add(root.get("materialCategory").in((Object[]) category.split(",")));
            }
            if (inspectionNo != null && !inspectionNo.isBlank()) ps.add(cb.like(root.get("inspectionNo"), "%" + inspectionNo + "%"));
            if (refDocNo != null && !refDocNo.isBlank()) ps.add(cb.like(root.get("refDocNo"), "%" + refDocNo + "%"));
            if (materialCode != null && !materialCode.isBlank()) ps.add(cb.like(root.get("materialCode"), "%" + materialCode + "%"));
            if (materialName != null && !materialName.isBlank()) ps.add(cb.like(root.get("materialName"), "%" + materialName + "%"));
            if (batchNo != null && !batchNo.isBlank()) ps.add(cb.like(root.get("batchNo"), "%" + batchNo + "%"));
            if (inspector != null && !inspector.isBlank()) ps.add(cb.like(root.get("inspector"), "%" + inspector + "%"));
            if (startDate != null) ps.add(cb.greaterThanOrEqualTo(root.get("inspectDate"), startDate));
            if (endDate != null) ps.add(cb.lessThanOrEqualTo(root.get("inspectDate"), endDate));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return qcRepo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    // ==================== 创建质检单 ====================

    /**
     * v5.0：解析质检物料的分类（A/P/F/R/S=材料、B=半成品、C=成品）。
     * 优先物料主档；匹配不到按编码首字符推断（编码规则：首字符即大类代码）
     */
    /**
     * 解析物料分类（质检 Tab 分材料/半成品/成品用）
     * 优先按物料编码（主档精确 → 首字母推断）；v5.7 编码缺失/无法解析时按名称匹配物料主档兜底
     * （如"碳酸钙研磨浆"匹配 B 类物料"碳酸钙浆"）
     */
    /** v5.81 取物料档案匹配维度（subCategory/mainMaterial/colorSeries），空安全 */
    private String matDim(String materialCode, String dim) {
        if (materialCode == null || materialCode.isBlank()) return null;
        return materialRepo.findByCode(materialCode).map(m -> {
            if ("subCategory".equals(dim)) return m.subCategory;
            if ("mainMaterial".equals(dim)) return m.mainMaterial;
            if ("colorSeries".equals(dim)) return m.colorSeries;
            return null;
        }).orElse(null);
    }

    /** v5.81 生产/委外入库反查配方绑定的质检模板（按产品编码取最新启用配方） */
    private Long boundQcTemplateId(String refDocType, String refDocNo, String materialCode) {
        try {
            if (materialCode == null || materialCode.isBlank()) return null;
            return recipeRepo.findTopByProductCodeAndEnabledTrueOrderByUpdateTimeDescIdDesc(materialCode)
                    .map(r -> r.qcTemplateId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveMaterialCategory(String materialCode, String materialName) {
        if (materialCode != null && !materialCode.isBlank()) {
            Optional<Material> mat = materialRepo.findByCode(materialCode);
            if (mat.isPresent() && mat.get().category != null && !mat.get().category.isBlank()) {
                return mat.get().category;
            }
            char c = materialCode.charAt(0);
            if ("APFRS".indexOf(c) >= 0) return String.valueOf(c);
            if (c == 'B' || c == 'C') return String.valueOf(c);
        }
        // v5.7：编码为空/解析失败时按名称匹配物料主档（双向包含）
        if (materialName != null && !materialName.isBlank()) {
            for (Material m : materialRepo.findAll()) {
                if (m.category == null || m.category.isBlank() || m.name == null || m.name.isBlank()) continue;
                if (materialName.contains(m.name) || m.name.contains(materialName)) {
                    return m.category;
                }
            }
        }
        return null;
    }

    /**
     * 创建来料质检单（采购到货时调用）
     */
    @Transactional
    public QualityInspection createIncoming(String refDocNo, String materialCode, String materialName,
                                            String batchNo, BigDecimal qty, String unit,
                                            String warehouseId, String locationId,
                                            BigDecimal unitPrice, LocalDate produceDate,
                                            String operator) {
        return createIncoming(refDocNo, materialCode, materialName, batchNo, qty, unit,
                warehouseId, locationId, unitPrice, produceDate, operator, null);
    }

    /**
     * v6.1.2：带 arrivalId 版本——到货审核生成质检单时建立精确关联，
     * 反审核按到货单隔离（同订单同物料分批到货不互删待检单）
     */
    @Transactional
    public QualityInspection createIncoming(String refDocNo, String materialCode, String materialName,
                                            String batchNo, BigDecimal qty, String unit,
                                            String warehouseId, String locationId,
                                            BigDecimal unitPrice, LocalDate produceDate,
                                            String operator, Long arrivalId) {
        // v5.24：质检单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.execute(() -> {
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = qcRepo.findMaxSeq("QC-IN-" + LocalDate.now().toString().replace("-", "") + "-%");
            String inspectionNo = String.format("QC-IN-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            QualityInspection qc = new QualityInspection();
            qc.inspectionNo = inspectionNo;
            qc.type = "INCOMING";
            qc.refDocNo = refDocNo;
            qc.refDocType = "PURCHASE";
            qc.arrivalId = arrivalId;
            qc.materialCode = materialCode;
            qc.materialName = materialName;
            qc.materialCategory = resolveMaterialCategory(materialCode, materialName);
            qc.batchNo = batchNo;
            qc.qty = qty;
            qc.unit = unit;
            qc.warehouseId = warehouseId;
            qc.locationId = locationId;
            qc.unitPrice = unitPrice;
            qc.produceDate = produceDate;
            qc.status = "PENDING";
            qc.createdBy = operator;
            qc.createTime = LocalDateTime.now();
            qcRepo.save(qc);
            // v5.81：按物料三维（小类/主材/色系）打分匹配模板快照（原料类无色系自动降维）
            qcTemplateService.snapshotTo(qc.id, qc.materialCategory, null,
                    matDim(materialCode, "subCategory"), matDim(materialCode, "mainMaterial"), matDim(materialCode, "colorSeries"));
            log.info("来料质检单创建: {} 物料={} 数量={} 分类={}", inspectionNo, materialCode, qty, qc.materialCategory);
            return qc;
        });
    }

    /**
     * 创建入库前质检单（生产入库 / 委外入库 通用）
     * 所有入库物料均需质检合格方可入库，统一为来料质检(INCOMING)类型。
     * @param refDocType PRODUCTION_INBOUND / OUTSOURCE_INBOUND，用于 triggerInventory 映射入库 docType
     */
    @Transactional
    public QualityInspection createForInbound(String refDocType, String refDocNo, String materialCode, String materialName,
                                              String batchNo, BigDecimal qty, String unit,
                                              String warehouseId, String locationId,
                                              LocalDate produceDate, String operator) {
        // v5.24：质检单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.execute(() -> {
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = qcRepo.findMaxSeq("QC-IN-" + LocalDate.now().toString().replace("-", "") + "-%");
            String inspectionNo = String.format("QC-IN-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            QualityInspection qc = new QualityInspection();
            qc.inspectionNo = inspectionNo;
            qc.type = "INCOMING";
            qc.refDocNo = refDocNo;
            qc.refDocType = refDocType;
            qc.materialCode = materialCode;
            qc.materialName = materialName;
            qc.materialCategory = resolveMaterialCategory(materialCode, materialName);
            qc.batchNo = batchNo;
            qc.qty = qty;
            qc.unit = unit;
            qc.warehouseId = warehouseId;
            qc.locationId = locationId;
            qc.produceDate = produceDate;
            qc.status = "PENDING";
            qc.createdBy = operator;
            qc.createTime = LocalDateTime.now();
            qcRepo.save(qc);
            // v5.81：优先配方绑定的质检模板；无绑定再按物料三维打分匹配
            qcTemplateService.snapshotTo(qc.id, qc.materialCategory, boundQcTemplateId(refDocType, refDocNo, materialCode),
                    matDim(materialCode, "subCategory"), matDim(materialCode, "mainMaterial"), matDim(materialCode, "colorSeries"));
            log.info("入库质检单创建: {} 类型={} 产品={} 数量={} 分类={}", inspectionNo, refDocType, materialCode, qty, qc.materialCategory);
            return qc;
        });
    }

    /**
     * 创建其他出入库质检单
     * @param qcType INCOMING(其他入库) / OUTGOING(其他出库)
     * @param refDocType OTHER_INBOUND / OTHER_OUTBOUND
     */
    @Transactional
    public QualityInspection createForOther(String qcType, String refDocNo, String refDocType,
                                            String materialCode, String materialName,
                                            String batchNo, BigDecimal qty, String unit,
                                            String warehouseId, String locationId,
                                            BigDecimal unitPrice, String operator) {
        // v5.24：质检单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.execute(() -> {
            String prefix = "INCOMING".equals(qcType) ? "QC-IN" : "QC-OUT";
            // v5.24：按最大序号+1（count 会删除错位且并发撞号）
            Integer maxSeq = qcRepo.findMaxSeq(prefix + "-" + LocalDateTime.now().getYear() + "-%");
            String inspectionNo = String.format("%s-%d-%04d", prefix, LocalDateTime.now().getYear(), (maxSeq == null ? 0 : maxSeq) + 1);
            QualityInspection qc = new QualityInspection();
            qc.inspectionNo = inspectionNo;
            qc.type = qcType;
            qc.refDocNo = refDocNo;
            qc.refDocType = refDocType;
            qc.materialCode = materialCode;
            qc.materialName = materialName;
            qc.materialCategory = resolveMaterialCategory(materialCode, materialName);
            qc.batchNo = batchNo;
            qc.qty = qty;
            qc.unit = unit;
            qc.warehouseId = warehouseId;
            qc.locationId = locationId;
            qc.unitPrice = unitPrice;
            qc.status = "PENDING";
            qc.createdBy = operator;
            qc.createTime = LocalDateTime.now();
            qcRepo.save(qc);
            // v5.81：按物料三维打分匹配模板快照
            qcTemplateService.snapshotTo(qc.id, qc.materialCategory, null,
                    matDim(materialCode, "subCategory"), matDim(materialCode, "mainMaterial"), matDim(materialCode, "colorSeries"));
            log.info("其他出入库质检单创建: {} 类型={} 单据={} 物料={} 分类={}", inspectionNo, refDocType, refDocNo, materialCode, qc.materialCategory);
            return qc;
        });
    }

    // ==================== QC判定 ====================

    /**
     * QC判定：合格/让步接收 → 触发库存变更；退货 → 不生效
     * @param id 质检单ID
     * @param result PASS / CONCESSION / REJECT
     * @param inspector 检验员
     * @param resultRemark 检测结果（v5.32：填了检测项实测值时可不填，为空自动按实测值汇总）
     * @param unqualifiedLocationId v5.31：判定不合格时的存放库位（不合格品库内，可空=默认库位）
     * @param items v5.32：检测项实测值 [{id, measuredValue, itemResult}]，可选
     * @param batchNo v5.32：批号补填（到货未带批号时检验员按实物包装录入；非空则更新并入库沿用，空=保持原值）
     */
    @Transactional
    public QualityInspection judge(Long id, String result, String inspector, String resultRemark,
                                   Long unqualifiedLocationId, List<Map<String, Object>> items, String batchNo,
                                   LocalDate reexpiryDate) {
        // v5.24：判定+入库+退货单生成整体排队（WriteQueue 全局锁），防并发重复判定/重复入库
        return writeQueue.execute(() -> {
            QualityInspection qc = qcRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("质检单不存在"));
            if (!"PENDING".equals(qc.status))
                throw new IllegalArgumentException("只有待检状态的质检单可判定");
            if (!List.of("PASS", "CONCESSION", "REJECT").contains(result))
                throw new IllegalArgumentException("无效的判定结果: " + result);

            // v5.32：批号补填（到货未带时按实物包装批号录入，合格入库台账沿用）
            if (batchNo != null && !batchNo.isBlank()) {
                qc.batchNo = batchNo.trim();
            }

            // v5.32：先保存检测项实测值（按 id 匹配且校验归属本单，防篡改他单数据）
            boolean hasMeasured = saveInspectionItems(id, items);
            // v5.32：判定说明放宽——填了任一实测值时可不填，为空自动按实测值汇总（"外观:透明；细度:25μm"）
            final String remark;
            if (resultRemark != null && !resultRemark.isBlank()) {
                remark = resultRemark.trim();
            } else if (hasMeasured) {
                remark = summarizeItems(id);
            } else {
                throw new IllegalArgumentException("必须填写检测结果");
            }

            qc.status = result;
            qc.inspector = inspector;
            qc.inspectDate = LocalDate.now();
            qc.resultRemark = remark;
            qc.updateTime = LocalDateTime.now();
            qcRepo.save(qc);

            // v5.37：过期复检单 —— 只更新原台账行（恢复放行/维持隔离），不走入库/退货/不合格品库
            if ("REINSPECTION".equals(qc.refDocType)) {
                finishReinspection(qc, reexpiryDate);
                log.info("复检判定: {} 结果={} 新有效期={} 检验员={}", qc.inspectionNo, result, reexpiryDate, inspector);
                return qc;
            }

            // 回写其他出入库单据状态
            updateRefDocStatus(qc, result);
    
            // 合格或让步接收 → 触发库存变更（入库或出库）
            if ("PASS".equals(result) || "CONCESSION".equals(result)) {
                triggerInventory(qc, inspector);
                // v5.27：台账批号生成后回写到入库单（生产/委外/其他入库，供列表展示与打印标签）
                writeBackBatchNo(qc);
            }
    
            // 来料质检判定退货 → 自动生成采购退货单（DRAFT，等待采购员审核）
            if ("REJECT".equals(result) && "INCOMING".equals(qc.type)
                    && "PURCHASE".equals(qc.refDocType)) {
                try {
                    returnOrderService.createFromQcReject(qc.id);
                } catch (Exception e) {
                    log.error("采购退货单自动生成失败: 质检单={} 原因={}", qc.inspectionNo, e.getMessage(), e);
                    // v6.1.7：补偿失败写回质检单（原仅日志，单据链断裂用户无感知，事后无从追溯）
                    qc.resultRemark = (qc.resultRemark == null ? "" : qc.resultRemark + "；") + "退货单自动生成失败，请人工处理";
                    qcRepo.save(qc);
                }
            }

            // v5.30：生产/委外/其他入库判定不合格 → 货物自动转入不合格品库（隔离仓，仅可报废/退货出库）
            // v5.31：不合格品精确到库位（指定库位或不合格品库默认库位）
            if ("REJECT".equals(result) && List.of("PRODUCTION_INBOUND", "OUTSOURCE_INBOUND", "OTHER_INBOUND")
                    .contains(qc.refDocType)) {
                try {
                    unqualifiedInbound(qc, inspector, unqualifiedLocationId);
                } catch (Exception e) {
                    log.error("不合格品转入不合格品库失败: 质检单={} 原因={}", qc.inspectionNo, e.getMessage(), e);
                    // v6.1.7：同上，失败写回质检单提示人工处理
                    qc.resultRemark = (qc.resultRemark == null ? "" : qc.resultRemark + "；") + "不合格品转库失败，请人工处理";
                    qcRepo.save(qc);
                }
            }

            log.info("质检判定: {} 结果={} 检验员={} 检测结果={}", qc.inspectionNo, result, inspector, remark);
            return qc;
        });
    }

    // ==================== v5.37 过期批次复检 ====================

    /**
     * v5.37：对过期批次发起复检评估 —— 生成 PENDING 复检质检单（refDocType=REINSPECTION，快照质检模板）。
     * 数量 = 该批所有在库台账行合计；仓库取该批首个有库存的仓；防同批次重复发起。
     */
    @Transactional
    public QualityInspection createReinspection(String materialCode, String batchNo, String operator) {
        if (materialCode == null || materialCode.isBlank() || batchNo == null || batchNo.isBlank()) {
            throw new IllegalArgumentException("物料编码和批号不能为空");
        }
        List<InventoryLedger> rows = ledgerRepo.findByMaterialCodeAndBatchNo(materialCode, batchNo);
        List<InventoryLedger> inStock = rows.stream()
                .filter(l -> l.qty != null && l.qty.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (inStock.isEmpty()) throw new IllegalArgumentException("该批次无在库库存");
        boolean expiredExists = inStock.stream()
                .anyMatch(l -> l.expiryDate != null && l.expiryDate.isBefore(LocalDate.now()));
        if (!expiredExists) throw new IllegalArgumentException("该批次未过期，无需复检");
        List<QualityInspection> pendingReins = qcRepo
                .findByMaterialCodeAndBatchNoAndStatusOrderByCreateTimeDesc(materialCode, batchNo, "PENDING")
                .stream().filter(q -> "REINSPECTION".equals(q.refDocType)).toList();
        if (!pendingReins.isEmpty()) {
            throw new IllegalArgumentException("该批次已有进行中的复检单 " + pendingReins.get(0).inspectionNo);
        }

        InventoryLedger first = inStock.get(0);
        BigDecimal totalQty = inStock.stream().map(l -> l.qty).reduce(BigDecimal.ZERO, BigDecimal::add);
        String oldestExpiry = inStock.stream().map(l -> l.expiryDate)
                .filter(d -> d != null && d.isBefore(LocalDate.now()))
                .min(LocalDate::compareTo).map(LocalDate::toString).orElse("");

        return writeQueue.execute(() -> {
            Integer maxSeq = qcRepo.findMaxSeq("QC-IN-" + LocalDate.now().toString().replace("-", "") + "-%");
            QualityInspection qc = new QualityInspection();
            qc.inspectionNo = String.format("QC-IN-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            qc.type = "INCOMING";
            qc.refDocType = "REINSPECTION";
            qc.refDocNo = "EXP-" + batchNo;
            qc.materialCode = materialCode;
            qc.materialName = first.materialName;
            qc.materialCategory = resolveMaterialCategory(materialCode, first.materialName);
            qc.batchNo = batchNo;
            qc.qty = totalQty;
            qc.unit = first.unit;
            qc.warehouseId = first.warehouseId;
            qc.locationId = first.locationId;
            qc.unitPrice = first.unitPrice;
            qc.remark = "过期批次复检评估（过期日 " + oldestExpiry + "）";
            qc.status = "PENDING";
            qc.createdBy = operator;
            qc.createTime = LocalDateTime.now();
            qcRepo.save(qc);
            qcTemplateService.snapshotTo(qc.id, qc.materialCategory, null,
                    matDim(materialCode, "subCategory"), matDim(materialCode, "mainMaterial"), matDim(materialCode, "colorSeries"));
            log.info("复检单创建: {} 物料={} 批次={} 数量={}{}", qc.inspectionNo, materialCode, batchNo, totalQty, first.unit);
            return qc;
        });
    }

    /**
     * v5.37：复检判定落地（judge 的 REINSPECTION 分支）：
     * PASS/CONCESSION → 该批所有 EXPIRED 在库台账行恢复可用 + 质检四件套 + expiryDate=复检有效期
     *                  + 移回普通库位（v5.38.2：原材料过期已自动移入不合格品库，合格后移回）
     *                  （日期更新后，v5.23 出库拦截与 FIFO 预检天然放行，无需改拦截代码）；
     * REJECT → 维持 EXPIRED 隔离，提示走「其他出库-报废」。
     */
    private void finishReinspection(QualityInspection qc, LocalDate newExpiryDate) {
        boolean pass = "PASS".equals(qc.status) || "CONCESSION".equals(qc.status);
        if (pass) {
            if (newExpiryDate == null) throw new IllegalArgumentException("复检合格必须填写复检后有效期");
            if (!newExpiryDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("复检后有效期必须晚于今天");
        }
        List<InventoryLedger> rows = ledgerRepo.findByMaterialCodeAndBatchNo(qc.materialCode, qc.batchNo);
        int updated = 0;
        for (InventoryLedger l : rows) {
            if (l.qty == null || l.qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (!"EXPIRED".equals(l.qcStatus)) continue;  // 只恢复被隔离的行
            l.qcStatus = qc.status;
            l.qcInspectionNo = qc.inspectionNo;
            l.qcResult = qc.resultRemark;
            l.qcInspector = qc.inspector;
            l.qcDate = qc.inspectDate;
            if (pass) {
                l.expiryDate = newExpiryDate;
                expiryQuarantineService.restoreToNormalLocation(l);
            }
            ledgerRepo.save(l);
            updated++;
        }
        if (pass) {
            log.info("复检合格放行: {} 批次 {} 共 {} 行台账恢复（移回普通库位），新有效期 {}", qc.materialCode, qc.batchNo, updated, newExpiryDate);
        } else {
            log.info("复检不合格维持隔离: {} 批次 {}（{} 行），请走其他出库-报废处理", qc.materialCode, qc.batchNo, updated);
        }
    }

    /**
     * v5.32：按质检单号取检测项（库存页展开查看批次检测明细）。
     * 历史单无快照返回空数组；不触发补建（已判定单不可变）。
     */
    public List<QualityInspectionItem> getItemsByNo(String inspectionNo) {
        QualityInspection qc = qcRepo.findByInspectionNo(inspectionNo)
                .orElseThrow(() -> new IllegalArgumentException("质检单不存在: " + inspectionNo));
        return inspectionItemRepo.findByInspectionIdOrderBySortOrderAscIdAsc(qc.id);
    }

    /**
     * v5.32：按质检单号取质检单完整信息+检测项（库存页点击检测结果弹窗查看质检报告）。
     */
    public Map<String, Object> getByNo(String inspectionNo) {
        QualityInspection qc = qcRepo.findByInspectionNo(inspectionNo)
                .orElseThrow(() -> new IllegalArgumentException("质检单不存在: " + inspectionNo));
        return Map.of(
                "qc", qc,
                "items", inspectionItemRepo.findByInspectionIdOrderBySortOrderAscIdAsc(qc.id)
        );
    }

    /**
     * v5.32：质检单检测项（判定弹窗/打印加载）。
     * 存量待检单无快照时，按当前物料大类的默认模板幂等补建；已判定单直接返回已有快照（历史单可能为空）。
     */
    @Transactional
    public List<QualityInspectionItem> getItems(Long id) {
        QualityInspection qc = qcRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("质检单不存在"));
        if ("PENDING".equals(qc.status)
                && inspectionItemRepo.findByInspectionIdOrderBySortOrderAscIdAsc(id).isEmpty()) {
            qcTemplateService.snapshotTo(id, qc.materialCategory, null,
                    matDim(qc.materialCode, "subCategory"), matDim(qc.materialCode, "mainMaterial"), matDim(qc.materialCode, "colorSeries"));
        }
        return inspectionItemRepo.findByInspectionIdOrderBySortOrderAscIdAsc(id);
    }

    /**
     * v5.32：批量取质检单检测项（导出明细列用），按质检单 ID 分组
     */
    public java.util.Map<Long, List<QualityInspectionItem>> getItemsIn(java.util.Collection<Long> inspectionIds) {
        java.util.Map<Long, List<QualityInspectionItem>> map = new java.util.LinkedHashMap<>();
        if (inspectionIds == null || inspectionIds.isEmpty()) return map;
        for (QualityInspectionItem i : inspectionItemRepo.findByInspectionIdInOrderByInspectionIdAscSortOrderAscIdAsc(inspectionIds)) {
            map.computeIfAbsent(i.inspectionId, k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    /**
     * v5.32：保存检测项实测值/单项判定（判定提交时调用）。
     * 按 id 匹配且只更新归属本质检单的项（忽略不存在的 id）；单项判定仅接受 PASS/FAIL，其余视为未检置空。
     * @return 是否存在任一已填实测值
     */
    private boolean saveInspectionItems(Long inspectionId, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return false;
        List<QualityInspectionItem> existing = inspectionItemRepo.findByInspectionIdOrderBySortOrderAscIdAsc(inspectionId);
        boolean hasMeasured = false;
        for (Map<String, Object> raw : items) {
            Long itemId = parseLong(raw.get("id"));
            if (itemId == null) continue;
            QualityInspectionItem target = existing.stream()
                    .filter(i -> itemId.equals(i.id)).findFirst().orElse(null);
            if (target == null) continue;
            Object mv = raw.get("measuredValue");
            target.measuredValue = mv == null || mv.toString().isBlank() ? null : mv.toString().trim();
            Object ir = raw.get("itemResult");
            String rs = ir == null ? null : ir.toString().trim();
            target.itemResult = "PASS".equals(rs) || "FAIL".equals(rs) ? rs : null;
            target.updateTime = LocalDateTime.now();
            inspectionItemRepo.save(target);
            if (target.measuredValue != null) hasMeasured = true;
        }
        return hasMeasured;
    }

    private Long parseLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o != null) {
            try { return Long.valueOf(o.toString().trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    /**
     * v5.32：按已填实测值汇总判定说明（resultRemark 为空时的兜底）。
     * 格式如 "外观:透明；细度:25μm"，截断至 500 字符（列宽限制）
     */
    private String summarizeItems(Long inspectionId) {
        StringBuilder sb = new StringBuilder();
        for (QualityInspectionItem i : inspectionItemRepo.findByInspectionIdOrderBySortOrderAscIdAsc(inspectionId)) {
            if (i.measuredValue == null || i.measuredValue.isBlank()) continue;
            if (sb.length() > 0) sb.append("；");
            sb.append(i.name).append(":").append(i.measuredValue);
            if (i.unit != null && !i.unit.isBlank()) sb.append(i.unit);
        }
        String s = sb.length() > 0 ? sb.toString() : "已逐项检验";
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /**
     * 判定后回写入库单据状态：合格/让步→DONE(已入库)，退货→REJECTED(质检不合格)
     * 覆盖其他入库 / 生产入库 / 委外入库
     */
    private void updateRefDocStatus(QualityInspection qc, String result) {
        String newStatus = "REJECT".equals(result) ? "REJECTED" : "DONE";
        if (qc.refDocNo == null) return;
        switch (qc.refDocType) {
            case "OTHER_INBOUND" -> otherInRepo.findByDocNo(qc.refDocNo).ifPresent(doc -> {
                doc.status = newStatus;
                doc.updateTime = LocalDateTime.now();
                otherInRepo.save(doc);
                // 质检合格 → 若单据勾选了生成应付，则生成AP
                if (!"REJECTED".equals(newStatus) && Boolean.TRUE.equals(doc.genFinance)
                        && doc.financeAmount != null && doc.financeAmount.compareTo(BigDecimal.ZERO) > 0
                        && doc.financePartnerId != null && doc.financeDocNo == null) {
                    AccountsPayable ap = new AccountsPayable();
                    ap.supplierId = doc.financePartnerId;
                    ap.payableType = "OTHER";
                    ap.amount = doc.financeAmount.setScale(2, java.math.RoundingMode.HALF_UP);
                    ap.dueDate = java.time.LocalDate.now().plusDays(30);
                    ap.status = "UNPAID";
                    ap.remark = "其他入库自动生成 " + doc.docNo;
                    AccountsPayable saved = financeService.createAP(ap);
                    doc.financeDocNo = saved.docNo;
                    otherInRepo.save(doc);
                    log.info("其他入库生成AP: 单据={} 金额={}", doc.docNo, doc.financeAmount);
                }
            });
            case "PRODUCTION_INBOUND" -> prodInRepo.findByDocNo(qc.refDocNo).ifPresent(doc -> {
                doc.status = newStatus;
                doc.updateTime = LocalDateTime.now();
                prodInRepo.save(doc);
                // 合格入库 → 自动完工关联生产订单（入库合格即完工）
                if (!"REJECTED".equals(newStatus) && doc.productionOrderNo != null) {
                    try {
                        productionOrderService.autoCompleteByOrderNo(doc.productionOrderNo);
                    } catch (Exception e) {
                        log.error("自动完工失败 入库单={} 订单={} 原因={}", doc.docNo, doc.productionOrderNo, e.getMessage(), e);
                    }
                }
            });
            case "OUTSOURCE_INBOUND" -> outsourceInRepo.findByDocNo(qc.refDocNo).ifPresent(doc -> {
                doc.status = newStatus;
                doc.updateTime = LocalDateTime.now();
                outsourceInRepo.save(doc);
                // 质检合格 → 生成委外加工费应付账款
                if (!"REJECTED".equals(newStatus)) {
                    generateOutsourceAP(doc);
                }
            });
            case "PURCHASE" -> {
                // 采购到货质检合格入库 → 按到货单生成采购应付账款（v5.27：非整单，按到货单金额）
                // refDocNo 为采购订单号；同一到货单仅生成一次 AP（arrivalId 幂等）
                if (!"REJECTED".equals(newStatus)) {
                    generatePurchaseAP(qc);
                }
            }
            default -> { }
        }
    }

    /**
     * 判定合格后触发入库（所有质检均为入库前质检，合格即入库）
     */
    private void triggerInventory(QualityInspection qc, String operator) {
        // 入库类：计算保质期
        LocalDate produceDate = qc.produceDate;
        LocalDate expiryDate = null;
        if (produceDate != null && qc.materialCode != null) {
            // v4.8：按编码直查（原 findAll 全表扫描后过滤，每次质检判定都全量扫物料表）
            var material = materialRepo.findByCode(qc.materialCode);
            if (material.isPresent() && material.get().shelfLifeDays != null) {
                expiryDate = produceDate.plusDays(material.get().shelfLifeDays);
            }
        }

        String docType = switch (qc.refDocType) {
            case "OTHER_INBOUND" -> "OTHER_IN";
            case "PRODUCTION_INBOUND" -> "PRODUCTION_IN";
            case "OUTSOURCE_INBOUND" -> "OUTSOURCE_IN";
            default -> "PURCHASE_IN";
        };
        // v5.63：生产/委外入库按订单归集成本写入批次台账价（此前成品台账价为 0，销售成本失真）；
        // 累计口径 =（领料/发料Σ成本 + 费用）÷（累计入库合格量，含本次），与成本核算页口径一致
        BigDecimal inboundPrice = qc.unitPrice;
        if (inboundPrice == null && ("PRODUCTION_INBOUND".equals(qc.refDocType) || "OUTSOURCE_INBOUND".equals(qc.refDocType))) {
            inboundPrice = orderAggregatedUnitCost(qc.refDocType, qc.refDocNo, qc.qty);
        }
        // 透传质检信息到库存台账（入库即合格标签）
        InventoryService.QcInfo qcInfo = new InventoryService.QcInfo(
                qc.status, qc.inspectionNo, qc.resultRemark, qc.inspector, qc.inspectDate);
        inventoryService.purchaseInboundWithDate(
                docType,
                qc.refDocNo,
                qc.materialCode != null ? qc.materialCode : qc.materialName,
                qc.materialName, qc.batchNo, qc.warehouseId,
                qc.locationId, qc.qty,
                inboundPrice != null ? inboundPrice : BigDecimal.ZERO,
                produceDate, expiryDate, operator, qcInfo);
    }

    /**
     * v5.63 订单归集单位成本（生产=领料Σ+人工+制费；委外=发料Σ+加工费立账），分母=累计合格入库量（含本次）。
     * refDocNo 为入库单号，反查其订单号后聚合；订单无领料记录时返回 0（保持旧口径兜底）。
     */
    private BigDecimal orderAggregatedUnitCost(String refDocType, String refDocNo, BigDecimal thisQty) {
        try {
            boolean production = "PRODUCTION_INBOUND".equals(refDocType);
            String orderNoCol = production ? "production_order_no" : "outsource_order_no";
            var orderRows = jdbc.queryForList(
                    "SELECT " + orderNoCol + " AS o FROM " + (production ? "production_inbound" : "outsource_finish_inbound")
                            + " WHERE doc_no = ?", refDocNo);
            if (orderRows.isEmpty() || orderRows.get(0).get("o") == null) return null;
            String orderNo = String.valueOf(orderRows.get(0).get("o"));

            BigDecimal material = toBd(jdbc.queryForObject(production
                    ? "SELECT COALESCE(SUM(cost),0) FROM production_outbound WHERE production_order_no = ? AND status = 'CONFIRMED'"
                    : "SELECT COALESCE(SUM(cost),0) FROM outsource_material_outbound WHERE outsource_order_no = ? AND status IN ('CONFIRMED','SIGNED')",
                    java.math.BigDecimal.class, orderNo));
            BigDecimal fee = BigDecimal.ZERO;
            if (production) {
                var po = jdbc.queryForList("SELECT COALESCE(labor_fee,0) AS l, COALESCE(overhead_fee,0) AS o FROM production_order WHERE order_no = ?", orderNo);
                if (!po.isEmpty()) {
                    fee = toBd(po.get(0).get("l")).add(toBd(po.get(0).get("o")));
                }
            } else {
                fee = toBd(jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount),0) FROM accounts_payable WHERE payable_type = 'OUTSOURCE' AND outsource_order_no = ?",
                        java.math.BigDecimal.class, orderNo));
            }
            BigDecimal output = toBd(jdbc.queryForObject(production
                    ? "SELECT COALESCE(SUM(qty),0) FROM production_inbound WHERE production_order_no = ? AND status = 'DONE'"
                    : "SELECT COALESCE(SUM(qty),0) FROM outsource_finish_inbound WHERE outsource_order_no = ? AND status = 'DONE'",
                    java.math.BigDecimal.class, orderNo));
            BigDecimal totalQty = output.add(thisQty == null ? BigDecimal.ZERO : thisQty);
            if (totalQty.compareTo(BigDecimal.ZERO) <= 0) return null;
            if (material.add(fee).compareTo(BigDecimal.ZERO) == 0) return null;   // 无成本数据（如老流程）保持 null→0 旧口径
            return material.add(fee).divide(totalQty, 4, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;   // 归集失败回退旧口径（0 价），不阻断入库
        }
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Double d) return BigDecimal.valueOf(d);
        return new BigDecimal(String.valueOf(v));
    }

    /**
     * v5.30：质检不合格 → 货物写入「不合格品库」台账（qcStatus=REJECT，异动复用原入库 docType）。
     * v5.31：精确到库位——优先用判定时指定的库位，未指定则用不合格品库默认库位（隔离区·不合格品位）。
     * 批次/单据号全程可追溯；后续仅可通过「其他出库-报废/退货」从该仓清账。
     */
    private void unqualifiedInbound(QualityInspection qc, String operator, Long unqualifiedLocationId) {
        // v5.38：隔离库为分库；v5.38.3 不合格品库按物料大类分三个——按质检单物料类别路由：
        // 原材料(A/P/F/R/S)→原材料不合格品库、半成品(B)→半成品不合格品库、成品(C)→成品不合格品库；
        // 优先质检单来源仓（隔离货就地存放），该仓无对应分库时兜底全局第一个
        String unqType;
        if (qc.materialCategory != null && "B".equals(qc.materialCategory)) unqType = "UNQUALIFIED_SEMI";
        else if (qc.materialCategory != null && "C".equals(qc.materialCategory)) unqType = "UNQUALIFIED_FIN";
        else unqType = "UNQUALIFIED_RAW";
        WarehouseZone unqZone = null;
        if (qc.warehouseId != null && !qc.warehouseId.isBlank()) {
            try {
                unqZone = zoneRepo.findFirstByWarehouseIdAndZoneTypeAndEnabledTrueOrderBySortOrderAsc(
                        Long.valueOf(qc.warehouseId), unqType).orElse(null);
            } catch (NumberFormatException ignored) { }
        }
        if (unqZone == null) {
            unqZone = zoneRepo.findFirstByZoneTypeAndEnabledTrueOrderBySortOrderAsc(unqType).orElse(null);
        }
        if (unqZone == null) {
            log.error("不合格品分库（zone_type={}）不存在，无法转入：质检单={} 物料={}", unqType, qc.inspectionNo, qc.materialCode);
            return;
        }
        // 解析存放库位：指定库位需属于隔离区分库；否则取默认库位（该分库排序最前的启用库位）
        String locationId;
        if (unqualifiedLocationId != null) {
            WarehouseLocation loc = locationService.getById(unqualifiedLocationId).orElse(null);
            if (loc == null || !unqZone.id.equals(loc.zoneId)) {
                throw new IllegalArgumentException("所选库位不属于该物料类别的不合格品库");
            }
            locationId = String.valueOf(unqualifiedLocationId);
        } else {
            locationId = locationService.listByZone(unqZone.id).stream()
                    .map(l -> String.valueOf(l.id)).findFirst().orElse(null);
            if (locationId == null) {
                log.warn("不合格品库无可用库位，转入无库位台账：质检单={}", qc.inspectionNo);
            }
        }
        String docType = switch (qc.refDocType) {
            case "PRODUCTION_INBOUND" -> "PRODUCTION_IN";
            case "OUTSOURCE_INBOUND" -> "OUTSOURCE_IN";
            default -> "OTHER_IN";
        };
        InventoryService.QcInfo qcInfo = new InventoryService.QcInfo(
                qc.status, qc.inspectionNo, qc.resultRemark, qc.inspector, qc.inspectDate);
        inventoryService.purchaseInboundWithDate(
                docType,
                qc.refDocNo,
                qc.materialCode != null ? qc.materialCode : qc.materialName,
                qc.materialName, qc.batchNo, String.valueOf(unqZone.warehouseId),
                locationId, qc.qty,
                qc.unitPrice != null ? qc.unitPrice : BigDecimal.ZERO,
                qc.produceDate, null, operator, qcInfo);
        log.info("质检不合格转入{}: {} 物料={} 批次={} 数量={} 库位={}", unqZone.name, qc.inspectionNo, qc.materialCode, qc.batchNo, qc.qty, locationId);
    }

    

    /**
     * 委外入库质检合格 → 生成加工费应付账款(AP)
     * AP金额 = 入库数量 × 委外订单加工费单价
     * 避免重复：同一委外订单只生成一次AP（按入库数量一次性结算）
     */
    private void generateOutsourceAP(OutsourceFinishInbound doc) {
        if (doc.outsourceOrderNo == null) return;
        // v6.1 修复（中#5）：原全单一次 AP 导致分批入库第二批不立应付；改按入库批次幂等（同一入库单号只立一次）
        if (!apRepo.findByPurchaseOrderNoOrderByCreateTimeAsc(doc.outsourceOrderNo).stream()
                .filter(ap -> doc.docNo.equals(ap.remark)).toList().isEmpty()) {
            return;
        }
        outsourceOrderRepo.findByOrderNo(doc.outsourceOrderNo).ifPresent(order -> {
            if (order.processingFee == null || order.processingFee.compareTo(BigDecimal.ZERO) <= 0) {
                return; // 未设置加工费，跳过
            }
            if (order.supplierId == null) {
                log.warn("委外订单 {} 未设置代工厂供应商，无法生成AP", order.orderNo);
                return;
            }
            BigDecimal amount = doc.qty.multiply(order.processingFee).setScale(2, java.math.RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

            AccountsPayable ap = new AccountsPayable();
            ap.supplierId = order.supplierId;
            ap.purchaseOrderNo = order.orderNo;
            ap.outsourceOrderNo = order.orderNo;
            ap.payableType = "OUTSOURCE";
            ap.amount = amount;
            ap.dueDate = java.time.LocalDate.now().plusDays(30);
            ap.status = "UNPAID";
            ap.remark = "委外入库自动生成加工费 " + doc.docNo + " (" + doc.qty + "×" + order.processingFee + ")";
            financeService.createAP(ap);
            log.info("委外入库生成AP: 订单={} 入库单={} 加工费={}", order.orderNo, doc.docNo, amount);
        });
    }

    /**
     * 采购质检合格入库 → 按到货单生成采购应付账款（v5.27）
     * AP金额 = 实际到货数量 × 采购单价（到货量可能与订单量不一致，只按到货立账）
     * 幂等：同一到货单仅生成一次 AP（arrivalId 唯一）
     * 赠送(isFree=true)建质检单时单价已传 0，金额为 0 自动跳过
     */
    /**
     * v5.27：质检合格入库后，将台账批号回写到入库单（生产/委外/其他入库）
     * 入库单 batch_no 此前未回写，列表/标签无法展示批号
     */
    private void writeBackBatchNo(QualityInspection qc) {
        if (qc.refDocNo == null) return;
        String batchNo = null;
        for (var l : ledgerRepo.findByQcInspectionNo(qc.inspectionNo)) {
            if (l.batchNo != null && !l.batchNo.isBlank()) { batchNo = l.batchNo; break; }
        }
        if (batchNo == null) return;
        final String fb = batchNo;
        try {
            switch (qc.refDocType) {
                case "PRODUCTION_INBOUND" -> prodInRepo.findByDocNo(qc.refDocNo).ifPresent(doc -> {
                    doc.batchNo = fb;
                    prodInRepo.save(doc);
                });
                case "OUTSOURCE_INBOUND" -> outsourceInRepo.findByDocNo(qc.refDocNo).ifPresent(doc -> {
                    doc.batchNo = fb;
                    outsourceInRepo.save(doc);
                });
                case "OTHER_INBOUND" -> otherInRepo.findByDocNo(qc.refDocNo).ifPresent(doc -> {
                    doc.batchNo = fb;
                    otherInRepo.save(doc);
                });
                // v5.95.2：采购到货合格 → 台账批号回填到货单（单号+物料+数量匹配），到货明细/台账批号一致
                case "PURCHASE" -> arrivalRepo.findByRefOrderNoAndMaterialCodeAndQty(qc.refDocNo, qc.materialCode, qc.qty)
                        .forEach(arr -> { if (arr.batchNo == null || arr.batchNo.isBlank()) { arr.batchNo = fb; arrivalRepo.save(arr); } });
                default -> { }
            }
            // 质检单自身批号也回填（判定时 qc.batchNo 为空 → 入库台账生成的批号回写，列表/追溯可见）
            if (qc.batchNo == null || qc.batchNo.isBlank()) { qc.batchNo = fb; qcRepo.save(qc); }
        } catch (Exception e) {
            log.warn("回写入库单批号失败: 质检单={} 原因={}", qc.inspectionNo, e.getMessage());
        }
    }

    /**
     * 历史数据补偿（v5.27）：扫描已合格的生产/委外/其他入库质检单，
     * 将台账批号回填到入库单（此前未回写）。幂等：只处理 batch_no 为空的行。
     * @return 回填条数
     */
    @Transactional
    public int backfillInboundBatchNo() {
        int count = 0;
        for (QualityInspection qc : qcRepo.findByTypeOrderByCreateTimeDesc("INCOMING")) {
            if (!"PASS".equals(qc.status) && !"CONCESSION".equals(qc.status)) continue;
            if (qc.refDocNo == null) continue;
            String batchNo = null;
            for (var l : ledgerRepo.findByQcInspectionNo(qc.inspectionNo)) {
                if (l.batchNo != null && !l.batchNo.isBlank()) { batchNo = l.batchNo; break; }
            }
            if (batchNo == null) continue;
            final String fb = batchNo;
            try {
                switch (qc.refDocType) {
                    case "PRODUCTION_INBOUND" -> {
                        var d = prodInRepo.findByDocNo(qc.refDocNo);
                        if (d.isPresent() && (d.get().batchNo == null || d.get().batchNo.isBlank())) {
                            d.get().batchNo = fb;
                            prodInRepo.save(d.get());
                            count++;
                        }
                    }
                    case "OUTSOURCE_INBOUND" -> {
                        var d = outsourceInRepo.findByDocNo(qc.refDocNo);
                        if (d.isPresent() && (d.get().batchNo == null || d.get().batchNo.isBlank())) {
                            d.get().batchNo = fb;
                            outsourceInRepo.save(d.get());
                            count++;
                        }
                    }
                    case "OTHER_INBOUND" -> {
                        var d = otherInRepo.findByDocNo(qc.refDocNo);
                        if (d.isPresent() && (d.get().batchNo == null || d.get().batchNo.isBlank())) {
                            d.get().batchNo = fb;
                            otherInRepo.save(d.get());
                            count++;
                        }
                    }
                    default -> { }
                }
            } catch (Exception e) {
                log.warn("回填入库单批号失败: 质检单={} 原因={}", qc.inspectionNo, e.getMessage());
            }
        }
        if (count > 0) {
            log.info("历史数据补偿：入库单批号回填 {} 条", count);
        }
        return count;
    }

    /**
     * v5.27：按采购单号+物料+数量反查合格入库批号（到货明细展示/打印标签）
     * 链路：质检单（同单号同物料同数量）→ 合格入库台账（qc_inspection_no）
     */
    public String resolveBatchNo(String refDocNo, String materialCode, java.math.BigDecimal qty) {
        if (refDocNo == null) return null;
        for (QualityInspection q : qcRepo.findByRefDocNoAndType(refDocNo, "INCOMING")) {
            if (materialCode != null && !materialCode.equals(q.materialCode)) continue;
            if (qty == null || q.qty == null || q.qty.compareTo(qty) != 0) continue;
            for (var l : ledgerRepo.findByQcInspectionNo(q.inspectionNo)) {
                if (l.batchNo != null && !l.batchNo.isBlank()) return l.batchNo;
            }
        }
        return null;
    }

    /**
     * 采购质检合格入库 → 按到货单生成采购应付账款（v5.27）
     * 立账依据 = 质检单对应的到货单：金额 = 到货数量 × 采购单价（非采购订单整单金额）
     * 幂等：同一到货单仅生成一次 AP（arrivalId 唯一）
     * @return 是否生成了 AP
     */
    private boolean generatePurchaseAP(QualityInspection qc) {
        if (qc == null || qc.refDocNo == null || qc.refDocNo.isBlank()) return false;
        // 匹配质检单对应的到货单（recordArrival 每批到货一行：同单号+同物料+同数量，取最新）
        PurchaseArrival arrival = arrivalRepo.findByRefOrderNo(qc.refDocNo).stream()
                .filter(a -> qc.materialCode != null && qc.materialCode.equals(a.materialCode))
                .filter(a -> a.qty != null && a.qty.compareTo(qc.qty) == 0)
                .max(java.util.Comparator.comparing(a -> a.id))
                .orElse(null);
        if (arrival == null) {
            log.warn("采购质检合格但未匹配到到货单（跳过立账，需人工核查）: 质检单={} 采购单={} 物料={} 数量={}",
                    qc.inspectionNo, qc.refDocNo, qc.materialCode, qc.qty);
            return false;
        }
        // 幂等：同一到货单只立一张 AP
        if (!apRepo.findByArrivalId(arrival.id).isEmpty()) return false;

        // 金额 = 到货数量 × 采购单价（赠送建质检单时单价已传 0）
        BigDecimal unitPrice = qc.unitPrice != null ? qc.unitPrice : BigDecimal.ZERO;
        BigDecimal amount = qc.qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || arrival.supplierId == null) return false;

        AccountsPayable ap = new AccountsPayable();
        ap.supplierId = arrival.supplierId;
        ap.purchaseOrderNo = qc.refDocNo;
        ap.arrivalId = arrival.id;
        ap.payableType = "PURCHASE";
        ap.amount = amount;
        ap.dueDate = financeService.calcApDueDate(arrival.supplierId, java.time.LocalDate.now().plusDays(30)); // v5.6：按供应商付款条件
        ap.status = "UNPAID";
        ap.remark = "采购到货质检合格自动生成 " + qc.refDocNo + "（到货单#" + arrival.id + "）";
        financeService.createAP(ap);
        log.info("采购到货生成AP(按到货单): 到货单#{} 订单={} 物料={} {}×{}={}", arrival.id, qc.refDocNo,
                qc.materialCode, qc.qty, unitPrice, amount);
        return true;
    }

    /**
     * 历史数据补偿：扫描已质检合格(PASS/CONCESSION)的采购质检单，
     * 按质检单（对应到货单）补生成采购应付账款（recordArrival 路径此前未生成AP）。
     * 幂等：generatePurchaseAP 内部按到货单查重。
     * 使用 JPA Repository 操作，确保日期字段存储格式与 JPA 一致。
     * @return 实际补生成的AP条数
     */
    @Transactional
    public int backfillPurchaseAPFromQc() {
        // 查所有采购类型已合格的质检单
        var qcs = qcRepo.findByTypeOrderByCreateTimeDesc("INCOMING");
        int count = 0;
        for (QualityInspection qc : qcs) {
            if (!"PURCHASE".equals(qc.refDocType)) continue;
            if (!"PASS".equals(qc.status) && !"CONCESSION".equals(qc.status)) continue;
            if (qc.refDocNo == null) continue;
            try {
                if (generatePurchaseAP(qc)) count++;
            } catch (Exception e) {
                log.warn("补偿生成AP失败: 质检单={} 原因={}", qc.inspectionNo, e.getMessage());
            }
        }
        if (count > 0) {
            log.info("历史数据补偿：质检单路径补生成 {} 条采购应付账款", count);
        }
        return count;
    }
}
