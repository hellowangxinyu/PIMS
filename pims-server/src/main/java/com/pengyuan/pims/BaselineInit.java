package com.pengyuan.pims;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * v8.10.1 空库自举入口：--init-db 启动参数触发。
 * 从 classpath 之外的仓库 db/baseline-schema.sql（打包时复制进 jar 根）读取基线 DDL，
 * 对 jdbc:sqlite:data/pims.db 逐句执行（幂等：SQLite 不支持 IF NOT EXISTS 语句级过滤时靠表已存在报错跳过）。
 * 服务器无需安装 sqlite3 CLI。
 */
public final class BaselineInit {

    private BaselineInit() {}

    public static void initIfNeeded() {
        String url = "jdbc:sqlite:data/pims.db";
        try (Connection c = DriverManager.getConnection(url)) {
            // 库里已有核心表 → 跳过（幂等）
            try (var rs = c.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='material'")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    System.out.println("[init-db] 检测到已有表结构，跳过基线导入");
                    return;
                }
            }
            List<String> statements = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    BaselineInit.class.getClassLoader().getResourceAsStream("baseline-schema.sql"), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.strip();
                    if (t.startsWith("--") || t.isEmpty()) continue;
                    sb.append(line).append('\n');
                    if (t.endsWith(";")) {
                        statements.add(sb.toString());
                        sb.setLength(0);
                    }
                }
            }
            int done = 0, skipped = 0;
            String firstSkip = null;
            try (Statement st = c.createStatement()) {
                for (String sql : statements) {
                    try {
                        st.execute(sql);
                        done++;
                    } catch (Exception skip) {
                        // 表已存在等幂等冲突跳过——但把数量与首条原因打出来，静默漏执行可发现
                        skipped++;
                        if (firstSkip == null) firstSkip = skip.getMessage();
                    }
                }
            }
            System.out.println("[init-db] 基线导入完成：" + done + " 条，跳过 " + skipped + " 条"
                    + (firstSkip != null ? "（首条跳过原因: " + firstSkip + "）" : "")
                    + "（源 " + statements.size() + " 条）");
        } catch (Exception e) {
            System.err.println("[init-db] 基线导入失败：" + e.getMessage());
            throw new IllegalStateException(e);
        }
    }
}
