package com.pengyuan.pims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 质检模板表结构初始化（v5.32：按物料大类区分质检检测内容）
 * 三张表：qc_template（模板）/ qc_template_item（模板检测项）/ quality_inspection_item（质检单检测项快照+实测结果）
 * 种子只在表为空时插入（用户数据至上，绝不覆盖已维护的模板）
 */
@Component
@Order(2)
public class QcTemplateSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QcTemplateSchemaInitializer.class);

    private final JdbcTemplate jdbc;

    public QcTemplateSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qc_template (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name VARCHAR(100) NOT NULL,
                apply_category VARCHAR(10) NOT NULL,
                is_default BOOLEAN DEFAULT 0,
                enabled BOOLEAN DEFAULT 1,
                remark VARCHAR(500),
                created_by VARCHAR(50),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        // v5.65.1 存量库补列（幂等）
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(qc_template)");
            if (cols.stream().noneMatch(c -> "created_by".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE qc_template ADD COLUMN created_by VARCHAR(50)");
            }
        } catch (Exception ignored) { }
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qc_template_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                template_id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                standard VARCHAR(200),
                unit VARCHAR(30),
                method VARCHAR(200),
                sort_order INTEGER DEFAULT 0
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS quality_inspection_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inspection_id BIGINT NOT NULL,
                template_id BIGINT,
                name VARCHAR(100) NOT NULL,
                standard VARCHAR(200),
                unit VARCHAR(30),
                method VARCHAR(200),
                measured_value VARCHAR(200),
                item_result VARCHAR(20),
                sort_order INTEGER DEFAULT 0,
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        seedDefaultTemplates();
    }

    /**
     * 种子：7 套默认模板（材料细分 溶剂S/树脂R/助剂A/颜料P/填料F + 半成品B + 成品C）
     * 检测内容为涂料行业通用出厂/来料检验项（国标方法 + 通用参考值），标准值可上线后在模板管理页调整
     */
    private void seedDefaultTemplates() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM qc_template", Integer.class);
        if (count != null && count > 0) return;

        record Item(String name, String standard, String unit, String method) { }
        record Tpl(String name, String category, List<Item> items) { }

        List<Tpl> templates = List.of(
            new Tpl("溶剂来料检验模板", "S", List.of(
                new Item("外观", "清澈透明液体，无机械杂质及悬浮物", "", "目测法 GB/T 1721"),
                new Item("颜色（铂-钴色号）", "≤30", "号", "GB/T 3143"),
                new Item("密度（20℃）", "按产品标准", "g/cm³", "GB/T 4472"),
                new Item("馏程（沸程）", "按产品标准", "℃", "GB/T 7534"),
                new Item("闪点（闭口杯）", "按产品标准", "℃", "GB/T 261"),
                new Item("水分", "≤0.1", "%", "卡尔·费休法 GB/T 6283"),
                new Item("酸值", "≤0.05", "mgKOH/g", "GB/T 264"),
                new Item("纯度", "≥99.0", "%", "气相色谱法 GB/T 9722")
            )),
            new Tpl("树脂来料检验模板", "R", List.of(
                new Item("外观", "透明均匀液体，无机械杂质、无结晶析出", "", "目测法 GB/T 1721"),
                new Item("颜色（铁钴比色）", "≤8", "号", "GB/T 9281"),
                new Item("固体含量", "按产品标准", "%", "GB/T 1725"),
                new Item("粘度", "按产品标准", "mPa·s", "旋转粘度计法 GB/T 2794"),
                new Item("酸值", "按产品标准", "mgKOH/g", "GB/T 6743"),
                new Item("羟值（羟基树脂）", "按产品标准", "mgKOH/g", "乙酐法"),
                new Item("环氧当量（环氧树脂）", "按产品标准", "g/eq", "GB/T 4612"),
                new Item("细度", "≤20", "μm", "GB/T 1724"),
                new Item("密度", "按产品标准", "g/cm³", "GB/T 4472"),
                new Item("软化点（固体树脂）", "按产品标准", "℃", "环球法 GB/T 12007.6")
            )),
            new Tpl("助剂来料检验模板", "A", List.of(
                new Item("外观", "按产品标准（液体透明/粉末均匀，无异物）", "", "目测"),
                new Item("颜色", "按产品标准", "号", "GB/T 3143"),
                new Item("密度（20℃）", "按产品标准", "g/cm³", "GB/T 4472"),
                new Item("粘度", "按产品标准", "mPa·s", "旋转粘度计法"),
                new Item("固体含量", "按产品标准", "%", "GB/T 1725"),
                new Item("水分", "按产品标准", "%", "卡尔·费休法 GB/T 6283"),
                new Item("pH值（水溶性助剂）", "按产品标准", "", "GB/T 9724"),
                new Item("有效成分含量", "按供货标准", "%", "按产品方法")
            )),
            new Tpl("颜料来料检验模板", "P", List.of(
                new Item("外观", "与标样相近，无结块、无杂质", "", "目测"),
                new Item("颜色（与标样比）", "近似～微", "级", "目视比色法"),
                new Item("着色力", "为标准样的 100±5", "%", "GB/T 13451.2"),
                new Item("遮盖力", "按产品标准", "g/m²", "GB/T 1709"),
                new Item("吸油量", "按产品标准", "g/100g", "GB/T 5211.15"),
                new Item("筛余物（45μm）", "≤0.1", "%", "GB/T 5211.14"),
                new Item("105℃挥发物", "≤0.5", "%", "GB/T 5211.3"),
                new Item("水溶物", "≤0.5", "%", "GB/T 5211.1"),
                new Item("pH值（水悬浮液）", "按产品标准", "", "GB/T 1717"),
                new Item("白度（白色颜料）", "按产品标准", "%", "白度仪法")
            )),
            new Tpl("填料来料检验模板", "F", List.of(
                new Item("外观", "白色（微色）粉末，无结块、无杂质", "", "目测"),
                new Item("白度", "≥90", "%", "白度仪法"),
                new Item("325目筛余物", "≤0.5", "%", "干筛法"),
                new Item("粒径 D50", "按产品标准", "μm", "激光粒度法"),
                new Item("水分（105℃挥发物）", "≤0.5", "%", "GB/T 5211.3"),
                new Item("吸油量", "按产品标准", "g/100g", "GB/T 5211.15"),
                new Item("pH值（水悬浮液）", "按产品标准", "", "GB/T 1717"),
                new Item("主含量（CaCO₃/BaSO₄）", "≥98", "%", "GB/T 19281")
            )),
            new Tpl("半成品（研磨浆）检验模板", "B", List.of(
                new Item("容器中状态", "无结皮、无干结，搅拌后均匀无粗粒", "", "目测"),
                new Item("细度", "≤15（按品种）", "μm", "刮板细度计 GB/T 1724"),
                new Item("粘度", "按产品标准", "KU", "斯托默粘度计 GB/T 9269"),
                new Item("固体含量", "按配方理论值±2", "%", "GB/T 1725"),
                new Item("密度", "按产品标准", "g/cm³", "GB/T 6750"),
                new Item("出料温度", "≤65", "℃", "温度计"),
                new Item("色差 ΔE（调色浆）", "≤0.5", "", "色差仪 GB/T 11186"),
                new Item("pH值", "按产品标准", "", "精密pH试纸 / GB/T 9724")
            )),
            new Tpl("成品漆出厂检验模板", "C", List.of(
                new Item("容器中状态", "无硬块、无沉淀结块，搅拌后均匀", "", "目测"),
                new Item("细度", "≤40（面漆）/ ≤60（底漆）", "μm", "GB/T 1724"),
                new Item("粘度", "按产品标准", "KU", "斯托默粘度计 GB/T 9269"),
                new Item("固体含量", "按产品标准", "%", "GB/T 1725"),
                new Item("密度", "按产品标准", "g/cm³", "GB/T 6750"),
                new Item("干燥时间（表干）", "按产品标准", "h", "GB/T 1728"),
                new Item("漆膜外观与颜色", "漆膜平整，与标准板颜色近似", "", "目视比色 GB/T 9761"),
                new Item("光泽（60°）", "按产品标准", "GU", "GB/T 9754"),
                new Item("附着力（划格法）", "≤1", "级", "GB/T 9286"),
                new Item("铅笔硬度", "按产品标准", "", "GB/T 6739"),
                new Item("耐冲击性", "50kg·cm 无开裂剥落", "kg·cm", "GB/T 1732"),
                new Item("遮盖力/对比率", "按产品标准", "", "GB/T 1709")
            ))
        );

        for (Tpl t : templates) {
            jdbc.update("INSERT INTO qc_template (name, apply_category, is_default, enabled, remark, create_time) VALUES (?, ?, 1, 1, ?, datetime('now'))",
                    t.name(), t.category(), "系统预置默认模板，可在模板管理页调整检测项与标准值");
            Long templateId = jdbc.queryForObject("SELECT MAX(id) FROM qc_template", Long.class);
            int sort = 1;
            for (Item item : t.items()) {
                jdbc.update("INSERT INTO qc_template_item (template_id, name, standard, unit, method, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
                        templateId, item.name(), item.standard(), item.unit(), item.method(), sort++);
            }
        }
        log.info("质检模板：预置 {} 套默认模板（溶剂/树脂/助剂/颜料/填料/半成品/成品）", templates.size());
    }
}
