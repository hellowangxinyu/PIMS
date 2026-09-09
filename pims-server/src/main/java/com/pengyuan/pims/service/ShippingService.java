package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.ShippingLog;
import com.pengyuan.pims.repository.SalesOrderRepository;
import com.pengyuan.pims.repository.ShippingLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 物流运费（v5.66）：每次发货登记运费，按销售订单归集进毛利成本与利润试算费用 */
@Service
public class ShippingService {

    private final ShippingLogRepository repo;
    private final SalesOrderRepository orderRepo;
    private final WriteQueue writeQueue;
    private final JdbcTemplate jdbc;

    public ShippingService(ShippingLogRepository repo, SalesOrderRepository orderRepo,
                           WriteQueue writeQueue, JdbcTemplate jdbc) {
        this.repo = repo;
        this.orderRepo = orderRepo;
        this.writeQueue = writeQueue;
        this.jdbc = jdbc;
    }

    /** 全部运费记录（含筛选在 Controller 层做） */
    public List<ShippingLog> list() { return repo.findAllByOrderByCreateTimeDescIdDesc(); }

    /** 按销售订单聚合的公司承担运费（订单列表/毛利归集用） */
    public java.util.Map<String, BigDecimal> companyFreightByOrder() {
        java.util.Map<String, BigDecimal> result = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT sales_order_no AS o, SUM(freight) AS f FROM shipping_log WHERE borne = 'COMPANY' GROUP BY sales_order_no")) {
            result.put(String.valueOf(row.get("o")), toBd(row.get("f")));
        }
        return result;
    }

    // v8.6（N3）：去 @Transactional——方法内 executeTx 已锁内包事务，外层注解=旧时序（先开事务后抢锁）
    public ShippingLog create(ShippingLog s) {
        if (s.salesOrderNo == null || s.salesOrderNo.isBlank()) throw new IllegalArgumentException("销售订单号不能为空");
        orderRepo.findByOrderNo(s.salesOrderNo)
                .orElseThrow(() -> new IllegalArgumentException("销售订单 " + s.salesOrderNo + " 不存在"));
        if (!"COMPANY".equals(s.borne) && !"CUSTOMER".equals(s.borne)) s.borne = "COMPANY";
        // 客户到付允许 0（记录承运信息本身）；公司承担必须 >0（进成本要有实额）
        validateFreight(s.freight, s.borne);
        if (s.shipDate == null || s.shipDate.isBlank()) s.shipDate = LocalDate.now().toString();
        // 一个发货单只能登记一次运费（一次发货一趟物流；分车发货请拆发货单，订单级仍可多笔）
        if (s.outboundDocNo != null && !s.outboundDocNo.isBlank()) {
            repo.findByOutboundDocNo(s.outboundDocNo.trim()).ifPresent(existing -> {
                throw new IllegalArgumentException("发货单 " + s.outboundDocNo + " 已登记过运费（" + existing.docNo
                        + " ¥" + existing.freight + "），如需调整请编辑原记录");
            });
            s.outboundDocNo = s.outboundDocNo.trim();
        }
        return writeQueue.executeTx(() -> {
            Integer maxSeq = repo.findMaxSeq("SHIP-" + LocalDate.now().toString().replace("-", "") + "-%");
            s.docNo = String.format("SHIP-%s-%04d", LocalDate.now().toString().replace("-", ""), (maxSeq == null ? 0 : maxSeq) + 1);
            return repo.save(s);
        });
    }

    @Transactional
    public ShippingLog update(Long id, ShippingLog in) {
        ShippingLog s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("运费记录不存在"));
        String newBorne = "CUSTOMER".equals(in.borne) ? "CUSTOMER" : "COMPANY";
        validateFreight(in.freight, newBorne);
        s.carrier = in.carrier;
        s.trackingNo = in.trackingNo;
        s.freight = in.freight == null ? BigDecimal.ZERO : in.freight;
        s.borne = newBorne;
        s.shipDate = in.shipDate == null ? s.shipDate : in.shipDate;
        s.remark = in.remark;
        s.updateTime = LocalDateTime.now();
        return repo.save(s);
    }

    /** 客户到付允许 0（只记录承运信息）；公司承担必须 >0 */
    private void validateFreight(BigDecimal freight, String borne) {
        if (freight == null) freight = BigDecimal.ZERO;
        if (freight.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("运费金额不能为负数");
        if ("COMPANY".equals(borne) && freight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("公司承担的运费金额必须大于 0（客户到付才允许 0）");
        }
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    /** 由发货单带出订单号+客户（快捷登记预填用）；v5.66.1 同时带出该发货单已登记的运费（前端直接进编辑态） */
    public Map<String, Object> resolveOutbound(String outboundDocNo) {
        if (outboundDocNo == null || outboundDocNo.isBlank()) throw new IllegalArgumentException("发货单号不能为空");
        var rows = jdbc.queryForList(
                "SELECT sales_order_no AS o, customer_name AS c FROM sales_outbound WHERE doc_no = ?", outboundDocNo);
        if (rows.isEmpty()) throw new IllegalArgumentException("发货单 " + outboundDocNo + " 不存在");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("salesOrderNo", rows.get(0).get("o"));
        result.put("customerName", rows.get(0).get("c"));
        repo.findByOutboundDocNo(outboundDocNo).ifPresent(existing -> result.put("existing", existing));
        return result;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Double d) return BigDecimal.valueOf(d);
        return new BigDecimal(String.valueOf(v));
    }
}
