package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.SalaryItem;
import com.pengyuan.pims.entity.SalarySheet;
import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.entity.VoucherEntry;
import com.pengyuan.pims.repository.EmployeeRepository;
import com.pengyuan.pims.repository.SalaryItemRepository;
import com.pengyuan.pims.repository.SalarySheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 工资核算（v5.62）：月度工资单（自动带出在职员工）+ 计提/发放凭证
 * 应发 gross = base + bonus + piecework − deduction；实发 net = gross − 代扣社保 − 代扣个税
 * 计提借方按部门归集（biz:salary:dept: 映射），贷 2211.01；
 * 发放借 2211.01，贷 银行(实发) + 2232(社保) + 2221.06(个税)，零额行跳过。
 */
@Service
public class SalaryService {

    private final SalarySheetRepository repo;
    private final SalaryItemRepository itemRepo;
    private final EmployeeRepository employeeRepo;
    private final AccountSubjectService subjectService;
    private final VoucherService voucherService;
    private final WriteQueue writeQueue;

    public SalaryService(SalarySheetRepository repo, SalaryItemRepository itemRepo,
                         EmployeeRepository employeeRepo, AccountSubjectService subjectService,
                         VoucherService voucherService, WriteQueue writeQueue) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.employeeRepo = employeeRepo;
        this.subjectService = subjectService;
        this.voucherService = voucherService;
        this.writeQueue = writeQueue;
    }

    public List<SalarySheet> list() {
        List<SalarySheet> all = repo.findAllByOrderByPeriodDescIdDesc();
        if (all.isEmpty()) return all;
        Map<Long, List<SalaryItem>> bySheet = new LinkedHashMap<>();
        for (SalaryItem item : itemRepo.findAll()) {   // 工资明细量级小（月×人数），全量取回内存分组
            bySheet.computeIfAbsent(item.sheetId, k -> new ArrayList<>()).add(item);
        }
        for (SalarySheet s : all) {
            bySheet.getOrDefault(s.id, new ArrayList<>()).sort(Comparator.comparingLong(x -> x.id));
            s.items = bySheet.getOrDefault(s.id, List.of());
        }
        return all;
    }

    /** 新建期间工资单：同期间唯一；自动带出在职员工（启用、未离职且离职月不早于本期间） */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public SalarySheet create(String period, String operator) {
        checkPeriod(period);
        return writeQueue.executeTx(() -> {
            if (repo.findByPeriod(period).isPresent()) throw new IllegalArgumentException(period + " 已存在工资单");
            List<com.pengyuan.pims.entity.Employee> staff = employeeRepo.findByStatusOrderByIdAsc("ENABLED");
            List<SalaryItem> items = new ArrayList<>();
            for (com.pengyuan.pims.entity.Employee e : staff) {
                boolean onLeave = e.leaveDate != null && e.leaveDate.toString().substring(0, 7).compareTo(period) < 0;
                if (onLeave) continue;   // 离职月早于本期间的不带出
                SalaryItem item = new SalaryItem();
                item.employeeId = e.id;
                item.employeeName = e.name;
                item.dept = e.dept;
                item.base = e.baseSalary == null ? BigDecimal.ZERO : e.baseSalary;
                items.add(item);
            }
            if (items.isEmpty()) throw new IllegalArgumentException("没有可带出的在职员工，请先在员工档案里登记");

            SalarySheet sheet = new SalarySheet();
            sheet.period = period;
            sheet.createdBy = operator;
            Integer maxSeq = repo.findMaxSeq("SAL-" + period.replace("-", "") + "-%");
            sheet.docNo = String.format("SAL-%s-%04d", period.replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            recalc(sheet, items);
            SalarySheet saved = repo.save(sheet);
            saveItems(saved, items);
            saved.items = itemRepo.findBySheetIdOrderByIdAsc(saved.id);
            return saved;
        });
    }

    /** 编辑明细（仅 DRAFT）：全量替换行，重算 gross/net/合计 */
    // v8.6（N3）：去 @Transactional——方法内 executeTx 已锁内包事务，外层注解=旧时序（先开事务后抢锁）
    public SalarySheet update(Long id, List<SalaryItem> items) {
        // v5.70 P2 防呆：某个员工应发环比波动超 100% 时日志预警
        try {
            SalarySheet old = repo.findById(id).orElse(null);
            if (old != null && old.items != null && items != null) {
                for (SalaryItem ni : items) {
                    for (SalaryItem oi : old.items) {
                        if (ni.employeeId.equals(oi.employeeId) && oi.gross.doubleValue() > 0) {
                            double change = Math.abs(ni.gross.doubleValue() - oi.gross.doubleValue()) / oi.gross.doubleValue();
                            if (change > 1.0) {
                                org.slf4j.LoggerFactory.getLogger(SalaryService.class)
                                    .warn("工资波动预警: {} 应发从 {} 变为 {}（变化 {}%）",
                                        ni.employeeName, oi.gross, ni.gross, String.format("%.0f", change * 100));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        SalarySheet sheet = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("工资单不存在"));
        if ("CONFIRMED".equals(sheet.status)) throw new IllegalArgumentException("工资单已确认锁定，不能修改");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("工资单至少需要一行");
        for (SalaryItem item : items) {
            if (item.employeeId == null || item.employeeName == null || item.employeeName.isBlank()) {
                throw new IllegalArgumentException("明细行缺少员工信息");
            }
        }
        return writeQueue.executeTx(() -> {
            itemRepo.deleteBySheetId(id);
            recalc(sheet, items);
            sheet.updateTime = LocalDateTime.now();
            repo.save(sheet);
            saveItems(sheet, items);
            sheet.items = itemRepo.findBySheetIdOrderByIdAsc(id);
            return sheet;
        });
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public SalarySheet confirm(Long id, String operator) {
        SalarySheet sheet = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("工资单不存在"));
        if ("CONFIRMED".equals(sheet.status)) throw new IllegalArgumentException("工资单已确认");
        return writeQueue.executeTx(() -> {
            sheet.status = "CONFIRMED";
            sheet.confirmedBy = operator;
            sheet.confirmedTime = LocalDateTime.now();
            sheet.updateTime = LocalDateTime.now();
            return repo.save(sheet);
        });
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public void delete(Long id) {
        SalarySheet sheet = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("工资单不存在"));
        if ("CONFIRMED".equals(sheet.status)) throw new IllegalArgumentException("已确认的工资单不能删除");
        writeQueue.executeTx(() -> {
            itemRepo.deleteBySheetId(id);
            repo.deleteById(id);
        });
    }

    /** 计提凭证：按部门汇总借费用科目，贷应付工资（source=SALARY_ACCRUAL 防重） */
    @Transactional
    public Voucher genAccrualVoucher(Long id) {
        SalarySheet sheet = loaded(id);
        Voucher v = new Voucher();
        v.voucherDate = periodEnd(sheet.period);
        v.source = "SALARY_ACCRUAL";
        v.refDocNo = sheet.docNo;
        v.remark = sheet.period + " 工资计提";
        v.createdBy = sheet.createdBy;
        List<VoucherEntry> entries = new ArrayList<>();
        Map<String, BigDecimal> byDept = new LinkedHashMap<>();
        for (SalaryItem item : sheet.items) {
            byDept.merge(item.dept, item.gross, BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> en : byDept.entrySet()) {
            if (en.getValue().compareTo(BigDecimal.ZERO) == 0) continue;
            String code = mapped("biz:salary:dept:" + en.getKey(), "6602.02");
            entries.add(entry(code, deptLabel(en.getKey()) + "工资 " + sheet.period, en.getValue(), null));
        }
        entries.add(entry(mapped("biz:salary:payable", "2211.01"), "应付工资 " + sheet.period,
                null, sheet.totalGross));
        v.entries = entries;
        return voucherService.create(v);
    }

    /** 发放凭证：借应付工资（应发），贷银行（实发）+其他应付款（代扣社保）+应交个税，零额行跳过 */
    @Transactional
    public Voucher genPayVoucher(Long id) {
        SalarySheet sheet = loaded(id);
        BigDecimal social = sum(sheet.items, i -> i.socialIns);
        BigDecimal tax = sum(sheet.items, i -> i.incomeTax);
        Voucher v = new Voucher();
        v.voucherDate = periodEnd(sheet.period);
        v.source = "SALARY_PAY";
        v.refDocNo = sheet.docNo;
        v.remark = sheet.period + " 工资发放";
        v.createdBy = sheet.createdBy;
        List<VoucherEntry> entries = new ArrayList<>();
        entries.add(entry(mapped("biz:salary:payable", "2211.01"), "发放工资 " + sheet.period, sheet.totalGross, null));
        if (sheet.totalNet.compareTo(BigDecimal.ZERO) != 0) {
            entries.add(entry(mapped("biz:salary:bank", "1002"), "工资实发 " + sheet.period, null, sheet.totalNet));
        }
        if (social.compareTo(BigDecimal.ZERO) != 0) {
            entries.add(entry(mapped("biz:salary:social", "2232"), "代扣社保 " + sheet.period, null, social));
        }
        if (tax.compareTo(BigDecimal.ZERO) != 0) {
            entries.add(entry(mapped("biz:salary:tax", "2221.06"), "代扣个税 " + sheet.period, null, tax));
        }
        v.entries = entries;
        return voucherService.create(v);
    }

    // ===== 内部 =====

    private SalarySheet loaded(Long id) {
        SalarySheet sheet = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("工资单不存在"));
        if (!"CONFIRMED".equals(sheet.status)) throw new IllegalArgumentException("工资单未确认，请先确认再生成凭证");
        sheet.items = itemRepo.findBySheetIdOrderByIdAsc(id);
        return sheet;
    }

    private void recalc(SalarySheet sheet, List<SalaryItem> items) {
        BigDecimal gross = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (SalaryItem item : items) {
            item.base = nvl(item.base);
            item.bonus = nvl(item.bonus);
            item.piecework = nvl(item.piecework);
            item.deduction = nvl(item.deduction);
            item.socialIns = nvl(item.socialIns);
            item.incomeTax = nvl(item.incomeTax);
            // v6.1.6：应发为负=录入错误（扣减超过收入），拦截并报员工姓名定位
            // v6.1.7：实发为负同样拦（社保+个税超过应发，会产生负数贷方分录）
            if (item.base.add(item.bonus).add(item.piecework).subtract(item.deduction)
                    .compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("员工 " + item.employeeName + " 应发工资为负数（扣减超过收入），请核对");
            }
            item.gross = item.base.add(item.bonus).add(item.piecework).subtract(item.deduction);
            if (item.gross.subtract(item.socialIns).subtract(item.incomeTax).compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("员工 " + item.employeeName + " 实发工资为负数（社保+个税超过应发），请核对");
            }
            item.net = item.gross.subtract(item.socialIns).subtract(item.incomeTax);
            gross = gross.add(item.gross);
            net = net.add(item.net);
        }
        sheet.totalGross = gross;
        sheet.totalNet = net;
    }

    private void saveItems(SalarySheet sheet, List<SalaryItem> items) {
        for (SalaryItem item : items) {
            item.id = null;
            item.sheetId = sheet.id;
            itemRepo.save(item);
        }
    }

    private VoucherEntry entry(String subjectCode, String digest, BigDecimal debit, BigDecimal credit) {
        VoucherEntry e = new VoucherEntry();
        e.subjectCode = subjectCode;
        e.subjectName = subjectName(subjectCode);
        e.digest = digest;
        e.debit = debit == null ? BigDecimal.ZERO : debit;
        e.credit = credit == null ? BigDecimal.ZERO : credit;
        return e;
    }

    private String mapped(String key, String fallback) {
        String code = subjectService.mappedSubject(key);
        return code != null ? code : fallback;
    }

    private String subjectName(String code) {
        return subjectService.list().stream().filter(s -> code.equals(s.code)).map(s -> s.name).findFirst().orElse(code);
    }

    private BigDecimal sum(List<SalaryItem> items, java.util.function.Function<SalaryItem, BigDecimal> f) {
        return items.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String deptLabel(String dept) {
        return switch (dept == null ? "" : dept) {
            case "PRODUCTION" -> "生产人员";
            case "SALES" -> "销售人员";
            default -> "管理人员";
        };
    }

    private LocalDate periodEnd(String period) {
        return LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5, 7)), 1)
                .plusMonths(1).minusDays(1);
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private void checkPeriod(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException("期间格式应为 YYYY-MM");
    }
}
