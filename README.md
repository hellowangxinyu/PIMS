# 芃远综合管理系统（PIMS）

广东芃远新材料有限公司自研一体化 ERP：覆盖涂料厂**采购、生产、委外、销售、库存、质检、财务总账**全业务链。业务单据实时联动库存与应收应付，财务在此基础上收付款、对账、凭证、结账、出三大报表。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.3 / Java 17 / Spring Data JPA / SQLite（单文件 WAL，全局写锁 WriteQueue 串行化） |
| 前端 | Vue 3 + Vite + Element Plus（构建产物打进 jar 的 static/） |
| 认证 | Sa-Token（HttpOnly Cookie + 粗细权限码双向展开 + 字段级金额脱敏） |
| 部署 | 单 jar + 数据库文件；Linux systemd（`-Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai`，低配机；高配见 start.bat `-Xmx4g`），Windows start.bat |

## 目录结构

```
pims-server/   后端（controller 55 / service 59 / entity 91 / config 含 36 个幂等建表 Initializer）
pims-web/      前端（views 90+ 页面 / components / composables / utils 打印与日期工具）
docs/          PRD、API（522 端点）、SOP 流程图版、数据字典、操作手册、部署文档
```

## 构建与运行

```bash
# 前端构建（产物自动写入 pims-server/src/main/resources/static/）
cd pims-web && npm install && npm run build

# 后端打包（跳过测试）
cd pims-server && mvn test            # 先跑测试（凭证平衡/库存守恒/结账/请购闭环等）
cd pims-server && mvn clean package -DskipTests

# 运行（data/pims.db 不在本仓库——需从数据库备份恢复，或空库启动自动初始化）
java -jar pims-server/target/pims-server-1.0.0.jar
```

> **本仓库只有源码**。数据库、日志、部署包均在 .gitignore 中。完整恢复系统 = 克隆本仓库 + 部署最新数据库备份（服务器每日 04:30 自动热备）。

## 版本管理

每个功能批次一个 git tag（如 `v8.0-p0-hotfix`、`v8.5-final-scope`），tag message 即变更清单。当前最新：`v8.6-review-round1`。

## 核心设计约定（改代码前必读）

- **时间毫秒存储**：所有时间列为毫秒时间戳，SQL 过滤/归月必须 `date(col/1000,'unixepoch','+8 hours')`
- **写锁**：写操作统一走 `WriteQueue.executeTx`（锁内包事务），方法上**不得**再叠 `@Transactional`
- **ddl-auto=none**：新表/新列必须写幂等 SchemaInitializer（CommandLineRunner）
- **库存铁律**：物料精确到批号、仓库精确到库位；不合格品库/油尾库对正常业务封闭
- **价税分离**（v8.2 起）：采购单价含税入库，台账成本折不含税（税率读字典 `tax_rate`）
- 详细规范见 `docs/SOP.md` 与 `docs/DataDictionary.md`

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/操作手册.pdf` | 零基础新人全模块操作指引（财务重点章 + 常见问题索引） |
| `docs/SOP.md` | 16 张流程图版标准操作流程 |
| `docs/API.md` | 522 个接口文档 |
| `docs/DataDictionary.md` | 97 张表数据字典 |
| `docs/DEPLOY.md` | 部署与备份恢复 SOP |

---
内部系统，请勿外传。维护：王新宇
