# 工艺路线体系设计（标准工艺改造）

- **日期**：2026-08-12
- **状态**：已确认（用户逐段审批通过）
- **模块**：标准工艺 → 工艺路线（ProcessRoute）

## 0. 设计原则

本系统的核心目标是**标准化、流程化、准确性**，本设计全程遵循：

- **标准化**：配方必须绑定一条工艺路线，不允许例外；存量配方统一回填默认路线，保证全覆盖。
- **流程化**：从配方 → 生产订单 → 投料单 → 工艺指导单，工艺信息一路贯通，从绑定的路线统一取数，不能断链、不能各取各的。
- **准确性**：删除被引用的路线必须拦截，杜绝配方绑定不存在路线的脏数据；路线类型与配方类型必须匹配。

## 1. 背景与目标

现有「标准工艺」按配方类型（GRINDING 制浆 / TINTING 制漆）各维护**唯一一套**模板（`process_template.recipe_type` UNIQUE），所有同类型配方共用，无法差异化。

用户诉求：

1. 工艺路线**单独维护**，可建**多条**命名模板（如"制浆标准工艺V6""细浆-高固含路线"）；
2. **建配方时选择**一条工艺路线绑定；
3. 后续生产直接选配方，工艺随配方自动带出，无需每次挑选。

核心变化：**模板与配方类型 1:1 → 路线库 1:N，配方显式绑定一条路线**。

## 2. 数据模型

### 2.1 `process_template`（工艺路线表，沿用原表名）

去掉 `recipe_type` 唯一约束，新增 `is_default` 列：

| 列 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | |
| recipe_type | VARCHAR(20) NOT NULL | GRINDING / TINTING，仅分类，**不再唯一** |
| name | VARCHAR(50) NOT NULL | 路线名（用户自定义，区分用） |
| packing_requirement | VARCHAR(1000) | 包装要求 |
| is_default | BOOLEAN DEFAULT 0 | 该类型的默认路线（每类型至多一条） |
| enabled | BOOLEAN DEFAULT 1 | 停用后不出现在配方下拉 |
| create_time | TIMESTAMP | |
| update_time | TIMESTAMP | |

子表 `process_stage` / `process_step` / `process_qc_item` **结构不变**，仍挂 `stage_id`/`template_id`。

> SQLite 无法直接 DROP UNIQUE 约束，需**重建表**：建新表 → 拷数据 → 删旧表 → 重命名。迁移幂等（先查 `sqlite_master` 判断唯一索引是否还在）。

### 2.2 `recipe`（配方表）

新增列：

| 列 | 类型 | 说明 |
|---|---|---|
| process_template_id | BIGINT | 绑定的工艺路线 id，**保存配方时必填** |

## 3. 后端接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/process/routes` | 路线列表（可选 `?recipeType=` 过滤；返回 id/name/recipeType/isDefault/enabled/stageCount/recipeCount/updateTime） |
| GET | `/api/process/route/{id}` | 路线详情（含 stages → steps/qcItems） |
| POST | `/api/process/route` | 新建路线（body: name/recipeType/packingRequirement/isDefault/stages） |
| PUT | `/api/process/route/{id}` | 全量保存（先删后插，沿用现有模式） |
| DELETE | `/api/process/route/{id}` | 删除；被配方引用时拦截（400 + 引用数提示） |
| POST | `/api/process/route/{id}/copy` | 复制为新路线（名 = 原名 + "(副本)"，isDefault=false） |
| POST | `/api/process/route/{id}/set-default` | 设为该类型默认（先清除同类型其他默认标记） |
| GET | `/api/process/template/{recipeType}` | **保留兼容**：改为返回该类型的**默认路线**（现有配方页展示不改动调用方） |

权限沿用 `process:read` / `process:write`。

**配方侧校验**（RecipeService.create/update）：
- `processTemplateId` 必填，缺失报"请选择工艺路线"；
- 路线必须存在且 `recipeType` 与配方一致，不一致报类型不匹配。

## 4. 前端

### 4.1 工艺路线页（原 ProcessTemplate.vue，菜单名改「工艺路线」）

- 顶部类型筛选 tab：全部 / 制浆 / 制漆；
- 列表：路线名 | 类型 | 工序数 | 绑定配方数 | 更新时间 | 操作（编辑/复制/删除/设为默认），默认路线显示 ★；
- 编辑/新建复用现有工序编辑器（工序→步骤→质检项），顶部增加路线名称输入；
- 删除被引用路线时展示后端返回的引用数提示。

### 4.2 配方表单（RecipeList.vue 新建/编辑弹窗）

- 新增「工艺路线」下拉，必选；选项按配方类型过滤，默认路线项标 ★；
- 切换配方类型时重新加载路线并自动选中该类型默认路线；
- 编辑存量配方：显示已回填的路线，可改。

### 4.3 配方详情 / 打印工艺指导单

- 工艺读取改为**按配方绑定的路线**（`/api/process/route/{id}`），不再按类型取；
- 标题展示路线名。

### 4.4 单据打印贯通（流程化关键链路）

以下打印场景现在都是按配方**类型**取默认工艺，必须全部改为**按配方绑定路线**取数，保证配方→单据工艺一致：

| 页面 | 现状 | 改造 |
|---|---|---|
| ProductionOrder.vue 生产订单打印（投料单/工艺/包装要求） | `loadProcessTpl` 按 recipeType 取 | 优先按配方 `processTemplateId` 取绑定路线，无绑定才回退类型默认 |
| OutsourceOrderList.vue 委外单打印 | 同上 | 同上 |
| RecipeList.vue 工艺指导单打印 | 按 recipeType 取 | 按绑定路线取 |

配方列表/详情接口需把 `processTemplateId` 带回前端（Recipe 实体加字段后自动序列化）。

## 5. 存量迁移与种子（ProcessSchemaInitializer，幂等）

执行顺序：

1. **重建 process_template 去唯一约束**（若唯一索引仍在），现有制浆模板数据保留并置 `is_default=1`；
2. `ensureColumn("recipe", "process_template_id", "BIGINT")`；
3. **补默认路线**：制浆路线不存在 → 种子建「制浆标准工艺」（现 V6 六工序），isDefault=1；制漆路线不存在 → 种子建「制漆标准工艺」草稿（预混/调漆→调色→过滤→打包留样送检→刷缸，内容上线后由用户按实际修正），isDefault=1；
4. **回填存量配方**：`process_template_id IS NULL` 的配方按类型绑定对应默认路线。

**种子策略变更**：取消 v6 的"版本号自动重建"机制——路线从此是用户维护数据，种子只"从无到有"创建默认路线，绝不覆盖已存在路线（否则用户在页面上的修改会在重启后丢失）。`version` 列保留但停用。

## 6. 边界规则

| 场景 | 行为 |
|---|---|
| 删除被配方引用的路线 | 拦截，提示"该路线被 N 个配方使用" |
| 删除默认路线 | 拦截（须先把默认转移到其他路线） |
| 停用路线 | 不出现在配方下拉；已绑配方照常展示/打印 |
| 配方切换类型 | 路线下拉重新过滤，自动选中新类型默认路线 |
| 配方绑定路线后被删 | 不会发生（删除有拦截） |

## 7. 不在本次范围

- 工艺执行记录 / 流转单（二期：实测值、操作人、时间追溯）；
- 细度不合格"循环重磨"的状态流转；
- 配方级工艺覆盖（个别微调）。
