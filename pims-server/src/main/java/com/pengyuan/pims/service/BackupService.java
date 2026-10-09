package com.pengyuan.pims.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据库自动备份（v5.48，P0 数据安全；v12.0 起 PG 唯一数据库）：
 * - 每日 04:30（避开 04:15 过期扫描）用 pg_dump -Fc 在线热备到 backups/pims-db-YYYYMMDD.dump
 *   （部署约定：PG 与应用同机且 pg_dump 在 PATH）
 * - 日备保留 90 天，到期自动清理
 * - 备份元数据写入 backup_meta 表（上次备份时间），Dashboard 待办显示超 25 小时未备份的告警
 * - 异地：用户电脑计划任务每周 scp 拉取服务器 backups/（防整机硬盘损坏）
 * - 恢复：停服 → pg_restore 到空库 → 启动（DEPLOY.md 有 SOP）
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** 日备保留天数 */
    private static final int RETAIN_DAYS = 90;

    private final JdbcTemplate jdbc;

    public BackupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 每日 04:30 自动备份 */
    // v9.2（P2-5 审计）：zone 固定 Asia/Shanghai——原依赖 OS 时区，系统时区一错备份日切全错
    @Scheduled(cron = "0 30 4 * * ?", zone = "Asia/Shanghai")
    public void scheduledBackup() {
        try {
            backupNow();
        } catch (Exception e) {
            // v9.2（P2-12 审计）：备份失败此前仅 log.error 于 backupNow 内部细节，这里统一以告警级留痕
            log.error("[备份告警] 定时备份失败，请立即排查！{}", e.getMessage(), e);
        }
    }

    /** 执行一次备份（定时触发；也可手动调用）。返回备份文件名，失败抛异常。 */
    public String backupNow() {
        String date = LocalDate.now().toString();
        File dir = new File("backups");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("备份目录创建失败: " + dir.getAbsolutePath());
        }
        long usable = dir.getUsableSpace();
        if (usable < 200L * 1024 * 1024) {
            throw new IllegalStateException(String.format(
                    "磁盘剩余空间不足（可用 %.1fMB，备份至少需 200MB），请先清理 backups 目录或扩容",
                    usable / 1048576.0));
        }
        String filename = "pims-db-" + date + ".dump";
        File target = new File(dir, filename);
        if (target.exists()) {
            return filename;  // 当天已备份（幂等）
        }
        long start = System.currentTimeMillis();
        File tmp = new File(dir, filename + ".tmp");
        if (tmp.exists()) tmp.delete();
        try {
            // 数据库连接信息从环境变量取（与 application.yml 同一来源）
            String url = System.getenv().getOrDefault("PIMS_DB_URL", "jdbc:postgresql://127.0.0.1:5432/pims");
            java.net.URI uri = java.net.URI.create(url.replace("jdbc:", ""));
            String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            int port = uri.getPort() < 0 ? 5432 : uri.getPort();
            String db = uri.getPath() == null ? "pims" : uri.getPath().replace("/", "");
            ProcessBuilder pb = new ProcessBuilder("pg_dump", "-Fc", "-h", host, "-p", String.valueOf(port),
                    "-U", System.getenv().getOrDefault("PIMS_DB_USER", "pims"), "-f", tmp.getAbsolutePath(), db);
            pb.environment().put("PGPASSWORD", System.getenv().getOrDefault("PIMS_DB_PASSWORD", ""));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalStateException("pg_dump 失败(" + code + "): " + out);
            }
            if (tmp.length() < 1024) {
                throw new IllegalStateException("pg_dump 产物异常过小: " + tmp.length() + "B");
            }
            if (!tmp.renameTo(target)) {
                throw new IllegalStateException("备份改名失败（tmp→正式名）: " + tmp.getAbsolutePath());
            }
        } catch (RuntimeException e) {
            tmp.delete();
            throw e;
        } catch (Exception e) {
            tmp.delete();
            throw new IllegalStateException("PG 备份执行失败: " + e.getMessage(), e);
        }
        long size = target.length();
        recordMeta(filename, size);
        cleanExpired();
        log.info("PostgreSQL 自动备份完成: {} ({}KB, 耗时{}ms)", filename, size / 1024, System.currentTimeMillis() - start);
        return filename;
    }

    /** 上次成功备份时间（Dashboard 备份健康检查用）；从未备份返回 null */
    public LocalDateTime lastBackupTime() {
        try {
            var rows = jdbc.queryForList("SELECT backup_time FROM backup_meta ORDER BY id DESC LIMIT 1");
            if (rows.isEmpty()) return null;
            Object v = rows.get(0).get("backup_time");
            if (v instanceof Number n) {
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(n.longValue()),
                        java.time.ZoneOffset.ofHours(8));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** PG 版过期清理（.dump 后缀） */
    private void cleanExpired() {
        try {
            File dir = new File("backups");
            File[] files = dir.listFiles((d, n) -> n.matches("pims-db-\\d{4}-\\d{2}-\\d{2}\\.dump"));
            if (files == null) return;
            LocalDate cutoff = LocalDate.now().minusDays(RETAIN_DAYS);
            int removed = 0;
            for (File f : files) {
                String d = f.getName().replace("pims-db-", "").replace(".dump", "");
                try {
                    if (LocalDate.parse(d).isBefore(cutoff) && f.delete()) removed++;
                } catch (Exception ignored) { }
            }
            if (removed > 0) log.info("过期备份清理: 删除 {} 个（保留 {} 天）", removed, RETAIN_DAYS);
        } catch (Exception e) {
            log.warn("备份清理失败: {}", e.getMessage());
        }
    }

    private void recordMeta(String filename, long size) {
        try {
            com.pengyuan.pims.common.SqlDdl.exec(jdbc, "CREATE TABLE IF NOT EXISTS backup_meta (id INTEGER PRIMARY KEY AUTOINCREMENT, filename VARCHAR(100), size_bytes BIGINT, backup_time TIMESTAMP)");
            jdbc.update("INSERT INTO backup_meta (filename, size_bytes, backup_time) VALUES (?, ?, ?)",
                    filename, size, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("备份元数据写入失败（不影响备份文件）: {}", e.getMessage());
        }
    }
}
