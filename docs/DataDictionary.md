# 芃远综合管理系统（PIMS）数据字典

> **版本**: v1.1（对应系统 v8.10，git tag v8.10.2-review5；v1.0→v1.1 增补打样任务三表与字段变更）
> **日期**: 2026-09-05
> **数据库**: SQLite 单文件（`data/pims.db`，WAL 模式），Hibernate ddl-auto=none——建表/补列由 30+ SchemaInitializer（CommandLineRunner）启动时幂等执行
> **通用约定**：
> - 时间戳字段存**毫秒 epoch**（项目铁律，SQL 比较用数值直比；报表取日期统一 `strftime(…, 'unixepoch', '+8 hours')` 北京时间）
> - 单据号规则：业务单据 `PREFIX-YYYYMMDD-NNNN`（按天）；凭证/工资 `PREFIX-YYYYMM`（按月·行业惯例）；历史编号不迁移
> - 金额 precision 14,2；数量 precision 14,3；单价 precision 12,2
> - 主键均为自增 `id`（BIGINT/INTEGER）

---

## 目录

1. [系统与权限（4 表）](#1-系统与权限)
2. [基础数据（10 表）](#2-基础数据)
3. [配方体系（7 表）](#3-配方体系)
4. [生产域（6 表）](#4-生产域)
5. [采购域（5 表）](#5-采购域)
6. [销售域（5 表）](#6-销售域)
7. [委外域（4 表）](#7-委外域)
8. [库存域（6 表）](#8-库存域)
9. [质检域（5 表）](#9-质检域)
10. [财务域（16 表）](#10-财务域)
11. [办公协同（6 表）](#11-办公协同)
12. [CRM 与打样（6 表）](#12-crm-与打样)
13. [运维与系统（13 表）](#13-运维与系统)

---

## 1. 系统与权限

### sys_user（用户）
| 字段 | 类型 | 说明 |
|---|---|---|
| username | VARCHAR(50) UNIQUE | 登录名 |
| password | VARCHAR(100) | BCrypt 哈希（**JsonProperty WRITE_ONLY**：请求可传入、响应不回传） |
| real_name | VARCHAR(50) | 真名 |
| role | VARCHAR(20) | 角色码（GM/SS/PM/BUYER/TECH_LEAD/…） |
| enabled | BOOLEAN | 启用 |
| must_change_pwd | BOOLEAN | v6.1 强制改密标记（管理员重置/种子置 1；改密后清 0；拦截器逐请求校验） |
| phone / create_time / update_time | | 常规 |

> 登录失败计数在内存 ConcurrentHashMap（用户名→[次数, 锁定截止]，5 次锁 10 分钟，重启清零）。

### sys_role（角色）/ sys_role_permission（角色权限）
- role: code/name/enabled；预置 10 角色（GM=总经理…），生产库 admin 账号实际挂 SS。
- role_permission: role_code + permission_code（细粒度权限码，23 模块 52 功能页 + 金额码 11 模块；权限矩阵 UI 维护）。

---

## 2. 基础数据

### material（物料）★核心主数据
| 字段 | 类型 | 说明 |
|---|---|---|
| code | VARCHAR(30) UNIQUE | 物料编码（规则见下方编码体系） |
| name | VARCHAR(100) | 品名 |
| brand | VARCHAR(100) | 牌号 |
| category | VARCHAR(5) | 大类：A 助剂/P 颜料/F 填料/R 树脂/S 溶剂/**B 半成品（色浆）/C 成品（漆）**——判 B/C 必查此列不可按首字符 |
| sub_category | VARCHAR(10) | 小类（编码前缀） |
| main_material | VARCHAR(20) | 主材（CZ 聚酯/CF 氟碳/CE 环氧/CA 丙烯酸；B/C 必填） |
| color_series | VARCHAR(20) | 色系（BK/WH/BU…面漆必填） |
| unit / spec / shelf_life_days / enabled / created_by / times | | 单位/规格/保质期天数/软删除/制单人 |

> **编码体系 v5.65**：原料 `小类(2)+流水(4)`；半成品 B=`小类(2)+主材(1)+流水(4)` 共 7 位；成品 C=`漆型(2)+主材(1)+色系(1)+流水(4)` 共 8 位。字母段禁 I/L/O；流水按属性前缀分组（同款连号）。物料编码编辑需 `material:code-edit` 权限。

### coding_rule（编码规则）/ dict_item（数据字典）/ released_code_seq（回收复用池）
- coding_rule: 前缀/当前序号/类别（取号与 validateCodeFormat 按类分流）。
- dict_item: type+value+label（material_category / payment_terms / outbound_reason 含 REWORK / …对接方应字典驱动）。
- released_code_seq（无实体，jdbc）：物料删除后编码回收复用。

### customer（客户）/ supplier（供应商）/ warehouse（仓库）/ warehouse_zone（分库）/ warehouse_location（库位）
- customer: credit_limit（v5.52 信用额度）、payment_terms、地址/联系人等 19 字段。
- supplier: processing_fee（委外加工费）、payment_terms。
- 仓库三分层：warehouse(zone_type=OWN_RAW/OUT_RAW/UNQUALIFIED_*/TAILING*) → zone → location（物料操作精确到批次+库位铁律）；隔离分库五类由 IsolatedZoneService 联动建。
- employee（员工档案，工资模块复用）。

---

## 3. 配方体系

### recipe（配方）
| 关键字段 | 说明 |
|---|---|
| product_code | 关联物料编码（findFirstByProductCode 匹配） |
| product_name | UNIQUE（配方名=物料名） |
| qc_template_id / packaging_standard_id | 质检模板/包装标准绑定（v5.81 四必填） |
| process_template_id | 工艺路线绑定 |

### recipe_version（配方版本）
- version_no（V1.0/V2.0…）、status DRAFT/RELEASED/ARCHIVED、released_by/released_time、**effective_date**（v6.3 独立生效日）、batch_qty 标准批量。
- 创建在 writeQueue 锁内取号（防并发同号）；发布自动归档旧 RELEASED。

### recipe_tree_node（配方树节点）
- node_type MATERIAL/SUB_RECIPE、ref_recipe_id（子配方）、material_code、qty 用量、parent_node_id 层级、sort_order。
- 递归展开有 visited 集合防循环引用（expandTree/traceRecipe）。

### recipe_change_log（配方变更日志，v6.3）
- action: CREATE/UPDATE/RELEASE/ARCHIVE/TREE_SAVE；detail 含节点构成摘要（截 400 字）；operator 服务端取登录人。

### process_template / process_stage / process_step / process_qc_item（标准工艺 v5.28+）
- 多条命名路线（route*），配方必选绑定；种子只从无到有。

### qc_template / qc_template_item（质检模板，v5.32）
- 按物料大类 7 套模板；三维打分匹配（小类/主材/色系）→ 质检单**快照**（改模板不影响历史单）。

### packaging_standard / packaging_standard_item（包装标准，v5.81）
- 组合包装：桶+袋+托盘一套多明细，套单价。

---

## 4. 生产域

### production_order（+ _item）
- order_no（MO-YYYYMMDD-NNNN）、status DRAFT/CONFIRMED/SCHEDULED/COMPLETED、sales_order_no+schedule_seq（排产）、io_ratio/io_status（投出比=产出÷投入，<95% ABNORMAL 进异常处置）、input_qty/output_qty、labor_fee/overhead_fee（归集成本）。
- **按实际完结**：SCHEDULED 完工须 reason；投出比异常自动建 production_order_exception。

### production_outbound（领料/退料/作废）
- doc_type ISSUE/RETURN（v5.54 退料负数行）；**supplement_type**（v6.8 COLOR_ADJUST 色差/OVER_CONSUME 超耗，仅补领有值）；成本 cost 按批次直取。
- 作废=原行+冲减行双 CANCELLED。

### production_inbound（生产入库）
- status CONFIRMED/DONE/REJECTED；REJECTED 自动入隔离库（按物料大类路由三隔离分库）；DONE 触发订单自动完工。

### production_order_exception（异常订单处置）
- order_no UNIQUE、io_ratio/input/output 快照、reason/measure/handler、status PENDING/PROCESSING/CLOSED；完工联动建档在独立事务。

---

## 5. 采购域

### purchase_order（+ _item）——v6.3.3 重建
- supplier_id / target_warehouse_id **放宽可空**（MRP 请购"供应商待定"场景）；状态机 DRAFT→APPROVED→CLOSED（转采购后）；remark 记录"已转采购：单号"；POST 支持 items 明细；短量关闭须 reason。

### purchase_arrival（到货）
- doc_no ARR-YYYYMMDD-NNNN；**arrival_id 是质检单/应付的精确关联锚**（v6.1.2 加列+历史回填，反审核按批隔离）；unit_price 含税固化（v5.73 从采购单带出）；status DRAFT/APPROVED。

### raw_material_purchase / finished_product_purchase
- 合同号（供应商首字母+日期+序号）、tax_rate（默认 13 含税）、receivedQty 回写（到货审核 backfill；短量回退 RECEIVED→APPROVED）、warehouse_id（收货仓库）。

### price_policy（价格政策，v6.3）
- material_code（空=按 material_category 兜底）+ min_qty 阶梯 + unit_price + effective/expiry_date + status。

---

## 6. 销售域

### sales_order（+ _item）
- order_no SO-YYYYMMDD-NNNN；status DRAFT/CONFIRMED/SHIPPED/CLOSED；**credit_exceeded**（v6.3 信用超限确认留痕布尔，确认时快照入变更日志）；tax_rate；hasB/hasC 冗余。

### sales_order_change_log（变更留痕，v5.47）
- detail：头字段差异+明细增删改+短交完结快照（"应发X实发Y差Z，原因…"）。

### sales_outbound（销售出库）
- 确认时 qtyOverride 只能改小；AR 按实发量×单价立账；allMatch(shipped≥qty) 置 SHIPPED。

### return_order（退货单，三类复用）
- type PURCHASE_RETURN/SALES_RETURN/TAILING_RETURN；ref_arrival_id（来料退货关联到货）；短量关闭/短交完结均须 reason。

### stock_check（盘库单）
- docNo CHK-YYYYMMDD-NNNN；stock_gain/loss/location_adjust；**差异以台账实数为准**（不信前端 systemQty）；库位调整复制批次属性+金额随量结转。

---

## 7. 委外域

### outsource_order（+ _item）
- 状态含 OUTSOURCED（发料加工中）；完工放行 CONFIRMED|OUTSOURCED。

### outsource_material_outbound / outsource_finish_inbound / outsource_material_consume
- FIFO 单号 OUT-IO-年-F 序号（按年前缀取号）；入库合格自动完工。

---

## 8. 库存域

### inventory_ledger（台账）★最核心
| 关键字段 | 说明 |
|---|---|
| material_code + batch_no + warehouse_id + location_id | 唯一行键（批次+库位铁律） |
| qty / available_qty / occupied_qty / in_transit_qty | 实物/可用/占用/在途 |
| unit_price / amount | 该成本层价格与金额（加权并入算法） |
| qc_status | PASS/CONCESSION/REJECT/TAILING/EXPIRED/PENDING_QC |
| produce_date / expiry_date / inbound_date | 批次属性（inboundDate 仅首次落不回写） |
| zone_name / location_name | 冗余展示 |

> 隔离行（qc_status ∈ REJECT/TAILING/EXPIRED）不计价、不参与低库存可用、不被正常出库扣（三道口拦截+无库位 FIFO 过滤）；REWORK_OUT 可从隔离库领出返工（v6.8）。

### inventory_movement（异动流水）+ inventory_movement_archive（30 天前归档）
- doc_type 全集：PURCHASE_IN/PRODUCTION_OUT/SALES_OUT/OUTSOURCE_OUT/OTHER_OUT/**REWORK_OUT**/TRANSFER/ADJUSTMENT/RETURN…；qty_before/after；location_id（v6.1.6）。

### other_inbound / other_outbound（其他出入库）
- 其他入库创建即 PENDING_QC（质检驱动落账）；reason 字典 outbound_reason（含 REWORK=返工领料→docType REWORK_OUT 不立应收）。

---

## 9. 质检域

### quality_inspection（质检单）
- inspection_no QC-IN-YYYYMMDD-NNNN；ref_doc_no/ref_doc_type（PURCHASE/PRODUCTION_INBOUND/OUTSOURCE_INBOUND/OTHER_INBOUND）；**arrival_id**（到货精确关联）；status PENDING/PASS/CONCESSION/REJECT；qc_result 快照；模板检测项快照在 quality_inspection_item。
- judge REJECT 分流：来料→自动退货单；生产/委外/其他→隔离库（按大类三路由）。

### supplier_quality_trace / loss_letter_template（供应商质量追溯，v5.59）
- 批号驱动带出采购链；处理完毕强制结果；30 字段含赔款/模板快照。

---

## 10. 财务域

### accounts_receivable / accounts_payable
- docNo AR-/AP-YYYYMMDD-NNNN；dueDate（**AP 从到货日起算** v6.5 会计口径）；received_amount/paid_amount FIFO 冲减；status UNPAID/PARTIAL/PAID。

### payment_receipt / payment_disbursement（收付款单）
- bank_account（银行对账日记账联）；超核销/负数拦截。

### invoice / expense / advance_payment（发票/费用/预收预付，v5.36）
- 发票红冲 status FLUSHED+flush_doc_no；负数单（红字）不可再红冲；已生凭证拒改删。

### 总账五表（v5.61）
- **account_subject**：code 4 位一级/4.2 位明细、category ASSET/LIABILITY/EQUITY/COST/PL、opening_balance+direction；期初锁定（有 POSTED 凭证拒改）。
- **voucher**：docNo VCH-YYYYMM-NNNN 按月；source MANUAL/RECEIPT/PAYMENT/EXPENSE/INVOICE/TRANSFER/SALARY/DEPRECIATION/YEAR_END；ref_doc_no 幂等锚（查重在锁内）；status DRAFT/POSTED；跨月改期重排编号。
- **voucher_entry**：debit/credit 均非负（红字用反方向；负数手工分录拦截）。
- **account_period**（结账月）/**account_mapping**（业务→科目 KV）。
- **年结**（v7.0）：YEAR_END 凭证结平 3104→3105。

### salary_sheet / salary_item / asset / asset_depreciation（工资+资产，v5.62）
- 工资应发/实发均禁负；资产折旧购入次月起、报废当月照提；草稿凭证删除可重提（孤儿折旧清理）。

### costing_method_log / costing_monthly_price / sys_config（计价+系统 KV，v5.63）
- 四档计价 SPECIFIC/FIFO/MOVING_AVG/MONTHLY_AVG；sys_config 兼存 UI 列宽/AI 配置/权限一次性标记。

### bank_account / bank_statement（银行对账，v6.3）
- 流水 amount 带符号（收+/支−）+balance 指纹防重；勾对 ref_type/ref_id；未达账项=截至对账日全量未勾对。

---

## 11. 办公协同

- **weekly_topic / rd_progress**（周会，v5.44）：议题+研发进度，Excel 导入。
- **task / task_progress**（任务督办，v5.67）：四态状态机、owner+collaborators；Dashboard 待办联动。

---

## 12. CRM 与打样

- **crm_contact / crm_opportunity / crm_follow_up**（v5.50）：管道阶段机。
- **quotation / quotation_item**（报价单，v5.52）：状态机+转订单。
- **sample_request**（打样，v5.53；v7.7 增派发字段）：APPLIED→ASSIGNED(已派发)→COLORING(已接收)→FORMULATED(已录配方)→SENT→SATISFIED/ADJUST（adjustCount 累计）→WON/LOST。v7.7 新列：assignee（派发的打样员账号）、assign_time、receive_time；v7.7.2 增 ref_sample_id（关联打样，复样参考）、sample_size（NORMAL 常规/A4）。寄样允许 COLORING/FORMULATED。
- **sample_formula**（打样配方，v7.7；与打样单 1:1）：formula_no（FY-日期-NNNN）、sample_request_id 唯一、material_code/material_name（首次保存自动生成的 C 类成品，9 位属性码）、sub_category/main_material/color_series（分类首存后锁定）、total_qty（明细合计，单位=**克**）、est_cost（估算成本 元/kg=Σ用量×移动加权均价÷总量）、sample_location（留样柜位）、converted_recipe_id/converted_time（转制漆回写防重复）。
- **sample_formula_item**（打样配方明细）：material_code/name/category/sub_category/qty（克，自由用量不强制 100——转制漆时按 100kg÷总量折算）。
- **sample_formula_history**（打样配方快照，纯存档无 UI）：saveFormula 覆盖前旧明细 JSON（round 对齐 adjustCount），「找回历史版本」用。
- **customer_complaint**（投诉）：批次追溯（含归档表关联）。

---

## 13. 运维与系统

- **operation_log + operation_log_YYYYMM 月表 + operation_log_archive_YYYYMM**：异步写、30 天归档改名、12 个月 DROP；respBody token 脱敏。
- **ai_config**：AI 模型 KV（apiKey 掩码回显）。
- **stat_ 汇总表 4 张**（stat_order_monthly/stat_finance_summary/stat_inventory_daily/stat_material_usage）：触发器增量维护（v7.0 时区统一 +8 hours）。
- **backup_meta**（备份健康）；**sys_maintenance**。

---

## 附：全局业务口径速查

| 口径 | 规则 |
|---|---|
| 立账时点 | AP 按到货单量、AR 按实际出库量（实收实发，未履行不负债） |
| 未达账项 | 截至对账日全量未勾对（含期初前） |
| 用量统计 | PRODUCTION_OUT/OUTSOURCE_OUT/OTHER_OUT/SALES_OUT（REWORK_OUT **不计入**——已耗料再利用） |
| 隔离库存 | 不计价、不算可用、不被正常出库扣；REWORK_OUT 可领出返工 |
| 投出比 | 产出(DONE 入库)÷投入(CONFIRMED 领料)，<95% ABNORMAL |
| 时区 | 一律 +8 hours 北京时间（触发器/报表已统一） |
| 退货成本 | 原批次台账成本→现货加权均价→退货单价兜底 |
| 信用软拦截 | 创建/确认/发货三道提示，不阻断；确认落 credit_exceeded+快照 |
| 删除 | 物料/仓库软删（enabled=false）；单据类多为状态机 CLOSED/作废 |
