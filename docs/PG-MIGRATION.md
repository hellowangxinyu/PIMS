# PIMS SQLite → PostgreSQL 迁移手册（v11.9）

> 状态：**✅ 生产已切换 PostgreSQL 并全面验证通过**（2026-10-08 14:40）；SQLite 最终备份保留 data/pims.db.final-20261008.db / .final-sqlite.bak；回滚=删除 drop-in /etc/systemd/system/pims.service.d/pg-env.conf 后重启
> - 后端双方言改造完成：SQLite / PostgreSQL 同一份代码双跑
> - 单元测试 21/21 绿（真实 PG 嵌入式实例）；财务接口回归 **362 项断言在 SQLite 与 PG 双环境全部全绿**
> - 数据迁移脚本经全量演练：本地业务库 96 张表迁移 + 行数/金额核对全部通过
> - 生产 PG16 已安装并运行（**尚未切换**，PIMS 仍跑 SQLite）

---

## 一、生产现状（阶段 5 已完成）

| 项 | 状态 |
|---|---|
| PostgreSQL 16.15 | 已装（PGDG 源），systemd `postgresql-16` active，数据目录 `/opt/pgsql/data` |
| 内存调优 | shared_buffers=128MB / max_connections=20 / 仅监听 127.0.0.1 |
| 库/用户 | 库 `pims`、用户 `pims`（密码 `Pims@2026pg`，仅本机 md5 认证） |
| 迁移脚本 | `/opt/pims/init_pg_schema.py`（基线翻译）、`/opt/pims/migrate_sqlite_to_pg.py`（数据迁移+核对）、`/opt/pims/pg_casts.sql`（隐式 CAST，需超户） |
| 生产 python | 迁移脚本用 `/usr/bin/python3.6`（已装 psycopg2 2.9.5；`/usr/local/bin/python3` 无 psycopg2，勿用） |

## 二、切换 Runbook（已于 2026-10-08 执行完毕，留档备查/重放）

```bash
# === 准备（切换前一天）===
# 1. 发布 v11.9 jar（含双方言改造），仍以 SQLite 模式运行验证一天：
#    部署 jar 后不加任何环境变量 = 默认 SQLite 行为（与 v11.8 完全一致）

# === T-0 发通知"系统维护 30 分钟"，确认无人操作 ===

# === T+0~2：最终备份（双份）===
ssh root@182.92.95.12
systemctl stop pims                                   # 停应用，保证库文件静止
cd /opt/pims
sqlite3 data/pims.db ".backup 'data/pims.db.final-$(date +%Y%m%d).db'"   # 在线快照即可，停服后亦可直接 cp
cp data/pims.db data/pims.db.final-sqlite.bak

# === T+2~5：建 PG 基线结构（脚本输出应 210 对象 0 失败）===
/usr/bin/python3.6 /opt/pims/init_pg_schema.py \
  --source /opt/pims/data/pims.db.final-$(date +%Y%m%d).db \
  --target "postgresql://pims:Pims@2026pg@127.0.0.1:5432/pims" --apply

# === T+5~6：超户建隐式 CAST（数据库级一次性配置，pims 用户无权建）===
su -s /bin/bash postgres -c "psql -d pims -f /opt/pims/pg_casts.sql"

# === T+6~15：数据迁移 + 自动核对（行数/金额，退出码 0 = 全通过）===
/usr/bin/python3.6 /opt/pims/migrate_sqlite_to_pg.py \
  --source /opt/pims/data/pims.db.final-$(date +%Y%m%d).db \
  --target "postgresql://pims:Pims@2026pg@127.0.0.1:5432/pims"
# 预期结尾：========== 迁移完成：全部核对通过 ✓ ==========

# === T+15~18：应用切 PG ===
# 编辑 /etc/systemd/system/pims.service 的 [Service] 段，在 ExecStart 前加：
#   Environment=PIMS_DB_URL=jdbc:postgresql://127.0.0.1:5432/pims
#   Environment=PIMS_DB_DRIVER=org.postgresql.Driver
#   Environment=PIMS_DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
#   Environment=PIMS_DB_USER=pims
#   Environment=PIMS_DB_PASSWORD=Pims@2026pg
systemctl daemon-reload
systemctl start pims            # 启动约 95 秒（2 核机）
systemctl is-active pims

# === T+18~25：验证 ===
curl -s http://127.0.0.1:8081/login -o /dev/null -w '%{http_code}\n'     # 200
curl -s https://pims.sdzcxc.cn/login -o /dev/null -w '%{http_code}\n'    # 200（经 Caddy）
# 浏览器验证：登录 → 应收总表/资产负债表/库存台账 数字与迁移前一致 → 开一张测试单再走通审核流程
# 服务器日志确认：grep -E "兼容函数就绪|PostgreSQL 触发器" /opt/pims/app.log

# === T+25：发通知恢复使用 ===
```

## 三、回滚（2 分钟）

```bash
systemctl stop pims
# 删除 pims.service 里 5 行 PIMS_DB_* Environment（或改指回 sqlite 默认即删即可）
systemctl daemon-reload && systemctl start pims        # 回到 SQLite 模式
# 数据：SQLite 库未动过（迁移是只读源），切换期间 PG 里的新写入会丢失——故切换窗口要短+通知到位
```

## 四、切换后待办

1. **备份体系改造**：BackupService 已内置 PG 分支（自动探测走 pg_dump -Fc，产物 `backups/pims-db-YYYY-MM-DD.dump`，保留 90 天）；办公电脑每周拉取 SOP 从拉 .db 改为拉 .dump
2. **备份恢复演练**：pg_restore 到临时库验证一次（必须做，不要假设能恢复）
3. **观察期 1-2 周**：关注 `journalctl -u postgresql-16` 慢查询、`/opt/pims/app.log`；watchdog/每日重启 cron 不变
4. **PG 大版本**：16.15 已是最新稳定线；yum 升级路径 `dnf upgrade postgresql16*`
5. **WriteQueue 全局锁**：PG 支持行锁，当前规模下先不动；如监控发现写并发瓶颈再评估拆除
6. **大小写不敏感搜索（v11.9 已解决）**：全站搜索统一 `LOWER(col) LIKE LOWER(?)` / JPQL `LOWER(x) LIKE LOWER(CONCAT(...))` / Criteria `cb.like(cb.lower(...), ...toLowerCase())`——双方言行为一致（ILIKE 方案已废弃：PG 方言，SQLite 不支持会启动失败）

## 五、技术要点（排障参考）

- **双方言机制**：`SqlDdl`（DDL 运行时翻译 AUTOINCREMENT→IDENTITY、INTEGER→BIGINT、TIMESTAMP/DATE/DATETIME→BIGINT、布尔列→BOOLEAN）、`LocalDateTimeMillisConverter`/`LocalDateMillisConverter`（autoApply，LocalDateTime/LocalDate↔毫秒 long）、`PgCompatFunctionsInitializer`（PG 兼容函数：now_ms/strftime×5 重载含 '+8 hours'/'-N months' 修饰/date/datetime/julianday/instr/group_concat）、`DbMeta`（PRAGMA/sqlite_master 的 information_schema 等价）
- **隐式 CAST**（pg_casts.sql，超户一次性）：VARCHAR/DATE/TIMESTAMP→BIGINT（to_bigint_ms，ISO 日期按东八区零点折毫秒；数字字符串透传）——应用侧 JdbcTemplate 传 String/LocalDate 给毫秒列的调用形态因此零改动
- **数据迁移口径**：ID 全保留；布尔 0/1→true/false；时间列 ISO 文本（早期 JPA 遗留混合存储）→毫秒；IDENTITY 序列搬后 setval 校准
- **14 个汇总触发器**：PG 版为 plpgsql 函数+触发器（CREATE OR REPLACE 幂等），SQLite 版保留（BEGIN..END 语法）
- **时间毫秒口径是全局基石**：所有报表 SQL（strftime 103 处）、单号取序号（SUBSTR/LENGTH 36 处）在双方言下行为一致
