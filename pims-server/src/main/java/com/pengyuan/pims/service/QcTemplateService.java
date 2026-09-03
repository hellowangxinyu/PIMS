package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.QcTemplate;
import com.pengyuan.pims.entity.QcTemplateItem;
import com.pengyuan.pims.entity.QualityInspectionItem;
import com.pengyuan.pims.repository.QcTemplateItemRepository;
import com.pengyuan.pims.repository.QcTemplateRepository;
import com.pengyuan.pims.repository.QualityInspectionItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检模板服务（v5.32：按物料大类区分检测内容）
 * 模板结构参照工艺路线：主表+子表聚合保存，同类别默认互斥。
 * 质检单创建时按物料大类快照默认模板的检测项（模板后续修改不影响已建质检单）。
 */
@Service
public class QcTemplateService {

    private static final Logger log = LoggerFactory.getLogger(QcTemplateService.class);

    /** 物料大类合法值（对齐 material_category 字典） */
    private static final List<String> VALID_CATEGORIES = List.of("A", "P", "F", "R", "S", "B", "C");

    private final QcTemplateRepository templateRepo;
    private final QcTemplateItemRepository itemRepo;
    private final QualityInspectionItemRepository inspectionItemRepo;
    private final UserService userService;

    public QcTemplateService(QcTemplateRepository templateRepo,
                             QcTemplateItemRepository itemRepo,
                             QualityInspectionItemRepository inspectionItemRepo,
                             UserService userService) {
        this.templateRepo = templateRepo;
        this.itemRepo = itemRepo;
        this.inspectionItemRepo = inspectionItemRepo;
        this.userService = userService;
    }

    /** 当前操作人（v5.60 制单人） */
    private String operatorOf(Map<String, Object> body) {
        Object op = body.get("createdBy");
        return op instanceof String s && !s.isBlank() ? s : userService.currentOperatorName();
    }

    // ==================== 查询 ====================

    /** 全部模板（含检测项），管理页一次拉全 */
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (QcTemplate t : templateRepo.findByOrderByCreateTimeDesc()) {
            result.add(buildTemplateMap(t));
        }
        return result;
    }

    /** 模板详情（含检测项） */
    public Map<String, Object> get(Long id) {
        QcTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("质检模板不存在: " + id));
        return buildTemplateMap(t);
    }

    // ==================== 保存/删除/设默认 ====================

    /** 聚合保存（id=null 新建，否则更新；检测项先删后插，参照工艺路线 saveRoute） */
    @Transactional
    @SuppressWarnings("unchecked")
    public Long save(Long id, Map<String, Object> body) {
        QcTemplate t = id != null
                ? templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("质检模板不存在: " + id))
                : new QcTemplate();
        Object name = body.get("name");
        if (!(name instanceof String) || ((String) name).isBlank()) throw new IllegalArgumentException("模板名称不能为空");
        t.name = ((String) name).trim();
        Object cat = body.get("applyCategory");
        if (!(cat instanceof String) || !VALID_CATEGORIES.contains(cat)) {
            throw new IllegalArgumentException("适用类别必须为 A/P/F/R/S/B/C 之一");
        }
        t.applyCategory = (String) cat;
        // v5.81 三维匹配字段（空=不限）：成品 小类+主材+色系；半成品 小类+主材
        t.subCategory = strOrNull(body.get("subCategory"));
        t.mainMaterial = strOrNull(body.get("mainMaterial"));
        t.colorSeries = strOrNull(body.get("colorSeries"));
        Object remark = body.get("remark");
        t.remark = remark instanceof String r && !r.isBlank() ? r : null;
        if (id == null) t.createdBy = operatorOf(body);   // v5.60 制单人：仅创建时记录
        Object en = body.get("enabled");
        t.enabled = en == null || Boolean.parseBoolean(en.toString());
        boolean wantDefault = body.get("isDefault") != null && Boolean.parseBoolean(body.get("isDefault").toString());
        if (wantDefault) clearDefault(t.applyCategory, t.id);
        t.isDefault = wantDefault;
        t.updateTime = LocalDateTime.now();
        templateRepo.save(t);

        itemRepo.deleteByTemplateId(t.id);
        itemRepo.flush();
        insertItems(t.id, (List<Map<String, Object>>) body.get("items"));
        log.info("质检模板已保存：{} (类别={}, {} 项)", t.name, t.applyCategory,
                body.get("items") == null ? 0 : ((List<?>) body.get("items")).size());
        return t.id;
    }

    /** 删除模板：默认模板拦截（防止该类别失去默认模板）；质检单已快照，历史单不受影响 */
    @Transactional
    public void delete(Long id) {
        QcTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("质检模板不存在: " + id));
        if (Boolean.TRUE.equals(t.isDefault)) throw new IllegalArgumentException("默认模板不能删除，请先将默认设置转移到其他模板");
        itemRepo.deleteByTemplateId(id);
        templateRepo.delete(t);
        log.info("质检模板已删除：{} (类别={})", t.name, t.applyCategory);
    }

    /** 设为该类别默认（同类别其他模板取消默认） */
    @Transactional
    public void setDefault(Long id) {
        QcTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("质检模板不存在: " + id));
        clearDefault(t.applyCategory, id);
        t.isDefault = true;
        t.updateTime = LocalDateTime.now();
        templateRepo.save(t);
        log.info("质检模板设为默认：{} (类别={})", t.name, t.applyCategory);
    }

    private String strOrNull(Object o) {
        return o instanceof String str && !str.isBlank() ? str : null;
    }

    /** v5.81 单维打分：模板限定了且物料相同 +1；模板不限（空）+0.1（弱通用）；限定了但不同 0（不匹配） */
    private double dimScore(String templateDim, String materialDim) {
        if (templateDim == null || templateDim.isBlank()) return 0.1;
        return templateDim.equals(materialDim) ? 1.0 : 0.0;
    }

    // ==================== 质检单快照 ====================

    /**
     * 按物料大类取启用的默认模板，将检测项快照到质检单（质检单创建时调用）。
     * 无默认模板/无检测项时静默跳过（质检单退回自由文本判定）。
     * @return 快照条数（0=无模板可快照）
     */
    @Transactional
    public int snapshotTo(Long inspectionId, String materialCategory) {
        return snapshotTo(inspectionId, materialCategory, null, null, null, null);
    }

    /**
     * v5.81 三维打分匹配快照：优先使用显式指定的模板（配方绑定）；
     * 否则在同大类启用模板中按 小类/主材/色系 打分取最优（每命中一维 +1，模板维度空=不限也计弱命中 +0.1），
     * 同分取 isDefault。全部模板都无命中时回落大类默认。
     */
    @Transactional
    public int snapshotTo(Long inspectionId, String materialCategory,
                          Long boundTemplateId, String subCategory, String mainMaterial, String colorSeries) {
        if (materialCategory == null && boundTemplateId == null) return 0;
        QcTemplate t = null;
        if (boundTemplateId != null) {
            t = templateRepo.findById(boundTemplateId).filter(x -> !Boolean.FALSE.equals(x.enabled)).orElse(null);
        }
        if (t == null) {
            var candidates = templateRepo.findByApplyCategoryAndEnabledTrue(materialCategory);
            QcTemplate best = null; double bestScore = -1;
            for (QcTemplate c : candidates) {
                double score = 0;
                score += dimScore(c.subCategory, subCategory);
                score += dimScore(c.mainMaterial, mainMaterial);
                if (colorSeries != null) score += dimScore(c.colorSeries, colorSeries);   // 半成品无色系不参与
                if (Boolean.TRUE.equals(c.isDefault)) score += 0.05;
                if (score > bestScore) { bestScore = score; best = c; }
            }
            t = best;
        }
        if (t == null) return 0;
        List<QcTemplateItem> items = itemRepo.findByTemplateIdOrderBySortOrderAscIdAsc(t.id);
        if (items.isEmpty()) return 0;
        int sort = 1;
        for (QcTemplateItem item : items) {
            QualityInspectionItem qi = new QualityInspectionItem();
            qi.inspectionId = inspectionId;
            qi.templateId = t.id;
            qi.name = item.name;
            qi.standard = item.standard;
            qi.unit = item.unit;
            qi.method = item.method;
            qi.sortOrder = sort++;
            inspectionItemRepo.save(qi);
        }
        log.info("质检单 #{} 快照检测模板：{}（{} 项）", inspectionId, t.name, items.size());
        return items.size();
    }

    /** 同类别其他模板取消默认 */
    private void clearDefault(String applyCategory, Long excludeId) {
        for (QcTemplate o : templateRepo.findByApplyCategoryOrderByIsDefaultDescCreateTimeDesc(applyCategory)) {
            if (Boolean.TRUE.equals(o.isDefault) && !o.id.equals(excludeId)) {
                o.isDefault = false;
                o.updateTime = LocalDateTime.now();
                templateRepo.save(o);
            }
        }
    }

    // ==================== 组装辅助 ====================

    private Map<String, Object> buildTemplateMap(QcTemplate t) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", t.id);
        result.put("name", t.name);
        result.put("applyCategory", t.applyCategory);
        result.put("isDefault", Boolean.TRUE.equals(t.isDefault));
        result.put("enabled", Boolean.TRUE.equals(t.enabled));
        result.put("remark", t.remark);
        result.put("createdBy", t.createdBy);   // v5.60 制单人（历史模板为空）
        result.put("itemCount", itemRepo.findByTemplateIdOrderBySortOrderAscIdAsc(t.id).size());
        List<Map<String, Object>> items = new ArrayList<>();
        for (QcTemplateItem i : itemRepo.findByTemplateIdOrderBySortOrderAscIdAsc(t.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id);
            m.put("name", i.name);
            m.put("standard", i.standard);
            m.put("unit", i.unit);
            m.put("method", i.method);
            m.put("sortOrder", i.sortOrder);
            items.add(m);
        }
        result.put("items", items);
        return result;
    }

    /** v5.99.1 检测项必填校验：名称/标准要求缺一不可（单位/检验方法选填）；空名称行拦截提示 */
    private void insertItems(Long templateId, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("质检模板至少需要一个检测项");
        int sort = 1;
        int saved = 0;
        for (Map<String, Object> raw : items) {
            Object name = raw.get("name");
            if (!(name instanceof String) || ((String) name).isBlank())
                throw new IllegalArgumentException("检测项第 " + sort + " 行的检测项目名称不能为空");
            Object standard = raw.get("standard");
            if (!(standard instanceof String) || ((String) standard).isBlank())
                throw new IllegalArgumentException("检测项「" + ((String) name).trim() + "」的标准要求不能为空");
            QcTemplateItem item = new QcTemplateItem();
            item.templateId = templateId;
            item.name = ((String) name).trim();
            item.standard = ((String) standard).trim();
            item.unit = str(raw.get("unit"));
            item.method = str(raw.get("method"));
            item.sortOrder = sort++;
            itemRepo.save(item);
            saved++;
        }
        if (saved == 0) throw new IllegalArgumentException("质检模板至少需要一个检测项");
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
