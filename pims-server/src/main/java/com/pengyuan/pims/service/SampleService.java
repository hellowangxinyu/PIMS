package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.RdProgress;
import com.pengyuan.pims.entity.Recipe;
import com.pengyuan.pims.entity.RecipeVersion;
import com.pengyuan.pims.entity.SampleFormula;
import com.pengyuan.pims.entity.SampleFormulaHistory;
import com.pengyuan.pims.entity.SampleFormulaItem;
import com.pengyuan.pims.entity.SampleRequest;
import com.pengyuan.pims.repository.RdProgressRepository;
import com.pengyuan.pims.repository.RecipeRepository;
import com.pengyuan.pims.repository.RecipeVersionRepository;
import com.pengyuan.pims.repository.SampleFormulaHistoryRepository;
import com.pengyuan.pims.repository.SampleFormulaItemRepository;
import com.pengyuan.pims.repository.SampleFormulaRepository;
import com.pengyuan.pims.repository.SampleRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * v5.53 打样/样品管理。涂料成单链路：
 * 申请 → 调色（自动在研发进度建条目，研发在熟悉的页面跟进）→ 寄样 → 客户反馈
 * → 满意则转单 / 需调整则回炉调色（adjustCount 计轮次）→ 成交或未成交（研发进度自动结案）。
 */
@Service
public class SampleService {

    private static final Logger log = LoggerFactory.getLogger(SampleService.class);

    private static final Map<String, String> STATUS_LABEL = Map.of(
            "APPLIED", "已申请", "ASSIGNED", "已派发", "COLORING", "调色中", "FORMULATED", "已录配方",
            "SENT", "已寄样", "SATISFIED", "客户满意", "ADJUST", "需调整", "WON", "已转单", "LOST", "未成交");

    private final SampleRequestRepository sampleRepo;
    private final RdProgressRepository rdRepo;
    private final WriteQueue writeQueue;
    private final SampleFormulaRepository formulaRepo;
    private final SampleFormulaItemRepository formulaItemRepo;
    private final SampleFormulaHistoryRepository formulaHistoryRepo;
    private final RecipeRepository recipeRepo;
    private final RecipeVersionRepository recipeVersionRepo;
    private final MaterialService materialService;
    private final RecipeService recipeService;
    private final com.pengyuan.pims.service.UserService userService;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public SampleService(SampleRequestRepository sampleRepo, RdProgressRepository rdRepo, WriteQueue writeQueue,
                         SampleFormulaRepository formulaRepo, SampleFormulaItemRepository formulaItemRepo,
                         SampleFormulaHistoryRepository formulaHistoryRepo,
                         RecipeRepository recipeRepo, RecipeVersionRepository recipeVersionRepo,
                         MaterialService materialService, RecipeService recipeService,
                         com.pengyuan.pims.service.UserService userService) {
        this.sampleRepo = sampleRepo;
        this.rdRepo = rdRepo;
        this.writeQueue = writeQueue;
        this.formulaRepo = formulaRepo;
        this.formulaItemRepo = formulaItemRepo;
        this.formulaHistoryRepo = formulaHistoryRepo;
        this.recipeRepo = recipeRepo;
        this.recipeVersionRepo = recipeVersionRepo;
        this.materialService = materialService;
        this.recipeService = recipeService;
        this.userService = userService;
    }

    public List<SampleRequest> list(Long customerId, String status) {
        List<SampleRequest> list;
        if (customerId != null && status != null && !status.isBlank()) list = sampleRepo.findByCustomerIdAndStatusOrderByCreateTimeDescIdDesc(customerId, status);
        else if (customerId != null) list = sampleRepo.findByCustomerIdOrderByCreateTimeDescIdDesc(customerId);
        else if (status != null && !status.isBlank()) list = sampleRepo.findByStatusOrderByCreateTimeDescIdDesc(status);
        else list = sampleRepo.findAllByOrderByCreateTimeDescIdDesc();
        list.forEach(this::fillLabel);
        return list;
    }

    private void fillLabel(SampleRequest s) { s.statusLabel = STATUS_LABEL.getOrDefault(s.status, s.status); }

    public SampleRequest getById(Long id) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        fillLabel(s);
        return s;
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public SampleRequest create(SampleRequest s) {
        return writeQueue.executeTx(() -> {
            if (s.customerName == null || s.customerName.isBlank()) throw new IllegalArgumentException("请填写客户/线索公司");
            if (s.materialDesc == null || s.materialDesc.isBlank()) throw new IllegalArgumentException("请填写意向产品/颜色要求");
            Integer maxSeq = sampleRepo.findMaxSeq("DY-" + LocalDate.now().toString().replace("-", "") + "-%");
            s.sampleNo = String.format("DY-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            s.status = "APPLIED";
            s.applyDate = LocalDate.now();
            s.adjustCount = 0;
            SampleRequest saved = sampleRepo.save(s);
            log.info("打样申请创建: {} 客户={}", saved.sampleNo, saved.customerName);
            fillLabel(saved);
            return saved;
        });
    }

    /** 仅 APPLIED 可编辑基本信息 */
    @Transactional
    public SampleRequest update(Long id, SampleRequest in) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"APPLIED".equals(s.status)) throw new IllegalArgumentException("只有已申请状态可编辑（进入调色后请走流转操作）");
        s.customerId = in.customerId;
        s.customerName = in.customerName;
        s.materialCode = in.materialCode;
        s.refSampleId = in.refSampleId;   // v7.7.2 关联打样（复样参考）
        s.materialDesc = in.materialDesc;
        s.qty = in.qty;
        s.unit = in.unit;
        s.sampleSize = in.sampleSize;   // v7.7.3 打样尺寸 NORMAL/A4
        s.applicant = in.applicant;
        s.remark = in.remark;
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 开始调色：APPLIED/ADJUST → COLORING，首次自动在研发进度建条目 */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public SampleRequest startColoring(Long id, String colorist, String colorNote) {
        return writeQueue.executeTx(() -> {
            SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
            if (!"APPLIED".equals(s.status) && !"ADJUST".equals(s.status))
                throw new IllegalArgumentException("只有已申请/需调整状态可开始调色");
            s.colorist = colorist;
            s.colorNote = (s.colorNote == null ? "" : s.colorNote + " | ") + (colorNote != null ? colorNote : "");
            s.status = "COLORING";
            // 研发进度联动：首次调色建条目（分类=调色），之后每次回炉更新进度备注
            if (s.rdProgressId == null) {
                RdProgress rd = new RdProgress();
                rd.raiseDate = LocalDate.now();
                rd.owner = s.applicant != null ? s.applicant : "销售";
                rd.category = "调色";
                rd.content = "打样 " + s.sampleNo + "：" + s.customerName + " " + s.materialDesc;
                rd.progress = "第 1 轮调色（调色员：" + (colorist != null ? colorist : "-") + "）";
                rd.createdBy = "sample:" + s.sampleNo;
                rd = rdRepo.save(rd);
                s.rdProgressId = rd.id;
            } else {
                RdProgress rd = rdRepo.findById(s.rdProgressId).orElse(null);
                if (rd != null) {
                    rd.progress = "第 " + (s.adjustCount + 1) + " 轮调色（调色员：" + (colorist != null ? colorist : "-") + "）";
                    rd.updateTime = java.time.LocalDateTime.now();
                    rdRepo.save(rd);
                }
            }
            s.updateTime = java.time.LocalDateTime.now();
            sampleRepo.save(s);
            fillLabel(s);
            return s;
        });
    }

    /** 寄样：COLORING → SENT */
    @Transactional
    public SampleRequest send(Long id, LocalDate sendDate, String expressNo) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        // v7.7：已录配方（FORMULATED）也可直接寄样；配方推荐但不强制
        if (!"COLORING".equals(s.status) && !"FORMULATED".equals(s.status))
            throw new IllegalArgumentException("只有调色中/已录配方状态可寄样");
        s.sendDate = sendDate != null ? sendDate : LocalDate.now();
        s.expressNo = expressNo;
        s.status = "SENT";
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 客户反馈：SENT → SATISFIED / ADJUST（需调整回炉，轮次+1） */
    @Transactional
    public SampleRequest feedback(Long id, boolean satisfied, String content, LocalDate feedbackDate) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"SENT".equals(s.status)) throw new IllegalArgumentException("只有已寄样状态可记录反馈");
        s.feedbackContent = content;
        s.feedbackDate = feedbackDate != null ? feedbackDate : LocalDate.now();
        if (satisfied) {
            s.status = "SATISFIED";
        } else {
            s.status = "ADJUST";
            s.adjustCount = (s.adjustCount == null ? 0 : s.adjustCount) + 1;
        }
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 转单：SATISFIED → WON（填成交订单号），研发进度结案 */
    @Transactional
    public SampleRequest win(Long id, String orderNo) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"SATISFIED".equals(s.status)) throw new IllegalArgumentException("只有客户满意状态可转单");
        if (orderNo == null || orderNo.isBlank()) throw new IllegalArgumentException("请填写成交的销售订单号");
        s.wonOrderNo = orderNo;
        s.status = "WON";
        closeRd(s, "已成交，转订单 " + orderNo);
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 未成交：任意未完结状态 → LOST，研发进度结案 */
    @Transactional
    public SampleRequest lose(Long id, String reason) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if ("WON".equals(s.status) || "LOST".equals(s.status)) throw new IllegalArgumentException("已完结的打样单不可再标记");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("请填写未成交原因");
        s.lossReason = reason;
        s.status = "LOST";
        closeRd(s, "未成交：" + reason);
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    private void closeRd(SampleRequest s, String result) {
        if (s.rdProgressId == null) return;
        rdRepo.findById(s.rdProgressId).ifPresent(rd -> {
            rd.result = result;
            rd.closedDate = LocalDate.now();
            rd.updateTime = java.time.LocalDateTime.now();
            rdRepo.save(rd);
        });
    }

    /** 仅 APPLIED 可删除（未进调色，无研发进度联动） */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public void delete(Long id) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"APPLIED".equals(s.status)) throw new IllegalArgumentException("只有已申请状态可删除（已进入流程请标记未成交）");
        sampleRepo.delete(s);
    }

    // ==================== v7.7 打样任务/打样配方 ====================

    /** 派发：APPLIED/ADJUST → ASSIGNED（ASSIGNED 重复派发=改派）。内勤自己打就派发给自己。 */
    public SampleRequest assign(Long id, String assignee) {
        if (assignee == null || assignee.isBlank()) throw new IllegalArgumentException("请选择打样员");
        return writeQueue.executeTx(() -> {
            SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
            if (!"APPLIED".equals(s.status) && !"ADJUST".equals(s.status) && !"ASSIGNED".equals(s.status))
                throw new IllegalArgumentException("只有已申请/需调整/已派发状态可派发（打样中如需换人请先由本人处理或标记未成交）");
            s.assignee = assignee.trim();
            s.assignTime = java.time.LocalDateTime.now();
            s.status = "ASSIGNED";
            s.updateTime = java.time.LocalDateTime.now();
            sampleRepo.save(s);
            fillLabel(s);
            return s;
        });
    }

    /** 打样员接收任务：ASSIGNED → COLORING（仅 assignee 本人；研发进度联动同原调色流转） */
    public SampleRequest accept(Long id) {
        return writeQueue.executeTx(() -> {
            SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
            if (!"ASSIGNED".equals(s.status)) throw new IllegalArgumentException("只有已派发状态可接收");
            String me = userService.currentUsername();
            if (!s.assignee.equals(me)) throw new IllegalArgumentException("该任务派发给 " + s.assignee + "，仅本人可接收");
            s.receiveTime = java.time.LocalDateTime.now();
            s.colorist = s.assignee;
            s.status = "COLORING";
            syncRd(s);
            s.updateTime = java.time.LocalDateTime.now();
            sampleRepo.save(s);
            fillLabel(s);
            return s;
        });
    }

    /** 研发进度联动：首次接收建条目，回炉更新轮次（accept 与兼容保留的 startColoring 共用口径） */
    private void syncRd(SampleRequest s) {
        if (s.rdProgressId == null) {
            RdProgress rd = new RdProgress();
            rd.raiseDate = LocalDate.now();
            rd.owner = s.applicant != null ? s.applicant : "销售";
            rd.category = "调色";
            rd.content = "打样 " + s.sampleNo + "：" + s.customerName + " " + s.materialDesc;
            rd.progress = "第 1 轮调色（打样员：" + (s.assignee != null ? s.assignee : "-") + "）";
            rd.createdBy = "sample:" + s.sampleNo;
            rd = rdRepo.save(rd);
            s.rdProgressId = rd.id;
        } else {
            RdProgress rd = rdRepo.findById(s.rdProgressId).orElse(null);
            if (rd != null) {
                rd.progress = "第 " + (s.adjustCount + 1) + " 轮调色（打样员：" + (s.assignee != null ? s.assignee : "-") + "）";
                rd.updateTime = java.time.LocalDateTime.now();
                rdRepo.save(rd);
            }
        }
    }

    /**
     * 保存打样配方（打样单 1:1）：
     * 首次保存自动创建 C 类成品物料（9 位码自动生成，色系/主材/小类为取码属性段必填）并回填；
     * 覆盖更新前旧明细 JSON 快照进 sample_formula_history；
     * 估算成本=Σ(用量×移动加权均价)÷总量（比值与量纲无关，元/kg 口径供报价参考）；
     * 用量自由合计、单位=克（v7.7.6；不强制 100——转制漆时按标准批量 100kg=100000g 折算）。
     */
    public java.util.Map<String, Object> saveFormula(Long requestId, java.util.Map<String, Object> payload) {
        return writeQueue.executeTx(() -> {
            SampleRequest s = sampleRepo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
            if (!"COLORING".equals(s.status) && !"ADJUST".equals(s.status) && !"FORMULATED".equals(s.status))
                throw new IllegalArgumentException("只有调色中/需调整/已录配方状态可保存配方");
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) payload.get("items");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("请至少录入一行用料明细");
            for (java.util.Map<String, Object> it : items) {
                if (it.get("materialCode") == null || String.valueOf(it.get("materialCode")).isBlank())
                    throw new IllegalArgumentException("存在未选择物料的明细行");
                java.math.BigDecimal q = toBd(it.get("qty"));
                if (q == null || q.signum() <= 0) throw new IllegalArgumentException("用量必须大于 0：" + it.get("materialName"));
            }

            SampleFormula f = formulaRepo.findBySampleRequestId(requestId).orElse(null);
            if (f == null) {
                String name = str(payload.get("name"));
                String sub = str(payload.get("subCategory"));
                String main = str(payload.get("mainMaterial"));
                String color = str(payload.get("colorSeries"));
                if (name.isBlank() || sub.isBlank() || main.isBlank() || color.isBlank())
                    throw new IllegalArgumentException("首次保存需完整填写：中文名、小类、主材、色系（成品取码属性段）");
                Material m = new Material();
                m.name = name.trim();
                m.brand = "打样";
                m.category = "C";
                m.subCategory = sub;
                m.mainMaterial = main;
                m.colorSeries = color;
                m.brandOwner = "芃远";
                m.shelfLifeDays = 365;
                m = materialService.create(m);
                f = new SampleFormula();
                f.sampleRequestId = requestId;
                Integer maxSeq = formulaRepo.findMaxSeq("FY-" + LocalDate.now().toString().replace("-", "") + "-%");
                f.formulaNo = String.format("FY-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
                f.materialCode = m.code;
                f.materialName = m.name;
                f.subCategory = sub;
                f.mainMaterial = main;
                f.colorSeries = color;
                f.createTime = java.time.LocalDateTime.now();
                f = formulaRepo.save(f);   // 先落库拿 id，明细行 formula_id 非空
                log.info("打样配方创建: {} 单={} 物料={}", f.formulaNo, s.sampleNo, m.code);
            } else {
                SampleFormulaHistory h = new SampleFormulaHistory();
                h.formulaId = f.id;
                h.round = (s.adjustCount == null ? 0 : s.adjustCount) + 1;
                java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
                snap.put("items", formulaItemRepo.findByFormulaIdOrderBySortOrder(f.id));
                snap.put("totalQty", f.totalQty);
                snap.put("estCost", f.estCost);
                try { h.snapshot = JSON.writeValueAsString(snap); }
                catch (Exception e) { h.snapshot = "{\"error\":\"serialize failed\"}"; }
                formulaHistoryRepo.save(h);
            }

            formulaItemRepo.deleteByFormulaId(f.id);
            formulaItemRepo.flush();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            int order = 0;
            for (java.util.Map<String, Object> it : items) {
                SampleFormulaItem row = new SampleFormulaItem();
                row.formulaId = f.id;
                row.materialCode = str(it.get("materialCode"));
                row.materialName = str(it.get("materialName"));
                row.category = str(it.get("category"));
                row.subCategory = str(it.get("subCategory"));
                row.unit = "g";   // v7.7.6 打样用料单位=克（打样间按克称量）
                row.qty = toBd(it.get("qty"));
                row.sortOrder = order++;
                formulaItemRepo.save(row);
                total = total.add(row.qty);
            }
            f.totalQty = total;
            f.sampleLocation = str(payload.get("sampleLocation"));

            java.math.BigDecimal costSum = java.math.BigDecimal.ZERO;
            java.util.Map<String, java.math.BigDecimal> priceMap = recipeService.getMaterialPriceMap();
            for (SampleFormulaItem row : formulaItemRepo.findByFormulaIdOrderBySortOrder(f.id)) {
                java.math.BigDecimal price = priceMap.getOrDefault(row.materialCode, java.math.BigDecimal.ZERO);
                costSum = costSum.add(row.qty.multiply(price));
            }
            f.estCost = total.signum() > 0
                    ? costSum.divide(total, 2, java.math.RoundingMode.HALF_UP) : null;
            f.updateTime = java.time.LocalDateTime.now();
            formulaRepo.save(f);

            boolean firstFormula = !"FORMULATED".equals(s.status);
            s.status = "FORMULATED";
            if (s.rdProgressId != null) {
                RdProgress rd = rdRepo.findById(s.rdProgressId).orElse(null);
                if (rd != null) {
                    rd.progress = (rd.progress == null ? "" : rd.progress + " | ")
                            + (firstFormula ? "已录入打样配方 " : "更新打样配方 ") + f.formulaNo
                            + "（估算成本 " + f.estCost + " 元/kg）";
                    rd.updateTime = java.time.LocalDateTime.now();
                    rdRepo.save(rd);
                }
            }
            s.updateTime = java.time.LocalDateTime.now();
            sampleRepo.save(s);
            fillLabel(s);
            return formulaDetail(f, s);
        });
    }

    /** 打样配方详情（含明细），打样任务页编辑回显用；未录过返回 null */
    public java.util.Map<String, Object> getFormula(Long requestId) {
        SampleRequest s = sampleRepo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        SampleFormula f = formulaRepo.findBySampleRequestId(requestId).orElse(null);
        if (f == null) return null;
        return formulaDetail(f, s);
    }

    private java.util.Map<String, Object> formulaDetail(SampleFormula f, SampleRequest s) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", f.id);
        m.put("formulaNo", f.formulaNo);
        m.put("sampleRequestId", f.sampleRequestId);
        m.put("sampleNo", s.sampleNo);
        m.put("customerName", s.customerName);
        m.put("materialDesc", s.materialDesc);
        m.put("sampleQty", s.qty);           // v7.7.3 打样寄样张数
        m.put("sampleUnit", s.unit);
        m.put("sampleSize", s.sampleSize);
        m.put("status", s.status);
        m.put("statusLabel", STATUS_LABEL.getOrDefault(s.status, s.status));
        m.put("assignee", s.assignee);
        m.put("materialCode", f.materialCode);
        m.put("materialName", f.materialName);
        m.put("subCategory", f.subCategory);
        m.put("mainMaterial", f.mainMaterial);
        m.put("colorSeries", f.colorSeries);
        m.put("totalQty", f.totalQty);
        m.put("estCost", f.estCost);
        m.put("sampleLocation", f.sampleLocation);
        m.put("convertedRecipeId", f.convertedRecipeId);
        m.put("convertedTime", f.convertedTime);
        m.put("items", formulaItemRepo.findByFormulaIdOrderBySortOrder(f.id));
        return m;
    }

    /** 打样配方列表（转制漆下拉/管理） */
    public List<java.util.Map<String, Object>> listFormulas() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (SampleFormula f : formulaRepo.findAllByOrderByCreateTimeDescIdDesc()) {
            sampleRepo.findById(f.sampleRequestId).ifPresent(s -> result.add(formulaDetail(f, s)));
        }
        return result;
    }

    /**
     * 转制漆校验（严格拦截）：每条色浆（B 类）明细必须能匹配「该物料编码 + GRINDING + 有 RELEASED 版本」的制浆配方。
     * 用 findByProductCode 全量列表遍历——findFirst 只取一条，多条配方记录时首条无 RELEASED 会误判缺失。
     */
    public java.util.Map<String, Object> convertCheck(Long formulaId) {
        SampleFormula f = formulaRepo.findById(formulaId).orElseThrow(() -> new IllegalArgumentException("打样配方不存在"));
        List<java.util.Map<String, Object>> missing = new java.util.ArrayList<>();
        for (SampleFormulaItem it : formulaItemRepo.findByFormulaIdOrderBySortOrder(f.id)) {
            if (!"B".equals(it.category)) continue;
            if (findGrindingReleased(it.materialCode) == null) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("materialCode", it.materialCode);
                m.put("materialName", it.materialName);
                missing.add(m);
            }
        }
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("ok", missing.isEmpty());
        r.put("missing", missing);
        return r;
    }

    /** 按物料编码找「GRINDING 且有 RELEASED 版本」的制浆配方（SUB_RECIPE 节点 refRecipeId 用） */
    private Recipe findGrindingReleased(String productCode) {
        for (Recipe r : recipeRepo.findByProductCode(productCode)) {
            if (!"GRINDING".equals(r.recipeType) || Boolean.FALSE.equals(r.enabled)) continue;
            if (recipeVersionRepo.findByRecipeIdAndStatus(r.id, "RELEASED").isPresent()) return r;
        }
        return null;
    }

    /**
     * 打样配方 → 制漆配方（一键转换）：建 Recipe（productCode=打样生成的成品物料）+ V1.0 DRAFT + 树预填（DRAFT 可微调）。
     * 折算口径：node.qty = 行用量 × (100 ÷ 打样总量)，3 位 HALF_UP，尾差归末行保证顶层合计=100.000（saveTree 守恒校验）。
     * 色浆行→SUB_RECIPE（refRecipeId=自动匹配的已发布制浆配方）；重复转拦截（convertedRecipeId 幂等）。
     */
    public java.util.Map<String, Object> toRecipe(Long formulaId, java.util.Map<String, Object> opts) {
        java.util.Map<String, Object> check = convertCheck(formulaId);
        if (!Boolean.TRUE.equals(check.get("ok"))) {
            List<?> missing = (List<?>) check.get("missing");
            StringBuilder sb = new StringBuilder("以下色浆没有已发布的制浆配方，请先在制浆配方中建立后再转换：");
            for (Object o : missing) {
                if (o instanceof java.util.Map<?, ?> mm) sb.append("\n").append(mm.get("materialCode")).append(" ").append(mm.get("materialName"));
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return writeQueue.executeTx(() -> {
            SampleFormula f = formulaRepo.findById(formulaId).orElseThrow(() -> new IllegalArgumentException("打样配方不存在"));
            if (f.convertedRecipeId != null) {
                Recipe old = recipeRepo.findById(f.convertedRecipeId).orElse(null);
                throw new IllegalArgumentException("该打样配方已转过制漆配方 " + (old != null ? old.recipeNo : f.convertedRecipeId) + "，不允许重复转换");
            }
            Recipe r = new Recipe();
            r.productCode = f.materialCode;
            r.productName = f.materialName;
            r.recipeType = "TINTING";
            r.processTemplateId = toLong(opts.get("processTemplateId"));
            r.qcTemplateId = toLong(opts.get("qcTemplateId"));
            r.packagingStandardId = toLong(opts.get("packagingStandardId"));
            r.category = str(opts.get("category"));
            r.description = "打样配方转入 " + f.formulaNo;
            r = recipeService.create(r);   // 内部再进 WriteQueue（ReentrantLock 可重入），自动建 V1.0 DRAFT

            RecipeVersion draft = recipeVersionRepo.findByRecipeIdAndStatus(r.id, "DRAFT")
                    .orElseThrow(() -> new IllegalStateException("配方版本创建异常"));
            List<SampleFormulaItem> items = formulaItemRepo.findByFormulaIdOrderBySortOrder(f.id);
            // v7.7.6 打样用量单位=克：折算系数=100kg÷总克数，克用量×系数直接得 kg 值
            // （如 150g:50g 总 200g → 系数 0.5 → 75kg:25kg；勿用 100000/总克——那是折算后的克值，saveTree 校验的是 kg）
            java.math.BigDecimal ratio = java.math.BigDecimal.valueOf(100).divide(f.totalQty, 6, java.math.RoundingMode.HALF_UP);
            List<java.util.Map<String, Object>> tree = new java.util.ArrayList<>();
            for (SampleFormulaItem it : items) {
                java.util.Map<String, Object> node = new java.util.LinkedHashMap<>();
                boolean semi = "B".equals(it.category);
                node.put("nodeType", semi ? "SUB_RECIPE" : "MATERIAL");
                node.put("materialCode", it.materialCode);
                node.put("materialName", it.materialName);
                node.put("category", it.category);
                node.put("subCategory", it.subCategory);
                node.put("qty", it.qty.multiply(ratio).setScale(3, java.math.RoundingMode.HALF_UP));
                if (semi) node.put("refRecipeId", findGrindingReleased(it.materialCode).id);
                tree.add(node);
            }
            java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
            for (java.util.Map<String, Object> n : tree) sum = sum.add((java.math.BigDecimal) n.get("qty"));
            java.math.BigDecimal diff = java.math.BigDecimal.valueOf(100).subtract(sum);
            java.util.Map<String, Object> last = tree.get(tree.size() - 1);
            last.put("qty", ((java.math.BigDecimal) last.get("qty")).add(diff).setScale(3, java.math.RoundingMode.HALF_UP));
            recipeService.saveTree(draft.id, tree);

            f.convertedRecipeId = r.id;
            f.convertedTime = java.time.LocalDateTime.now();
            f.updateTime = java.time.LocalDateTime.now();
            formulaRepo.save(f);
            log.info("打样配方转制漆: {} -> {} 树节点 {} 个", f.formulaNo, r.recipeNo, tree.size());
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("recipeId", r.id);
            result.put("recipeNo", r.recipeNo);
            result.put("versionId", draft.id);
            return result;
        });
    }

    private static String str(Object v) { return v == null ? "" : String.valueOf(v).trim(); }

    private static java.math.BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof java.math.BigDecimal bd) return bd;
        try { return new java.math.BigDecimal(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }
}
