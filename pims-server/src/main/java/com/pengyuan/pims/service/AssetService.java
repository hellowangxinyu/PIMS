package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Asset;
import com.pengyuan.pims.entity.AssetDepreciation;
import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.entity.VoucherEntry;
import com.pengyuan.pims.repository.AccountPeriodRepository;
import com.pengyuan.pims.repository.AccountSubjectRepository;
import com.pengyuan.pims.repository.AssetDepreciationRepository;
import com.pengyuan.pims.repository.AssetRepository;
import com.pengyuan.pims.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 固定资产（v5.62）：卡片 + 平均年限法月度折旧 + 计提凭证
 * 计提规则（中国准则）：当月增加当月不提（购入次月起提）、当月减少当月照提（报废当月仍计提）；
 * 应提总额 = 原值×(1−残值率)，最后一月提尾差。
 */
@Service
public class AssetService {

    private final AssetRepository repo;
    private final AssetDepreciationRepository depRepo;
    private final AccountPeriodRepository periodRepo;
    private final AccountSubjectRepository subjectRepo;
    private final AccountSubjectService subjectService;
    private final VoucherService voucherService;
    private final VoucherRepository voucherRepo;
    private final WriteQueue writeQueue;

    public AssetService(AssetRepository repo, AssetDepreciationRepository depRepo,
                        AccountPeriodRepository periodRepo, AccountSubjectRepository subjectRepo,
                        AccountSubjectService subjectService, VoucherService voucherService,
                        VoucherRepository voucherRepo, WriteQueue writeQueue) {
        this.repo = repo;
        this.depRepo = depRepo;
        this.periodRepo = periodRepo;
        this.subjectRepo = subjectRepo;
        this.subjectService = subjectService;
        this.voucherService = voucherService;
        this.voucherRepo = voucherRepo;
        this.writeQueue = writeQueue;
    }

    /** 卡片列表 + 每卡累计已提/净值（批量预取禁 N+1） */
    public List<Map<String, Object>> list() {
        List<Asset> assets = repo.findAllByOrderByIdDesc();
        Map<Long, BigDecimal> accumulated = new HashMap<>();
        for (AssetDepreciation d : depRepo.findAll()) {
            accumulated.merge(d.assetId, d.amount, BigDecimal::add);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset a : assets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.id);
            row.put("docNo", a.docNo);
            row.put("name", a.name);
            row.put("category", a.category);
            row.put("purchaseDate", a.purchaseDate);
            row.put("originalValue", a.originalValue);
            row.put("usefulLifeMonths", a.usefulLifeMonths);
            row.put("residualRate", a.residualRate);
            row.put("expenseSubject", a.expenseSubject);
            row.put("expenseSubjectName", subjectName(a.expenseSubject));
            row.put("location", a.location);
            row.put("keeper", a.keeper);
            row.put("status", a.status);
            row.put("scrapDate", a.scrapDate);
            row.put("remark", a.remark);
            row.put("monthlyDep", monthlyDep(a));
            BigDecimal acc = accumulated.getOrDefault(a.id, BigDecimal.ZERO);
            row.put("accumulatedDep", acc);
            row.put("netValue", a.originalValue.subtract(acc));
            result.add(row);
        }
        return result;
    }

    @Transactional
    public Asset create(Asset a) {
        validate(a);
        return writeQueue.execute(() -> {
            Integer maxSeq = repo.findMaxSeq("FA-" + LocalDate.now().toString().replace("-", "") + "-%");
            a.docNo = String.format("FA-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            return repo.save(a);
        });
    }

    @Transactional
    public Asset update(Long id, Asset in) {
        Asset a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("资产卡片不存在"));
        validate(in);
        a.name = in.name;
        a.category = in.category;
        a.purchaseDate = in.purchaseDate;
        a.originalValue = in.originalValue;
        a.usefulLifeMonths = in.usefulLifeMonths;
        a.residualRate = in.residualRate;
        a.expenseSubject = in.expenseSubject;
        a.location = in.location;
        a.keeper = in.keeper;
        a.remark = in.remark;
        a.updateTime = LocalDateTime.now();
        return repo.save(a);
    }

    /** 报废：记 scrapDate（报废当月仍计提折旧，次月起停） */
    @Transactional
    public Asset scrap(Long id, LocalDate scrapDate) {
        Asset a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("资产卡片不存在"));
        if ("SCRAPPED".equals(a.status)) throw new IllegalArgumentException("资产已报废");
        a.status = "SCRAPPED";
        a.scrapDate = scrapDate == null ? LocalDate.now() : scrapDate;
        a.updateTime = LocalDateTime.now();
        return repo.save(a);
    }

    public List<AssetDepreciation> depRecords(String period) {
        return depRepo.findByPeriodOrderByIdAsc(period);
    }

    public List<AssetDepreciation> history(Long assetId) {
        return depRepo.findByAssetIdOrderByPeriodAsc(assetId);
    }

    /**
     * 月度计提：逐张算折旧（购入次月起、报废当月照提、提满只提尾差、已提过跳过）
     * → 按 expense_subject 汇总生成计提凭证（借费用科目，贷累计折旧，source=DEPRECIATION 防重）
     * → 凭证落库成功后回写折旧记录（关联 voucherId）
     */
    // v6.1.2（新3）：整体锁内包事务（executeTx）——查重、#N 选号、凭证、折旧记录同锁同事务，
    // 并发同期计提不再双过查重；不再用外层 @Transactional（等价旧时序）
    public Map<String, Object> depreciate(String period) {
        checkPeriod(period);
        checkPeriodOpen(period);
        String defaultSubject = mapped("biz:depreciation:default", "6602.05");

        return writeQueue.executeTx(() -> {
        // v6.1.3：先处理当期草稿凭证——草稿=期间未结账、金额未生效，删除草稿时连同该期全部折旧记录
        // 一起清掉走全量重算（此前只删凭证留下旧记录：旧记录 voucherId 悬空成孤儿、重算只按增量汇总，
        // 凭证金额缺旧部分；无新增资产时更在前面就抛"无可计提"，重算分支成死代码）
        String refDoc = period;
        for (int i = 2; i < 100; i++) {
            var exist = voucherRepo.findBySourceAndRefDocNo("DEPRECIATION", refDoc);
            if (exist.isEmpty()) break;
            if ("DRAFT".equals(exist.get().status)) {
                voucherService.delete(exist.get().id);   // 锁重入；删草稿后沿用原 refDocNo 重新生成
                // v6.1.5（高B）：只清该草稿关联的折旧记录——period 本号草稿删除后 stale 清理+existsByPeriod 跳过逻辑
                // 会全量重建本批；period#N 补提草稿删除时已 POSTED 的基准记录不受影响（原按整期清会翻倍计提）
                depRepo.deleteByVoucherId(exist.get().id);
                break;
            }
            refDoc = period + "#" + i;   // 已记账 → 后续生成"补提"凭证（增量）
        }

        List<AssetDepreciation> newRecords = new ArrayList<>();
        // v6.1：总账删凭证不联动折旧记录——先清理「折旧记录还在、凭证已不存在」的孤儿，否则该期间永远提示已计提、无法重提
        List<AssetDepreciation> stale = new ArrayList<>();
        for (AssetDepreciation d : depRepo.findByPeriodOrderByIdAsc(period)) {
            if (d.voucherId == null || !voucherRepo.existsById(d.voucherId)) stale.add(d);
        }
        if (!stale.isEmpty()) depRepo.deleteAll(stale);
        for (Asset a : repo.findAllByOrderByIdAsc()) {
            if (!shouldDepreciate(a, period)) continue;
            if (depRepo.existsByPeriodAndAssetId(period, a.id)) continue;
            BigDecimal amount = depAmount(a, period);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;   // 已提满
            AssetDepreciation d = new AssetDepreciation();
            d.period = period;
            d.assetId = a.id;
            d.assetName = a.name;
            d.expenseSubject = a.expenseSubject == null || a.expenseSubject.isBlank() ? defaultSubject : a.expenseSubject;
            d.amount = amount;
            newRecords.add(d);
        }
        if (newRecords.isEmpty()) throw new IllegalArgumentException(period + " 无可计提的折旧（均已计提/提满/未到计提月）");

        // 按费用科目汇总生成计提凭证（voucherService.create 内含期间校验 + source/refDocNo 查重）
        Map<String, BigDecimal> bySubject = new LinkedHashMap<>();
        for (AssetDepreciation d : newRecords) bySubject.merge(d.expenseSubject, d.amount, BigDecimal::add);
        Voucher v = new Voucher();
        v.voucherDate = periodEnd(period);
        v.source = "DEPRECIATION";
        v.refDocNo = refDoc;
        v.remark = period + " 固定资产折旧计提" + (refDoc.equals(period) ? "" : "（补提）");
        List<VoucherEntry> entries = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> en : bySubject.entrySet()) {
            VoucherEntry e = new VoucherEntry();
            e.subjectCode = en.getKey();
            e.subjectName = subjectName(en.getKey());
            e.digest = "折旧 " + period;
            e.debit = en.getValue();
            e.credit = BigDecimal.ZERO;
            entries.add(e);
        }
        VoucherEntry accu = new VoucherEntry();
        accu.subjectCode = mapped("biz:depreciation:accu", "1602");
        accu.subjectName = subjectName(accu.subjectCode);
        accu.digest = "累计折旧 " + period;
        accu.credit = newRecords.stream().map(d -> d.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        entries.add(accu);
        v.entries = entries;
        Voucher saved = voucherService.create(v);

        for (AssetDepreciation d : newRecords) {
            d.voucherId = saved.id;
            depRepo.save(d);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("voucher", saved);
        result.put("count", newRecords.size());
        result.put("total", accu.credit);
        return result;
        });
    }

    // ===== 内部 =====

    /** 计提月条件：购入次月起提（购入期 < period），报废当月照提、次月停（scrap 期 >= period） */
    private boolean shouldDepreciate(Asset a, String period) {
        if (a.purchaseDate != null && a.purchaseDate.toString().substring(0, 7).compareTo(period) >= 0) return false;
        if (a.scrapDate != null && a.scrapDate.toString().substring(0, 7).compareTo(period) < 0) return false;
        return true;
    }

    /** 本期折旧额 = min(月折旧, 剩余应提)；月末汇总前逐卡判断（剩余 = 应提总额 − 历史累计） */
    private BigDecimal depAmount(Asset a, String period) {
        BigDecimal total = totalDepreciable(a);
        BigDecimal accumulated = BigDecimal.ZERO;
        for (AssetDepreciation d : depRepo.findByAssetIdOrderByPeriodAsc(a.id)) {
            accumulated = accumulated.add(d.amount);
        }
        BigDecimal remaining = total.subtract(accumulated);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal monthly = monthlyDep(a);
        return monthly.min(remaining);
    }

    /** 月折旧 = 原值×(1−残值率/100)/年限（2 位，HALF_UP） */
    public BigDecimal monthlyDep(Asset a) {
        BigDecimal rate = a.residualRate == null ? new BigDecimal("5") : a.residualRate;
        BigDecimal net = a.originalValue.multiply(BigDecimal.ONE.subtract(rate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        return net.divide(new BigDecimal(a.usefulLifeMonths), 2, RoundingMode.HALF_UP);
    }

    /** 应提总额 = 原值×(1−残值率/100)，2 位 */
    public BigDecimal totalDepreciable(Asset a) {
        BigDecimal rate = a.residualRate == null ? new BigDecimal("5") : a.residualRate;
        return a.originalValue.multiply(BigDecimal.ONE.subtract(rate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validate(Asset a) {
        if (a.name == null || a.name.isBlank()) throw new IllegalArgumentException("资产名称不能为空");
        if (a.category == null || a.category.isBlank()) throw new IllegalArgumentException("资产类别不能为空");
        if (a.originalValue == null || a.originalValue.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("原值必须大于 0");
        if (a.usefulLifeMonths == null || a.usefulLifeMonths <= 0) throw new IllegalArgumentException("使用年限（月）必须大于 0");
        String subject = a.expenseSubject == null || a.expenseSubject.isBlank() ? "6602.05" : a.expenseSubject;
        a.expenseSubject = subject;
        subjectRepo.findByCode(subject).orElseThrow(() -> new IllegalArgumentException("折旧费用科目 " + subject + " 不存在"));
    }

    private void checkPeriodOpen(String period) {
        if (periodRepo.findByPeriod(period).filter(p -> Boolean.TRUE.equals(p.closed)).isPresent()) {
            throw new IllegalArgumentException(period + " 已结账，不能计提折旧（如需调整请先反结账）");
        }
    }

    private String mapped(String key, String fallback) {
        String code = subjectService.mappedSubject(key);
        return code != null ? code : fallback;
    }

    private String subjectName(String code) {
        return subjectRepo.findByCode(code).map(s -> s.name).orElse(code);
    }

    private LocalDate periodEnd(String period) {
        return LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5, 7)), 1)
                .plusMonths(1).minusDays(1);
    }

    private void checkPeriod(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException("期间格式应为 YYYY-MM");
    }
}
