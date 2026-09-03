package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * v5.61 总账体系建表：会计科目 / 记账凭证 / 凭证分录 / 账务期间 / 科目映射
 * ddl-auto=none，所有新表必须在此手写幂等 DDL。
 * 种子只从无到有（缺哪条补哪条），存量库安全。
 */
@Component
@Order(3)
public class VoucherSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VoucherSchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public VoucherSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS account_subject (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code VARCHAR(20) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    parent_code VARCHAR(20),
                    category VARCHAR(20) NOT NULL,
                    direction VARCHAR(5) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                    opening_balance DECIMAL(14,2) DEFAULT 0,
                    opening_direction VARCHAR(5),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("会计科目表 account_subject 就绪");
        } catch (Exception e) { log.warn("会计科目表建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS voucher (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_no VARCHAR(20) NOT NULL UNIQUE,
                    voucher_date DATE NOT NULL,
                    period VARCHAR(10) NOT NULL,
                    total_debit DECIMAL(14,2) NOT NULL DEFAULT 0,
                    total_credit DECIMAL(14,2) NOT NULL DEFAULT 0,
                    attachment_count INTEGER,
                    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
                    ref_doc_no VARCHAR(30),
                    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                    created_by VARCHAR(50),
                    posted_by VARCHAR(50),
                    posted_time TIMESTAMP,
                    remark VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
            log.info("记账凭证表 voucher 就绪");
        } catch (Exception e) { log.warn("记账凭证表建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS voucher_entry (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    voucher_id BIGINT NOT NULL,
                    line_no INTEGER,
                    subject_code VARCHAR(20) NOT NULL,
                    subject_name VARCHAR(100) NOT NULL,
                    digest VARCHAR(200),
                    debit DECIMAL(14,2) DEFAULT 0,
                    credit DECIMAL(14,2) DEFAULT 0,
                    aux_type VARCHAR(20),
                    aux_name VARCHAR(100)
                )
                """);
            log.info("凭证分录表 voucher_entry 就绪");
        } catch (Exception e) { log.warn("凭证分录表建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS account_period (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    period VARCHAR(10) NOT NULL UNIQUE,
                    closed INTEGER NOT NULL DEFAULT 1,
                    closed_by VARCHAR(50),
                    close_time TIMESTAMP
                )
                """);
            log.info("账务期间表 account_period 就绪");
        } catch (Exception e) { log.warn("账务期间表建表失败: {}", e.getMessage()); }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS account_mapping (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    map_key VARCHAR(60) NOT NULL UNIQUE,
                    subject_code VARCHAR(20) NOT NULL,
                    remark VARCHAR(200),
                    update_time TIMESTAMP
                )
                """);
            log.info("科目映射表 account_mapping 就绪");
        } catch (Exception e) { log.warn("科目映射表建表失败: {}", e.getMessage()); }

        // 索引：期间/状态等值查询、分录批量预取、科目余额聚合
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_voucher_period ON voucher(period)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_voucher_source_ref ON voucher(source, ref_doc_no)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_voucher_entry_vid ON voucher_entry(voucher_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_voucher_entry_subject ON voucher_entry(subject_code)");
        } catch (Exception e) { log.warn("总账索引创建失败: {}", e.getMessage()); }

        ensureSubjects();
        ensureMappings();
    }

    /**
     * 科目种子（小企业会计准则常用科目，两级结构：parent_code 空=一级）。
     * {code, name, parentCode, category, direction}
     */
    private void ensureSubjects() {
        String[][] seeds = {
            // ===== 资产类 =====
            {"1001", "库存现金", null, "ASSET", "DR"},
            {"1002", "银行存款", null, "ASSET", "DR"},
            {"1012", "其他货币资金", null, "ASSET", "DR"},
            {"1121", "应收票据", null, "ASSET", "DR"},
            {"1122", "应收账款", null, "ASSET", "DR"},
            {"1123", "预付账款", null, "ASSET", "DR"},
            {"1131", "其他应收款", null, "ASSET", "DR"},
            {"1403", "原材料", null, "ASSET", "DR"},
            {"1405", "库存商品", null, "ASSET", "DR"},
            {"1408", "委托加工物资", null, "ASSET", "DR"},
            {"1411", "周转材料", null, "ASSET", "DR"},
            {"1501", "固定资产", null, "ASSET", "DR"},
            {"1602", "累计折旧", null, "ASSET", "CR"},
            {"1701", "无形资产", null, "ASSET", "DR"},
            {"1801", "长期待摊费用", null, "ASSET", "DR"},
            // ===== 负债类 =====
            {"2001", "短期借款", null, "LIABILITY", "CR"},
            {"2201", "应付票据", null, "LIABILITY", "CR"},
            {"2202", "应付账款", null, "LIABILITY", "CR"},
            {"2203", "预收账款", null, "LIABILITY", "CR"},
            {"2211", "应付职工薪酬", null, "LIABILITY", "CR"},
            {"2211.01", "应付工资", "2211", "LIABILITY", "CR"},
            {"2211.02", "社保公积金", "2211", "LIABILITY", "CR"},
            {"2211.03", "职工福利", "2211", "LIABILITY", "CR"},
            {"2221", "应交税费", null, "LIABILITY", "CR"},
            {"2221.01", "应交增值税-销项税额", "2221", "LIABILITY", "CR"},
            {"2221.02", "应交增值税-进项税额", "2221", "LIABILITY", "CR"},
            {"2221.03", "未交增值税", "2221", "LIABILITY", "CR"},
            {"2221.04", "应交企业所得税", "2221", "LIABILITY", "CR"},
            {"2221.05", "应交城建及附加", "2221", "LIABILITY", "CR"},
            {"2221.06", "应交个人所得税", "2221", "LIABILITY", "CR"},
            {"2231", "应付利息", null, "LIABILITY", "CR"},
            {"2232", "其他应付款", null, "LIABILITY", "CR"},
            {"2501", "长期借款", null, "LIABILITY", "CR"},
            // ===== 权益类 =====
            {"3001", "实收资本", null, "EQUITY", "CR"},
            {"3101", "资本公积", null, "EQUITY", "CR"},
            {"3104", "本年利润", null, "EQUITY", "CR"},
            {"3105", "利润分配", null, "EQUITY", "CR"},
            {"3105.01", "未分配利润", "3105", "EQUITY", "CR"},
            // ===== 成本类 =====
            {"5001", "生产成本", null, "COST", "DR"},
            {"5001.01", "直接材料", "5001", "COST", "DR"},
            {"5001.02", "直接人工", "5001", "COST", "DR"},
            {"5001.03", "制造费用转入", "5001", "COST", "DR"},
            {"5101", "制造费用", null, "COST", "DR"},
            // ===== 损益类 =====
            {"6001", "主营业务收入", null, "PL", "CR"},
            {"6051", "其他业务收入", null, "PL", "CR"},
            {"6301", "营业外收入", null, "PL", "CR"},
            {"6401", "主营业务成本", null, "PL", "DR"},
            {"6402", "其他业务成本", null, "PL", "DR"},
            {"6403", "税金及附加", null, "PL", "DR"},
            {"6601", "销售费用", null, "PL", "DR"},
            {"6601.01", "销售费用-运费", "6601", "PL", "DR"},
            {"6601.02", "销售费用-广告宣传费", "6601", "PL", "DR"},
            {"6601.03", "销售费用-销售人员工资", "6601", "PL", "DR"},
            {"6601.04", "销售费用-业务招待费", "6601", "PL", "DR"},
            {"6601.05", "销售费用-其他", "6601", "PL", "DR"},
            {"6602", "管理费用", null, "PL", "DR"},
            {"6602.01", "管理费用-办公费", "6602", "PL", "DR"},
            {"6602.02", "管理费用-管理人员工资", "6602", "PL", "DR"},
            {"6602.03", "管理费用-差旅费", "6602", "PL", "DR"},
            {"6602.04", "管理费用-水电费", "6602", "PL", "DR"},
            {"6602.05", "管理费用-折旧费", "6602", "PL", "DR"},
            {"6602.06", "管理费用-业务招待费", "6602", "PL", "DR"},
            {"6602.07", "管理费用-修理费", "6602", "PL", "DR"},
            {"6602.08", "管理费用-检测费", "6602", "PL", "DR"},
            {"6602.99", "管理费用-其他", "6602", "PL", "DR"},
            {"6603", "财务费用", null, "PL", "DR"},
            {"6603.01", "财务费用-利息支出", "6603", "PL", "DR"},
            {"6603.02", "财务费用-手续费", "6603", "PL", "DR"},
            {"6711", "营业外支出", null, "PL", "DR"},
            {"6801", "所得税费用", null, "PL", "DR"},
        };
        int added = 0;
        for (String[] s : seeds) {
            var exists = jdbc.queryForList("SELECT id FROM account_subject WHERE code = ?", s[0]);
            if (exists.isEmpty()) {
                jdbc.update("INSERT INTO account_subject (code, name, parent_code, category, direction, status, opening_balance, create_time) VALUES (?,?,?,?,?,'ENABLED',0,?)",
                        s[0], s[1], s[2], s[3], s[4], System.currentTimeMillis());
                added++;
            }
        }
        if (added > 0) log.info("会计科目种子补录 {} 条", added);
    }

    /**
     * 科目映射种子：业务转凭证默认科目（biz:）+ 现金流量归集（cf:）。
     * {mapKey, subjectCode, remark} —— cf 映射的 subjectCode 列存现金流量项目编码（CF01~CF16）。
     */
    private void ensureMappings() {
        String[][] seeds = {
            // 费用类型 → 费用科目（biz:expense: / biz:income:）
            {"biz:expense:FREIGHT", "6601.01", "运费"},
            {"biz:expense:PACKAGING", "6601.05", "包装费"},
            {"biz:expense:UTILITIES", "6602.04", "水电费"},
            {"biz:expense:OFFICE", "6602.01", "办公费"},
            {"biz:expense:TRAVEL", "6602.03", "差旅费"},
            {"biz:expense:MAINTENANCE", "6602.07", "维修费"},
            {"biz:expense:TESTING", "6602.08", "检测费"},
            {"biz:expense:OTHER", "6602.99", "其他支出"},
            {"biz:income:SCRAP_SALE", "6051", "废料回收"},
            {"biz:income:RENT", "6051", "租金收入"},
            {"biz:income:INTEREST", "6051", "利息收入"},
            {"biz:income:SUBSIDY", "6301", "政府补贴"},
            {"biz:income:OTHER", "6301", "其他收入"},
            // 收款方式 → 资金科目（biz:method:RECEIPT:）
            {"biz:method:RECEIPT:CASH", "1001", "收款-现金"},
            {"biz:method:RECEIPT:BANK", "1002", "收款-银行"},
            {"biz:method:RECEIPT:ACCEPTANCE", "1121", "收款-承兑→应收票据"},
            {"biz:method:RECEIPT:WECHAT", "1012", "收款-微信→其他货币资金"},
            {"biz:method:RECEIPT:OTHER", "1002", "收款-其他→银行"},
            // 付款方式 → 资金科目（biz:method:PAYMENT:）
            {"biz:method:PAYMENT:CASH", "1001", "付款-现金"},
            {"biz:method:PAYMENT:BANK", "1002", "付款-银行"},
            {"biz:method:PAYMENT:ACCEPTANCE", "2201", "付款-承兑→应付票据"},
            {"biz:method:PAYMENT:WECHAT", "1012", "付款-微信→其他货币资金"},
            {"biz:method:PAYMENT:OTHER", "1002", "付款-其他→银行"},
            // 现金流量表归集：现金流入的对方科目（cf:in:）
            {"cf:in:1122", "CF01", "收回应收账款"},
            {"cf:in:1121", "CF01", "应收票据到期/贴现"},
            {"cf:in:6001", "CF01", "直接收现销售"},
            {"cf:in:6051", "CF01", "其他业务直接收现"},
            {"cf:in:2203", "CF01", "预收货款"},
            {"cf:in:1131", "CF03", "收回其他应收款"},
            {"cf:in:6301", "CF03", "营业外收入收现"},
            {"cf:in:2221", "CF02", "税费返还"},
            {"cf:in:2001", "CF14", "取得短期借款"},
            {"cf:in:2501", "CF14", "取得长期借款"},
            {"cf:in:3001", "CF13", "吸收投资"},
            {"cf:in:1501", "CF10", "处置固定资产"},
            {"cf:in:1602", "CF10", "处置固定资产折旧"},
            {"cf:in:1701", "CF10", "处置无形资产"},
            // 现金流量表归集：现金流出的对方科目（cf:out:）
            {"cf:out:1123", "CF04", "预付货款"},
            {"cf:out:2202", "CF04", "支付应付账款"},
            {"cf:out:2201", "CF04", "应付票据到期支付"},
            {"cf:out:1403", "CF04", "直接现购原材料"},
            {"cf:out:1405", "CF04", "直接现购库存商品"},
            {"cf:out:1408", "CF04", "现付委托加工"},
            {"cf:out:1411", "CF04", "现购周转材料"},
            {"cf:out:2211", "CF05", "支付职工薪酬"},
            {"cf:out:5001", "CF05", "支付生产人工"},
            {"cf:out:2221", "CF06", "支付各项税费"},
            {"cf:out:6403", "CF06", "支付税金及附加"},
            {"cf:out:6801", "CF06", "支付所得税"},
            {"cf:out:2001", "CF15", "偿还短期借款"},
            {"cf:out:2501", "CF15", "偿还长期借款"},
            {"cf:out:3105", "CF16", "分配股利"},
            {"cf:out:6603", "CF16", "偿付利息"},
            {"cf:out:1501", "CF11", "购建固定资产"},
            {"cf:out:1701", "CF11", "购建无形资产"},
            {"cf:out:1801", "CF11", "支付长期待摊费用"},
            {"cf:out:6601", "CF07", "支付销售费用"},
            {"cf:out:6602", "CF07", "支付管理费用"},
            {"cf:out:5101", "CF07", "支付制造费用"},
            {"cf:out:1131", "CF07", "借出其他应收款"},
            {"cf:out:6711", "CF07", "营业外支出付现"},
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
        if (added > 0) log.info("科目映射种子补录 {} 条", added);
    }
}
