package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.CustomerComplaint;
import com.pengyuan.pims.entity.InventoryMovement;
import com.pengyuan.pims.repository.CustomerComplaintRepository;
import com.pengyuan.pims.repository.InventoryMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * v5.53 客户投诉/质量反馈。登记（PROCESSING）→ 原因+措施（RESOLVED）→ 客户确认关闭（CLOSED）。
 * 填了批号可追溯该批次全部出入库流水（主表+归档表合并，2 年前的批次也能查）。
 */
@Service
public class ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintService.class);

    private final CustomerComplaintRepository complaintRepo;
    private final InventoryMovementRepository movementRepo;
    private final WriteQueue writeQueue;
    private final UserService userService;

    public ComplaintService(CustomerComplaintRepository complaintRepo, InventoryMovementRepository movementRepo,
                            WriteQueue writeQueue, UserService userService) {
        this.complaintRepo = complaintRepo;
        this.movementRepo = movementRepo;
        this.writeQueue = writeQueue;
        this.userService = userService;
    }

    public List<CustomerComplaint> list(Long customerId, String status) {
        if (customerId != null && status != null && !status.isBlank()) return complaintRepo.findByCustomerIdAndStatusOrderByCreateTimeDescIdDesc(customerId, status);
        if (customerId != null) return complaintRepo.findByCustomerIdOrderByCreateTimeDescIdDesc(customerId);
        if (status != null && !status.isBlank()) return complaintRepo.findByStatusOrderByCreateTimeDescIdDesc(status);
        return complaintRepo.findAllByOrderByCreateTimeDescIdDesc();
    }

    public CustomerComplaint getById(Long id) {
        return complaintRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("投诉单不存在"));
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public CustomerComplaint create(CustomerComplaint c) {
        return writeQueue.executeTx(() -> {
            if (c.customerName == null || c.customerName.isBlank()) throw new IllegalArgumentException("请填写客户");
            if (c.description == null || c.description.isBlank()) throw new IllegalArgumentException("请填写问题描述");
            Integer maxSeq = complaintRepo.findMaxSeq("TS-" + LocalDate.now().toString().replace("-", "") + "-%");
            c.complaintNo = String.format("TS-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            c.status = "PROCESSING";
            c.createdBy = userService.currentOperatorName();  // v5.60 制单人
            if (c.complaintDate == null) c.complaintDate = LocalDate.now();
            CustomerComplaint saved = complaintRepo.save(c);
            log.info("客户投诉登记: {} 客户={}", saved.complaintNo, saved.customerName);
            return saved;
        });
    }

    /** 仅处理中可编辑 */
    @Transactional
    public CustomerComplaint update(Long id, CustomerComplaint in) {
        CustomerComplaint c = complaintRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("投诉单不存在"));
        if (!"PROCESSING".equals(c.status)) throw new IllegalArgumentException("只有处理中状态可编辑（已处理/已关闭的单据不可改）");
        c.customerId = in.customerId;
        c.customerName = in.customerName;
        c.materialCode = in.materialCode;
        c.materialName = in.materialName;
        c.batchNo = in.batchNo;
        c.qcDocNo = in.qcDocNo;
        c.salesOrderNo = in.salesOrderNo;
        c.complaintDate = in.complaintDate;
        c.category = in.category;
        c.description = in.description;
        c.remark = in.remark;
        c.updateTime = java.time.LocalDateTime.now();
        return complaintRepo.save(c);
    }

    /** 处理完成：PROCESSING → RESOLVED（填原因分析+处理措施） */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public CustomerComplaint resolve(Long id, String cause, String action, String handler, LocalDate resolveDate) {
        return writeQueue.executeTx(() -> {
            CustomerComplaint c = complaintRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("投诉单不存在"));
            if (!"PROCESSING".equals(c.status)) throw new IllegalArgumentException("只有处理中状态可标记已处理");
            if (cause == null || cause.isBlank()) throw new IllegalArgumentException("请填写原因分析");
            if (action == null || action.isBlank()) throw new IllegalArgumentException("请填写处理措施");
            c.cause = cause;
            c.action = action;
            c.handler = handler;
            c.resolveDate = resolveDate != null ? resolveDate : LocalDate.now();
            c.status = "RESOLVED";
            c.updateTime = java.time.LocalDateTime.now();
            return complaintRepo.save(c);
        });
    }

    /** 客户确认关闭：RESOLVED → CLOSED */
    @Transactional
    public CustomerComplaint close(Long id) {
        CustomerComplaint c = complaintRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("投诉单不存在"));
        if (!"RESOLVED".equals(c.status)) throw new IllegalArgumentException("只有已处理状态可关闭（客户确认后）");
        c.closeDate = LocalDate.now();
        c.status = "CLOSED";
        c.updateTime = java.time.LocalDateTime.now();
        return complaintRepo.save(c);
    }

    /** 仅处理中可删除 */
    @Transactional
    public void delete(Long id) {
        CustomerComplaint c = complaintRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("投诉单不存在"));
        if (!"PROCESSING".equals(c.status)) throw new IllegalArgumentException("只有处理中状态可删除");
        complaintRepo.delete(c);
    }

    /** 批次追溯：该投诉涉及物料+批号的全部出入库流水（主表+归档表） */
    public List<InventoryMovement> trace(Long id) {
        CustomerComplaint c = getById(id);
        if (c.materialCode == null || c.materialCode.isBlank() || c.batchNo == null || c.batchNo.isBlank())
            throw new IllegalArgumentException("该投诉未填写物料编码或批号，无法追溯");
        return movementRepo.findByMaterialCodeAndBatchNoIncludingArchive(c.materialCode, c.batchNo);
    }
}
