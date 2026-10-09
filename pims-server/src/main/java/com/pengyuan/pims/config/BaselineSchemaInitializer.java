package com.pengyuan.pims.config;

import com.pengyuan.pims.common.DbMeta;
import com.pengyuan.pims.common.SqlDdl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * v12.0：空库基线自举（PG 唯一数据库）。
 *
 * 背景：核心 96 张业务表的历史 DDL 以 SQLite 风味收录在 db/baseline-schema.sql（打包进 jar 根），
 * 经 SqlDdl 运行期翻译为 PG 方言后逐句执行（幂等：已存在的表/索引报错跳过并计数）。
 * 生产 PG 由 init_pg_schema.py 离线建库不受影响（本初始化器检测到表已存在即跳过）；
 * 本地开发/全新部署的空 PG 库则由此完成自举，无需任何外部脚本。
 *
 * 执行顺序：必须在一切补列/迁移初始化器之前（表都还没建，ALTER 无从谈起）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)   // PgCompatFunctionsInitializer(MIN) 之后、TaxSchemaInitializer(MIN+2) 之前
public class BaselineSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BaselineSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public BaselineSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws Exception {
        if (DbMeta.tableExists(jdbc, "material")) {
            return;  // 已有表结构（生产/已初始化库），跳过
        }
        List<String> statements = loadBaseline();
        int done = 0, skipped = 0;
        String firstSkip = null;
        for (String sql : statements) {
            try {
                SqlDdl.exec(jdbc, sql);
                done++;
            } catch (Exception skip) {
                // 幂等冲突（表/索引已存在等）跳过——但计数并记录首条原因，静默漏执行可发现
                skipped++;
                if (firstSkip == null) firstSkip = skip.getMessage();
            }
        }
        log.info("空库基线自举完成：{} 条执行，{} 条跳过{}（源 {} 条）",
                done, skipped,
                firstSkip != null ? "（首条跳过原因: " + firstSkip + "）" : "",
                statements.size());
        if (done == 0) {
            throw new IllegalStateException("基线自举未执行任何语句，请检查 baseline-schema.sql 与数据库连接");
        }
    }

    /** 读取 jar 内 baseline-schema.sql，按「行尾分号」切分为单条语句（DDL 均为单行结尾，无触发器体内分号） */
    private List<String> loadBaseline() throws Exception {
        var in = BaselineSchemaInitializer.class.getClassLoader().getResourceAsStream("baseline-schema.sql");
        if (in == null) {
            throw new IllegalStateException("classpath 中找不到 baseline-schema.sql（打包资源丢失）");
        }
        List<String> statements = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
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
        return statements;
    }
}
