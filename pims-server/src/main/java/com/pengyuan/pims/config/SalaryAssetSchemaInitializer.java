package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.62 工资核算 + 固定资产建表：员工档案 / 工资单 / 工资明细 / 资产卡片 / 折旧记录
 * ddl-auto=none，幂等 DDL；字典与科目映射种子照 VoucherSchemaInitializer 的 SELECT+INSERT 模式（只从无到有）。
 */
@Component
@Order(3)
public class SalaryAssetSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SalaryAssetSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SalaryAssetSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        table("员工档案表 employee", """
            CREATE TABLE IF NOT EXISTS employee (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name VARCHAR(50) NOT NULL,
                dept VARCHAR(20) NOT NULL,
                position VARCHAR(50),
                hire_date DATE,
                leave_date DATE,
                status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                base_salary DECIMAL(12,2) DEFAULT 0,
                bank_card VARCHAR(50),
                phone VARCHAR(30),
                remark VARCHAR(500),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
            """);
        table("工资单表 salary_sheet", """
            CREATE TABLE IF NOT EXISTS salary_sheet (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doc_no VARCHAR(20) NOT NULL UNIQUE,
                period VARCHAR(10) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                total_gross DECIMAL(14,2) NOT NULL DEFAULT 0,
                total_net DECIMAL(14,2) NOT NULL DEFAULT 0,
                created_by VARCHAR(50),
                confirmed_by VARCHAR(50),
                confirmed_time TIMESTAMP,
                remark VARCHAR(500),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
            """);
        table("工资明细表 salary_item", """
            CREATE TABLE IF NOT EXISTS salary_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sheet_id BIGINT NOT NULL,
                employee_id BIGINT NOT NULL,
                employee_name VARCHAR(50) NOT NULL,
                dept VARCHAR(20) NOT NULL,
                base DECIMAL(12,2) DEFAULT 0,
                bonus DECIMAL(12,2) DEFAULT 0,
                piecework DECIMAL(12,2) DEFAULT 0,
                deduction DECIMAL(12,2) DEFAULT 0,
                social_ins DECIMAL(12,2) DEFAULT 0,
                income_tax DECIMAL(12,2) DEFAULT 0,
                gross DECIMAL(12,2) NOT NULL DEFAULT 0,
                net DECIMAL(12,2) NOT NULL DEFAULT 0
            )
            """);
        table("资产卡片表 asset", """
            CREATE TABLE IF NOT EXISTS asset (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doc_no VARCHAR(20) NOT NULL UNIQUE,
                name VARCHAR(100) NOT NULL,
                category VARCHAR(20) NOT NULL,
                purchase_date DATE,
                original_value DECIMAL(14,2) NOT NULL,
                useful_life_months INTEGER NOT NULL,
                residual_rate DECIMAL(5,2) DEFAULT 5,
                expense_subject VARCHAR(20) NOT NULL DEFAULT '6602.05',
                location VARCHAR(100),
                keeper VARCHAR(50),
                status VARCHAR(20) NOT NULL DEFAULT 'IN_USE',
                scrap_date DATE,
                remark VARCHAR(500),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
            """);
        table("折旧记录表 asset_depreciation", """
            CREATE TABLE IF NOT EXISTS asset_depreciation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period VARCHAR(10) NOT NULL,
                asset_id BIGINT NOT NULL,
                asset_name VARCHAR(100) NOT NULL,
                expense_subject VARCHAR(20) NOT NULL,
                amount DECIMAL(14,2) NOT NULL,
                voucher_id BIGINT,
                create_time TIMESTAMP
            )
            """);

        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_salary_item_sheet ON salary_item(sheet_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_asset_dep_period ON asset_depreciation(period)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_asset_dep_asset ON asset_depreciation(asset_id)");
        } catch (Exception e) { log.warn("工资资产索引创建失败: {}", e.getMessage()); }

        ensureDicts();
        ensureMappings();
    }

    private void table(String label, String ddl) {
        try {
            jdbc.execute(ddl);
            log.info("{} 就绪", label);
        } catch (Exception e) { log.warn("{}建表失败: {}", label, e.getMessage()); }
    }

    /** 字典种子：部门（决定工资计提借方科目）+ 资产类别 */
    private void ensureDicts() {
        String[][] seeds = {
            {"salary_dept", "生产", "PRODUCTION", "1"},
            {"salary_dept", "销售", "SALES", "2"},
            {"salary_dept", "行政", "ADMIN", "3"},
            {"salary_dept", "技术", "TECH", "4"},
            {"salary_dept", "质检", "QC", "5"},
            {"salary_dept", "其他", "OTHER", "6"},
            {"asset_category", "房屋建筑", "BUILDING", "1"},
            {"asset_category", "机器设备", "MACHINE", "2"},
            {"asset_category", "运输工具", "VEHICLE", "3"},
            {"asset_category", "电子设备", "ELECTRONIC", "4"},
            {"asset_category", "其他", "OTHER", "5"},
        };
        int added = 0;
        for (String[] s : seeds) {
            var exists = jdbc.queryForList("SELECT id FROM dict_item WHERE type = ? AND value = ?", s[0], s[2]);
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO dict_item (type, label, value, sort_order, enabled, create_time) VALUES (?,?,?,?,1,?)",
                        s[0], s[1], s[2], Integer.parseInt(s[3]), System.currentTimeMillis());
                added++;
            }
        }
        if (added > 0) log.info("工资/资产字典种子补录 {} 条", added);
    }

    /** 科目映射种子：部门→工资费用科目、折旧默认科目（页面可在科目设置里改） */
    private void ensureMappings() {
        String[][] seeds = {
            {"biz:salary:dept:PRODUCTION", "5001.02", "生产工人工资→生产成本-直接人工"},
            {"biz:salary:dept:SALES", "6601.03", "销售人员工资→销售费用"},
            {"biz:salary:dept:ADMIN", "6602.02", "行政人员工资→管理费用"},
            {"biz:salary:dept:TECH", "6602.02", "技术人员工资→管理费用"},
            {"biz:salary:dept:QC", "6602.02", "质检人员工资→管理费用"},
            {"biz:salary:dept:OTHER", "6602.02", "其他人员工资→管理费用"},
            {"biz:salary:payable", "2211.01", "应付工资科目"},
            {"biz:salary:bank", "1002", "工资发放科目"},
            {"biz:salary:social", "2232", "代扣社保→其他应付款"},
            {"biz:salary:tax", "2221.06", "代扣个税→应交个人所得税"},
            {"biz:depreciation:default", "6602.05", "折旧默认费用科目"},
            {"biz:depreciation:accu", "1602", "累计折旧科目"},
        };
        int added = 0;
        for (String[] s : seeds) {
            var exists = jdbc.queryForList("SELECT id FROM account_mapping WHERE map_key = ?", s[0]);
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO account_mapping (map_key, subject_code, remark, update_time) VALUES (?,?,?,?)",
                        s[0], s[1], s[2], System.currentTimeMillis());
                added++;
            }
        }
        if (added > 0) log.info("工资/资产科目映射种子补录 {} 条", added);
    }
}
