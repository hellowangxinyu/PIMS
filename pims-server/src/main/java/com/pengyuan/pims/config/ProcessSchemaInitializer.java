package com.pengyuan.pims.config;

import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 工艺路线（标准工艺）表结构初始化（ddl-auto=none 后手动维护）。
 * - 幂等建表 + 去 recipe_type 唯一约束（同类型允许多条路线）+ is_default 列
 * - recipe.process_template_id 补列 + 存量配方回填默认路线
 * - 种子只「从无到有」创建默认路线，绝不覆盖已存在路线（路线是用户维护数据）
 */
@Component
@Order(3)
public class ProcessSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessSchemaInitializer.class);
    private final JdbcTemplate jdbc;
    private final ProcessTemplateRepository templateRepo;
    private final ProcessStageRepository stageRepo;
    private final ProcessStepRepository stepRepo;
    private final ProcessQcItemRepository qcRepo;

    public ProcessSchemaInitializer(JdbcTemplate jdbc, ProcessTemplateRepository templateRepo,
                                    ProcessStageRepository stageRepo, ProcessStepRepository stepRepo,
                                    ProcessQcItemRepository qcRepo) {
        this.jdbc = jdbc;
        this.templateRepo = templateRepo;
        this.stageRepo = stageRepo;
        this.stepRepo = stepRepo;
        this.qcRepo = qcRepo;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_template (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_type VARCHAR(20) NOT NULL UNIQUE,
                name VARCHAR(50) NOT NULL,
                enabled BOOLEAN DEFAULT 1,
                created_by VARCHAR(50),
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        // v5.65.1 存量库补列（幂等）
        try {
            var cols = jdbc.queryForList("PRAGMA table_info(process_template)");
            if (cols.stream().noneMatch(c -> "created_by".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE process_template ADD COLUMN created_by VARCHAR(50)");
            }
        } catch (Exception ignored) { }
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_stage (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                template_id BIGINT NOT NULL,
                stage_no VARCHAR(10),
                stage_name VARCHAR(50) NOT NULL,
                role_hint VARCHAR(50),
                sort_order INTEGER DEFAULT 0
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_step (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                stage_id BIGINT NOT NULL,
                step_code VARCHAR(10),
                description VARCHAR(1000),
                params VARCHAR(200),
                sort_order INTEGER DEFAULT 0
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS process_qc_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                stage_id BIGINT NOT NULL,
                name VARCHAR(50) NOT NULL,
                standard VARCHAR(100),
                test_times INTEGER DEFAULT 1,
                unit VARCHAR(20),
                method VARCHAR(200),
                sort_order INTEGER DEFAULT 0
            )
        """);
        log.info("工艺路线表结构：process_template / process_stage / process_step / process_qc_item 就绪");
        ensureColumn("process_template", "packing_requirement", "VARCHAR(1000)");
        migrateTemplateTable();
        ensureColumn("recipe", "process_template_id", "BIGINT");
        seedDefaultRoutes();
        backfillRecipes();
    }

    /** 幂等补列：表不存在该列时执行 ALTER TABLE ADD COLUMN */
    private void ensureColumn(String table, String column, String type) {
        var cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        boolean exists = cols.stream().anyMatch(m -> column.equals(m.get("name")));
        if (!exists) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("表结构：{} 新增 {} 列", table, column);
        }
    }

    /** 工艺路线改造：去掉 recipe_type 唯一约束 + 加 is_default 列（SQLite 需重建表），幂等 */
    private void migrateTemplateTable() {
        boolean hasUnique = !jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='process_template' AND name LIKE 'sqlite_autoindex%'").isEmpty();
        boolean hasDefaultCol = jdbc.queryForList("PRAGMA table_info(process_template)").stream()
                .anyMatch(m -> "is_default".equals(m.get("name")));
        if (!hasUnique && hasDefaultCol) return;
        jdbc.execute("""
            CREATE TABLE process_template_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_type VARCHAR(20) NOT NULL,
                name VARCHAR(50) NOT NULL,
                packing_requirement VARCHAR(1000),
                is_default BOOLEAN DEFAULT 0,
                enabled BOOLEAN DEFAULT 1,
                create_time TIMESTAMP,
                update_time TIMESTAMP
            )
        """);
        jdbc.update("INSERT INTO process_template_new (id, recipe_type, name, packing_requirement, is_default, enabled, create_time, update_time) "
                + "SELECT id, recipe_type, name, packing_requirement, 1, enabled, create_time, update_time FROM process_template");
        jdbc.execute("DROP TABLE process_template");
        jdbc.execute("ALTER TABLE process_template_new RENAME TO process_template");
        log.info("工艺路线：process_template 已重建（去唯一约束，存量路线标记为默认）");
    }

    /** 每类型没有任何路线时才种子创建默认路线（用户数据至上，绝不覆盖已存在路线） */
    private void seedDefaultRoutes() {
        if (!templateRepo.existsByRecipeType("GRINDING")) {
            ProcessTemplate t = newTemplate("GRINDING", "制浆标准工艺");
            seedGrindingStages(t.id);
            log.info("工艺路线：种子创建制浆默认路线（6 工序）");
        }
        if (!templateRepo.existsByRecipeType("TINTING")) {
            ProcessTemplate t = newTemplate("TINTING", "制漆标准工艺");
            seedTintingStages(t.id);
            log.info("工艺路线：种子创建制漆默认路线（5 工序草稿）");
        }
    }

    private ProcessTemplate newTemplate(String recipeType, String name) {
        ProcessTemplate t = new ProcessTemplate();
        t.recipeType = recipeType;
        t.name = name;
        t.isDefault = true;
        t.enabled = true;
        t.createTime = LocalDateTime.now();
        t.updateTime = t.createTime;
        return templateRepo.save(t);
    }

    /** 存量配方回填：未绑定路线的按类型绑默认路线 */
    private void backfillRecipes() {
        int n = jdbc.update("""
            UPDATE recipe SET process_template_id = (
                SELECT id FROM process_template
                WHERE recipe_type = recipe.recipe_type AND is_default = 1
                LIMIT 1)
            WHERE process_template_id IS NULL
        """);
        if (n > 0) log.info("工艺路线：回填 {} 条存量配方的工艺路线绑定", n);
    }

    /** 制浆默认路线：预混 → 砂磨 → 调色 → 过滤 → 打包留样送检 → 刷缸（用户确认的实际工艺） */
    private void seedGrindingStages(Long t) {
        // 01 预混
        Long s1 = stage(t, "01", "预混", "配料人", 1);
        step(s1, "A", "按配方量准确投入{{1}}，开动搅拌，转速500-600rpm，边搅拌边加入{{2}}、{{3}}，加入完毕后搅拌5min；开始时间______，结束时间______", "500-600rpm、搅拌5min", 1);
        step(s1, "B", "在搅拌状态下投入{{4}}，投料过程中适当提高分散盘高度和分散速度，以粉料不堆积为准", "", 2);
        step(s1, "C", "{{4}}加入完毕后，用铁铲铲去缸壁和搅拌器残余粉料，使用{{5}}清洗缸壁堆积粉料", "", 3);
        step(s1, "D", "以800~1000rpm高速分散30min，开始时间______，结束时间______", "800-1000rpm、30min", 4);
        step(s1, "E", "用部分溶剂清洗搅拌器，洗液回收，专桶专用，标示清晰：刷机液，下次同一类产品再生产时投入使用", "", 5);
        step(s1, "F", "上机研磨", "", 6);

        // 02 砂磨
        Long s2 = stage(t, "02", "砂磨", "砂磨工", 2);
        step(s2, "A", "砂磨方式：研磨时进料流量约2-3圈/秒，研磨出口浆温度报警设置≤65℃", "2-3圈/秒、温度≤65℃", 1);
        step(s2, "B", "砂磨中用环己酮清洗缸内壁，记录用量：①___+②___+③___+④___+⑤___", "", 2);
        qc(s2, "出口温度", "≤65℃", 1, "℃", "", 1);
        qc(s2, "细度", "≤10μm", 5, "μm", "刮板细度计", 2);

        // 03 调色
        Long s3 = stage(t, "03", "调色", "调色工", 3);
        step(s3, "A", "细度合格后转入调色缸，低速搅拌（300-500rpm）下按配方补加色浆/树脂/溶剂，搅拌均匀", "300-500rpm", 1);
        step(s3, "B", "取样对比标准样，测色差；色差合格后按标准调整粘度、固含，搅拌均匀", "", 2);
        qc(s3, "色差", "与标准样一致（ΔE≤0.5）", 1, "", "", 1);
        qc(s3, "粘度", "按产品标准", 1, "", "", 2);
        qc(s3, "固含", "按产品标准", 1, "%", "", 3);

        // 04 过滤
        Long s4 = stage(t, "04", "过滤", "打包工", 4);
        step(s4, "A", "调色合格后用滤网/滤袋过滤，去除粗颗粒、结皮及研磨碎珠，滤后浆料接入洁净包装桶", "", 1);
        step(s4, "B", "过滤过程中观察滤网，破损及时更换；滤渣收集后按废弃物处理", "", 2);
        qc(s4, "滤网目数", "100-200目（按产品标准）", 1, "目", "", 1);

        // 05 打包、留样、送检
        Long s5 = stage(t, "05", "打包、留样、送检", "打包工", 5);
        step(s5, "A", "过滤后浆料上机搅拌（转速500-600rpm）10min，搅拌均匀后称量装桶", "500-600rpm、10min", 1);
        step(s5, "B", "打包过程中，取样300ml左右。贴标签标示：名称、生产日期、磨号", "", 2);
        step(s5, "C", "将样瓶送技术部检测，并最终给出使用范围，标示：合格/不合格，告知仓库使用注意事项", "", 3);
        qc(s5, "技术部判定", "合格/不合格", 1, "", "", 1);

        // 06 刷缸
        Long s6 = stage(t, "06", "刷缸", "配料人", 6);
        step(s6, "A", "打包完毕后，用环己酮清洗拉缸、搅拌器，洗液回收，专桶专用，标示清晰：刷机液，下次同一类产品再生产时投入使用", "", 1);
    }

    /** 制漆默认路线（草稿，上线后由用户按实际工艺修正） */
    private void seedTintingStages(Long t) {
        Long s1 = stage(t, "01", "预混/调漆", "调色工", 1);
        step(s1, "A", "按配方量准确投入{{1}}，开动搅拌（500-600rpm），依次加入{{2}}、{{3}}，搅拌均匀", "500-600rpm", 1);
        step(s1, "B", "在搅拌状态下投入{{4}}，低速分散均匀，铲净缸壁残留", "", 2);

        Long s2 = stage(t, "02", "调色", "调色工", 2);
        step(s2, "A", "取样对比标准样，按色差结果补加色浆微调，搅拌均匀后复测", "", 1);
        qc(s2, "色差", "与标准样一致（ΔE≤0.5）", 1, "", "", 1);

        Long s3 = stage(t, "03", "过滤", "打包工", 3);
        step(s3, "A", "调色合格后用滤网/滤袋过滤，去除粗颗粒、结皮及杂质，滤后漆料接入洁净包装桶", "", 1);
        qc(s3, "滤网目数", "100-200目（按产品标准）", 1, "目", "", 1);

        Long s4 = stage(t, "04", "打包、留样、送检", "打包工", 4);
        step(s4, "A", "过滤后漆料搅拌均匀，称量装桶", "", 1);
        step(s4, "B", "打包过程中取样300ml左右，贴标签标示：名称、生产日期、批号", "", 2);
        step(s4, "C", "将样瓶送技术部检测，标示：合格/不合格，告知仓库使用注意事项", "", 3);
        qc(s4, "技术部判定", "合格/不合格", 1, "", "", 1);

        Long s5 = stage(t, "05", "刷缸", "配料人", 5);
        step(s5, "A", "打包完毕后，用溶剂清洗拉缸、搅拌器，洗液回收，专桶专用，标示清晰", "", 1);
    }

    private Long stage(Long templateId, String no, String name, String role, int order) {
        ProcessStage s = new ProcessStage();
        s.templateId = templateId;
        s.stageNo = no;
        s.stageName = name;
        s.roleHint = role;
        s.sortOrder = order;
        stageRepo.save(s);
        return s.id;
    }

    private void step(Long stageId, String code, String desc, String params, int order) {
        ProcessStep st = new ProcessStep();
        st.stageId = stageId;
        st.stepCode = code;
        st.description = desc;
        st.params = params;
        st.sortOrder = order;
        stepRepo.save(st);
    }

    private void qc(Long stageId, String name, String standard, int times, String unit, String method, int order) {
        ProcessQcItem q = new ProcessQcItem();
        q.stageId = stageId;
        q.name = name;
        q.standard = standard;
        q.testTimes = times;
        q.unit = unit;
        q.method = method;
        q.sortOrder = order;
        qcRepo.save(q);
    }
}
