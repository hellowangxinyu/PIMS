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
 * 数据库自动备份（v5.48，P0 数据安全）：
 * - 每日 04:30（避开 04:15 过期扫描）用 SQLite VACUUM INTO 在线热备到 backups/pims-db-YYYYMMDD.db
 *   （WAL 模式下安全，无需停服；@Scheduled 方法无事务，VACUUM 可用——历史坑：事务内 VACUUM 报错）
 * - 日备保留 90 天，到期自动清理（库 ~1.5MB，90 天约 140MB）
 * - 备份元数据写入 backup_meta 表（上次备份时间），Dashboard 待办显示超 25 小时未备份的告警
 * - 异地：用户电脑计划任务每周 scp 拉取服务器 backups/（防整机硬盘损坏）
 * - 恢复：停服 → 覆盖 data/pims.db → 启动（DEPLOY.md 有 SOP）
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
        // v9.2（P2-12 审计）：磁盘预检——剩余空间不足库体积 5 倍（VACUUM 全量拷贝+余量）时拒绝执行并明示
        long usable = dir.getUsableSpace();
        File dbFile = new File("data/pims.db");
        long need = Math.max(dbFile.length() * 5, 200L * 1024 * 1024);
        if (usable < need) {
            throw new IllegalStateException(String.format(
                    "磁盘剩余空间不足（可用 %.1fMB，备份至少需 %.1fMB），请先清理 backups 目录或扩容",
                    usable / 1048576.0, need / 1048576.0));
        }
        String filename = "pims-db-" + date + ".db";
        File target = new File(dir, filename);
        if (target.exists()) {
            return filename;  // 当天已备份（幂等）
        }
        long start = System.currentTimeMillis();
        // v8.8（D8）：临时文件 + 完整性校验 + 原子改名——原直接 VACUUM INTO 目标名，
        // 中断会留半截文件，下次启动 target.exists() 即视为"当天已备份"，当天不再重试
        File tmp = new File(dir, filename + ".tmp");
        if (tmp.exists()) tmp.delete();   // 上次中断的半截临时文件
        try {
            jdbc.execute("VACUUM INTO 'backups/" + filename + ".tmp'");
            // 完整性校验：对临时文件开门检查（只读），损坏则删除重抛（明天/手动重试）
            // v8.9（三次复查）：只读开门——URL 加 mode=ro（原 Properties "open" 不是 sqlite-jdbc 参数，实际以读写打开）
            try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:backups/" + filename + ".tmp?mode=ro")) {
                var rs = c.createStatement().executeQuery("PRAGMA integrity_check");
                rs.next();
                if (!"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new IllegalStateException("备份完整性校验失败: " + rs.getString(1));
                }
            } catch (java.sql.SQLException se) {
                throw new IllegalStateException("备份完整性校验无法执行", se);
            }
            if (!tmp.renameTo(target)) {
                throw new IllegalStateException("备份改名失败（tmp→正式名）: " + tmp.getAbsolutePath());
            }
        } catch (RuntimeException e) {
            tmp.delete();   // 清理半截临时文件，保持"未备份"状态可重试
            throw e;
        }
        long size = target.length();
        recordMeta(filename, size);
        cleanExpired();
        log.info("数据库自动备份完成: {} ({}KB, 含完整性校验, 耗时{}ms)", filename, size / 1024, System.currentTimeMillis() - start);
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

    private void recordMeta(String filename, long size) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS backup_meta (id INTEGER PRIMARY KEY AUTOINCREMENT, filename VARCHAR(100), size_bytes BIGINT, backup_time TIMESTAMP)");
            jdbc.update("INSERT INTO backup_meta (filename, size_bytes, backup_time) VALUES (?, ?, ?)",
                    filename, size, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("备份元数据写入失败（不影响备份文件）: {}", e.getMessage());
        }
    }

    /** 清理超过保留期的日备（按文件名日期判断，失败仅告警） */
    private void cleanExpired() {
        try {
            File dir = new File("backups");
            File[] files = dir.listFiles((d, n) -> n.matches("pims-db-\\d{4}-\\d{2}-\\d{2}\\.db"));
            if (files == null) return;
            LocalDate cutoff = LocalDate.now().minusDays(RETAIN_DAYS);
            int removed = 0;
            for (File f : files) {
                String d = f.getName().replace("pims-db-", "").replace(".db", "");
                try {
                    if (LocalDate.parse(d).isBefore(cutoff) && f.delete()) removed++;
                } catch (Exception ignored) { }
            }
            if (removed > 0) log.info("过期备份清理: 删除 {} 个（保留 {} 天）", removed, RETAIN_DAYS);
        } catch (Exception e) {
            log.warn("备份清理失败: {}", e.getMessage());
        }
    }
}
