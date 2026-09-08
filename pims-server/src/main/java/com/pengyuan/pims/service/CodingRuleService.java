package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.CodingRule;
import com.pengyuan.pims.repository.CodingRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CodingRuleService {

    private final CodingRuleRepository repo;

    public List<CodingRule> listAll() { return repo.findByEnabledTrueOrderByCategoryCodeAscSubCategoryCodeAsc(); }

    @Transactional
    public CodingRule create(CodingRule rule) { return repo.save(rule); }

    @Transactional
    public CodingRule update(Long id, CodingRule rule) {
        CodingRule exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("编码规则不存在"));
        exist.category = rule.category;
        exist.categoryCode = rule.categoryCode;
        exist.subCategory = rule.subCategory;
        exist.subCategoryCode = rule.subCategoryCode;
        exist.numberStart = rule.numberStart;
        exist.updateTime = java.time.LocalDateTime.now();
        return repo.save(exist);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    /**
     * 根据小类代码自动生成物料编码。
     * v5.40 格式：小类码(2) + 序号(4) = 6 位，小类码首字母恒为大类字母（字母只区分类别）。
     * v5.42 防投错料（用户定稿口径）：**数字 0001-9999 全局连续、单次使用**——AC0001 之后无论什么类别
     * 都是 0002，任何两个物料的数字都不相同，工人只看数字也绝不拿错料；无跳号（容量全 9999）。
     * 全局游标 = 全部规则行 currentSeq 最大值（每次发放记录在该小类行上），writeQueue 内调用并发安全。
     */
    @Transactional
    public String generateCode(String subCategoryCode) {
        if (subCategoryCode == null || subCategoryCode.isBlank()) {
            throw new IllegalArgumentException("小类代码不能为空");
        }
        String sub = subCategoryCode.trim().toUpperCase();
        if (!sub.matches("[A-Z]{2}")) throw new IllegalArgumentException("小类代码须为两位字母: " + subCategoryCode);
        return doGenerate(sub);
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.pengyuan.pims.common.WriteQueue writeQueue;

    public CodingRuleService(CodingRuleRepository repo, org.springframework.jdbc.core.JdbcTemplate jdbc,
                             com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.writeQueue = writeQueue;
    }

    private String doGenerate(String sub) {
        validateNoConfusing(sub);
        CodingRule rule = repo.findBySubCategoryCodeAndEnabledTrue(sub)
                .orElseThrow(() -> new IllegalArgumentException("未找到小类 \"" + sub + "\" 的编码规则"));
        // v5.42：优先复用回收池里的释放码（删除无引用物料时回收，取最小保持整体紧凑）
        Integer released = null;
        try {
            var pool = jdbc.queryForList("SELECT seq FROM released_code_seq ORDER BY seq LIMIT 1");
            if (!pool.isEmpty()) released = ((Number) pool.get(0).get("seq")).intValue();
        } catch (Exception ignored) { }
        int next;
        if (released != null) {
            jdbc.update("DELETE FROM released_code_seq WHERE seq = ?", released);
            next = released;
        } else {
            int globalMax = repo.findAll().stream()
                    .mapToInt(r -> r.currentSeq == null ? 0 : r.currentSeq).max().orElse(0);
            next = globalMax + 1;
            if (next > 9999) {
                throw new IllegalArgumentException("全局编码序号已达上限 9999，请联系管理员处理");
            }
        }
        rule.currentSeq = Math.max(rule.currentSeq == null ? 0 : rule.currentSeq, next);
        rule.updateTime = java.time.LocalDateTime.now();
        repo.save(rule);
        return String.format("%s%04d", sub, next);
    }

    // ==================== v5.65 半成品/成品新编码体系 ====================
    // 参照大厂涂料产品编码（产品线码+属性段+独立流水），与原料 6 位体系彻底分开；历史编码零变动。
    // 易混字符约束（用户定稿）：新码字母段禁用 I/L/O（防与数字 1/0 形近）；流水数字段 0/1 正常（无字母对照物不混淆）。

    /** 主材 → 编码字母位（CZ聚酯/CF氟碳/CE环氧/CA丙烯酸 → Z/F/E/A，均无 I/L/O） */
    private static final java.util.Map<String, String> MAIN_LETTER = java.util.Map.of(
            // v5.86 Z 与 2 易混禁用：主材 CZ 字母位 Z->T（历史 Z 位编码不动，仅新取号换）
            "CZ", "T", "CF", "F", "CE", "E", "CA", "A");
    /** 色系 → 编码字母位（BK黑/WH白/BU蓝/GN绿/GY灰/RD红/YW黄 → K/H/U/N/Y/R/W，均无 I/L/O） */
    private static final java.util.Map<String, String> COLOR_LETTER = java.util.Map.of(
            "BK", "K", "WH", "H", "BU", "U", "GN", "N", "GY", "Y", "RD", "R", "YW", "W");
    /** 含 I/L/O 的历史小类 → 新码前缀替换（蓝浆 BL→BU；历史 BL 物料不动，仅新增取号换前缀） */
    private static final java.util.Map<String, String> SUB_PREFIX_FIX = java.util.Map.of("BL", "BU");
    private static final String CONFUSING = "ILOZ";

    private String mainLetter(String mainMaterial, String what) {
        if (mainMaterial == null || mainMaterial.isBlank())
            throw new IllegalArgumentException(what + "编码需要选择主材（聚酯/氟碳/环氧/丙烯酸）");
        String letter = MAIN_LETTER.get(mainMaterial.trim().toUpperCase());
        if (letter == null) throw new IllegalArgumentException("无法识别的主材: " + mainMaterial);
        return letter;
    }

    /**
     * 半成品（色浆）取号：色浆小类(2) + 主材(1) + 流水(5) = 8 位，如 BWFT00010 = 白浆/氟碳系。
     * v5.89 起序号 5 位全局池（v5.65 原为 4 位 7 位码，扩位后此注释同步修正）。
     */
    @Transactional
    public String generateSemiCode(String subCategoryCode, String mainMaterial) {
        if (subCategoryCode == null || !subCategoryCode.trim().toUpperCase().matches("[A-Z]{2}"))
            throw new IllegalArgumentException("小类代码须为两位字母: " + subCategoryCode);
        String sub = subCategoryCode.trim().toUpperCase();
        repo.findBySubCategoryCodeAndEnabledTrue(sub)
                .orElseThrow(() -> new IllegalArgumentException("未找到小类 \"" + sub + "\" 的编码规则"));
        String prefix = SUB_PREFIX_FIX.getOrDefault(sub, sub) + mainLetter(mainMaterial, "半成品");
        return prefix + seqFor("B", 4);
    }

    /**
     * 成品取号：漆型(2) + 主材(1) + 色系(1) + 流水(5) = 9 位，如 CWTH00010 = 面漆/聚酯/白。
     * v5.89 起序号 5 位全局池（v5.65 原为 4 位 8 位码，扩位后此注释同步修正）。
     */
    @Transactional
    public String generateProductCode(String subCategoryCode, String mainMaterial, String colorSeries) {
        if (subCategoryCode == null || !subCategoryCode.trim().toUpperCase().matches("[A-Z]{2}"))
            throw new IllegalArgumentException("小类代码须为两位字母: " + subCategoryCode);
        String sub = subCategoryCode.trim().toUpperCase();
        repo.findBySubCategoryCodeAndEnabledTrue(sub)
                .orElseThrow(() -> new IllegalArgumentException("未找到小类 \"" + sub + "\" 的编码规则"));
        if (colorSeries == null || colorSeries.isBlank())
            throw new IllegalArgumentException("成品编码需要选择色系（黑/白/蓝/绿/灰/红/黄）");
        String colorLetter = COLOR_LETTER.get(colorSeries.trim().toUpperCase());
        if (colorLetter == null) throw new IllegalArgumentException("无法识别的色系: " + colorSeries);
        String prefix = SUB_PREFIX_FIX.getOrDefault(sub, sub) + mainLetter(mainMaterial, "成品") + colorLetter;
        return prefix + seqFor("C", 5);
    }

    /**
     * v5.86 数字只用一次：半成品/成品各自一套全局流水（不再按前缀分组）。prefix 传首字母 B/C；
     * 新码 7/8 位数字段从第 4/5 位起，历史 6 位码从第 3 位起——两条 MAX 合并全局池防重。
     */
    private String seqFor(String prefix, int seqStartIndex) {
        return writeQueue.executeTx(() -> {
            Integer maxNew = jdbc.queryForObject(
                    "SELECT MAX(CAST(SUBSTR(code, " + seqStartIndex + ") AS INTEGER)) FROM material WHERE code LIKE ? AND LENGTH(code) > 6",
                    Integer.class, prefix + "%");
            Integer maxOld = jdbc.queryForObject(
                    "SELECT MAX(CAST(SUBSTR(code, 3) AS INTEGER)) FROM material WHERE code LIKE ? AND LENGTH(code) = 6",
                    Integer.class, prefix + "%");
            Integer max = maxNew == null ? maxOld : (maxOld == null ? maxNew : Math.max(maxNew, maxOld));
            int next = (max == null ? 0 : max) + 1;
            // v5.84 防错序号：跳过含易错数字模式的号段——三连同号(000/111/…)、三连递增(012/123/…)、三连递减(987/…)
            // v5.89 序号扩 5 位（实际物料过万，4 位不够）：B/C 各自全局池上限 99999（扣易错约 6.6 万可用）
            while (next <= 99999 && isConfusingSeq(String.format("%05d", next))) next++;
            if (next > 99999) throw new IllegalArgumentException("前缀 " + prefix + " 序号已达上限 99999");
            return String.format("%05d", next);
        });
    }

    /**
     * v5.92 手工改码完整规则校验（有 material:code-edit 权限也必须过）：
     * ① 小类码须在编码规则表注册；② B 主材位/C 主材位须在映射集（T聚酯/F氟碳/E环氧/A丙烯酸）、C 色系位须合法（KHUNYRW）；
     * ③ 序号不得含三连同号/连增/连减易错段；④ 字母段禁 I/L/O/Z（validateCodeFormat 已查，此处兜底）。
     */
    public void validateCustomCode(String code, String category) {
        int len = "C".equals(category) ? 9 : ("B".equals(category) ? 8 : 6);
        int seqLen = len == 6 ? 4 : 5;
        String sub = code.substring(0, 2);
        String seq = code.substring(len - seqLen);
        // 小类须注册
        if (!repo.findBySubCategoryCodeAndEnabledTrue(sub).isPresent())
            throw new IllegalArgumentException("小类码 " + sub + " 未在编码规则中注册，不允许使用");
        // B/C 属性位校验
        if ("B".equals(category) && !MAIN_LETTER.containsValue(code.substring(2, 3)))
            throw new IllegalArgumentException("主材位 " + code.charAt(2) + " 非法（须为 T聚酯/F氟碳/E环氧/A丙烯酸）");
        if ("C".equals(category)) {
            if (!MAIN_LETTER.containsValue(code.substring(2, 3)))
                throw new IllegalArgumentException("主材位 " + code.charAt(2) + " 非法（须为 T/F/E/A）");
            if (!COLOR_LETTER.containsValue(code.substring(3, 4)))
                throw new IllegalArgumentException("色系位 " + code.charAt(3) + " 非法（须为 K黑/H白/U蓝/N绿/Y灰/R红/W黄）");
        }
        // 序号易错段
        if (isConfusingSeq(seq))
            throw new IllegalArgumentException("序号 " + seq + " 含三连同号/连增/连减易错段，不符合编码规则");
        // 字母段易混字符
        for (char c : code.substring(0, len - seqLen).toCharArray()) {
            if (CONFUSING.indexOf(c) >= 0)
                throw new IllegalArgumentException("字母段不能使用易混字符 " + c + "（I/L/O/Z）");
        }
    }

    /** v5.84 四位序号含三连同号/三连递增/三连递减即视为易错（如 0123、1123、0987） */
    private boolean isConfusingSeq(String seq) {
        for (int i = 0; i + 2 < seq.length(); i++) {
            int a = seq.charAt(i) - '0', b = seq.charAt(i + 1) - '0', c = seq.charAt(i + 2) - '0';
            if ((a == b && b == c) || (b == a + 1 && c == b + 1) || (b == a - 1 && c == b - 1)) return true;
        }
        return false;
    }

    /** 新码字母段易混字符校验（小类码含 I/L/O 拒绝自动取号，防未来新建小类踩雷；历史小类均不含） */
    private void validateNoConfusing(String sub) {
        for (char c : sub.toCharArray()) {
            if (CONFUSING.indexOf(c) >= 0)
                throw new IllegalArgumentException("小类码 " + sub + " 含易混字符 " + c
                        + "（与数字 1/0 形近），请更换小类码后再自动取码");
        }
    }
}
