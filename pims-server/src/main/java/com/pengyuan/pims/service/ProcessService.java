package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工艺路线管理：多条命名路线独立维护（按 GRINDING/TINTING 分类），配方绑定其中一条。
 * 路线 = 工序列表 → 每工序的步骤（含参数）+ 质检项。
 * 步骤描述支持 {{N}} 占位符（N=配方顶层物料投料顺序），由前端展示/打印时替换为物料名。
 */
@Service
public class ProcessService {

    private static final Logger log = LoggerFactory.getLogger(ProcessService.class);
    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final ProcessTemplateRepository templateRepo;
    private final ProcessStageRepository stageRepo;
    private final ProcessStepRepository stepRepo;
    private final ProcessQcItemRepository qcRepo;
    private final RecipeRepository recipeRepo;
    private final UserService userService;

    public ProcessService(ProcessTemplateRepository templateRepo, ProcessStageRepository stageRepo,
                          ProcessStepRepository stepRepo, ProcessQcItemRepository qcRepo,
                          RecipeRepository recipeRepo, UserService userService, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.templateRepo = templateRepo;
        this.stageRepo = stageRepo;
        this.stepRepo = stepRepo;
        this.qcRepo = qcRepo;
        this.recipeRepo = recipeRepo;
        this.userService = userService;
    }

    // ==================== 路线管理 ====================

    /** 路线列表（可按类型过滤），含工序数与绑定配方数 */
    public List<Map<String, Object>> listRoutes(String recipeType) {
        List<ProcessTemplate> list = (recipeType == null || recipeType.isBlank())
                ? templateRepo.findAllByOrderByUpdateTimeDesc()
                : templateRepo.findByRecipeTypeOrderByUpdateTimeDesc(recipeType);
        List<Map<String, Object>> result = new ArrayList<>();
        // v5.80 修列表 N+1：一次预取全部工序数/配方绑定数（原每行 2 次查询）
        Map<Long, Integer> stageCountByTmpl = new HashMap<>();
        for (ProcessStage st : stageRepo.findAll()) {
            stageCountByTmpl.merge(st.templateId, 1, Integer::sum);
        }
        Map<Long, Integer> recipeCountByTmpl = new HashMap<>();
        for (com.pengyuan.pims.entity.Recipe r : recipeRepo.findAll()) {
            if (r.processTemplateId != null) recipeCountByTmpl.merge(r.processTemplateId, 1, Integer::sum);
        }
        for (ProcessTemplate t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id);
            m.put("name", t.name);
            m.put("recipeType", t.recipeType);
            m.put("isDefault", Boolean.TRUE.equals(t.isDefault));
            m.put("enabled", Boolean.TRUE.equals(t.enabled));
            m.put("stageCount", stageCountByTmpl.getOrDefault(t.id, 0));
            m.put("recipeCount", recipeCountByTmpl.getOrDefault(t.id, 0));
            m.put("packingRequirement", t.packingRequirement);
            m.put("createdBy", t.createdBy);   // v5.60 制单人（历史为空）
            m.put("updateTime", t.updateTime);
            result.add(m);
        }
        return result;
    }

    /** 路线详情（含工序/步骤/质检项） */
    public Map<String, Object> getRoute(Long id) {
        ProcessTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id));
        Map<String, Object> result = buildTemplateMap(t.recipeType, t);
        result.put("id", t.id);
        return result;
    }

    /** 全量保存路线（id=null 新建，否则更新；工序先删后插） */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    @SuppressWarnings("unchecked")
    public Long saveRoute(Long id, Map<String, Object> body) {
        return writeQueue.executeTx(() -> {
            ProcessTemplate t = id != null
                    ? templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id))
                    : new ProcessTemplate();
            if (id == null) {
                t.createTime = LocalDateTime.now();
                t.createdBy = userService.currentOperatorName();   // v5.60 制单人：仅创建时记录
            }
            Object name = body.get("name");
            if (!(name instanceof String) || ((String) name).isBlank()) throw new IllegalArgumentException("路线名称不能为空");
            t.name = ((String) name).trim();
            Object rt = body.get("recipeType");
            if (!(rt instanceof String) || ((String) rt).isBlank()) throw new IllegalArgumentException("路线类型不能为空");
            t.recipeType = (String) rt;
            t.packingRequirement = body.get("packingRequirement") != null ? body.get("packingRequirement").toString() : null;
            Object en = body.get("enabled");
            t.enabled = en == null || Boolean.parseBoolean(en.toString());
            boolean wantDefault = body.get("isDefault") != null && Boolean.parseBoolean(body.get("isDefault").toString());
            if (wantDefault) clearDefault(t.recipeType, t.id);
            t.isDefault = wantDefault;
            t.updateTime = LocalDateTime.now();
            templateRepo.save(t);

            List<ProcessStage> oldStages = stageRepo.findByTemplateIdOrderBySortOrderAsc(t.id);
            if (!oldStages.isEmpty()) {
                List<Long> stageIds = oldStages.stream().map(s -> s.id).toList();
                stepRepo.deleteByStageIdIn(stageIds);
                qcRepo.deleteByStageIdIn(stageIds);
                stageRepo.deleteByTemplateId(t.id);
                stageRepo.flush();
            }
            insertStages(t.id, (List<Map<String, Object>>) body.get("stages"));
            log.info("工艺路线已保存：{} ({})", t.name, t.recipeType);
            return t.id;
    
        });
    }

    /** 删除路线：被配方引用或是默认路线时拦截 */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void deleteRoute(Long id) {
        writeQueue.executeTx(() -> {
            ProcessTemplate t = templateRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id));
            long used = recipeRepo.countByProcessTemplateId(id);
            if (used > 0) throw new IllegalArgumentException("该路线被 " + used + " 个配方使用，不能删除");
            if (Boolean.TRUE.equals(t.isDefault)) throw new IllegalArgumentException("默认路线不能删除，请先将默认设置转移到其他路线");
            List<Long> stageIds = stageRepo.findByTemplateIdOrderBySortOrderAsc(id).stream().map(s -> s.id).toList();
            if (!stageIds.isEmpty()) {
                stepRepo.deleteByStageIdIn(stageIds);
                qcRepo.deleteByStageIdIn(stageIds);
                stageRepo.deleteByTemplateId(id);
            }
            templateRepo.delete(t);
            log.info("工艺路线已删除：{}", t.name);
    
        });
    }

    /** 复制路线（副本不继承默认标记） */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public Long copyRoute(Long id) {
        Map<String, Object> src = getRoute(id);
        Map<String, Object> body = new LinkedHashMap<>(src);
        body.remove("id");
        body.put("name", src.get("name") + "(副本)");
        body.put("isDefault", false);
        return saveRoute(null, body);
    }

    /** 设为该类型默认（同类型其他路线取消默认） */
    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void setDefault(Long id) {
        writeQueue.executeTx(() -> {
            ProcessTemplate t = templateRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id));
            clearDefault(t.recipeType, id);
            t.isDefault = true;
            t.updateTime = LocalDateTime.now();
            templateRepo.save(t);
            log.info("工艺路线设为默认：{} ({})", t.name, t.recipeType);
    
        });
    }

    private void clearDefault(String recipeType, Long excludeId) {
        for (ProcessTemplate o : templateRepo.findByRecipeTypeOrderByUpdateTimeDesc(recipeType)) {
            if (Boolean.TRUE.equals(o.isDefault) && !o.id.equals(excludeId)) {
                o.isDefault = false;
                templateRepo.save(o);
            }
        }
    }

    /** 旧接口兼容：按类型返回默认路线（无默认时取该类型任一路线） */
    public Map<String, Object> getDefaultTemplate(String recipeType) {
        ProcessTemplate t = templateRepo.findFirstByRecipeTypeAndIsDefaultTrue(recipeType)
                .or(() -> templateRepo.findByRecipeType(recipeType))
                .orElse(null);
        return t != null ? buildTemplateMap(recipeType, t) : emptyTemplate(recipeType);
    }

    // ==================== 组装辅助 ====================

    private Map<String, Object> buildTemplateMap(String recipeType, ProcessTemplate t) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipeType", recipeType);
        result.put("name", t.name);
        result.put("isDefault", Boolean.TRUE.equals(t.isDefault));
        result.put("packingRequirement", t.packingRequirement);
        result.put("enabled", Boolean.TRUE.equals(t.enabled));
        List<Map<String, Object>> stages = new ArrayList<>();
        for (ProcessStage s : stageRepo.findByTemplateIdOrderBySortOrderAsc(t.id)) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("stageNo", s.stageNo);
            sm.put("stageName", s.stageName);
            sm.put("roleHint", s.roleHint);
            sm.put("sortOrder", s.sortOrder);
            sm.put("steps", buildSteps(s.id));
            sm.put("qcItems", buildQcItems(s.id));
            stages.add(sm);
        }
        result.put("stages", stages);
        return result;
    }

    private Map<String, Object> emptyTemplate(String recipeType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipeType", recipeType);
        result.put("name", null);
        result.put("packingRequirement", null);
        result.put("enabled", false);
        result.put("stages", new ArrayList<>());
        return result;
    }

    private void insertStages(Long templateId, List<Map<String, Object>> stages) {
        if (stages == null) return;
        int sOrder = 0;
        for (Map<String, Object> sm : stages) {
            ProcessStage s = new ProcessStage();
            s.templateId = templateId;
            s.stageNo = (String) sm.get("stageNo");
            s.stageName = (String) sm.get("stageName");
            s.roleHint = (String) sm.get("roleHint");
            Object so = sm.get("sortOrder");
            s.sortOrder = so instanceof Number ? ((Number) so).intValue() : sOrder++;
            stageRepo.save(s);

            int stOrder = 0;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) sm.get("steps");
            if (steps != null) {
                for (Map<String, Object> stm : steps) {
                    ProcessStep st = new ProcessStep();
                    st.stageId = s.id;
                    st.stepCode = (String) stm.get("stepCode");
                    st.description = (String) stm.get("description");
                    st.params = (String) stm.get("params");
                    Object sto = stm.get("sortOrder");
                    st.sortOrder = sto instanceof Number ? ((Number) sto).intValue() : stOrder++;
                    stepRepo.save(st);
                }
            }
            int qOrder = 0;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> qcs = (List<Map<String, Object>>) sm.get("qcItems");
            if (qcs != null) {
                for (Map<String, Object> qm : qcs) {
                    ProcessQcItem q = new ProcessQcItem();
                    q.stageId = s.id;
                    q.name = (String) qm.get("name");
                    q.standard = (String) qm.get("standard");
                    Object tt = qm.get("testTimes");
                    q.testTimes = tt instanceof Number ? ((Number) tt).intValue() : 1;
                    q.unit = (String) qm.get("unit");
                    q.method = (String) qm.get("method");
                    Object qso = qm.get("sortOrder");
                    q.sortOrder = qso instanceof Number ? ((Number) qso).intValue() : qOrder++;
                    qcRepo.save(q);
                }
            }
        }
    }

    private List<Map<String, Object>> buildSteps(Long stageId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProcessStep st : stepRepo.findByStageIdOrderBySortOrderAsc(stageId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stepCode", st.stepCode);
            m.put("description", st.description);
            m.put("params", st.params);
            m.put("sortOrder", st.sortOrder);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildQcItems(Long stageId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProcessQcItem q : qcRepo.findByStageIdOrderBySortOrderAsc(stageId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", q.name);
            m.put("standard", q.standard);
            m.put("testTimes", q.testTimes);
            m.put("unit", q.unit);
            m.put("method", q.method);
            m.put("sortOrder", q.sortOrder);
            list.add(m);
        }
        return list;
    }
}
