package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.AccountMapping;
import com.pengyuan.pims.entity.AccountSubject;
import com.pengyuan.pims.repository.AccountMappingRepository;
import com.pengyuan.pims.repository.AccountSubjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 会计科目（v5.61 总账体系）：两级科目维护 + 期初建账 + 业务转凭证默认科目映射 */
@Service
public class AccountSubjectService {

    private static final Set<String> CATEGORIES = Set.of("ASSET", "LIABILITY", "EQUITY", "COST", "PL");
    private static final Set<String> DIRECTIONS = Set.of("DR", "CR");

    private final AccountSubjectRepository repo;
    private final AccountMappingRepository mappingRepo;
    private final WriteQueue writeQueue;
    private final JdbcTemplate jdbc;

    public AccountSubjectService(AccountSubjectRepository repo, AccountMappingRepository mappingRepo, WriteQueue writeQueue, JdbcTemplate jdbc) {
        this.repo = repo;
        this.mappingRepo = mappingRepo;
        this.writeQueue = writeQueue;
        this.jdbc = jdbc;
    }

    public List<AccountSubject> list() { return repo.findAllByOrderByCodeAsc(); }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public AccountSubject create(AccountSubject s) {
        validate(s);
        if (s.code == null || !s.code.matches("\\d{4}(\\.\\d{2})?")) {
            throw new IllegalArgumentException("科目编码格式应为 4 位数字，明细为 4 位.2 位（如 6602.01）");
        }
        return writeQueue.executeTx(() -> {
            if (repo.findByCode(s.code).isPresent()) throw new IllegalArgumentException("科目编码 " + s.code + " 已存在");
            AccountSubject saved = repo.save(s);
            saved.createTime = LocalDateTime.now();
            return saved;
        });
    }

    /** 科目编码/类别/方向不可改（编码是分录与报表的锚点）；只允许改名和停用 */
    // v9.2（P2-1 审计）：写路径收口 executeTx（原裸 @Transactional 绕过全局写锁）
    public AccountSubject update(Long id, AccountSubject in) {
        return writeQueue.executeTx(() -> {
            AccountSubject s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("科目不存在"));
            if (in.name == null || in.name.isBlank()) throw new IllegalArgumentException("科目名称不能为空");
            s.name = in.name;
            s.updateTime = LocalDateTime.now();
            return repo.save(s);
        });
    }

    public AccountSubject toggle(Long id) {
        return writeQueue.executeTx(() -> {
            AccountSubject s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("科目不存在"));
            s.status = "ENABLED".equals(s.status) ? "DISABLED" : "ENABLED";
            s.updateTime = LocalDateTime.now();
            return repo.save(s);
        });
    }

    /**
     * 期初建账：全量覆盖写各科目期初余额。
     * 校验：损益类科目期初必须为 0（上年末损益已结转）；借方期初合计 = 贷方期初合计。
     * 传 List<{code, openingBalance, openingDirection}>，不在列表中的科目期初清零。
     */
    // v8.6（N3）：去 @Transactional——方法内 executeTx 已锁内包事务，外层注解=旧时序（先开事务后抢锁）
    public Map<String, Object> saveOpeningBalance(List<Map<String, Object>> itemsInput) {
        // v5.70 期初锁定：由凭证记账间接保障（有已记账凭证时修改期初会导致余额表不平，科目余额表可发现）
        // v6.1 收紧：存在已记账凭证直接拒绝——期初一旦启用（有 POSTED 凭证）即锁定，防止改期初导致试算平衡被破坏
        Integer posted = jdbc.queryForObject("SELECT COUNT(*) FROM voucher WHERE status = 'POSTED'", Integer.class);
        if (posted != null && posted > 0) {
            throw new IllegalArgumentException("已存在记账凭证，期初余额已锁定不可修改（调整请通过期初调整凭证）");
        }
        final List<Map<String, Object>> items = itemsInput == null ? List.of() : itemsInput;
        List<AccountSubject> all = repo.findAll();
        java.util.Map<String, AccountSubject> byCode = all.stream().collect(Collectors.toMap(x -> x.code, x -> x));

        BigDecimal drSum = BigDecimal.ZERO, crSum = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            String code = String.valueOf(it.get("code"));
            AccountSubject s = byCode.get(code);
            if (s == null) throw new IllegalArgumentException("科目 " + code + " 不存在");
            BigDecimal bal = toBd(it.get("openingBalance"));
            String dir = it.get("openingDirection") == null ? null : String.valueOf(it.get("openingDirection"));
            if (bal != null && bal.compareTo(BigDecimal.ZERO) > 0) {
                if ("PL".equals(s.category)) throw new IllegalArgumentException("损益类科目 " + code + " " + s.name + " 不允许有期初余额（上年末已结转）");
                if (dir == null || !DIRECTIONS.contains(dir)) dir = s.direction;
                if ("DR".equals(dir)) drSum = drSum.add(bal); else crSum = crSum.add(bal);
            }
        }
        if (drSum.compareTo(crSum) != 0) {
            throw new IllegalArgumentException("期初余额不平衡：借方合计 " + drSum + " ≠ 贷方合计 " + crSum);
        }

        writeQueue.executeTx(() -> {
            for (AccountSubject s : all) s.openingBalance = BigDecimal.ZERO;
            for (Map<String, Object> it : items) {
                AccountSubject s = byCode.get(String.valueOf(it.get("code")));
                BigDecimal bal = toBd(it.get("openingBalance"));
                String dir = it.get("openingDirection") == null ? null : String.valueOf(it.get("openingDirection"));
                if (bal != null && bal.compareTo(BigDecimal.ZERO) > 0) {
                    if (dir == null || !DIRECTIONS.contains(dir)) dir = s.direction;
                    s.openingBalance = bal;
                    s.openingDirection = dir;
                } else {
                    s.openingDirection = null;
                }
                s.updateTime = LocalDateTime.now();
            }
            repo.saveAll(all);
        });
        return Map.of("drSum", drSum, "crSum", crSum);
    }

    /** 业务映射（biz: 前缀，页面维护） */
    public List<AccountMapping> mappings() {
        return mappingRepo.findAllByOrderByMapKeyAsc().stream().filter(m -> m.mapKey.startsWith("biz:")).toList();
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public void saveMapping(String mapKey, String subjectCode) {
        if (mapKey == null || !mapKey.startsWith("biz:")) throw new IllegalArgumentException("只允许维护 biz: 业务映射");
        if (subjectCode != null && !subjectCode.isBlank()) {
            repo.findByCode(subjectCode).orElseThrow(() -> new IllegalArgumentException("科目 " + subjectCode + " 不存在"));
        }
        writeQueue.executeTx(() -> {
            Optional<AccountMapping> existing = mappingRepo.findByMapKey(mapKey);
            if (subjectCode == null || subjectCode.isBlank()) {
                existing.ifPresent(mappingRepo::delete);   // 清空即删除映射，转凭证时人工选科目
                return;
            }
            AccountMapping m = existing.orElseGet(AccountMapping::new);
            m.mapKey = mapKey;
            m.subjectCode = subjectCode;
            m.updateTime = LocalDateTime.now();
            mappingRepo.save(m);
        });
    }

    /** 取映射指向的科目编码（VoucherService 业务转凭证用） */
    public String mappedSubject(String mapKey) {
        return mappingRepo.findByMapKey(mapKey).map(m -> m.subjectCode).orElse(null);
    }

    private void validate(AccountSubject s) {
        if (s.name == null || s.name.isBlank()) throw new IllegalArgumentException("科目名称不能为空");
        if (!CATEGORIES.contains(s.category)) throw new IllegalArgumentException("科目类别必须是 ASSET/LIABILITY/EQUITY/COST/PL");
        if (!DIRECTIONS.contains(s.direction)) throw new IllegalArgumentException("余额方向必须是 DR(借) 或 CR(贷)");
        if (s.parentCode != null && !s.parentCode.isBlank()) {
            AccountSubject parent = repo.findByCode(s.parentCode)
                    .orElseThrow(() -> new IllegalArgumentException("上级科目 " + s.parentCode + " 不存在"));
            if (parent.parentCode != null && !parent.parentCode.isBlank()) {
                throw new IllegalArgumentException("最多两级科目：明细科目的上级必须是一级科目");
            }
            // 明细科目类别/方向必须与父级一致（报表归集按类别汇总）
            s.category = parent.category;
            s.direction = parent.direction;
        } else {
            s.parentCode = null;
        }
    }

    /** sqlite-jdbc 聚合/参数可能是 Integer/Long/Double，统一转 BigDecimal */
    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(String.valueOf(v));
    }
}
