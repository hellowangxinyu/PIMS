# 工艺路线体系实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「标准工艺」从"每类型唯一模板"改造为「工艺路线」体系——多条命名路线独立维护，配方必选绑定一条路线，全链路按绑定路线取数。

**Architecture:** 后端在现有 ProcessSchemaInitializer 幂等迁移（重建表去唯一约束 + 补列 + 回填存量配方），ProcessService 增加路线 CRUD/复制/设默认，Recipe 增加 processTemplateId 必填校验；前端 ProcessTemplate.vue 重构为路线列表+编辑器，RecipeList.vue 表单加路线下拉，三处打印链路改按绑定路线取数。

**Tech Stack:** Spring Boot + JPA + SQLite（ddl-auto=none，手写迁移）、Vue3 + Element Plus

**项目约定（必读）：**
- 本项目**无单测目录、非 git 仓库**——验证方式为「构建 → 重启 → curl 接口断言」，不写单测、不做 git commit
- 构建部署标准流程：`cd pims-web && npm run build` → netstat 找 8080 PID `taskkill //F //PID` → `cd pims-server && mvn clean package -q -DskipTests` → 后台 `java -jar target/pims-server-1.0.0.jar` → curl 验证
- 登录拿 token：`POST /api/auth/login {"username":"admin","password":"admin123"}` → `data.token`，后续带 header `pims-token: <token>`；**curl 登录会顶掉浏览器会话（单点登录，正常现象）**
- 后台 java 任务停止时可能误报 exit 1，以 `netstat -ano | grep ":8080" | grep LISTENING` 为准

**Spec:** `docs/superpowers/specs/2026-08-12-process-route-design.md`

---

### Task 1: 后端——实体、仓储、表结构迁移与种子

**Files:**
- Modify: `pims-server/src/main/java/com/pengyuan/pims/entity/ProcessTemplate.java`
- Modify: `pims-server/src/main/java/com/pengyuan/pims/entity/Recipe.java`
- Modify: `pims-server/src/main/java/com/pengyuan/pims/repository/ProcessTemplateRepository.java`
- Modify: `pims-server/src/main/java/com/pengyuan/pims/repository/RecipeRepository.java`
- Modify: `pims-server/src/main/java/com/pengyuan/pims/config/ProcessSchemaInitializer.java`

- [ ] **Step 1: ProcessTemplate 实体——去唯一约束、加 isDefault**

```java
package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 工艺路线（标准工艺模板）：同配方可建多条命名路线，配方绑定其中一条 */
@Entity
@Table(name = "process_template")
public class ProcessTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 配方类型：GRINDING / TINTING（仅分类，同类型允许多条路线） */
    @Column(nullable = false, length = 20)
    public String recipeType;

    @Column(nullable = false, length = 50)
    public String name;

    /** 包装要求（投料单/工艺指导单打印时带上） */
    @Column(length = 1000)
    public String packingRequirement;

    /** 该类型的默认路线（配方回填/自动选中用，每类型至多一条） */
    @Column(nullable = false)
    public Boolean isDefault = false;

    public Boolean enabled = true;

    public LocalDateTime createTime;
    public LocalDateTime updateTime;
}
```

- [ ] **Step 2: Recipe 实体——加绑定列**

在 `recipeType` 字段后追加：

```java
    /** 绑定的工艺路线 id（保存必填；工艺展示/打印按此取数） */
    public Long processTemplateId;
```

- [ ] **Step 3: ProcessTemplateRepository——补查询方法**

```java
package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProcessTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplate, Long> {
    Optional<ProcessTemplate> findByRecipeType(String recipeType);
    List<ProcessTemplate> findByRecipeTypeOrderByUpdateTimeDesc(String recipeType);
    List<ProcessTemplate> findAllByOrderByUpdateTimeDesc();
    Optional<ProcessTemplate> findFirstByRecipeTypeAndIsDefaultTrue(String recipeType);
    boolean existsByRecipeType(String recipeType);
}
```

（先读原文件确认现有方法，保留已有的，追加缺少的。）

- [ ] **Step 4: RecipeRepository——加引用计数**

追加方法（供删除路线时拦截）：

```java
    long countByProcessTemplateId(Long processTemplateId);
```

- [ ] **Step 5: ProcessSchemaInitializer——迁移 + 种子重写**

`run()` 改为（建表语句不变，调整后续步骤）：

```java
    @Override
    public void run(String... args) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_template (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_type VARCHAR(20) NOT NULL UNIQUE,
                name VARCHAR(50) NOT NULL,
                enabled BOOLEAN DEFAULT 1,
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_stage (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                template_id BIGINT NOT NULL,
                stage_no VARCHAR(10),
                stage_name VARCHAR(50) NOT NULL,
                role_hint VARCHAR(50),
                sort_order INTEGER DEFAULT 0
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_step (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                stage_id BIGINT NOT NULL,
                step_code VARCHAR(10),
                description VARCHAR(1000),
                params VARCHAR(200),
                sort_order INTEGER DEFAULT 0
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_qc_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                stage_id BIGINT NOT NULL,
                name VARCHAR(50) NOT NULL,
                standard VARCHAR(100),
                test_times INTEGER DEFAULT 1,
                unit VARCHAR(20),
                method VARCHAR(200),
                sort_order INTEGER DEFAULT 0
            )
        """);
        log.info("工艺路线表结构：process_template / process_stage / process_step / process_qc_item 就绪");
        ensureColumn("process_template", "packing_requirement", "VARCHAR(1000)");
        migrateTemplateTable();
        ensureColumn("recipe", "process_template_id", "BIGINT");
        seedDefaultRoutes();
        backfillRecipes();
    }
```

删除旧的 `GRINDING_SEED_VERSION`、`seedGrinding()`、`rebuildGrindingStages()`，替换为以下方法：

```java
    /** 工艺路线改造：去掉 recipe_type 唯一约束 + 加 is_default 列（SQLite 需重建表），幂等 */
    private void migrateTemplateTable() {
        boolean hasUnique = !jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='process_template' AND name LIKE 'sqlite_autoindex%'").isEmpty();
        boolean hasDefaultCol = jdbc.queryForList("PRAGMA table_info(process_template)").stream()
                .anyMatch(m -> "is_default".equals(m.get("name")));
        if (!hasUnique && hasDefaultCol) return;
        jdbc.execute("""
            CREATE TABLE process_template_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_type VARCHAR(20) NOT NULL,
                name VARCHAR(50) NOT NULL,
                packing_requirement VARCHAR(1000),
                is_default BOOLEAN DEFAULT 0,
                enabled BOOLEAN DEFAULT 1,
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        jdbc.update("INSERT INTO process_template_new (id, recipe_type, name, packing_requirement, is_default, enabled, create_time, update_time) "
                + "SELECT id, recipe_type, name, packing_requirement, 1, enabled, create_time, update_time FROM process_template");
        jdbc.execute("DROP TABLE process_template");
        jdbc.execute("ALTER TABLE process_template_new RENAME TO process_template");
        log.info("工艺路线：process_template 已重建（去唯一约束，存量路线标记为默认）");
    }

    /** 每类型没有任何路线时才种子创建默认路线（用户数据至上，绝不覆盖已存在路线） */
    private void seedDefaultRoutes() {
        if (!templateRepo.existsByRecipeType("GRINDING")) {
            ProcessTemplate t = newTemplate("GRINDING", "制浆标准工艺");
            seedGrindingStages(t.id);
            log.info("工艺路线：种子创建制浆默认路线（6 工序）");
        }
        if (!templateRepo.existsByRecipeType("TINTING")) {
            ProcessTemplate t = newTemplate("TINTING", "制漆标准工艺");
            seedTintingStages(t.id);
            log.info("工艺路线：种子创建制漆默认路线（5 工序草稿）");
        }
    }

    private ProcessTemplate newTemplate(String recipeType, String name) {
        ProcessTemplate t = new ProcessTemplate();
        t.recipeType = recipeType;
        t.name = name;
        t.isDefault = true;
        t.enabled = true;
        t.createTime = LocalDateTime.now();
        t.updateTime = t.createTime;
        return templateRepo.save(t);
    }

    /** 存量配方回填：未绑定路线的按类型绑默认路线 */
    private void backfillRecipes() {
        int n = jdbc.update("""
            UPDATE recipe SET process_template_id = (
                SELECT id FROM process_template
                WHERE recipe_type = recipe.recipe_type AND is_default = 1
                LIMIT 1)
            WHERE process_template_id IS NULL
        """);
        if (n > 0) log.info("工艺路线：回填 {} 条存量配方的工艺路线绑定", n);
    }
```

`seedGrindingStages(Long templateId)` = 原 `rebuildGrindingStages()` 的工序内容，去掉开头删旧部分和模板查询（参数传入 templateId，直接用 `stage(templateId, ...)`）。

`seedTintingStages` 新增：

```java
    /** 制漆默认路线（草稿，上线后由用户按实际工艺修正） */
    private void seedTintingStages(Long t) {
        Long s1 = stage(t, "01", "预混/调漆", "调色工", 1);
        step(s1, "A", "按配方量准确投入{{1}}，开动搅拌（500-600rpm），依次加入{{2}}、{{3}}，搅拌均匀", "500-600rpm", 1);
        step(s1, "B", "在搅拌状态下投入{{4}}，低速分散均匀，铲净缸壁残留", "", 2);

        Long s2 = stage(t, "02", "调色", "调色工", 2);
        step(s2, "A", "取样对比标准样，按色差结果补加色浆微调，搅拌均匀后复测", "", 1);
        qc(s2, "色差", "与标准样一致（ΔE≤0.5）", 1, "", "", 1);

        Long s3 = stage(t, "03", "过滤", "打包工", 3);
        step(s3, "A", "调色合格后用滤网/滤袋过滤，去除粗颗粒、结皮及杂质，滤后漆料接入洁净包装桶", "", 1);
        qc(s3, "滤网目数", "100-200目（按产品标准）", 1, "目", "", 1);

        Long s4 = stage(t, "04", "打包、留样、送检", "打包工", 4);
        step(s4, "A", "过滤后漆料搅拌均匀，称量装桶", "", 1);
        step(s4, "B", "打包过程中取样300ml左右，贴标签标示：名称、生产日期、批号", "", 2);
        step(s4, "C", "将样瓶送技术部检测，标示：合格/不合格，告知仓库使用注意事项", "", 3);
        qc(s4, "技术部判定", "合格/不合格", 1, "", "", 1);

        Long s5 = stage(t, "05", "刷缸", "配料人", 5);
        step(s5, "A", "打包完毕后，用溶剂清洗拉缸、搅拌器，洗液回收，专桶专用，标示清晰", "", 1);
    }
```

（`stage()/step()/qc()` 辅助方法保持不变。）

- [ ] **Step 6: 构建、重启、验证迁移**

```bash
# 杀进程（若有）→ 打包 → 启动
cd D:/开发/PIMS/pims-server && mvn clean package -q -DskipTests
java -jar target/pims-server-1.0.0.jar   # 后台任务方式
```

启动日志应含：`工艺路线：process_template 已重建（去唯一约束，存量路线标记为默认）`、`回填 N 条存量配方的工艺路线绑定`。

登录拿 token 后验证存量配方已回填：

```bash
curl -s -H "pims-token: $TOKEN" "http://localhost:8080/api/recipe?page=1&size=3"
```

预期：返回的配方对象带 `processTemplateId` 且非空。旧接口兼容验证：`GET /api/process/template/GRINDING` 仍返回制浆工艺（此 Task 暂不改该接口逻辑，Task 2 改）。

---

### Task 2: 后端——路线 CRUD/复制/设默认接口

**Files:**
- Modify: `pims-server/src/main/java/com/pengyuan/pims/service/ProcessService.java`
- Modify: `pims-server/src/main/java/com/pengyuan/pims/controller/ProcessController.java`

- [ ] **Step 1: ProcessService 新增路线管理方法**

构造器注入追加 `RecipeRepository recipeRepo`。新增方法：

```java
    /** 路线列表（可按类型过滤），含工序数与绑定配方数 */
    public List<Map<String, Object>> listRoutes(String recipeType) {
        List<ProcessTemplate> list = (recipeType == null || recipeType.isBlank())
                ? templateRepo.findAllByOrderByUpdateTimeDesc()
                : templateRepo.findByRecipeTypeOrderByUpdateTimeDesc(recipeType);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProcessTemplate t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id);
            m.put("name", t.name);
            m.put("recipeType", t.recipeType);
            m.put("isDefault", Boolean.TRUE.equals(t.isDefault));
            m.put("enabled", Boolean.TRUE.equals(t.enabled));
            m.put("stageCount", stageRepo.findByTemplateIdOrderBySortOrderAsc(t.id).size());
            m.put("recipeCount", recipeRepo.countByProcessTemplateId(t.id));
            m.put("packingRequirement", t.packingRequirement);
            m.put("updateTime", t.updateTime);
            result.add(m);
        }
        return result;
    }

    /** 路线详情（含工序/步骤/质检项） */
    public Map<String, Object> getRoute(Long id) {
        ProcessTemplate t = templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id));
        Map<String, Object> result = buildTemplateMap(t.recipeType, t);
        result.put("id", t.id);
        return result;
    }

    /** 全量保存路线（新建或更新；先删后插） */
    @Transactional
    @SuppressWarnings("unchecked")
    public Long saveRoute(Long id, Map<String, Object> body) {
        ProcessTemplate t = id != null ? templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id)) : new ProcessTemplate();
        if (id == null) t.createTime = LocalDateTime.now();
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("路线名称不能为空");
        t.name = name.trim();
        String rt = (String) body.get("recipeType");
        if (rt == null || rt.isBlank()) throw new IllegalArgumentException("路线类型不能为空");
        t.recipeType = rt;
        t.packingRequirement = body.get("packingRequirement") != null ? body.get("packingRequirement").toString() : null;
        Object en = body.get("enabled");
        t.enabled = en == null || Boolean.parseBoolean(en.toString());
        Object def = body.get("isDefault");
        if (def != null && Boolean.parseBoolean(def.toString())) clearDefault(rt, t.id);
        t.isDefault = def != null && Boolean.parseBoolean(def.toString());
        t.updateTime = LocalDateTime.now();
        templateRepo.save(t);
        // 删旧插新（与 saveTemplate 同模式）
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
    }

    /** 删除路线：被配方引用或是默认路线时拦截 */
    @Transactional
    public void deleteRoute(Long id) {
        ProcessTemplate t = templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id));
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
    }

    /** 复制路线（副本不继承默认标记） */
    @Transactional
    public Long copyRoute(Long id) {
        Map<String, Object> src = getRoute(id);
        Map<String, Object> body = new LinkedHashMap<>(src);
        body.remove("id");
        body.put("name", src.get("name") + "(副本)");
        body.put("isDefault", false);
        return saveRoute(null, body);
    }

    /** 设为该类型默认（同类型其他路线取消默认） */
    @Transactional
    public void setDefault(Long id) {
        ProcessTemplate t = templateRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("工艺路线不存在: " + id));
        clearDefault(t.recipeType, id);
        t.isDefault = true;
        t.updateTime = LocalDateTime.now();
        templateRepo.save(t);
        log.info("工艺路线设为默认：{} ({})", t.name, t.recipeType);
    }

    private void clearDefault(String recipeType, Long excludeId) {
        for (ProcessTemplate o : templateRepo.findByRecipeTypeOrderByUpdateTimeDesc(recipeType)) {
            if (Boolean.TRUE.equals(o.isDefault) && !o.id.equals(excludeId)) {
                o.isDefault = false;
                templateRepo.save(o);
            }
        }
    }

    /** 旧接口兼容：按类型返回默认路线 */
    public Map<String, Object> getDefaultTemplate(String recipeType) {
        ProcessTemplate t = templateRepo.findFirstByRecipeTypeAndIsDefaultTrue(recipeType)
                .or(() -> templateRepo.findByRecipeType(recipeType)).orElse(null);
        return t != null ? buildTemplateMap(recipeType, t) : emptyTemplate(recipeType);
    }
```

重构：把现有 `getTemplate` 里组装 stages 的部分抽为 `buildTemplateMap(String recipeType, ProcessTemplate t)`（含 recipeType/name/packingRequirement/enabled/stages，**加放 `id` 与 `isDefault`**），`getTemplate(recipeType)` 改为调 `getDefaultTemplate`。`saveTemplate` 里的插新循环抽为 `insertStages(Long templateId, List<Map<String,Object>> stages)` 供复用。`emptyTemplate(recipeType)` 返回只有 recipeType + 空 stages 的 map（原逻辑 t==null 时的返回）。

- [ ] **Step 2: ProcessController 新增端点**

```java
    /** 路线列表（可选 recipeType 过滤） */
    @GetMapping("/routes")
    @SaCheckPermission("process:read")
    public Result listRoutes(@RequestParam(required = false) String recipeType) {
        return Result.ok(service.listRoutes(recipeType));
    }

    /** 路线详情 */
    @GetMapping("/route/{id}")
    @SaCheckPermission("process:read")
    public Result getRoute(@PathVariable Long id) {
        return Result.ok(service.getRoute(id));
    }

    /** 新建路线 */
    @PostMapping("/route")
    @SaCheckPermission("process:write")
    public Result createRoute(@RequestBody Map<String, Object> body) {
        return Result.ok(service.saveRoute(null, body));
    }

    /** 全量保存路线 */
    @PutMapping("/route/{id}")
    @SaCheckPermission("process:write")
    public Result saveRoute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.saveRoute(id, body);
        return Result.ok(null);
    }

    /** 删除路线（被引用/默认时拦截） */
    @DeleteMapping("/route/{id}")
    @SaCheckPermission("process:write")
    public Result deleteRoute(@PathVariable Long id) {
        service.deleteRoute(id);
        return Result.ok(null);
    }

    /** 复制路线 */
    @PostMapping("/route/{id}/copy")
    @SaCheckPermission("process:write")
    public Result copyRoute(@PathVariable Long id) {
        return Result.ok(service.copyRoute(id));
    }

    /** 设为默认路线 */
    @PostMapping("/route/{id}/set-default")
    @SaCheckPermission("process:write")
    public Result setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return Result.ok(null);
    }

    /** 旧接口兼容：返回该类型默认路线 */
    @GetMapping("/template/{recipeType}")
    @SaCheckPermission("process:read")
    public Result getTemplate(@PathVariable String recipeType) {
        return Result.ok(service.getDefaultTemplate(recipeType));
    }
```

删除旧的 `PUT /template/{recipeType}`（前端 Task 4 会改调新接口；此时旧前端会报错——**Task 2 与 Task 4 必须同一次部署上线**）。

- [ ] **Step 3: 构建、重启、curl 全量验证**

```bash
# 登录拿 token 后：
curl -s -H "pims-token: $TOKEN" "http://localhost:8080/api/process/routes"
# 预期：数组，含制浆默认路线（isDefault=true, stageCount=6, recipeCount=存量配方数）和制漆默认路线

curl -s -H "pims-token: $TOKEN" "http://localhost:8080/api/process/routes?recipeType=GRINDING"
# 预期：只返回制浆路线

curl -s -H "pims-token: $TOKEN" "http://localhost:8080/api/process/template/GRINDING"
# 预期：返回默认路线详情（兼容接口）

# 复制 → 验证副本
curl -s -X POST -H "pims-token: $TOKEN" "http://localhost:8080/api/process/route/1/copy"
# 设默认到新副本之外的路线、删除副本（无引用可删）
# 删除默认路线 → 预期报错 "默认路线不能删除"
# 删除被配方引用的默认路线 → 预期报错 "被 N 个配方使用"
```

---

### Task 3: 后端——配方绑定路线校验

**Files:**
- Modify: `pims-server/src/main/java/com/pengyuan/pims/controller/RecipeController.java`（create/update 解析字段）
- Modify: `pims-server/src/main/java/com/pengyuan/pims/service/RecipeService.java`（校验）

- [ ] **Step 1: RecipeController create/update 解析 processTemplateId**

create 方法追加：

```java
        recipe.processTemplateId = body.get("processTemplateId") != null ? Long.valueOf(body.get("processTemplateId").toString()) : null;
```

update 方法同样追加一行。

- [ ] **Step 2: RecipeService 校验**

注入 `ProcessTemplateRepository`（若未注入）。`create`/`update` 开头调用：

```java
    private void validateProcessRoute(Recipe recipe) {
        if (recipe.processTemplateId == null) throw new IllegalArgumentException("请选择工艺路线");
        ProcessTemplate t = processTemplateRepo.findById(recipe.processTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在"));
        if (!t.recipeType.equals(recipe.recipeType))
            throw new IllegalArgumentException("工艺路线类型与配方类型不一致");
    }
```

`create` 的 writeQueue.execute 内 `recipeRepo.save(recipe)` 之前、`update` 的 `recipeRepo.save(recipe)` 之前各调用一次；`update` 里补 `recipe.processTemplateId = updated.processTemplateId;`。

- [ ] **Step 3: listReleased 返回绑定字段**

`listReleased()` 的 item map 追加：

```java
            item.put("processTemplateId", recipe.processTemplateId);
```

- [ ] **Step 4: 构建、重启、curl 验证**

```bash
# 缺路线 → 预期 "请选择工艺路线"
curl -s -X POST -H "pims-token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"productName":"测试配方","recipeType":"GRINDING","productCode":"任一B类物料编码"}' http://localhost:8080/api/recipe
# 类型不匹配 → 预期 "工艺路线类型与配方类型不一致"（用 GRINDING 配方 + 制漆路线id）
# 正确组合 → 预期 200，返回带 processTemplateId
# 验证后删除测试配方：DELETE /api/recipe/{id}
```

---

### Task 4: 前端——工艺路线管理页

**Files:**
- Rewrite: `pims-web/src/views/ProcessTemplate.vue`（列表 + 编辑器）
- Modify: `pims-web/src/views/Layout.vue`（菜单文案）

- [ ] **Step 1: ProcessTemplate.vue 重写为列表+编辑双模式**

页面结构：

```
mode = 'list'（默认）
┌─────────────────────────────────────────────────────────┐
│ 工艺路线                                    [+ 新建路线] │
│ [全部] [制浆] [制漆]  ← 筛选 tab（el-radio-group button）│
│ 表格列：路线名(★默认) | 类型 | 工序数 | 绑定配方数 |      │
│         更新时间 | 操作[编辑 复制 设默认 删除]            │
└─────────────────────────────────────────────────────────┘
mode = 'edit'
┌─────────────────────────────────────────────────────────┐
│ ← 返回列表   路线名称[____] 类型[GRINDING▾]             │
│ 包装要求[textarea]                                       │
│ （现有工序编辑器 stages 区块原样保留）                    │
│                              [保存路线]                   │
└─────────────────────────────────────────────────────────┘
```

关键实现点（完整代码写在此文件）：
- `fetchRoutes()`：`api.get('/process/routes' + (filter.value ? '?recipeType=' + filter.value : ''))`
- 列表行操作：编辑→`openEdit(row)` 调 `api.get('/process/route/' + row.id)` 载入 `tpl` 并切 mode；复制→`api.post('/process/route/' + row.id + '/copy')` 后刷新；设默认→`api.post('/process/route/' + row.id + '/set-default')`；删除→`ElMessageBox.confirm` 后 `api.delete('/process/route/' + row.id)`，失败显示后端 msg
- `save()`：`editingId ? api.put('/process/route/' + editingId, tpl) : api.post('/process/route', tpl)`，body 含 name/recipeType/packingRequirement/enabled/stages；保存成功回列表
- 新建：`tpl = { name:'', recipeType: filter或'GRINDING', packingRequirement:'', stages:[] }`
- 保留现有 `addStage/removeStage/addStep/removeStep/addQc/removeQc` 与 {{N}} 提示条
- 默认路线行显示 ★（`row.isDefault`），"设默认"按钮对已是默认的行隐藏

- [ ] **Step 2: Layout.vue 菜单文案**

`标准工艺` → `工艺路线`（Layout.vue:53 的 router-link 文本）。

- [ ] **Step 3: 构建前端 + 重启 + 浏览器/curl 验证**

`npm run build` → 重启 → curl `GET /api/process/routes` 确认数据；浏览器打开「生产管理→工艺路线」验证：列表展示、新建/编辑/复制/删除/设默认全流程（删除验证拦截提示）。**提醒用户 Ctrl+F5。**

---

### Task 5: 前端——配方表单绑定路线 + 详情展示

**Files:**
- Modify: `pims-web/src/views/RecipeList.vue`

- [ ] **Step 1: 加载路线下拉数据**

script 区新增：

```js
const processRoutes = ref([])
async function fetchProcessRoutes(type) {
  try { processRoutes.value = await api.get(`/process/routes?recipeType=${type}`) }
  catch { processRoutes.value = [] }
}
```

- [ ] **Step 2: 配方弹窗加「工艺路线」下拉**

配方类型 radio 之后、半成品/成品选择之前插入：

```vue
        <el-form-item label="工艺路线" required>
          <el-select v-model="recipeForm.processTemplateId" placeholder="选择工艺路线" style="width:100%">
            <el-option v-for="r in processRoutes" :key="r.id" :label="r.name + (r.isDefault ? ' ★' : '')" :value="r.id" :disabled="!r.enabled" />
          </el-select>
        </el-form-item>
```

- [ ] **Step 3: 打开弹窗/切换类型时自动选默认路线**

```js
// openCreateRecipe：
recipeForm.value = { productName: '', productCode: '', recipeType: activeType.value, category: '', description: '', processTemplateId: null }
await fetchProcessRoutes(recipeForm.value.recipeType)
autoSelectDefaultRoute()

// onRecipeTypeChange 追加：
await fetchProcessRoutes(recipeForm.value.recipeType)
recipeForm.value.processTemplateId = null
autoSelectDefaultRoute()

// openEditRecipe：
recipeForm.value = { ..., processTemplateId: current.value.processTemplateId || null }
await fetchProcessRoutes(recipeForm.value.recipeType)
if (!recipeForm.value.processTemplateId) autoSelectDefaultRoute()

function autoSelectDefaultRoute() {
  const def = processRoutes.value.find(r => r.isDefault && r.enabled !== false)
  recipeForm.value.processTemplateId = def ? def.id : null
}
```

- [ ] **Step 4: submitRecipe 前端拦截**

`if (!recipeForm.value.productCode)` 校验后追加：

```js
  if (!recipeForm.value.processTemplateId) { ElMessage.warning('请选择工艺路线'); return }
```

- [ ] **Step 5: 详情工艺展示改按绑定路线**

`fetchProcess()` 改为：

```js
function fetchProcess() {
  const pid = current.value?.processTemplateId
  const url = pid ? `/process/route/${pid}` : `/process/template/${current.value?.recipeType || 'GRINDING'}`
  api.get(url).then(d => { processTpl.value = d }).catch(() => { processTpl.value = null })
}
```

详情区标题 `标准工艺` 保留，`ver-label` 已显示路线名。

- [ ] **Step 6: 构建、重启、验证**

浏览器验证：新建配方（自动选中默认路线★、不选报错）、编辑存量配方（显示回填路线、可改）、详情页工艺按绑定路线展示、切换配方类型自动切换默认路线。curl 建一条配方验证 `processTemplateId` 落库后删除。

---

### Task 6: 前端——单据打印贯通（生产订单/委外单/工艺指导单）

**Files:**
- Modify: `pims-web/src/views/ProductionOrder.vue`（loadProcessTpl）
- Modify: `pims-web/src/views/OutsourceOrderList.vue`（loadProcessTpl）
- Modify: `pims-web/src/views/RecipeList.vue`（打印文案）

- [ ] **Step 1: ProductionOrder.vue / OutsourceOrderList.vue loadProcessTpl**

两处同改（先按绑定路线，无绑定回退类型默认）：

```js
// 按订单配方的绑定路线取工艺（含工艺/检测计划/包装要求），无绑定回退类型默认
async function loadProcessTpl(order) {
  try {
    const r = releasedRecipes.value.find(x => x.versionId === order.recipeVersionId)
    if (r?.processTemplateId) return await api.get(`/process/route/${r.processTemplateId}`)
    return await api.get(`/process/template/${r?.recipeType || 'GRINDING'}`)
  } catch { return null }
}
```

（Task 3 已让 `/recipe/released` 返回 processTemplateId。）

- [ ] **Step 2: RecipeList.vue 打印兜底文案**

`printProcessSheet` 的警告文案 `'当前配方类型未配置标准工艺'` → `'当前配方未绑定工艺路线'`。

- [ ] **Step 3: 构建、重启、端到端验证**

1. 复制制浆默认路线为"测试路线"，编辑改一个步骤描述以便肉眼区分；
2. 建一个 GRINDING 测试配方绑定"测试路线"，发布一个版本；
3. 建生产订单选该配方 → 打印 → 打印内容应为"测试路线"的工艺；
4. 配方详情页打印工艺指导单 → 同为"测试路线"；
5. 清理：删测试订单/配方/测试路线（测试路线无引用后可删）。

---

### Task 7: 收尾——全量回归与记忆更新

- [ ] **Step 1: 全流程回归**

按序验证：工艺路线页 CRUD → 配方绑定 → 配方详情 → 三个打印场景 → 旧接口 `/api/process/template/{type}` 兼容 → 停用路线后不出现在配方下拉但已绑配方照常展示。

- [ ] **Step 2: 更新项目记忆**

更新 `standard-process-module.md`：路线体系（多路线/配方绑定/is_default/接口清单/迁移机制变更——种子只从无到有）。

- [ ] **Step 3: 提醒用户 Ctrl+F5 刷新浏览器。**
