# PIMS 部署运维指南（机房服务器 + 外网访问）

> 配套：PRD §9 部署运维、SOP §13 系统维护
> 目标环境：本地机房服务器（内存 64G 等高配）、企业认证域名、公网访问

---

## 一、64G 内存高配服务器如何利用

### 1.1 核心认知：SQLite 的"内存数据库"就是操作系统页缓存

PIMS 使用 SQLite 单文件数据库。SQLite 每次读数据都先经过**操作系统页缓存**——
64G 内存服务器上，**最有效的利用方式是让 OS 用空闲内存缓存整个 pims.db 文件**，
而不是把 JVM 堆开大。堆开得越大，留给 OS 缓存的内存反而越少，垃圾回收停顿也更长。

| 内存去向 | 建议配置 | 说明 |
|---|---|---|
| JVM 堆 | `-Xms512m -Xmx4g` | 本系统 5-10 人并发，4G 堆绰绰有余；堆太大反而增加 GC 停顿 |
| GC | `-XX:+UseG1GC -XX:MaxGCPauseMillis=200` | G1 目标停顿 200ms，界面无感知 |
| OS 页缓存 | 剩余 ~60G | 自动缓存 pims.db（几十 MB 级），**全库常驻内存，读性能=内存速度** |
| SQLite 页缓存 | 64MB（已配置 cache_size=-64000） | 辅助缓存，大头靠 OS |
| WAL | 上限 64MB（已配置 journal_size_limit） | 防止 WAL 无限膨胀 |

> 结论：**不要动 JVM 参数去"吃满"64G**。当前 `start.bat` 的参数即推荐值。
> 若未来并发/数据量翻几十倍，可上调至 `-Xmx8g`，但 SQLite 单写者的瓶颈不在堆。

### 1.2 启动脚本

- Windows：`pims-server/start.bat`（已含推荐 JVM 参数，日志输出到 `logs/pims.log`）
- Linux（systemd，见 §2.2）使用同样的 JVM 参数

### 1.3 服务器其他建议

- 磁盘：SSD（SQLite 写放大小、checkpoint 快）；日志与数据库同盘即可
- 系统：Windows Server 2019+ 或 Ubuntu 22.04+/CentOS 7+（64 位）
- 每日备份：复制 `pims-server/data/pims.db` 到异盘/异地（WAL 模式建议先停服或使用 sqlite3 在线备份）
- 每月维护：`mvn` 无需干预，VACUUM 与 WAL checkpoint 已由系统启动时自动执行（≥50MB 且距上次 >30 天）

---

## 二、部署方式

### 2.1 Windows 部署

1. 拷贝整个 `pims-server` 目录到服务器（含 `data/`、`target/pims-server-1.0.0.jar`）
2. 双击 `start.bat` 启动
3. 开机自启：任务计划程序 → 创建任务 → 触发器"启动时" → 操作启动 `start.bat`

### 2.2 Linux 部署（推荐生产环境）

```bash
# 安装 JDK 17
sudo apt install -y openjdk-17-jre-headless   # Ubuntu
# sudo yum install -y java-17-openjdk          # CentOS/RHEL

# 目录结构
mkdir -p /opt/pims && cp -r pims-server/* /opt/pims/
```

创建 `/etc/systemd/system/pims.service`：

```ini
[Unit]
Description=PIMS 芃远综合管理系统
After=network.target

[Service]
WorkingDirectory=/opt/pims
ExecStart=/usr/bin/java -Xms512m -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
    -Dfile.encoding=UTF-8 -jar /opt/pims/target/pims-server-1.0.0.jar
Restart=always
RestartSec=5
User=pims

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pims
sudo systemctl status pims        # 查看状态
sudo journalctl -u pims -f        # 查看日志
```

---

## 二点五、数据库自动备份与恢复（v5.48）

**自动备份**：系统每日 04:30 自动热备 SQLite 库到 `backups/pims-db-YYYYMMDD.db`（无需停服），保留 90 天自动清理；工作台待办显示"备份异常"（超 25 小时未备份）。手动即时备份：`POST /api/dashboard/backup`（升级/重要操作前建议先手动备一次）。

**异地备份**：办公电脑计划任务"PIMS每周备份拉取"（每周日 20:00）自动 `scp` 拉取服务器 `backups/` 到 `D:\PIMS-backups\`，日志见 `D:\PIMS-backups\pull.log`。

**恢复步骤**（数据损坏/误操作回滚）：
1. `sudo systemctl stop pims`
2. `cp /opt/pims/data/pims.db /opt/pims/data/pims.db.broken`（保留现场）
3. `cp /opt/pims/backups/pims-db-选定的日期.db /opt/pims/data/pims.db`
4. `sudo systemctl start pims` → 登录验证数据
5. 注意：恢复会回到备份时点，之后的单据需重新录入

## 三、外网访问方案（企业认证域名）

前提：域名已完成企业认证/ICP 备案，服务器有**公网 IP**。

### 3.1 整体架构

```
外网用户
   │  https://pims.你的域名.com:443
   ▼
服务器公网 IP（防火墙放行 80/443）
   ▼
nginx / Caddy 反向代理（HTTPS 终结）
   ▼
127.0.0.1:8080  ← PIMS（保持内网监听，不直接暴露）
```

### 3.2 DNS 解析

在域名服务商控制台添加 A 记录：

| 主机记录 | 类型 | 记录值 |
|---|---|---|
| pims（或 www 等） | A | 服务器公网 IP |

### 3.3 方案 A：Caddy（推荐，自动 HTTPS，配置最简）

```bash
# Ubuntu 安装
sudo apt install -y caddy
```

`/etc/caddy/Caddyfile`：

```caddy
pims.你的域名.com {
    reverse_proxy 127.0.0.1:8080
    encode gzip
    header {
        Strict-Transport-Security "max-age=31536000"
        X-Content-Type-Options "nosniff"
    }
}
```

Caddy 会自动申请并续期 Let's Encrypt 证书，无需手工维护。

### 3.4 方案 B：nginx（传统方案，证书手动配置）

```bash
sudo apt install -y nginx
```

`/etc/nginx/sites-available/pims`：

```nginx
server {
    listen 80;
    server_name pims.你的域名.com;
    # HTTP 强制跳转 HTTPS
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name pims.你的域名.com;

    ssl_certificate     /etc/nginx/ssl/pims.pem;
    ssl_certificate_key /etc/nginx/ssl/pims.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    client_max_body_size 50m;          # 采购合同 PDF 上传

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
```

证书获取（二选一）：
- **certbot**（Let's Encrypt，免费自动续期）：`sudo apt install certbot python3-certbot-nginx && sudo certbot --nginx -d pims.你的域名.com`
- **云厂商免费证书**（阿里云/腾讯云/华为云）：申请后下载 nginx 格式，放入 `/etc/nginx/ssl/`

### 3.5 防火墙放行

```bash
# Linux（ufw）
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# 不对外放行 8080（PIMS 仅内网反代访问）
```

Windows Server：防火墙入站规则放行 80/443（8080 不放行或仅限本机）。

### 3.6 无公网 IP 的情况（备选）

机房无公网 IP 时，用**内网穿透**（frp / nps / 花生壳）把服务器 8080 映射到一台有公网 IP 的云主机，
再由云主机 nginx 反代 + 域名 + HTTPS。Caddy 方案同样适用。

---

## 四、安全基线（对外网开放必读）

1. **立即修改默认密码**：admin/admin123 → 登录后到「系统设置 → 用户管理」修改
2. **HTTPS 强制**：务必启用 §3.3/3.4 的 301 跳转，避免明文传输
3. **关闭 8080 直连**：只允许反代访问（防火墙只放行 80/443）
4. **账号管理**：按角色最小权限分配；不用的账号禁用（`sys_user.enabled=false`）
5. **定期备份**：`pims-server/data/pims.db` 每日异盘复制；升级前必先备份
6. **升级流程**：停服 → 备份 db → 替换 jar → 启动（表结构差异由启动初始化器幂等补齐）

---

## 五、常见问题

| 现象 | 处理 |
|---|---|
| 外网打不开 | 检查 DNS 解析、防火墙 80/443、nginx/caddy 状态（`sudo systemctl status nginx`） |
| 页面能开但接口 502 | 确认 PIMS 进程存活（`sudo systemctl status pims` / 查看 `logs/pims.log`） |
| 上传合同失败 | 检查反代 `client_max_body_size`（nginx 默认 1M 需调大） |
| 域名被墙/备案未过审 | 国内服务器必须 ICP 备案；未备案只能用 IP:端口或海外节点 |
| 提示"状态显示英文" | 后端升级后 Ctrl+F5 强刷浏览器缓存 |
