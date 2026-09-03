package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.CrmContact;
import com.pengyuan.pims.entity.CrmFollowUp;
import com.pengyuan.pims.entity.CrmOpportunity;
import com.pengyuan.pims.repository.CrmContactRepository;
import com.pengyuan.pims.repository.CrmFollowUpRepository;
import com.pengyuan.pims.repository.CrmOpportunityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRM（v5.50）：联系人 + 商机管道 + 跟进记录。
 * 商机阶段机：LEAD → QUOTED → SAMPLING → NEGOTIATING → WON/LOST（终态不可回退，可反结案式重开）。
 */
@Service
public class CrmService {

    private static final Logger log = LoggerFactory.getLogger(CrmService.class);

    public static final List<String> STAGES = List.of("LEAD", "QUOTED", "SAMPLING", "NEGOTIATING", "WON", "LOST");
    private static final Map<String, String> STAGE_LABEL = Map.of(
            "LEAD", "初步接触", "QUOTED", "已报价", "SAMPLING", "样品测试", "NEGOTIATING", "商务谈判", "WON", "成交", "LOST", "流失");

    private final CrmContactRepository contactRepo;
    private final CrmOpportunityRepository oppRepo;
    private final CrmFollowUpRepository followRepo;
    private final WriteQueue writeQueue;

    public CrmService(CrmContactRepository contactRepo, CrmOpportunityRepository oppRepo,
                      CrmFollowUpRepository followRepo, WriteQueue writeQueue) {
        this.contactRepo = contactRepo;
        this.oppRepo = oppRepo;
        this.followRepo = followRepo;
        this.writeQueue = writeQueue;
    }

    // ===== 联系人 =====

    public List<CrmContact> listContacts() { return contactRepo.findAllByOrderByCreateTimeDescIdDesc(); }

    @Transactional
    public CrmContact createContact(CrmContact c) {
        if (c.name == null || c.name.isBlank()) throw new IllegalArgumentException("联系人姓名不能为空");
        if (c.companyName == null || c.companyName.isBlank()) throw new IllegalArgumentException("所属公司不能为空");
        return writeQueue.execute(() -> contactRepo.save(c));
    }

    @Transactional
    public CrmContact updateContact(Long id, CrmContact in) {
        CrmContact c = contactRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("联系人不存在"));
        if (in.name == null || in.name.isBlank()) throw new IllegalArgumentException("联系人姓名不能为空");
        c.customerId = in.customerId;
        c.companyName = in.companyName;
        c.name = in.name;
        c.title = in.title;
        c.phone = in.phone;
        c.wechat = in.wechat;
        c.email = in.email;
        c.isPrimary = in.isPrimary;
        c.remark = in.remark;
        c.updateTime = java.time.LocalDateTime.now();
        return contactRepo.save(c);
    }

    @Transactional
    public void deleteContact(Long id) { contactRepo.deleteById(id); }

    // ===== 商机 =====

    public List<CrmOpportunity> listOpportunities() { return oppRepo.findAllByOrderByCreateTimeDescIdDesc(); }

    /** 管道汇总：各阶段数量与预计金额（打开的商机=非终态） */
    public Map<String, Object> pipelineSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> open = List.of("LEAD", "QUOTED", "SAMPLING", "NEGOTIATING");
        java.math.BigDecimal openAmount = java.math.BigDecimal.ZERO;
        int openCount = 0, wonCount = 0, lostCount = 0;
        java.math.BigDecimal wonAmount = java.math.BigDecimal.ZERO;
        Map<String, Object> byStage = new LinkedHashMap<>();
        for (String st : STAGES) {
            var list = oppRepo.findByStageOrderByCreateTimeDesc(st);
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("count", list.size());
            sm.put("amount", list.stream().map(o -> o.expectAmount == null ? java.math.BigDecimal.ZERO : o.expectAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
            byStage.put(st, sm);
            if (open.contains(st)) { openCount += list.size(); openAmount = openAmount.add((java.math.BigDecimal) sm.get("amount")); }
            if ("WON".equals(st)) { wonCount = list.size(); wonAmount = wonAmount.add((java.math.BigDecimal) sm.get("amount")); }
            if ("LOST".equals(st)) lostCount = list.size();
        }
        m.put("byStage", byStage);
        m.put("openCount", openCount);
        m.put("openAmount", openAmount);
        m.put("wonCount", wonCount);
        m.put("wonAmount", wonAmount);
        m.put("lostCount", lostCount);
        return m;
    }

    @Transactional
    public CrmOpportunity createOpportunity(CrmOpportunity o, String operator) {
        if (o.title == null || o.title.isBlank()) throw new IllegalArgumentException("商机名称不能为空");
        if (o.companyName == null || o.companyName.isBlank()) throw new IllegalArgumentException("客户/公司名称不能为空");
        if (o.stage == null || !STAGES.contains(o.stage)) o.stage = "LEAD";
        o.createdBy = operator;
        return writeQueue.execute(() -> oppRepo.save(o));
    }

    @Transactional
    public CrmOpportunity updateOpportunity(Long id, CrmOpportunity in) {
        CrmOpportunity o = oppRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("商机不存在"));
        if (in.title == null || in.title.isBlank()) throw new IllegalArgumentException("商机名称不能为空");
        o.title = in.title;
        o.companyName = in.companyName;
        o.customerId = in.customerId;
        o.productInterest = in.productInterest;
        o.expectAmount = in.expectAmount;
        o.expectDate = in.expectDate;
        o.owner = in.owner;
        o.remark = in.remark;
        o.updateTime = java.time.LocalDateTime.now();
        return oppRepo.save(o);
    }

    /** 阶段流转：WON 需填成交单号或关联客户；LOST 需填流失原因；终态可重开（stage 传回打开态） */
    @Transactional
    public CrmOpportunity changeStage(Long id, String stage, String wonOrderNo, String lossReason) {
        if (!STAGES.contains(stage)) throw new IllegalArgumentException("无效阶段: " + stage);
        CrmOpportunity o = oppRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("商机不存在"));
        if ("WON".equals(stage)) {
            if ((wonOrderNo == null || wonOrderNo.isBlank()) && o.customerId == null && (o.wonOrderNo == null || o.wonOrderNo.isBlank())) {
                throw new IllegalArgumentException("成交请填写关联销售订单号（或先关联正式客户）");
            }
            if (wonOrderNo != null && !wonOrderNo.isBlank()) o.wonOrderNo = wonOrderNo;
        }
        if ("LOST".equals(stage) && (lossReason == null || lossReason.isBlank()) && (o.lossReason == null || o.lossReason.isBlank())) {
            throw new IllegalArgumentException("流失请填写原因");
        }
        if ("LOST".equals(stage) && lossReason != null && !lossReason.isBlank()) o.lossReason = lossReason;
        o.stage = stage;
        o.updateTime = java.time.LocalDateTime.now();
        log.info("商机阶段流转: {}「{}」→ {}", o.id, o.title, STAGE_LABEL.get(stage));
        return oppRepo.save(o);
    }

    @Transactional
    public void deleteOpportunity(Long id) {
        followRepo.deleteAll(followRepo.findByOpportunityIdOrderByFollowDateDescIdDesc(id));
        oppRepo.deleteById(id);
    }

    // ===== 跟进记录 =====

    public List<CrmFollowUp> listFollowUps(Long opportunityId, Long customerId) {
        if (opportunityId != null) return followRepo.findByOpportunityIdOrderByFollowDateDescIdDesc(opportunityId);
        if (customerId != null) return followRepo.findByCustomerIdOrderByFollowDateDescIdDesc(customerId);
        return List.of();
    }

    @Transactional
    public CrmFollowUp createFollowUp(CrmFollowUp f, String operator) {
        if (f.opportunityId == null && f.customerId == null) throw new IllegalArgumentException("跟进记录须挂商机或客户");
        if (f.content == null || f.content.isBlank()) throw new IllegalArgumentException("跟进内容不能为空");
        if (f.followDate == null) f.followDate = LocalDate.now();
        f.operator = operator;
        return writeQueue.execute(() -> followRepo.save(f));
    }

    @Transactional
    public void deleteFollowUp(Long id) { followRepo.deleteById(id); }

    /** Dashboard 待办：今天及以前该跟进的记录数 */
    public long dueFollowUpCount() {
        return followRepo.findByNextDateNotNullAndNextDateLessThanEqual(LocalDate.now()).size();
    }
}
