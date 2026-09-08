package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.FinishedProductPurchase;
import com.pengyuan.pims.entity.InventoryLedger;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.entity.LossLetterTemplate;
import com.pengyuan.pims.entity.OutsourceFinishInbound;
import com.pengyuan.pims.entity.OutsourceOrder;
import com.pengyuan.pims.entity.PurchaseArrival;
import com.pengyuan.pims.entity.QualityInspection;
import com.pengyuan.pims.entity.RawMaterialPurchase;
import com.pengyuan.pims.entity.SupplierQualityTrace;
import com.pengyuan.pims.repository.FinishedProductPurchaseRepository;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import com.pengyuan.pims.repository.InventoryMovementRepository;
import com.pengyuan.pims.repository.LossLetterTemplateRepository;
import com.pengyuan.pims.repository.OutsourceFinishInboundRepository;
import com.pengyuan.pims.repository.OutsourceOrderRepository;
import com.pengyuan.pims.repository.PurchaseArrivalRepository;
import com.pengyuan.pims.repository.QualityInspectionRepository;
import com.pengyuan.pims.repository.RawMaterialPurchaseRepository;
import com.pengyuan.pims.repository.SupplierQualityTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v5.59 供应商质量追溯。批号驱动：品控选批次（必须精确到批号）→ 系统自动反查原始采购入库信息
 * （台账→质检单 refDocNo→到货表/采购单的供应商；备用链含归档流水 PURCHASE_IN 的 docNo）→
 * 打损失沟通函（多模板）→ 采购处理（协商折让/赔款/补货/换货/供应商拒绝赔付/免赔/其他）→
 * 处理完毕强制有处理结果，RESOLVED 永久留档。
 */
@Service
public class SupplierQualityTraceService {

    private static final Logger log = LoggerFactory.getLogger(SupplierQualityTraceService.class);

    private final SupplierQualityTraceRepository traceRepo;
    private final LossLetterTemplateRepository templateRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final QualityInspectionRepository qcRepo;
    private final InventoryMovementRepository movementRepo;
    private final PurchaseArrivalRepository arrivalRepo;
    private final RawMaterialPurchaseRepository rawRepo;
    private final FinishedProductPurchaseRepository finishedRepo;
    private final OutsourceOrderRepository outsourceRepo;
    private final OutsourceFinishInboundRepository finishInboundRepo;
    private final UserService userService;
    private final WriteQueue writeQueue;

    public SupplierQualityTraceService(SupplierQualityTraceRepository traceRepo,
                                       LossLetterTemplateRepository templateRepo,
                                       InventoryLedgerRepository ledgerRepo,
                                       QualityInspectionRepository qcRepo,
                                       InventoryMovementRepository movementRepo,
                                       PurchaseArrivalRepository arrivalRepo,
                                       RawMaterialPurchaseRepository rawRepo,
                                       FinishedProductPurchaseRepository finishedRepo,
                                       OutsourceOrderRepository outsourceRepo,
                                       OutsourceFinishInboundRepository finishInboundRepo,
                                       UserService userService,
                                       WriteQueue writeQueue) {
        this.traceRepo = traceRepo;
        this.templateRepo = templateRepo;
        this.ledgerRepo = ledgerRepo;
        this.qcRepo = qcRepo;
        this.movementRepo = movementRepo;
        this.arrivalRepo = arrivalRepo;
        this.rawRepo = rawRepo;
        this.finishedRepo = finishedRepo;
        this.outsourceRepo = outsourceRepo;
        this.finishInboundRepo = finishInboundRepo;
        this.userService = userService;
        this.writeQueue = writeQueue;
    }

    public List<SupplierQualityTrace> list(Long supplierId, String status) {
        if (supplierId != null && status != null && !status.isBlank()) return traceRepo.findBySupplierIdAndStatusOrderByCreateTimeDescIdDesc(supplierId, status);
        if (supplierId != null) return traceRepo.findBySupplierIdOrderByCreateTimeDescIdDesc(supplierId);
        if (status != null && !status.isBlank()) return traceRepo.findByStatusOrderByCreateTimeDescIdDesc(status);
        return traceRepo.findAllByOrderByCreateTimeDescIdDesc();
    }

    public SupplierQualityTrace getById(Long id) {
        return traceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("追溯单不存在"));
    }

    /** 登记（品控发起）：批次必选，供应商反查兜底补全，采购信息快照存单 */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public SupplierQualityTrace create(SupplierQualityTrace t) {
        return writeQueue.executeTx(() -> {
            if (t.materialCode == null || t.materialCode.isBlank() || t.batchNo == null || t.batchNo.isBlank())
                throw new IllegalArgumentException("必须选择批次（精确到批号）");
            if (t.description == null || t.description.isBlank()) throw new IllegalArgumentException("请填写问题描述");
            // 兜底补全：前端选批次时已调 /purchase-info 回填，此处再查一次防绕过/漏字段
            Map<String, Object> info = purchaseInfo(t.materialCode, t.batchNo);
            if (t.supplierName == null || t.supplierName.isBlank()) {
                if (info.get("supplierName") == null)
                    throw new IllegalArgumentException("该批次反查不到供应商，请手动选择供应商");
                t.supplierName = String.valueOf(info.get("supplierName"));
                t.supplierId = toLong(info.get("supplierId"));
            }
            if (isBlank(t.materialName) && info.get("materialName") != null) t.materialName = String.valueOf(info.get("materialName"));
            if (isBlank(t.purchaseOrderNo) && info.get("purchaseOrderNo") != null) t.purchaseOrderNo = String.valueOf(info.get("purchaseOrderNo"));
            if (isBlank(t.orderCategory) && info.get("orderCategory") != null) t.orderCategory = String.valueOf(info.get("orderCategory"));
            if (isBlank(t.qcInspectionNo) && info.get("qcInspectionNo") != null) t.qcInspectionNo = String.valueOf(info.get("qcInspectionNo"));
            if (isBlank(t.qcStatus) && info.get("qcStatus") != null) t.qcStatus = String.valueOf(info.get("qcStatus"));
            if (t.arrivalDate == null && info.get("arrivalDate") != null) t.arrivalDate = toLocalDate(info.get("arrivalDate"));
            if (t.purchaseQty == null) t.purchaseQty = toBd(info.get("inboundQty"));
            if (t.purchaseUnitPrice == null) t.purchaseUnitPrice = toBd(info.get("unitPrice"));
            if (t.purchaseAmount == null) t.purchaseAmount = toBd(info.get("amount"));

            Integer maxSeq = traceRepo.findMaxSeq("ZS-" + LocalDate.now().toString().replace("-", "") + "-%");
            t.traceNo = String.format("ZS-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            t.status = "PROCESSING";
            t.printCount = 0;
            t.createdBy = userService.currentOperatorName();  // v5.60 制单人
            if (t.issueDate == null) t.issueDate = LocalDate.now();
            t.createTime = LocalDateTime.now();
            SupplierQualityTrace saved = traceRepo.save(t);
            log.info("质量追溯单登记: {} 供应商={} 批次={}/{} 损失={}", saved.traceNo, saved.supplierName,
                    saved.materialCode, saved.batchNo, saved.lossAmount);
            return saved;
        });
    }

    /** 仅处理中可编辑 */
    @Transactional
    public SupplierQualityTrace update(Long id, SupplierQualityTrace in) {
        SupplierQualityTrace t = getById(id);
        if (!"PROCESSING".equals(t.status)) throw new IllegalArgumentException("已处理完毕的追溯单不可编辑（记录留存）");
        t.supplierId = in.supplierId;
        t.supplierName = in.supplierName;
        t.purchaseOrderNo = in.purchaseOrderNo;
        t.orderCategory = in.orderCategory;
        t.materialCode = in.materialCode;
        t.materialName = in.materialName;
        t.batchNo = in.batchNo;
        t.arrivalDate = in.arrivalDate;
        t.purchaseQty = in.purchaseQty;
        t.purchaseUnitPrice = in.purchaseUnitPrice;
        t.purchaseAmount = in.purchaseAmount;
        t.qcInspectionNo = in.qcInspectionNo;
        t.qcStatus = in.qcStatus;
        t.issueDate = in.issueDate;
        t.category = in.category;
        t.description = in.description;
        t.lossAmount = in.lossAmount;
        t.remark = in.remark;
        t.updateTime = LocalDateTime.now();
        return traceRepo.save(t);
    }

    /** 处理完毕（采购）：PROCESSING → RESOLVED。必须有处理结果：结果类型+说明必填，赔款时金额必填 */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public SupplierQualityTrace resolve(Long id, String resultType, String resultRemark,
                                        BigDecimal compensationAmount, String handler, LocalDate resolveDate) {
        return writeQueue.executeTx(() -> {
            SupplierQualityTrace t = getById(id);
            if (!"PROCESSING".equals(t.status)) throw new IllegalArgumentException("该追溯单已处理完毕");
            if (resultType == null || resultType.isBlank()) throw new IllegalArgumentException("请选择处理结果类型");
            if (resultRemark == null || resultRemark.isBlank()) throw new IllegalArgumentException("请填写处理说明");
            if ("赔款".equals(resultType) && (compensationAmount == null || compensationAmount.compareTo(BigDecimal.ZERO) <= 0))
                throw new IllegalArgumentException("处理结果为「赔款」时必须填写赔付金额");
            t.resultType = resultType;
            t.resultRemark = resultRemark;
            t.compensationAmount = compensationAmount;
            t.handler = handler;
            t.resolveDate = resolveDate != null ? resolveDate : LocalDate.now();
            t.status = "RESOLVED";
            t.updateTime = LocalDateTime.now();
            SupplierQualityTrace saved = traceRepo.save(t);
            log.info("质量追溯单处理完毕: {} 结果={} 赔付={}", saved.traceNo, saved.resultType, saved.compensationAmount);
            return saved;
        });
    }

    /** 仅处理中可删除（处理完毕的永久留存） */
    @Transactional
    public void delete(Long id) {
        SupplierQualityTrace t = getById(id);
        if (!"PROCESSING".equals(t.status)) throw new IllegalArgumentException("已处理完毕的追溯单不可删除（记录留存）");
        traceRepo.delete(t);
        log.info("质量追溯单删除（处理中纠错）: {}", t.traceNo);
    }

    /** 批次追溯：该批次全部出入库流水（主表+归档表合并） */
    public List<InventoryMovement> trace(Long id) {
        SupplierQualityTrace t = getById(id);
        if (t.materialCode == null || t.materialCode.isBlank() || t.batchNo == null || t.batchNo.isBlank())
            throw new IllegalArgumentException("该追溯单缺少物料编码或批号，无法追溯");
        return movementRepo.findByMaterialCodeAndBatchNoIncludingArchive(t.materialCode, t.batchNo);
    }

    /** 品控选批次：关键字（编码/品名/批号）搜台账，按物料+批号聚合。含不合格/油尾批次——追溯对象常是它们 */
    public List<Map<String, Object>> batchOptions(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<InventoryLedger> rows = ledgerRepo.searchForQualityTrace(kw,
                org.springframework.data.domain.PageRequest.of(0, 300)).getContent();
        Map<String, Map<String, Object>> byBatch = new LinkedHashMap<>();
        for (InventoryLedger l : rows) {
            if (l.materialCode == null || l.batchNo == null || l.batchNo.isBlank()) continue;
            String key = l.materialCode + "|" + l.batchNo;
            Map<String, Object> b = byBatch.get(key);
            if (b == null) {
                b = new LinkedHashMap<>();
                b.put("materialCode", l.materialCode);
                b.put("materialName", l.materialName);
                b.put("batchNo", l.batchNo);
                b.put("unit", l.unit);
                b.put("inboundDate", l.inboundDate);
                b.put("unitPrice", l.unitPrice);
                b.put("qcStatus", l.qcStatus);
                b.put("qcInspectionNo", l.qcInspectionNo);
                b.put("qty", BigDecimal.ZERO);
                byBatch.put(key, b);
            }
            if (l.qty != null) b.put("qty", ((BigDecimal) b.get("qty")).add(l.qty));
        }
        return new java.util.ArrayList<>(byBatch.values());
    }

    /** 按物料+批号反查原始采购入库信息（品控选批次后自动带出，不手填）。
     * 主链：台账行（质检单号/单价/入库日期）→ 质检单 refDocNo（采购单号，refDocType=PURCHASE）→ 到货表反查供应商。
     * 备用链（老批次可靠）：含归档表的入库流水 docType=PURCHASE_IN 行的 docNo 即采购单号（有索引）。
     * 供应商反查不到返回 null（前端允许手选供应商兜底，不阻断登记）。
     */
    public Map<String, Object> purchaseInfo(String materialCode, String batchNo) {
        if (materialCode == null || materialCode.isBlank() || batchNo == null || batchNo.isBlank())
            throw new IllegalArgumentException("请提供物料编码和批号");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("materialCode", materialCode);
        info.put("batchNo", batchNo);

        List<InventoryLedger> ledgers = ledgerRepo.findByMaterialCodeAndBatchNo(materialCode, batchNo);
        InventoryLedger ledger = ledgers.isEmpty() ? null : ledgers.get(0);
        if (ledger != null) {
            info.put("materialName", ledger.materialName);
            info.put("unit", ledger.unit);
            info.put("inboundDate", ledger.inboundDate);
            info.put("ledgerQty", ledgers.stream().map(l -> l.qty).filter(q -> q != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            info.put("ledgerUnitPrice", ledger.unitPrice);
            info.put("qcInspectionNo", ledger.qcInspectionNo);
            info.put("qcStatus", ledger.qcStatus);
            info.put("qcResult", ledger.qcResult);
        }

        QualityInspection qc = null;
        String qcNo = ledger != null ? ledger.qcInspectionNo : null;
        if (qcNo != null) qc = qcRepo.findByInspectionNo(qcNo).orElse(null);
        // 来源单号与类型（v5.59.2 委外链）：采购链 PURCHASE→采购单号；委外链 OUTSOURCE_INBOUND→
        // refDocNo 是委外成品入库单号（OUT-FG-），需映射到委外订单号（OO-，代工厂挂在委外订单上）
        String orderNo = null;
        String orderCategory = null;
        OutsourceFinishInbound finInbound = null;
        if (qc != null && qc.refDocNo != null) {
            if ("PURCHASE".equals(qc.refDocType)) {
                orderNo = qc.refDocNo;
                orderCategory = "PURCHASE";
            } else if ("OUTSOURCE_INBOUND".equals(qc.refDocType)) {
                finInbound = finishInboundRepo.findByDocNo(qc.refDocNo).orElse(null);
                if (finInbound != null && finInbound.outsourceOrderNo != null) {
                    orderNo = finInbound.outsourceOrderNo;
                    orderCategory = "OUTSOURCE";
                }
            }
        }
        if (orderNo == null) {    // 备用链：含归档流水（老批次可靠）
            for (InventoryMovement m : movementRepo.findByMaterialCodeAndBatchNoIncludingArchive(materialCode, batchNo)) {
                if (!"IN".equals(m.direction) || m.docNo == null) continue;
                if ("PURCHASE_IN".equals(m.docType)) { orderNo = m.docNo; orderCategory = "PURCHASE"; break; }
                if ("OUTSOURCE_IN".equals(m.docType)) {
                    finInbound = finishInboundRepo.findByDocNo(m.docNo).orElse(null);
                    if (finInbound != null && finInbound.outsourceOrderNo != null) {
                        orderNo = finInbound.outsourceOrderNo;
                        orderCategory = "OUTSOURCE";
                        break;
                    }
                }
            }
        }
        info.put("purchaseOrderNo", orderNo);
        info.put("orderCategory", orderCategory);

        // 供应商反查：委外→委外订单（processor 代工厂 + supplierId）；采购→到货表→原料/成品采购单兜底
        Long supplierId = null;
        String supplierName = null;
        LocalDate arrivalDate = null;
        if ("OUTSOURCE".equals(orderCategory) && orderNo != null) {
            OutsourceOrder oo = outsourceRepo.findByOrderNo(orderNo).orElse(null);
            if (oo != null) {
                supplierId = oo.supplierId;
                supplierName = oo.processor;
            }
            if (finInbound != null && finInbound.createTime != null) arrivalDate = finInbound.createTime.toLocalDate();
        } else if (orderNo != null) {
            PurchaseArrival arrival = arrivalRepo.findByRefOrderNo(orderNo).stream()
                    .filter(a -> materialCode.equals(a.materialCode))
                    .max(Comparator.comparing(a -> a.id))
                    .orElse(null);
            if (arrival != null) {
                supplierId = arrival.supplierId;
                supplierName = arrival.supplierName;
                arrivalDate = arrival.arrivalDate;
            }
            if (supplierId == null) {   // 到货表缺供应商时按采购单明细兜底
                for (RawMaterialPurchase rp : rawRepo.findAllByOrderNo(orderNo)) {
                    if (materialCode.equals(rp.materialCode)) { supplierId = rp.supplierId; supplierName = rp.supplierName; break; }
                }
            }
            if (supplierId == null) {
                for (FinishedProductPurchase fp : finishedRepo.findAllByOrderNo(orderNo)) {
                    if (materialCode.equals(fp.materialCode)) { supplierId = fp.supplierId; supplierName = fp.supplierName; break; }
                }
            }
        }
        info.put("supplierId", supplierId);
        info.put("supplierName", supplierName);
        info.put("arrivalDate", arrivalDate);

        // 入库数量：质检单 qty（=到货量）→ 委外入库单 qty → 台账合计（余量）
        BigDecimal inboundQty = qc != null ? qc.qty : null;
        if (inboundQty == null && finInbound != null) inboundQty = finInbound.qty;
        if (inboundQty == null && ledger != null) inboundQty = toBd(info.get("ledgerQty"));
        info.put("inboundQty", inboundQty);
        // 单价：台账（入库时采购价）→ 质检单 → 委外加工费 / 采购单明细
        BigDecimal unitPrice = ledger != null ? ledger.unitPrice : null;
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) unitPrice = qc != null ? qc.unitPrice : null;
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            if ("OUTSOURCE".equals(orderCategory) && orderNo != null) {
                unitPrice = outsourceRepo.findByOrderNo(orderNo).map(o -> o.processingFee).orElse(null);
            } else if (orderNo != null) {
                unitPrice = findPurchaseUnitPrice(orderNo, materialCode);
            }
        }
        info.put("unitPrice", unitPrice);
        info.put("amount", inboundQty != null && unitPrice != null
                ? inboundQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP) : null);
        return info;
    }

    /** 按采购单号+物料取采购单价（照 ReturnOrderService 口径） */
    private BigDecimal findPurchaseUnitPrice(String orderNo, String materialCode) {
        if (orderNo == null || materialCode == null) return null;
        for (RawMaterialPurchase r : rawRepo.findAllByOrderNo(orderNo)) {
            if (materialCode.equals(r.materialCode) && r.unitPrice != null) return r.unitPrice;
        }
        for (FinishedProductPurchase f : finishedRepo.findAllByOrderNo(orderNo)) {
            if (materialCode.equals(f.materialCode) && f.unitPrice != null) return f.unitPrice;
        }
        return null;
    }

    // ==================== 损失沟通函模板 ====================

    public List<LossLetterTemplate> listTemplates() {
        return templateRepo.findAllByOrderByIsDefaultDescIdAsc();
    }

    public LossLetterTemplate createTemplate(LossLetterTemplate t) {
        return writeQueue.executeTx(() -> {
            if (t.name == null || t.name.isBlank()) throw new IllegalArgumentException("请填写模板名称");
            if (t.bodyText == null || t.bodyText.isBlank()) throw new IllegalArgumentException("请填写问题与损失正文");
            if (Boolean.TRUE.equals(t.isDefault)) clearDefault();
            t.id = null;
            if (t.enabled == null) t.enabled = true;
            if (t.isDefault == null) t.isDefault = false;
            t.createTime = LocalDateTime.now();
            return templateRepo.save(t);
        });
    }

    public LossLetterTemplate updateTemplate(Long id, LossLetterTemplate in) {
        return writeQueue.executeTx(() -> {
            LossLetterTemplate t = templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("模板不存在"));
            if (in.name == null || in.name.isBlank()) throw new IllegalArgumentException("请填写模板名称");
            if (in.bodyText == null || in.bodyText.isBlank()) throw new IllegalArgumentException("请填写问题与损失正文");
            t.name = in.name;
            t.openingText = in.openingText;
            t.bodyText = in.bodyText;
            t.requireText = in.requireText;
            t.closingText = in.closingText;
            t.updateTime = LocalDateTime.now();
            return templateRepo.save(t);
        });
    }

    /** 至少保留一个模板；删的是默认模板时把默认转移给剩余第一个 */
    public void deleteTemplate(Long id) {
        writeQueue.executeTx(() -> {
            LossLetterTemplate t = templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("模板不存在"));
            long total = templateRepo.count();
            if (total <= 1) throw new IllegalArgumentException("至少保留一个函件模板");
            boolean wasDefault = Boolean.TRUE.equals(t.isDefault);
            templateRepo.delete(t);
            if (wasDefault) {
                templateRepo.findAllByOrderByIsDefaultDescIdAsc().stream().findFirst().ifPresent(next -> {
                    next.isDefault = true;
                    templateRepo.save(next);
                });
            }
            log.info("损失沟通函模板删除: {}{}", t.name, wasDefault ? "（默认已转移）" : "");
            return null;
        });
    }

    public LossLetterTemplate setDefault(Long id) {
        return writeQueue.executeTx(() -> {
            LossLetterTemplate t = templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("模板不存在"));
            clearDefault();
            t.isDefault = true;
            t.updateTime = LocalDateTime.now();
            return templateRepo.save(t);
        });
    }

    private void clearDefault() {
        templateRepo.findByIsDefaultTrue().ifPresent(d -> { d.isDefault = false; templateRepo.save(d); });
    }

    // ==================== 小工具（类型兼容） ====================

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        String s = v.toString();
        return s.length() >= 10 ? LocalDate.parse(s.substring(0, 10)) : null;
    }
}
