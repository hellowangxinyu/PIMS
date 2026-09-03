package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.RdProgress;
import com.pengyuan.pims.entity.SampleRequest;
import com.pengyuan.pims.repository.RdProgressRepository;
import com.pengyuan.pims.repository.SampleRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * v5.53 打样/样品管理。涂料成单链路：
 * 申请 → 调色（自动在研发进度建条目，研发在熟悉的页面跟进）→ 寄样 → 客户反馈
 * → 满意则转单 / 需调整则回炉调色（adjustCount 计轮次）→ 成交或未成交（研发进度自动结案）。
 */
@Service
public class SampleService {

    private static final Logger log = LoggerFactory.getLogger(SampleService.class);

    private static final Map<String, String> STATUS_LABEL = Map.of(
            "APPLIED", "已申请", "COLORING", "调色中", "SENT", "已寄样",
            "SATISFIED", "客户满意", "ADJUST", "需调整", "WON", "已转单", "LOST", "未成交");

    private final SampleRequestRepository sampleRepo;
    private final RdProgressRepository rdRepo;
    private final WriteQueue writeQueue;

    public SampleService(SampleRequestRepository sampleRepo, RdProgressRepository rdRepo, WriteQueue writeQueue) {
        this.sampleRepo = sampleRepo;
        this.rdRepo = rdRepo;
        this.writeQueue = writeQueue;
    }

    public List<SampleRequest> list(Long customerId, String status) {
        List<SampleRequest> list;
        if (customerId != null && status != null && !status.isBlank()) list = sampleRepo.findByCustomerIdAndStatusOrderByCreateTimeDescIdDesc(customerId, status);
        else if (customerId != null) list = sampleRepo.findByCustomerIdOrderByCreateTimeDescIdDesc(customerId);
        else if (status != null && !status.isBlank()) list = sampleRepo.findByStatusOrderByCreateTimeDescIdDesc(status);
        else list = sampleRepo.findAllByOrderByCreateTimeDescIdDesc();
        list.forEach(this::fillLabel);
        return list;
    }

    private void fillLabel(SampleRequest s) { s.statusLabel = STATUS_LABEL.getOrDefault(s.status, s.status); }

    public SampleRequest getById(Long id) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        fillLabel(s);
        return s;
    }

    @Transactional
    public SampleRequest create(SampleRequest s) {
        return writeQueue.execute(() -> {
            if (s.customerName == null || s.customerName.isBlank()) throw new IllegalArgumentException("请填写客户/线索公司");
            if (s.materialDesc == null || s.materialDesc.isBlank()) throw new IllegalArgumentException("请填写意向产品/颜色要求");
            Integer maxSeq = sampleRepo.findMaxSeq("DY-" + LocalDate.now().toString().replace("-", "") + "-%");
            s.sampleNo = String.format("DY-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            s.status = "APPLIED";
            s.applyDate = LocalDate.now();
            s.adjustCount = 0;
            SampleRequest saved = sampleRepo.save(s);
            log.info("打样申请创建: {} 客户={}", saved.sampleNo, saved.customerName);
            fillLabel(saved);
            return saved;
        });
    }

    /** 仅 APPLIED 可编辑基本信息 */
    @Transactional
    public SampleRequest update(Long id, SampleRequest in) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"APPLIED".equals(s.status)) throw new IllegalArgumentException("只有已申请状态可编辑（进入调色后请走流转操作）");
        s.customerId = in.customerId;
        s.customerName = in.customerName;
        s.materialCode = in.materialCode;
        s.materialDesc = in.materialDesc;
        s.qty = in.qty;
        s.unit = in.unit;
        s.applicant = in.applicant;
        s.remark = in.remark;
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 开始调色：APPLIED/ADJUST → COLORING，首次自动在研发进度建条目 */
    @Transactional
    public SampleRequest startColoring(Long id, String colorist, String colorNote) {
        return writeQueue.execute(() -> {
            SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
            if (!"APPLIED".equals(s.status) && !"ADJUST".equals(s.status))
                throw new IllegalArgumentException("只有已申请/需调整状态可开始调色");
            s.colorist = colorist;
            s.colorNote = (s.colorNote == null ? "" : s.colorNote + " | ") + (colorNote != null ? colorNote : "");
            s.status = "COLORING";
            // 研发进度联动：首次调色建条目（分类=调色），之后每次回炉更新进度备注
            if (s.rdProgressId == null) {
                RdProgress rd = new RdProgress();
                rd.raiseDate = LocalDate.now();
                rd.owner = s.applicant != null ? s.applicant : "销售";
                rd.category = "调色";
                rd.content = "打样 " + s.sampleNo + "：" + s.customerName + " " + s.materialDesc;
                rd.progress = "第 1 轮调色（调色员：" + (colorist != null ? colorist : "-") + "）";
                rd.createdBy = "sample:" + s.sampleNo;
                rd = rdRepo.save(rd);
                s.rdProgressId = rd.id;
            } else {
                RdProgress rd = rdRepo.findById(s.rdProgressId).orElse(null);
                if (rd != null) {
                    rd.progress = "第 " + (s.adjustCount + 1) + " 轮调色（调色员：" + (colorist != null ? colorist : "-") + "）";
                    rd.updateTime = java.time.LocalDateTime.now();
                    rdRepo.save(rd);
                }
            }
            s.updateTime = java.time.LocalDateTime.now();
            sampleRepo.save(s);
            fillLabel(s);
            return s;
        });
    }

    /** 寄样：COLORING → SENT */
    @Transactional
    public SampleRequest send(Long id, LocalDate sendDate, String expressNo) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"COLORING".equals(s.status)) throw new IllegalArgumentException("只有调色中状态可寄样");
        s.sendDate = sendDate != null ? sendDate : LocalDate.now();
        s.expressNo = expressNo;
        s.status = "SENT";
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 客户反馈：SENT → SATISFIED / ADJUST（需调整回炉，轮次+1） */
    @Transactional
    public SampleRequest feedback(Long id, boolean satisfied, String content, LocalDate feedbackDate) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"SENT".equals(s.status)) throw new IllegalArgumentException("只有已寄样状态可记录反馈");
        s.feedbackContent = content;
        s.feedbackDate = feedbackDate != null ? feedbackDate : LocalDate.now();
        if (satisfied) {
            s.status = "SATISFIED";
        } else {
            s.status = "ADJUST";
            s.adjustCount = (s.adjustCount == null ? 0 : s.adjustCount) + 1;
        }
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 转单：SATISFIED → WON（填成交订单号），研发进度结案 */
    @Transactional
    public SampleRequest win(Long id, String orderNo) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"SATISFIED".equals(s.status)) throw new IllegalArgumentException("只有客户满意状态可转单");
        if (orderNo == null || orderNo.isBlank()) throw new IllegalArgumentException("请填写成交的销售订单号");
        s.wonOrderNo = orderNo;
        s.status = "WON";
        closeRd(s, "已成交，转订单 " + orderNo);
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    /** 未成交：任意未完结状态 → LOST，研发进度结案 */
    @Transactional
    public SampleRequest lose(Long id, String reason) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if ("WON".equals(s.status) || "LOST".equals(s.status)) throw new IllegalArgumentException("已完结的打样单不可再标记");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("请填写未成交原因");
        s.lossReason = reason;
        s.status = "LOST";
        closeRd(s, "未成交：" + reason);
        s.updateTime = java.time.LocalDateTime.now();
        sampleRepo.save(s);
        fillLabel(s);
        return s;
    }

    private void closeRd(SampleRequest s, String result) {
        if (s.rdProgressId == null) return;
        rdRepo.findById(s.rdProgressId).ifPresent(rd -> {
            rd.result = result;
            rd.closedDate = LocalDate.now();
            rd.updateTime = java.time.LocalDateTime.now();
            rdRepo.save(rd);
        });
    }

    /** 仅 APPLIED 可删除（未进调色，无研发进度联动） */
    @Transactional
    public void delete(Long id) {
        SampleRequest s = sampleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("打样单不存在"));
        if (!"APPLIED".equals(s.status)) throw new IllegalArgumentException("只有已申请状态可删除（已进入流程请标记未成交）");
        sampleRepo.delete(s);
    }
}
