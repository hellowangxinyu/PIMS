package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.ProductionOrder;
import com.pengyuan.pims.entity.ProductionOrderException;
import com.pengyuan.pims.repository.ProductionOrderExceptionRepository;
import com.pengyuan.pims.repository.ProductionOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 异常订单处理服务
 * 投入产出比 < 95% 的已完工订单：实时计算 + 处置闭环。
 * 处置记录落 production_order_exception 表（按 orderNo 唯一）；投出比为实时计算值（见 ProductionOrder.ioRatio @Transient）。
 */
@Service
public class AbnormalOrderService {
    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.10

    private static final Logger log = LoggerFactory.getLogger(AbnormalOrderService.class);

    private final ProductionOrderRepository orderRepo;
    private final ProductionOrderService productionOrderService;
    private final ProductionOrderExceptionRepository exceptionRepo;

    public AbnormalOrderService(ProductionOrderRepository orderRepo,
                                ProductionOrderService productionOrderService,
                                ProductionOrderExceptionRepository exceptionRepo, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.orderRepo = orderRepo;
        this.productionOrderService = productionOrderService;
        this.exceptionRepo = exceptionRepo;
    }

    /**
     * 异常订单列表：所有已完工且投出比<95%(ABNORMAL)的订单，附处置状态。
     */
    public Map<String, Object> list() {
        List<ProductionOrder> orders = orderRepo.findByOrderByCreateTimeDesc();
        productionOrderService.fillIoRatio(orders);

        Map<String, ProductionOrderException> handleMap = new HashMap<>();
        for (ProductionOrderException e : exceptionRepo.findAll()) {
            handleMap.put(e.orderNo, e);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int pending = 0, processing = 0, closed = 0;
        for (ProductionOrder o : orders) {
            if (!"ABNORMAL".equals(o.ioStatus)) continue;
            ProductionOrderException ex = handleMap.get(o.orderNo);
            String handleStatus = ex == null ? "PENDING" : ex.status;
            if ("CLOSED".equals(handleStatus)) closed++;
            else if ("PROCESSING".equals(handleStatus)) processing++;
            else pending++;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.orderNo);
            m.put("productName", o.productName);
            m.put("productCode", o.productCode);
            m.put("inputQty", o.inputQty);
            m.put("outputQty", o.outputQty);
            m.put("ioRatio", o.ioRatio);
            m.put("ioStatus", o.ioStatus);
            m.put("createTime", o.createTime);
            m.put("handleStatus", handleStatus);
            m.put("reason", ex != null ? ex.reason : null);
            m.put("measure", ex != null ? ex.measure : null);
            m.put("handler", ex != null ? ex.handler : null);
            m.put("handleTime", ex != null ? ex.updateTime : null);
            m.put("closedTime", ex != null ? ex.closedTime : null);
            m.put("remark", ex != null ? ex.remark : null);
            rows.add(m);
        }
        // 待处理在前，已闭环在后
        rows.sort(Comparator.comparingInt(m -> statusOrder((String) m.get("handleStatus"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", rows.size());
        result.put("pendingCount", pending);
        result.put("processingCount", processing);
        result.put("closedCount", closed);
        return result;
    }

    private int statusOrder(String s) {
        return "PENDING".equals(s) ? 0 : "PROCESSING".equals(s) ? 1 : 2;
    }

    /**
     * 提交异常处置（upsert）。首次提交时写入投出比快照便于追溯。
     */
    // v8.10（A2 三批）：去 @Transactional，锁内包事务
    public ProductionOrderException handle(String orderNo, String reason, String measure,
                                           String status, String remark, String operator) {
        return writeQueue.executeTx(() -> {
        ProductionOrder order = orderRepo.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("生产订单不存在: " + orderNo));
        ProductionOrderException ex = exceptionRepo.findByOrderNo(orderNo).orElse(null);
        if (ex == null) {
            ex = new ProductionOrderException();
            ex.orderNo = orderNo;
            ex.createdBy = operator;
            ex.status = "PENDING";
            // 首次记录写入投出比快照
            productionOrderService.fillIoRatio(List.of(order));
            ex.ioRatio = order.ioRatio;
            ex.inputQty = order.inputQty;
            ex.outputQty = order.outputQty;
        }
        ex.reason = reason;
        ex.measure = measure;
        ex.remark = remark;
        ex.handler = operator;
        if (status != null && !status.isBlank()) {
            boolean wasClosed = "CLOSED".equals(ex.status);
            ex.status = status;
            if ("CLOSED".equals(status) && !wasClosed) {
                ex.closedTime = LocalDateTime.now();   // 新闭环
            } else if (!"CLOSED".equals(status)) {
                ex.closedTime = null;                  // 重开
            }
        }
        ex.updateTime = LocalDateTime.now();
        log.info("异常订单处置：{} 状态={} 处理人={}", orderNo, ex.status, operator);
        return exceptionRepo.save(ex);
        });
    }

    public ProductionOrderException get(String orderNo) {
        return exceptionRepo.findByOrderNo(orderNo).orElse(null);
    }

    /** 导出异常订单 Excel（与列表同口径） */
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows =
                (java.util.List<java.util.Map<String, Object>>) list().get("rows");
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> m : rows) {
            out.add(new Object[]{
                    m.get("orderNo"), m.get("productName"), m.get("productCode"),
                    m.get("inputQty"), m.get("outputQty"), m.get("ioRatio"),
                    handleStatusLabel((String) m.get("handleStatus")),
                    m.get("reason"), m.get("measure"), m.get("handler"), m.get("handleTime")
            });
        }
        com.pengyuan.pims.common.ExcelUtil.export(response,
                "异常订单-" + java.time.LocalDate.now(), "异常订单",
                new String[]{"订单号", "产品名称", "产品编码", "投入量", "产出量", "投入产出比(%)", "处置状态", "异常原因", "处置措施", "处理人", "处理时间"},
                out);
    }

    private String handleStatusLabel(String s) {
        if ("CLOSED".equals(s)) return "已闭环";
        if ("PROCESSING".equals(s)) return "处理中";
        return "待处理";
    }
}
