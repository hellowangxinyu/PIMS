# 芃远综合管理系统（PIMS）外部系统对接接口文档

> **版本**: v2.0（对应系统 v7.3，git tag v7.3-bucket-labels）
> **日期**: 2026-09-05
> **适用对象**: 需要与本系统做数据对接的外部系统（ERP、MES、WMS、客户门户等）的开发者
> **对接方式**: HTTP RESTful API，JSON 数据格式
> **v2.0 变更**（v5.28→v7.3 累计，接口总数 265→**513**，55 个 Controller）：
> - **新增模块**：出纳银行对账（/api/bank 10 端点）、MRP 采购建议（/api/mrp）、价格政策（/api/price-policy）、任务督办（/api/task）、供应商质量追溯（/api/quality-trace）、质检模板（/api/qc-template）、包装标准（/api/packaging-standard）、账号级 UI 配置（/api/ui-config）、异常订单处置（/api/abnormal-order）
> - **请购单闭环**：POST 支持明细、PUT /{id}/header（补供应商）、POST /{id}/audit、POST /{id}/to-purchase（转采购）、DELETE（草稿）
> - **新报表端点**：GET /api/qc/statistics（不良率统计）、GET /api/report/turnover（周转率）、GET /api/finance-report/supplier-statement（供应商对账单）、POST /api/voucher/year-end-close（年结）、GET /api/recipe/{id}/changes（配方变更日志）
> - **安全**：Token 改 HttpOnly Cookie（header 兼容）、登录 5 次锁定 10 分钟、must_change_pwd 强制改密、AI 查询敏感表黑名单
> - **口径**：对账单/调节表时点口径（未达账项截至对账日全量）、REWORK_OUT 返工不计用量、时区统一 +8 hours、物料/大类阶梯价
> - 详见 §4.26~§4.35 新章节

---

## 1. 系统概述

- **系统名称**: 广东芃远新材料有限公司 · 芃远综合管理系统（PIMS）
- **系统定位**: 涂料企业综合管理系统，覆盖采购、生产（配方）、委外、库存、质检、销售、财务、报表全业务域
- **技术栈**: Spring Boot 3.3（Java 17）+ SQLite + Vue 3
- **接口 Base URL**: `http://<host>:8080/api`（生产环境经 nginx/Caddy 反代后为 `https://<域名>/api`）
- **数据格式**: 请求/响应均为 `application/json;charset=UTF-8`（导出接口除外，返回 xlsx 文件流）
- **时间格式**: 业务日期字段为 `yyyy-MM-dd`（如 `2026-08-08`）；时间戳字段为 ISO 格式（如 `2026-08-06T08:42:04`）
- **编码**: 物料编码、批次号等业务编码全局唯一，由系统自动生成，对接方**不应自行造码**

> ⚠️ **重要**：本系统为**单实例部署**（内嵌 Tomcat 端口 8080 + 单文件 SQLite），对接方需注意并发量级（5-10 人小团队规模），高并发写入请排队或限流。

---

## 2. 认证鉴权

### 2.1 获取 Token（登录）

```
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{ "username": "admin", "password": "admin123" }
```

响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "username": "admin",
    "realName": "系统管理员",
    "userId": 1,
    "role": "SS",
    "permissions": ["supplier:read", "material:read", "..."],
    "token": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  }
}
```

### 2.2 Token 使用方式

- 除登录/登出/当前用户信息外，**所有接口**都必须在请求头携带 Token：

```
pims-token: <token>
```

- Token 有效期由服务端 Sa-Token 管理（默认长期有效，登出失效）。
- 错误响应：Token 缺失/失效 → `401 {"code":401,"msg":"请先登录"}`；权限不足 → `403 {"code":403,"msg":"无权限访问"}`。

### 2.3 ⚠️ 单点登录（对接方必读）

系统**已启用单点登录（同一账号同时只能有一个会话）**：

- 同一账号**新登录会踢掉旧会话**（旧 Token 立即失效，下一次请求返回 401）。
- 对接方如果使用**专用对接账号**，请确保该账号不会被人工登录占用；或为对接方单独创建账号（推荐做法：在「系统设置 → 用户管理」为对接系统创建专用账号并分配所需权限码）。
- **建议**：对接方与人工使用分开账号，避免互踢。

### 2.4 权限码体系

每个接口标注了所需权限码（见第 4 节各表）。对接账号需由管理员在「角色权限」中授予对应权限。常用权限码：

| 模块 | 权限码 |
|---|---|
| 物料 | material:read / write / delete |
| 客户 | customer:read / write / delete |
| 供应商 | supplier:read / write / delete / payment（付款信息，仅GM+FINANCE） |
| 仓库 | warehouse:read / write / delete |
| 库存 | inventory:read / write |
| 配方 | recipe:read / write |
| 生产 | production:read / write |
| 采购 | purchase:read / write / audit / reverse-audit / price（价格可见） |
| 销售 | sales:read / write / audit / reverse-audit |
| 委外 | outsource:read / write / audit / reverse-audit |
| 财务 | finance:read / write / audit / reverse-audit / amount（金额可见） |
| 质检 | qc:read / write |
| AI 智能助手 | ai:read / write |
| 用户/字典 | user:read / write / delete；dict:read / write |

> **字段级脱敏**：`purchase:price` 控制采购价格字段、`finance:amount` 控制财务金额字段。**无对应权限时，接口返回的实体中这些字段会被删除（不返回）**，导出接口中对应列会被省略。对接方需要价格/金额数据时，必须为对接账号授予 `purchase:price` / `finance:amount`。

---

## 3. 通用约定

### 3.1 响应格式（标准 JSON 壳）

```json
{
  "code": 200,      // 200=成功；400=业务校验失败（msg 为中文原因）；401=未登录；403=无权限；404=资源不存在；500=系统异常
  "msg": "success", // 失败时为人性化中文错误信息
  "data": { ... }   // 业务数据
}
```

- 业务校验失败（如库存不足、编码重复）返回 **HTTP 200 + code 400**（msg 为错误原因）；鉴权失败返回对应 HTTP 状态码。
- 对接方应同时处理 HTTP 状态码与 body 中的 code。

### 3.2 分页格式（两种并存，注意区分）

| 风格 | 适用接口 | 分页参数 | 返回结构 |
|---|---|---|---|
| **新风格**（rows/total） | 库存、出入库单、到货明细、采购历史等 | `page`（从 1 开始）、`pageSize` | `{"rows": [...], "total": n}` |
| **旧风格**（Spring Page） | 原料/成品采购 search、质检 search | `page`（**从 0 开始**）、`size` | `{"content": [...], "totalElements": n, "totalPages": n, "number": n, "size": n}` |

> 对接时请逐个接口核对分页风格（第 4 节表中已标注）。**pageSize 无上限限制**，取全量可传大值（如 100000）。

### 3.3 导出接口（文件流）

- 路径含 `/export` 的接口返回 **xlsx 二进制流**，不是 JSON。
- 响应头：`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`、`Content-Disposition: attachment; filename*=UTF-8''<文件名>.xlsx`。
- 仍需要携带 `pims-token` 请求头。
- 对接方用 HTTP 客户端直接保存响应体为文件即可。

### 3.4 日期字段说明

- **业务日期**（采购日期、订单日期、检验日期等）：`yyyy-MM-dd` 字符串。
- **创建/更新时间**（createTime/updateTime）：ISO 本地时间字符串。
- **台账日期**（inbound_date/expiry_date，实体序列化为 `yyyy-MM-dd`）：实体字段直接可用；原生 SQL 统计场景请使用 `date(col/1000,'unixepoch','+8 hours')` 转换（北京时间）。

### 3.5 单据状态流转（通用）

| 单据 | 状态流 |
|---|---|
| 生产/委外订单 | DRAFT（草稿）→ CONFIRMED（已确认）→ COMPLETED（已完工） |
| 销售订单 | DRAFT → CONFIRMED → SHIPPED（全部发完自动） |
| 采购单（原料/成品） | DRAFT（开立）→ APPROVED（已审核）→ CLOSED（已关闭）；另有已到货数量字段 |
| 质检单 | PENDING（待检）→ PASS（合格）/ CONCESSION（让步接收）/ REJECT（退货） |
| 应收/应付 | UNPAID（未结）→ PARTIAL（部分）→ PAID（已结清） |
| 采购退货单 | DRAFT（待审核）→ DONE（已退货完成）/ REJECTED（已驳回） |
| 销售退货单 | DRAFT → APPROVED（已审核待入库）→ DONE（已入库完成）/ REJECTED |

### 3.6 核心业务规则（对接必须遵守）

1. **物料编码自动生成且不可修改**：新增物料时选择小类后由系统生成编码（`POST /api/coding-rule/generate?subCategoryCode=`），创建物料必须携带编码，编码全局唯一（含已禁用物料）。
2. **批次规则**：所有入库自动生成批号（格式 `B+日期-当日序号`，如 `B20260808-001`）；出库必须选择批号（`/api/inventory/batch` 查询可选批号）。
3. **所有入库需经质检合格**：采购到货/生产入库/委外入库/其他入库确认后生成待检质检单（PENDING），质检判定 PASS/CONCESSION 才正式入库；REJECT 不入库。
4. **过期批次禁出**（v5.23）：`expiryDate < 今天` 的批次禁止正常业务出库（销售/生产领料/委外发料/其他出库非报废类），仅其他出库原因 `SCRAP`/`SAMPLE`/`RETURN` 放行。
5. **生产批量下限 100kg**：生产/委外订单生产批量不得低于 100。
6. **配方引用校验**：被禁用的配方（`recipe.enabled=false`）不可被生产/委外订单引用。
7. **成本保密**：操作界面/出库选批号不返回价格；成本仅在报表与导出场景（且按权限）可见。

---

## 4. 接口清单（按模块）

> 参数说明：`?` 开头为 Query 参数；`{id}` 为路径参数；`JSON` 为请求体。分页风格：`R/T`=rows/total（page 从 1），`S/P`=Spring Page（page 从 0）。

### 4.1 认证

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/auth/login | 无 | 登录获取 Token（见 §2.1） |
| POST | /api/auth/logout | 无 | 登出（需 pims-token 头） |
| GET | /api/auth/info | 无 | 当前用户信息（含权限列表） |

### 4.2 基础数据

**物料**（权限 material:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/material?keyword=&category=&enabled= | 物料列表（enabled=true 仅启用中） |
| GET | /api/material/{id} | 物料详情 |
| POST | /api/material | 新增（编码自动生成后传入，见 §3.6-1） |
| PUT | /api/material/{id} | 修改（编码不可修改） |
| DELETE | /api/material/{id} | 删除（软删除：enabled=false） |
| GET | /api/material/export?keyword=&category=&enabled= | 导出 xlsx（大类/小类/主材/色系中文） |
| GET | /api/coding-rule | 编码规则列表 |
| POST | /api/coding-rule/generate?subCategoryCode= | 生成物料编码（必填小类代码） |

**客户/供应商/仓库**（权限 customer:*/supplier:*/warehouse:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | /api/customer、/api/customer/{id} | 客户 CRUD（含收款条件 payment_terms、收款方式 payment_method） |
| GET/POST/PUT/DELETE | /api/supplier、/api/supplier/{id} | 供应商 CRUD（type=MATERIAL 材料/FINISHED 成品；付款条件受 supplier:payment 控制） |
| GET/POST/PUT/DELETE | /api/warehouse、/api/warehouse/{id} | 仓库 CRUD（三级结构：仓库→分库 zone→库位 location） |
| GET | /api/warehouse/{warehouseId}/zone | 分库列表 |
| GET | /api/warehouse/zone/{zoneId}/location | 库位列表 |
| GET | /api/dict | 数据字典列表（物料大类 material_category、小类 material_sub_category、主材 material_main_material、色系 material_color_series、付款条件 payment_terms 等） |

### 4.3 配方（权限 recipe:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/recipe?keyword= | 配方列表（含成本） |
| GET | /api/recipe/released | 已发布配方（含 recipeNo/versionId/versionNo/recipeType，创建订单参照用） |
| GET | /api/recipe/{id} | 配方详情 |
| GET | /api/recipe/{id}/versions | 版本列表 |
| GET | /api/recipe/version/{versionId}/tree | 配方树（多级 BOM） |
| GET | /api/recipe/version/{versionId}/plan?qty= | **投料计划展开**（按生产批量比例计算实际投料量，创建生产/委外订单核心接口） |
| GET | /api/recipe/version/{versionId}/cost | 版本成本 |
| GET | /api/recipe/costs | 已发布配方成本列表 |
| GET | /api/recipe/material-prices | **物料价格映射 {编码:单价}**（库存加权均价→采购价回退，与配方树成本同源，配方页选物料实时显示单价用） |
| POST | /api/recipe、/api/recipe/{id}/version、/api/recipe/version/{versionId}/tree | 创建配方/版本/保存配方树 |
| POST | /api/recipe/version/{versionId}/release | 发布版本 |
| PUT | /api/recipe/{id}/enabled?enabled=true\|false | 禁用/启用配方（禁用后不可被订单引用） |

**标准工艺（权限 process:read/write，生产管理 → 标准工艺）**：按配方类型（GRINDING/TINTING）维护标准工艺流程（工序→步骤/质检项）+ 包装要求；步骤描述支持 `{{N}}` 占位符（N=配方投料顺序，投料单/加工单打印时替换为物料名）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/process/template/{recipeType} | 取标准工艺模板（name/packingRequirement/stages[]→steps[]/qcItems[]） |
| PUT | /api/process/template/{recipeType} | 全量保存标准工艺模板 |

### 4.4 生产订单（权限 production:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/production-order?status= | 订单列表 |
| GET | /api/production-order/{id} | **订单详情（含 items 投料明细）** |
| GET | /api/production-order/{id}/items | 投料明细 |
| POST | /api/production-order | 创建（body：productName/productCode/batchQty/unit/recipeVersionId/remark/items[]；批量≥100） |
| PUT | /api/production-order/{id} | 修改（仅草稿） |
| POST | /api/production-order/{id}/confirm | 确认 |
| POST | /api/production-order/{id}/complete | 完工 |
| DELETE | /api/production-order/{id} | 删除（仅草稿） |
| GET | /api/production-order/{id}/trace | 配方谱系+实际领料批次追溯 |
| GET | /api/production-order/{id}/stock-check | **出库前库存预检**（订单各物料需求 vs 自有仓可用库存，含缺口/enough，只算不扣） |
| POST | /api/production-order/{id}/auto-outbound | **确认并自动出库**（只从自有仓、按最早入库批号先进先出、批次不足自动补下一批、按库位拆行记录、库存不足整体回滚；返回出库明细列表） |
| GET | /api/production-order/{id}/outbounds | 出库记录明细（production_outbound 按订单号，含批号/仓库/库位/数量） |

### 4.5 委外订单（权限 outsource:*，与生产订单结构对称）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/outsource-order?status= | 订单列表 |
| GET | /api/outsource-order/{id} | 订单详情（含 items） |
| POST | /api/outsource-order | 创建（额外字段：supplierId 代工厂、processingFee 加工费单价——选代工厂时从供应商档案 supplier.processing_fee 自动带出，可改） |
| POST | /api/outsource-order/{id}/confirm / complete | 确认 / 完工 |
| GET | /api/outsource-order/{id}/trace | 追溯（含代工厂） |
| GET | /api/outsource-order/{id}/stock-check | **出库前库存预检**（需求 vs 该代工厂仓可用库存） |
| POST | /api/outsource-order/{id}/auto-outbound | **确认并自动出库**（只从该代工厂对应仓库、先进先出、按库位拆行） |
| GET | /api/outsource-order/{id}/outbounds | 出库记录明细 |

### 4.6 采购（权限 purchase:*）

| 方法 | 路径 | 分页 | 说明 |
|---|---|---|---|
| GET | /api/raw-material-purchase | — | 原料采购全量列表 |
| GET | /api/raw-material-purchase/search | S/P | 多条件分页搜索（status/orderNo/supplierName/materialCode/materialName/startDate/endDate） |
| GET | /api/finished-product-purchase/search | S/P | 成品采购搜索（同参数） |
| POST | /api/raw-material-purchase | — | 创建原料采购（单物料） |
| POST | /api/raw-material-purchase/batch | — | **批量创建（一张单据一个供应商多个物料，原子事务）** |
| POST | /api/finished-product-purchase/batch | — | 成品采购批量创建 |
| POST | /api/raw-material-purchase/{id}/audit / reverse-audit | — | 审核 / 反审核 |
| POST | /api/finished-product-purchase/{id}/audit / reverse-audit | — | 审核 / 反审核 |
| GET | /api/purchase-arrival?type=&keyword= | R/T | 到货明细（type=RAW/FINISHED） |
| POST | /api/purchase-arrival | — | 录入到货（审核后生成待检质检单） |
| POST | /api/purchase-arrival/{id}/audit / reverse-audit | — | 到货审核 / 反审核（审核即生成应付 AP） |
| GET | /api/purchase/incomplete?type= | — | 未完全到货订单列表（到货录入参照） |
| POST | /api/purchase/arrival/record | — | 到货录入（id/type/arrivalQty/warehouseId/locationId；v5.35 批号自动生成，不接受手工批号） |
| POST | /api/purchase/close/{id}?type= | — | 手动关闭采购订单 |
| GET | /api/raw-material-purchase/export、/api/finished-product-purchase/export | — | 导出 xlsx（价格列受 purchase:price 控制） |
| GET | /api/return-order | — | 采购退货单列表（type=PURCHASE_RETURN） |
| POST | /api/return-order/{id}/approve / reject | — | 退货单审核通过 / 驳回（审核即完成，无库存变动） |

### 4.7 库存（权限 inventory:*）

| 方法 | 路径 | 分页 | 说明 |
|---|---|---|---|
| GET | /api/inventory/summary?view=code\|batch&warehouseId=&keyword= | R/T | **库存双视图**：view=code 按编码聚合；view=batch 按批次聚合（含质检状态/过期日期） |
| GET | /api/inventory/batch?materialCode=&warehouseId= | — | 某物料可选批号（含可用量） |
| GET | /api/inventory/total/{materialCode} | — | 某物料总库存 |
| GET | /api/inventory/movements/{materialCode} | — | 库存异动日志 |
| GET | /api/inventory/trace?materialCode=&batchNo= | — | 批次全链路追溯（含归档） |
| GET | /api/inventory/low-stock | — | 低库存预警（可用天数<15天） |
| GET | /api/inventory/price-trend/{materialCode} | — | 价格走势（采购单+入库单合并） |
| GET | /api/inventory/export?view=&warehouseId=&keyword= | — | 库存导出 xlsx（双视图） |
| POST | /api/stock-check/gain / loss / location-adjust | — | 盘点：盘盈 / 盘亏 / 库位调整。**location-adjust 参数**：materialCode/materialName/batchNo（必传，按批次精确定位）/warehouseId/fromLocationId/toLocationId/qty + **toWarehouseId（可空，非空=跨仓库调整：原仓扣减→目标仓建立/增加台账行，写 ADJUSTMENT 异动留痕）** |
| GET | /api/outbound/production / sales / outsource / other | R/T | 各类出库单列表 |
| GET | /api/outbound/production-inbound / outsource-inbound / other-inbound | R/T | 各类入库单列表 |
| POST | /api/outbound/other | — | 其他出库（reason=SCRAP/SAMPLE/LOSS/RETURN/OTHER；**过期批次仅 SCRAP/SAMPLE/RETURN 放行**） |
| POST | /api/outbound/other-inbound | — | 其他入库 |
| POST | /api/outbound/other-inbound/{id}/confirm | — | 其他入库确认（生成待检质检单） |
| POST | /api/outbound/production-inbound、/outsource-inbound | — | 生产/委外入库创建（参照订单，前置校验：必须先出库） |

### 4.8 销售（权限 sales:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/sales-order | 销售订单列表（全量） |
| GET | /api/sales-order/{id}、/api/sales-order/{id}/items | 订单详情 / 明细 |
| POST | /api/sales-order | 创建（customerId/contractNo/sourceWarehouseId/expectedShipDate/items[{materialCode,qty,unit,unitPrice}]） |
| POST | /api/sales-order/{id}/confirm | 确认 |
| POST | /api/sales-order/{id}/ship | 发货（直接扣库存+生成应收，建议用出库单流程） |
| DELETE | /api/sales-order/{id} | 删除（仅草稿） |
| GET | /api/sales-order/export | 导出 xlsx |
| POST | /api/outbound/sales | 销售出库单创建（草稿，参照订单） |
| POST | /api/outbound/sales/{id}/confirm | 出库审核（扣库存→回写已发量→生成应收 AR） |
| GET | /api/sales-return | 销售退货单列表 |
| POST | /api/sales-return | 创建退货单（参照销售出库单或手工） |
| POST | /api/sales-return/{id}/approve / reject | 审核 / 驳回 |
| POST | /api/sales-return/{id}/inbound?warehouseId= | 退货入库（自动新批号+冲减应收） |

### 4.9 质检（权限 qc:*）

| 方法 | 路径 | 分页 | 说明 |
|---|---|---|---|
| GET | /api/qc?type= | — | 质检单列表 |
| GET | /api/qc/pending?type= | — | 待检列表 |
| GET | /api/qc/search?category=&status=&inspectionNo=&materialCode=&materialName=&batchNo=&inspector=&startDate=&endDate= | S/P | 多条件搜索（category=A,P,F,R,S/B/C 逗号分隔） |
| POST | /api/qc/{id}/judge | — | **质检判定**：body `{"result":"PASS\|CONCESSION\|REJECT","resultRemark":"说明"}`（PASS/CONCESSION 触发入库，REJECT 触发采购退货单） |
| GET | /api/qc/export?category=&status=... | — | 质检记录导出 xlsx |

### 4.10 财务（权限 finance:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/finance/ar、/ap | 应收 / 应付列表（金额受 finance:amount 控制） |
| GET | /api/finance/ar/total、/ap/total | 按客户/供应商聚合总表 |
| GET | /api/finance/receipt、/disbursement | 收款单 / 付款单流水 |
| POST | /api/finance/receipt | 创建收款单（docNo 自动生成 PR-YYYY-NNNN，自动冲减 AR） |
| POST | /api/finance/disbursement | 创建付款单（自动冲减 AP） |
| GET | /api/finance/ar/export、/ap/export、/receipt/export、/disbursement/export | 导出 xlsx（金额列受 finance:amount 控制） |

> 应收应付由业务单据自动生成（销售发货→AR；采购到货/委外入库质检合格→AP），**不建议外部系统直接创建 AR/AP**，应通过单据流程驱动。

### 4.10.1 发票管理（v5.36，权限 finance:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/invoice | 发票列表（金额受 finance:amount 控制） |
| POST | /api/invoice | 登记发票（docNo 自动生成 INV-YYYY-NNNN；taxAmount/totalAmount 服务端按 amount×taxRate 计算；partnerType 按方向自动 CUSTOMER/SUPPLIER） |
| PUT | /api/invoice/{id} | 编辑（仅 NORMAL 状态） |
| DELETE | /api/invoice/{id} | 删除（仅 NORMAL 且未被红冲引用） |
| POST | /api/invoice/{id}/red-flush?redInvoiceNo=&reason= | 红冲：生成负数对冲发票，原单标记 FLUSHED 并指向红字单 |
| GET | /api/invoice/customer-summary | 客户开票汇总：开票净额(不含税/价税合计/税额) vs 应收立账 vs 已回款 |
| GET | /api/invoice/export | 导出 xlsx |

### 4.10.2 费用管理（v5.36，权限 finance:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/expense | 费用单列表（direction EXPENSE/INCOME；金额受 finance:amount 控制） |
| POST | /api/expense | 登记费用单（EXP-YYYY-NNNN；expenseType 走字典 expense_type/other_income_type） |
| PUT | /api/expense/{id}、DELETE /api/expense/{id} | 编辑 / 删除 |
| GET | /api/expense/export | 导出 xlsx |

### 4.10.3 预收预付（v5.36，权限 finance:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/advance | 预存单列表（direction RECEIVE/PAY；金额受 finance:amount 控制） |
| POST | /api/advance | 登记预存单（ADV-YYYY-NNNN） |
| DELETE | /api/advance/{id} | 删除（已冲抵不可删） |
| POST | /api/advance/{id}/apply-ar | 预收冲应收：body `{arId, amount}`（校验同客户、不超预收余额；AR 按收款处理） |
| POST | /api/advance/{id}/apply-ap | 预付冲应付：body `{apId, amount}`（校验同供应商） |
| GET | /api/advance/export | 导出 xlsx |

### 4.10.4 成本核算与财务报表（v5.36，权限 finance:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/cost/order?type=PRODUCTION\|OUTSOURCE | 订单成本归集：材料成本/加工费/人工/制费/总成本/产出量/单位成本/理论成本(配方BOM按批量折算)/差异%；成本列受 finance:amount 控制 |
| PUT | /api/cost/order/{orderNo}/fees?laborFee=&overheadFee= | 生产订单人工/制费补录（finance:write） |
| GET | /api/finance-report/profit-trial?month=YYYY-MM | 月度利润试算：收入(AR 立账)/成本(销售出库)/毛利/费用分列/其他收入/净利 + 资金占用参考 + 近12月走势 |
| GET | /api/finance-report/profit-trial/export?month= | 导出 xlsx |
| GET | /api/finance-report/statement?customerId=&from=&to= | 客户对账单取数：customer/from/to/opening/lines(立账+/收款−/退货−/冲减调整)/debit/credit/closing（期末锚定 AR 台账余额） |

### 4.11 报表（权限按模块 read）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /api/report/overview?months= | finance:read | 经营看板 KPI |
| GET | /api/report/sales?months= | sales:read | 销售报表 |
| GET | /api/report/margin?months= | sales:read | 毛利分析 |
| GET | /api/report/purchase?months= | purchase:read | 采购报表 |
| GET | /api/report/purchase-analysis | purchase:read | 采购分析（比价/完成率） |
| GET | /api/report/inventory?months= | inventory:read | 库存报表 |
| GET | /api/report/stock-analysis | inventory:read | 库存分析（库龄/呆滞） |
| GET | /api/report/production?months= | inventory:read | 生产报表 |
| GET | /api/report/production-progress | inventory:read | 生产进度 |
| GET | /api/report/low-stock | inventory:read | 低库存预警 |
| GET | /api/report/expiry | inventory:read | **批次过期预警**（已过期+30天内） |
| GET | /api/report/expiry/export | inventory:read | 过期预警导出 |
| GET | /api/report/qc?months= | qc:read | 质检报表 |
| GET | /api/report/aging | finance:read | 账龄分析 |
| GET | /api/report/outsource?months= | outsource:read | 委外报表 |
| GET | /api/report/order-exec | sales:read | 销售订单执行率 |

### 4.12 系统管理（权限 user:*/dict:*/log:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | /api/user、/api/user/{id} | 用户 CRUD（reset-password 重置密码） |
| GET/POST/PUT/DELETE | /api/role、/api/role/{id} | 角色 CRUD |
| GET | /api/role/permissions/tree | 权限树 |
| PUT | /api/role/{roleCode}/permissions | 设置角色权限 |
| GET | /api/dict、POST/PUT/DELETE /api/dict | 数据字典 CRUD |
| GET | /api/log?module=&startDate=&endDate= | 操作日志查询 |
| POST | /api/dashboard/rebuild-stats | 重建统计汇总表（维护用） |

### 4.13 AI 智能助手（权限 ai:*）

> 系统内置 AI 助手的对接接口（对话式查数/统计/分析，**仅只读**）。配置存 `ai_config` 表（key-value），**API Key 明文存储**，对接方注意保密。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/ai/config | 读取 AI 配置（baseUrl/apiKey/model/enabled/protocol/thinking；Key 明文返回） |
| PUT | /api/ai/config | 保存 AI 配置（JSON 对象：baseUrl/apiKey/model/enabled/protocol/thinking；protocol=openai\|anthropic，thinking=off\|adaptive\|deep；请求体非 JSON 对象返回 400「请求体必须是 JSON 对象」） |
| POST | /api/ai/chat | 对话（body：`{"messages":[{"role":"user","content":"..."}]}`，传全量历史 → 响应 `{"reply":"...","rounds":N}`，非流式，单次最长约 2 分钟） |
| POST | /api/ai/test | 测试连接（返回 `{"ok":true,"model":"...","reply":"..."}`；未配置地址/Key/模型时返回对应错误提示） |

**AI 对话机制说明**：

- **双协议**由配置 `protocol` 决定：OpenAI 兼容 `{baseUrl}/chat/completions`（`Authorization: Bearer`）／Anthropic 兼容 `{baseUrl}/v1/messages`（`x-api-key`）；地址拼接容错（裸地址/带 /v1/带完整端点均可）
- **内置工具**：`list_tables`（全库表及字段结构）、`query_data`（只读 SQL：去字面量后须 SELECT/WITH 开头 + 写关键字黑名单；无 LIMIT 自动兜底 100 行）；agent loop 最多 10 轮，连续 3 轮相同工具调用自动收敛强制总结
- **只读约束**：AI 只能查数据，禁止任何增删改；`enabled=false` 或配置不完整时调用返回错误提示（"AI 助手未启用…"）
- **思考强度**：off/adaptive/deep 按协议映射为 disabled/adaptive/enabled+budget_tokens；Anthropic 协议回复中的思考块已被后端过滤，OpenAI adaptive 模式回复可能含 `<think>` 文本（前端未处理）
- 系统提示词由后端注入（含当天日期），调用方只需传 user/assistant 历史消息

---

### 4.13A CRM 客户经营（v5.50，权限 crm:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | /api/crm/contact... | 联系人 CRUD（挂 customerId 或独立线索公司；isPrimary 主联系人标记） |
| GET/POST/PUT/DELETE | /api/crm/opportunity... | 商机 CRUD（stage：LEAD/QUOTED/SAMPLING/NEGOTIATING/WON/LOST） |
| GET | /api/crm/pipeline-summary | 管道汇总：byStage 各阶段数量金额 + openCount/openAmount/wonCount/wonAmount/lostCount |
| POST | /api/crm/opportunity/{id}/stage | 阶段流转（body `{stage, wonOrderNo?, lossReason?}`；WON 须单号或已关联客户、LOST 须原因） |
| GET/POST/DELETE | /api/crm/follow-up?opportunityId=&customerId= | 跟进记录（method：PHONE/VISIT/WECHAT/EMAIL/MEETING/OTHER；nextDate 到期进 dashboard todos.dueFollowUp） |

### 4.14 周度会议（v5.44，权限 meeting:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/meeting/topic | 每周议题列表（按计划日期倒序） |
| POST / PUT /{id} / DELETE /{id} | /api/meeting/topic | 新增 / 编辑 / 删除 |
| POST | /api/meeting/topic/{id}/close?close= | 结案（true=填今天）/ 反结案（false 清空） |
| GET / POST | /api/meeting/topic/export、/topic/import | 导出 xlsx / 单表导入（表头名定位列，去重：责任人+待办事项一致跳过，返回"新增 X 跳过 Y"） |
| GET/POST/PUT/DELETE | /api/meeting/rd... | 研发进度同款 CRUD/close/export/import |
| POST | /api/meeting/import | 原双 sheet（每周议题+研发进度）存量导入，幂等=表非空跳过 |

### 4.15 过期批次复检（v5.37/39，权限 qc:*）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/qc/reinspection | 对过期批次发起复检（body `{materialCode, batchNo}`）→ 生成 PENDING 复检单（快照质检模板）；每日 04:15 过期隔离后系统自动触发 |
| POST | /api/qc/{id}/judge | 判定；**body 增 reexpiryDate**（复检单 PASS/CONCESSION 必填）——合格恢复可用并移回普通库位、台账过期日期更新为新有效期；REJECT 维持隔离 |

### 4.16 仓库隔离分库（v5.38，权限 warehouse:read）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/warehouse/{id}/locations | 库位列表（**默认排除隔离分库**库位；?includeIsolated=true 包含——隔离货管理场景） |
| GET | /api/warehouse/isolated-locations?type=&warehouseId= | 按类型取隔离库位（type ∈ UNQUALIFIED_RAW/UNQUALIFIED_SEMI/UNQUALIFIED_FIN/TAILING/TAILING_FC，传 UNQUALIFIED 兼容映射原材料库） |
| GET | /api/tailing-return/warehouse-options | 各启用仓的聚酯/氟碳油尾库信息（油尾退回创建选仓用） |

分库结构：每个一级仓必备 5 隔离分库（3 不合格品库按物料大类 + 2 油尾库按体系）；隔离判断=库位→分库 zone_type；入库/出库/FIFO/盘库四道防线见 PRD §5.9。

### 4.17 物料编码与生命周期（v5.40-43）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/coding-rule/generate?subCategoryCode= | 生成 6 位编码：小类码+4 位序号，**数字全局连续单次使用**（0001-9999，跨类别接着排）；优先复用回收池 |
| PUT | /api/material/{id}/enabled?value= | 禁用/启用（禁用后选料下拉不可见+采购/销售创建被拒） |
| DELETE | /api/material/{id} | **真删除**：被 23 张单据表引用或有台账记录→400 拒绝（提示占用来源）；无引用→删除且编码数字回收入池复用 |

编码格式：首位字母=物料大类（A助剂/P颜料/F填料/R树脂/S溶剂/B半成品/C成品）；保存时才取号；`/api/material` 支持 enabled 过滤参数（业务页面应传 enabled=true）。

### 4.18 经营效率端点（v5.47-48）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/dashboard | **todos** 待办聚合：pendingQc/pendingReinspect/pendingReturn/pendingTailing/dueArCount/dueArAmount/overdueTopics/lastBackup/backupStale |
| POST | /api/dashboard/backup | 手动即时备份（user:write，返回备份文件名） |
| GET | /api/supplier/{id}/profile | 供应商 360°（v5.49）：apBalance/apTotal/apPaid/payTotal/invoiceNetTotal/orderCount + recentOrders/recentPayments/recentReturns |
| GET | /api/customer/{id}/profile | 客户 360°：arBalance/arTotal/arReceived/receiptTotal/invoiceNetTotal/orderCount + recentOrders/recentReceipts/recentReturns |
| PUT | /api/sales-order/{id} | 编辑订单（仅 DRAFT；body 同创建，自动 diff 留痕） |
| GET | /api/sales-order/{id}/changes | 变更记录时间线（operator/detail/createTime） |
| POST | /api/print-count | docType 增 **SALES_OUTBOUND**（送货单打印计数，body `{docType:"SALES_OUTBOUND", docNo}`） |

### 4.19 物料操作铁律（v5.46）

所有库存相关写接口（出入库/盘库/调拨）：**批号与库位必填**，缺失返回 400（"物料操作精确到批次/库位铁律"）；盘库三入口（盘盈/盘亏/库位调整 location-adjust）前置校验；库存流水（inventory_movement）新记录含 locationId（精确到库位）。

### 4.20 销售防错三件套（v5.52）

- **报价单**（权限沿用 sales:read/write）：
  - `GET /api/quotation?customerId=&status=` 列表（含 materialNames 摘要、expired 动态过期标记）
  - `GET /api/quotation/{id}` / `GET /api/quotation/{id}/items` 详情与明细
  - `POST /api/quotation` `{customerId, customerName, validUntil?, remark?, items:[{materialCode, materialName, qty, unit?, unitPrice?}]}` 建草稿（单号 BJ-YYYY-NNNN 自动生成，validUntil 默认当天+30 天）
  - `PUT /api/quotation/{id}` 编辑（仅 DRAFT）；`DELETE /api/quotation/{id}` 删除（仅 DRAFT）
  - `POST /api/quotation/{id}/submit` 提交报价（DRAFT→QUOTED）；`POST /api/quotation/{id}/reject` 标记未接受（QUOTED→REJECTED）
  - `POST /api/quotation/{id}/to-order` 转销售订单（QUOTED 且未过期；生成 DRAFT 订单，单号/合同号自动；报价单→ACCEPTED 并回填 salesOrderNo）
- **成交价**：
  - `GET /api/sales-order/recent-price?customerId=&materialCode=` 该客户该物料最近成交价 `{unitPrice, orderNo, orderDate}`（非草稿订单最新一笔，无历史返回空对象）
  - `GET /api/sales-order/recent-prices?customerId=` 该客户各物料最新成交价列表（窗口函数去重）
- **信用检查**：
  - `GET /api/customer/{id}/credit-check?amount=本单金额` → `{creditLimit, arBalance, orderAmount, projected, exceed}`（空/0 额度=不限额 exceed 恒 false；前端 exceed=true 时弹确认框，软拦截）

### 4.21 打样与客户投诉（v5.53）

- **打样**（权限 sample:read/write）：
  - `GET /api/sample?customerId=&status=` 列表（statusLabel 中文状态）
  - `POST /api/sample` `{customerName(必填,可为线索公司), customerId?, materialCode?, materialDesc(必填), qty?, applicant?}` 建申请（单号 DY-YYYY-NNNN）
  - `PUT /api/sample/{id}` 编辑（仅 APPLIED）
  - `POST /api/sample/{id}/coloring` `{colorist, colorNote}` 开始/回炉调色——**首次自动在研发进度建条目**，回炉更新轮次备注
  - `POST /api/sample/{id}/send` `{sendDate?, expressNo}` 寄样
  - `POST /api/sample/{id}/feedback` `{satisfied: bool, content, feedbackDate?}` 客户反馈（false=需调整回炉，adjustCount+1）
  - `POST /api/sample/{id}/win` `{orderNo}` 转单（研发进度自动结案）；`POST /api/sample/{id}/lose` `{reason}` 未成交（同样结案）
  - `DELETE /api/sample/{id}` 删除（仅 APPLIED）
- **投诉**（权限 complaint:read/write）：
  - `GET /api/complaint?customerId=&status=`（PROCESSING/RESOLVED/CLOSED）
  - `POST /api/complaint` `{customerName, materialCode?, batchNo?, qcDocNo?, salesOrderNo?, category?, description}` 登记（单号 TS-YYYY-NNNN，状态 PROCESSING）
  - `PUT /api/complaint/{id}` 编辑（仅 PROCESSING）
  - `POST /api/complaint/{id}/resolve` `{cause, action, handler, resolveDate?}` 标记已处理；`POST /api/complaint/{id}/close` 关闭（客户确认）
  - `GET /api/complaint/{id}/trace` 批次追溯——该投诉物料+批号的全部出入库流水（主表+归档表，含仓库/库位/操作人）
  - `DELETE /api/complaint/{id}` 删除（仅 PROCESSING）

### 4.22 总账体系：凭证/结账/三大报表（v5.61，权限 finance:read/write/audit/reverse-audit/amount）

- **会计科目** `GET /api/account-subject`（两级，parent_code 空=一级；含期初余额）；`POST /api/account-subject` 新增（明细自动继承父级类别/方向）；`PUT /{id}` 改名；`PUT /{id}/toggle` 停用启用（编码/类别/方向不可改）
- **期初建账** `PUT /api/account-subject/opening-balance` `[{code, openingBalance, openingDirection}]` 全量覆盖，借贷必须平衡，损益类禁止期初
- **业务映射** `GET/PUT /api/account-subject/mapping`（biz:expense:类型 / biz:income:类型 / biz:method:RECEIPT|PAYMENT:方式 → 科目编码；清空=删除映射）
- **记账凭证** `GET /api/voucher`（含分录批量预取）；`POST` 保存草稿（≥2 行、科目启用、Σ借=Σ贷>0，业务来源 source+refDocNo 查重）；`PUT /{id}`/`DELETE /{id}` 仅草稿；`PUT /{id}/post` 记账（finance:audit）；`PUT /{id}/unpost` 反记账（finance:reverse-audit）；`GET /export` Excel
  - source：MANUAL/RECEIPT/PAYMENT/EXPENSE/INVOICE/TRANSFER；单号 VCH-YYYY-NNNN（按凭证日期年份）；period=YYYY-MM 独立列
- **业务转凭证** `POST /api/voucher/generate` `{sourceType, refId}` 生成预览（按 account_mapping 带默认分录），前端弹窗可改后 `POST /api/voucher` 落库
- **结转损益** `POST /api/voucher/transfer-profit` `{period}`——按「年初至该期损益类科目 POSTED 余额」生成结转凭证（幂等：已结转部分余额为 0），存为草稿需人工记账
- **期间结账** `GET /api/voucher/period-status` 各月凭证数/草稿数/结账态/未结转损益；`PUT /api/voucher/close-period` `{period}`（拦截：有草稿、损益未结平）；`PUT /api/voucher/reopen-period`（仅最近已结期间逐月反结）
- **账簿与三大报表**（挂 finance-report）：
  - `GET /api/finance-report/account-balance?period=&level=TOP|ALL` 科目余额表（期初/本期/期末借贷）+ /export
  - `GET /api/finance-report/account-detail?subjectCode=&from=&to=&auxName=` 明细账（期初+流水+逐行余额）+ /export
  - `GET /api/finance-report/balance-sheet?period=` 资产负债表（年初/期末两栏，含平衡差额 diff，未分配利润=3104+3105+未结转损益）+ /export
  - `GET /api/finance-report/income-statement?period=` 利润表（本月/本年累计，**排除 TRANSFER 结转凭证**）+ /export
  - `GET /api/finance-report/cash-flow?period=` 现金流量表（简化直接法：现金类科目 1001/1002/1012 分录按同凭证对方科目比例分摊，cf:in:/cf:out: 映射归集；现金内部转账不计入）+ /export
- **统一口径**：一切余额 = 期初建账数 + 年初至该期 POSTED 净发生额（含 TRANSFER）；已结账期间禁止一切凭证写操作
- 种子：70 个小企业会计准则科目 + 61 条映射（biz 业务映射页面可改 / cf 现金流量归集种子内置）

### 4.23 工资核算 + 固定资产（v5.62，权限复用 finance:read/write/amount）

- **员工档案** `GET/POST/PUT /api/employee`、`PUT /{id}/toggle`、`DELETE /{id}`（已进工资单禁删）——dept: PRODUCTION/SALES/ADMIN/TECH/QC/OTHER 决定计提借方科目；base_salary 工资单默认带出；离职（leave_date 早于期间）不再带出
- **工资单** `GET /api/salary`（含明细）；`POST {period}` 新建自动带出在职员工（同期间唯一，SAL-YYYYMM-NNNN）；`PUT /{id} {items}` 编辑明细（仅草稿，全量替换）；`PUT /{id}/confirm` 确认锁定；`DELETE /{id}` 仅草稿；`GET /export?period=` 工资表 Excel
  - 明细构成：base 基本 + bonus 奖金 + piecework 计件（手填）− deduction 扣款 = gross 应发；gross − socialIns 代扣社保 − incomeTax 代扣个税 = net 实发（个税/社保由会计手填，系统不做累计预扣）
  - `POST /{id}/accrual-voucher` 计提凭证（source=SALARY_ACCRUAL）：借方按部门经 biz:salary:dept: 映射归集（生产 5001.02/销售 6601.03/其他 6602.02），贷 2211.01 应付工资
  - `POST /{id}/pay-voucher` 发放凭证（source=SALARY_PAY）：借 2211.01（应发），贷 1002 银行（实发）+ 2232（代扣社保）+ 2221.06（代扣个税），零额行跳过
- **固定资产** `GET /api/asset`（卡片+月折旧/累计/净值）；`POST`、`PUT /{id}`（docNo FA-YYYY-NNNN，折旧科目 expense_subject 默认 6602.05 可改 5101 等）；`PUT /{id}/scrap {scrapDate}` 报废；`GET /depreciation?period=` 期间折旧记录；`GET /{id}/history` 单卡历史；`GET /export`
  - 平均年限法：月折旧 = 原值×(1−残值率)/年限月；购入次月起提、报废当月照提、提满只提尾差
  - `POST /depreciate {period}` 月度计提：逐卡生成折旧记录（(period, assetId) 防重）+ 按 expense_subject 汇总生成计提凭证（source=DEPRECIATION，refDocNo=period）：借费用科目、贷 1602 累计折旧；已结账期间拦截
- 新 source 标签：SALARY_ACCRUAL 工资计提 / SALARY_PAY 工资发放 / DEPRECIATION 折旧计提（会计凭证页可识别）
- 种子：11 条字典（salary_dept / asset_category）+ 12 条映射（biz:salary:* / biz:depreciation:*）

### 4.24 存货计价方式可配置（v5.63，权限 finance:read/write）

- **计价方式**（sys_config: inventory.costing_method）四档：SPECIFIC 个别计价（默认=既有口径，出库成本=批次台账价）/ FIFO 先进先出（同批次价，出库界面引导默认选最早批次）/ MOVING_AVG 移动加权平均（物料在库 Σ金额÷Σ数量实时）/ MONTHLY_AVG 全月平均（月中出库按上月快照价暂估，无快照回退移动加权）
- **配置与变更**：`GET /api/costing/config`（当前方式+当月出库单数）；`PUT /api/costing/method {method, reason, force}`——reason 必填、当月已有出库单时首次返回 needConfirm 由前端强确认（建议月初切换）、变更写入 costing_method_log 留痕、**绝不重算历史单据**；`GET /api/costing/log` 变更历史
- **全月平均月末计算**：`POST /api/costing/monthly-close {period}`——均价=（当前台账Σamount+该期出库Σ原cost）÷（Σqty 同理），守恒倒推严格等于（期初+本期入库）加权；回填该期四类出库单（CONFIRMED）cost/unit_price + 落 costing_monthly_price 快照（(period,material) 唯一，算过不能重跑）；period 已结账拦截；`GET /api/costing/monthly-status?period=` 查计算状态
- **结账联动**：MONTHLY_AVG 模式下该期未做存货成本计算则 closePeriod 拦截（期末结账页有「存货成本计算」按钮）
- **成本收口**：全部出库单（销售/生产领料/委外/其他）成本统一经 CostingService 分流——OutboundService.fillBatchCost 委托，四类出库调用点零改动；批次下拉接口 /api/inventory/batch 增返 earliestInbound 并按最早排序
- **成品入库归集成本**（口径修正）：生产/委外入库批次台账价 = 订单累计归集成本（领料Σ+人工+制费 / 委外发料Σ+加工费）÷ 累计合格入库量（含本次）——此前成品批次价为 0 导致销售成本失真、毛利虚高；仅对新入库生效，历史批次不回填

### 4.25 生产领料差异处理（v5.64，权限 production:write）

- **领料单作废** `PUT /api/outbound/production/{id}/void`：整行冲回——生成 PROD-RET 负数冲减行（qty/cost 取负、库存按原批次原库位加回）+ 原行与冲减行同置 status=CANCELLED（成本 SUM(CONFIRMED) 双双清零、可退明细双双消失、投出比自动剔除）；不可逆。拦截链：已作废再作废 / 非 ISSUE 行 / 非 CONFIRMED / 订单已 DONE 入库 / 该行已被部分退料（剩余可退量不足整行）
- 至此领料差异三通道齐备：**多领 → 生产退料**（`POST /production/return`，部分冲减）；**少领 → 补领**（`POST /production?supplement=true`）；**开错整单 → 作废**（本接口）
- **物流运费（v5.66，权限 sales:read/write + finance:amount 脱敏）**：`GET/POST/PUT/DELETE /api/shipping`（SHIP-YYYY-NNNN；salesOrderNo 必填、freight>0；borne=COMPANY 公司承担进成本 / CUSTOMER 客户到付仅记录）；`GET /shipping/resolve-outbound?outboundDocNo=` 发货单带出订单/客户。**归集口径**：订单列表 freightTotal=Σ公司承担；毛利分析月度/客户维度 cost 含运费（两层聚合防多行出库重复计），产品维度不含运费（订单级费用不按产品摊分）；利润试算费用追加当月公司承担运费（与 expense 的 FREIGHT 散运费互斥使用防双算）。销售订单列表带「运费」列；销售出库确认行有「运费」快捷登记；送货单打印带收货地址。
- **物料编码三体系（v5.65，历史编码零变动仅新增生效）**：原料 A/P/F/R/S 不变（小类 2+流水 4 全局连续）；**半成品 B = 色浆小类(2)+主材(1)+流水(4) 共 7 位**（BWF0001=白浆/氟碳系）；**成品 C = 漆型(2)+主材(1)+色系(1)+流水(4) 共 8 位**（CWZH0001=面漆/聚酯/白）。主材 CZ→Z/CF→F/CE→E/CA→A；色系 BK→K/WH→H/BU→U/GN→N/GY→Y/RD→R/YW→W；流水按属性前缀分组独立（同款连号、容量百万级）。易混字符约束：新码字母段禁 I/L/O（蓝浆 BL 新码自动映射 BU；原料小类含 I/L/O 拒绝自动取号）；流水数字 0/1 正常（无字母对照物）。取号入口：POST /api/material（code 留空自动取，C 类需 mainMaterial+colorSeries、B 类需 mainMaterial，缺失报错）；Excel 导入同口径。手填 code 优先。
- **领料差异分析** `GET /api/report/material-variance`（+ /export）：实际净领料 vs 配方计划用量——按配方/按原料聚合（订单数、多领/少领/相符行数、金额加权差异率、差异金额）+ 订单×物料明细；|加权差异率|≥5% 触发配方预警（持续多领=配方用量偏低应补损耗率，持续少领=用量偏高可降本）；单行 ±2% 内视为相符；差异率按金额加权（Σ实际金额/Σ计划金额−1，计划金额=计划量×净领料加权单价，跨物料单位可加总）；60 秒缓存

### 4.26 质检模板（v5.32，权限 qc:read/write）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/qc-template/list | 全部模板（含检测项） |
| GET | /api/qc-template/{id} | 模板详情 |
| POST | /api/qc-template | 新建（主表+检测项聚合保存） |
| PUT | /api/qc-template/{id} | 更新（检测项先删后插） |
| DELETE | /api/qc-template/{id} | 删除（默认模板拦截） |
| POST | /api/qc-template/{id}/set-default | 同类互斥设默认 |

> 按物料大类三维匹配（小类/主材/色系打分制）→ 质检单快照检测项（改模板不影响历史单）。

### 4.27 供应商质量追溯（v5.59，权限 strace:read/write）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/quality-trace?supplierId=&status= | 追溯单列表 |
| GET | /api/quality-trace/{id} | 详情 |
| GET | /api/quality-trace/batches?keyword= | 搜台账批次（含不合格/油尾） |
| GET | /api/quality-trace/purchase-info?materialCode=&batchNo= | 批号带出原采购链 |
| POST | /api/quality-trace | 创建追溯单 |
| PUT | /api/quality-trace/{id} | 更新 |
| POST | /api/quality-trace/{id}/resolve | 处理完毕（结果类型+说明必填，赔款时金额必填） |
| DELETE | /api/quality-trace/{id} | 删除（未处理拦截） |
| GET | /api/quality-trace/{id}/trace | 批次全出入库流水（主表+归档合并） |
| GET/POST/PUT/DELETE | /api/quality-trace/templates | 损失沟通函模板 CRUD |
| PUT | /api/quality-trace/templates/{id}/default | 设默认模板 |

### 4.28 任务督办（v5.67，权限 task:read/write）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/task | 列表（有 task:write 看全部，普通员工看自己相关） |
| GET | /api/task/users | 可选执行人列表 |
| POST | /api/task | 创建（owner+collaborators 多人协作） |
| PUT | /api/task/{id} | 更新 |
| DELETE | /api/task/{id} | 删除 |
| POST | /api/task/{id}/start | 开始（执行人） |
| POST | /api/task/{id}/report | 汇报进度 |
| POST | /api/task/{id}/complete | 完工确认（仅 task:write） |
| POST | /api/task/{id}/cancel | 取消（task:write） |
| POST | /api/task/{id}/reopen | 重开（task:write） |
| GET | /api/task/my-count | 我的待办数（Dashboard 联动） |

> 状态机：PENDING→IN_PROGRESS→COMPLETED/CANCELLED（可 REOPEN）。

### 4.29 请购单闭环（v6.3~v6.6，权限 purchase:read/write）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/purchase-order | 列表 |
| GET | /api/purchase-order/{id} | 详情 |
| GET | /api/purchase-order/{id}/items | 明细 |
| POST | /api/purchase-order | 创建（body 含 items 数组：materialCode/qty/unitPrice/unit） |
| PUT | /api/purchase-order/{id}/header | 编辑头（仅 DRAFT；补供应商/仓库/交期/备注） |
| POST | /api/purchase-order/{id}/audit | DRAFT→APPROVED（须已补供应商+有明细） |
| POST | /api/purchase-order/{id}/to-purchase | APPROVED→CLOSED（逐明细生成采购单，单事务+幂等防重） |
| DELETE | /api/purchase-order/{id} | 删除（仅 DRAFT，MRP 误单清理） |

> 状态机：DRAFT→(audit)→APPROVED→(to-purchase)→CLOSED；短量关闭（receivedQty<qty）须传 reason。

### 4.30 MRP 采购建议（v6.3，权限 purchase:read/write）

**POST /api/mrp/suggest**（body 可空 `{orderIds:[1,2]}`，空=全部 CONFIRMED 销售订单）

响应：`{orders: <分析订单数>, lines: [{materialCode, materialName, materialCategory, need, stock, transit, gap, suggested, orders: "SO-xxx，SO-yyy"}]}`
- need=配方树展开需求（含半成品递归）；stock 排除隔离（REJECT/TAILING/EXPIRED）；transit=APPROVED 未到货；suggested=gap×1.05；orders 为需求来源单号串。

**POST /api/mrp/create-order**（body `{lines:[{materialCode, suggested|gap}]}`）→ `{orderNo, itemCount}`（DRAFT 请购单）。

### 4.31 价格政策（v6.3，权限 sales:read/write）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/price-policy | 全部政策 |
| GET | /api/price-policy/match?materialCode=&qty= | 取价 `{price, policyId, tier:"物料档 ≥10"}`；无匹配 price=null |
| POST | /api/price-policy | 创建 |
| PUT | /api/price-policy/{id} | 更新 |
| DELETE | /api/price-policy/{id} | 删除 |

> 阶梯规则：物料精确档（按 minQty 取已达最高阶梯）＞大类兜底档；需在生效期内；下单选物料/改数量自动带出。

### 4.32 出纳银行对账（v6.3，权限 finance:read/write）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/bank/account | 账户列表 |
| POST | /api/bank/account | 建账户（name/bankName/openingBalance） |
| GET | /api/bank/journal?accountId=&from=&to= | 出纳日记账（opening/closing/rows[signed/balance/matched]） |
| GET | /api/bank/statement/template | 流水导入模板 xlsx |
| POST | /api/bank/statement/import?accountId= | 流水导入（multipart file；余额指纹防重）→ {inserted, skipped} |
| GET | /api/bank/statement?accountId=&from=&to=&status= | 流水列表 |
| POST | /api/bank/reconcile/auto?accountId= | 自动勾对（金额相等+日期±3天+对方名加分）→ {matched, remaining} |
| POST | /api/bank/reconcile/{id}/bind?refType=RECEIPT|DISBURSEMENT&refId= | 手工勾对 |
| POST | /api/bank/reconcile/{id}/unbind | 取消勾对 |
| GET | /api/bank/reconcile/report?accountId=&from=&to=&bankEnding= | 双侧余额调节表 |

> 调节表：`{bookClosing, bankEnding, firmInNotInBank, firmOutNotInBank, bankInNotInFirm, bankOutNotInFirm, adjustedBank, adjustedBook, diff}`；未达账项=截至对账日全量未勾对（会计准则口径）；diff=0 即平衡。

### 4.33 包装标准 / UI 配置 / 异常订单 / 打印计数

**包装标准**（v5.81，权限 recipe:read/write）：`GET/POST/PUT/DELETE /api/packaging-standard`（组合包装：桶+袋+托盘一套多明细）。

**UI 配置**（v6.0，登录即可）：`GET /api/ui-config?key=pims.ui.cols.{username}.{table}`（只许读自己的）/ `POST /api/ui-config` body `{key, value}`（≤100KB UPSERT）。

**异常订单处置**（权限 production:read/write）：`GET /api/abnormal-order`（列表）、`POST /{orderNo}/handle`（原因+措施闭环）、`GET /{orderNo}`（记录）、`GET /export`（Excel）。

**打印计数**（登录即可）：`POST /api/print-count` body `{docType, docNo}`。

### 4.34 新报表端点（v6.3+）

| 端点 | 权限 | 说明 |
|---|---|---|
| GET /api/qc/statistics?from=&to= | qc:read | 质量统计：overall{total,pass,reject,rate%}/monthly 趋势/byMaterial TOP20/byCategory/bySupplier（按 arrival_id 关联到货） |
| GET /api/report/turnover?days=90 | inventory:read | 周转率：byCategory 汇总+details（年化=出库/库存×365/days，呆滞在前 TOP300）；REWORK 不计用量 |
| GET /api/finance-report/supplier-statement?supplierId=&from=&to= | finance:read | 供应商对账单：期初+三流（应付立账/付款/退货冲减）+期末（时点口径） |
| POST /api/voucher/year-end-close?year=YYYY | finance:audit | 年结：结平本年利润→未分配利润（POSTED 凭证）+12月月结；前置校验 1-11 月已结+损益已转 |
| GET /api/recipe/{id}/changes | recipe:read | 配方变更日志（CREATE/UPDATE/RELEASE/ARCHIVE/TREE_SAVE 留痕） |

### 4.35 v5.28 后散点新增（既有 Controller 内）

- 生产退料：`GET /api/outbound/production/issued-lines`（可退明细）、`POST /api/outbound/production/return`（PROD-RET 负数冲减）、`PUT /api/outbound/production/{id}/void`（作废）
- 领料补领：`POST /api/outbound/production?supplement=true&supplementType=COLOR_ADJUST|OVER_CONSUME`（色差/超耗分类标签）
- 工艺路线：`/api/process/route*` 7 端点（多条命名路线）
- 销售订单：`PUT /api/sales-order/{id}`（编辑+变更留痕）、`GET /{id}/changes`、`GET /recent-price(s)`（成交价带出）、`POST /{id}/close`（短交完结须 reason）
- 采购关闭：`POST /api/purchase/close/{id}?type=RAW|FINISHED&reason=`（短量关闭须 reason+已到齐拒关）
- 客户信用：`GET /api/customer/{id}/credit-check?amount=`（超额弹窗）、`GET /{id}/profile`（客户 360°）
- 配方版本：`GET /api/recipe/{id}/versions`、`POST /{id}/version`、`PUT /version/{versionId}`（草稿编辑+变更日志）
- 质检反审核：到货 `POST /api/purchase-arrival/{id}/reverse-audit`（按 arrivalId 精确隔离）
- 操作日志归档：`GET /api/log/archives`、`GET /api/log/archives/{month}`
- 备份：`POST /api/dashboard/backup`（手动触发）、健康状态含 backupStale
- 物料编码权限：`PUT /api/material/{id}/enabled`（生命周期）+ 编码手填需 material:code-edit 权限
- 入库标签分桶：打印标签时弹桶数+微调（Σ守恒禁打），标签带第 N/M 桶序号+批量合计

## 5. 核心接口对接示例

### 5.1 登录并调用（完整流程示例）

```bash
# 1. 登录拿 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"api_user","password":"****"}'
# → data.token = "abc..."

# 2. 带 token 查询库存
curl "http://localhost:8080/api/inventory/summary?view=batch&warehouseId=1&page=1&pageSize=50" \
  -H "pims-token: abc..."
```

### 5.2 创建销售订单（含明细）

```
POST /api/sales-order
```

```json
{
  "customerId": 3,
  "customerName": "山东亚泰新材料科技有限公司",
  "contractNo": "HT20260808001",
  "sourceWarehouseId": "1",
  "expectedShipDate": "2026-08-10",
  "remark": "外部系统对接测试",
  "items": [
    { "materialCode": "CW73001", "materialName": "黑色面漆", "qty": 500, "unit": "kg", "unitPrice": 38.5 }
  ]
}
```

### 5.3 查询库存（按批次视图响应示例）

```
GET /api/inventory/summary?view=batch&page=1&pageSize=5
```

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "rows": [
      {
        "materialCode": "AC40001", "materialName": "SLS051", "batchNo": "B20260607-001",
        "unit": "kg", "qty": 76.0, "availableQty": 76.0, "unitPrice": 15.04, "amount": 1143.04,
        "inboundDate": "2026-06-07", "expiryDate": "2027-08-27",
        "qcStatus": "PASS", "qcInspectionNo": "QC-IN-2026-0001",
        "qcResult": "合格", "qcInspector": "系统管理员", "qcDate": "2026-06-07"
      }
    ],
    "total": 257
  }
}
```

### 5.4 质检判定（触发入库）

```
POST /api/qc/{id}/judge
Content-Type: application/json
```

```json
{ "result": "PASS", "resultRemark": "外观/粘度/细度检测合格" }
```

### 5.5 批量创建原料采购单（一单一多物料）

```
POST /api/raw-material-purchase/batch
```

```json
{
  "supplierId": 5,
  "supplierName": "上海俊彩材料科技有限公司",
  "purchaseDate": "2026-08-08",
  "remark": "批量对接",
  "items": [
    { "materialCode": "AC40002", "materialName": "2500", "qty": 100, "unitPrice": 12.5, "isFree": false },
    { "materialCode": "PK21003", "materialName": "R960", "qty": 200, "unitPrice": 8.2, "isFree": false }
  ]
}
```

### 5.6 创建生产订单（参照已发布配方）

```
POST /api/production-order
```

```json
{
  "productName": "黑色聚氨酯面漆",
  "productCode": "CP10016",
  "batchQty": 100,
  "unit": "kg",
  "recipeVersionId": 68,
  "remark": "",
  "items": [
    { "materialCode": "RCP-0012", "materialName": "炭黑研磨浆", "qty": 20, "unit": "kg", "nodeType": "SUB_RECIPE" },
    { "materialCode": "AC40004", "materialName": "Z-024", "qty": 15, "unit": "kg", "nodeType": "MATERIAL" }
  ]
}
```

> 推荐流程：先 `GET /api/recipe/version/{versionId}/plan?qty=<批量>` 自动展开投料计划，再原样提交。

---

## 6. 对接注意事项（踩坑清单）

1. **单点登录互踢**：对接账号必须与人工使用分离（见 §2.3）。
2. **分页两种格式**：`page` 从 0（Spring 风格）还是从 1（rows/total 风格），按接口表核对，传错会错页。
3. **价格/金额脱敏**：未授 `purchase:price` / `finance:amount` 时字段直接不返回，对接方校验字段存在性。
4. **编码不可自造**：物料编码、单据号、批号均由系统生成；跨系统需要外部编码时放入 remark/合同号等自由字段。
5. **库存变动必须走单据**：不允许直接改库存；调库存走其他出入库（`/api/outbound/other`、`/api/outbound/other-inbound`），入库后需质检合格才生效（其他入库确认后生成待检质检单）。
6. **过期批次**：对接出库前建议先查 `/api/report/expiry` 或库存批次视图的 expiryDate，避免被禁出拦截。
7. **写入串行化**：系统内所有库存写操作经应用层写队列串行执行，对接方无需额外加锁，但并发写入时响应可能有排队延迟。
8. **数据量提示**：SQLite 单文件库适合中小规模数据；大批量历史数据同步建议分批（每批 ≤ 500 条）。
9. **导出接口为文件流**：不要按 JSON 解析。
10. **删除是软删除**：物料/仓库等 DELETE 后 `enabled=false`，列表默认过滤，但单据/台账仍可引用历史数据。

---

## 7. 附录：常用字典码

| 字典 type | 常见 value | 含义 |
|---|---|---|
| material_category | A/P/F/R/S/B/C | 助剂/颜料/填料/树脂/溶剂/半成品/成品 |
| material_sub_category | AC、FS、AR、PM、PK、PW、CD、CQ、CW、CB、BW… | 小类代码（编码规则前缀） |
| material_main_material | CZ/CF/CE/CA | 主材：聚酯/氟碳/环氧/丙烯酸（成品） |
| material_color_series | BK/WH/GY/BU/GN/RD/YW | 色系：黑/白/灰/蓝/绿/红/黄（面漆） |
| payment_terms | PREPAID/CREDIT_30/CREDIT_60/MONTHLY/TWO_MONTH/THREE_MONTH/COD | 收款/付款条件 |
| payment_method | TRANSFER/ACCEPTANCE | 电汇/承兑 |

> 字典可通过 `GET /api/dict` 获取全量（`dict_item` 表，type/value/label 三要素），对接方应优先以字典驱动下拉选择，避免硬编码。
