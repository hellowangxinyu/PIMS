package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.*;
import com.pengyuan.pims.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 销售订单：创建（合同号自动生成 HT+拼音+日期）、确认、发货状态流转、明细已发量回写。
 * 关键口径：订单确认后才可发货；明细未定价禁止出库确认（出库必须立应收）；一键转生产/委外防重复。
 */
@Service
public class SalesOrderService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SalesOrderService.class);

    private final SalesOrderRepository orderRepo;
    private final SalesOrderItemRepository itemRepo;
    private final InventoryService inventoryService;
    private final ProductionOrderService productionOrderService;
    private final OutsourceOrderService outsourceOrderService;
    private final RecipeService recipeService;
    private final MaterialRepository materialRepo;
    private final MaterialService materialService;
    private final FinanceService financeService;
    // v5.24：全局写锁（单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    private final SalesOrderChangeLogRepository changeLogRepo;

    public SalesOrderService(SalesOrderRepository orderRepo,
                             SalesOrderItemRepository itemRepo,
                             InventoryService inventoryService,
                             ProductionOrderService productionOrderService,
                             OutsourceOrderService outsourceOrderService,
                             RecipeService recipeService,
                             MaterialRepository materialRepo,
                             WriteQueue writeQueue,
                             MaterialService materialService,
                             SalesOrderChangeLogRepository changeLogRepo,
                             org.springframework.jdbc.core.JdbcTemplate jdbc,
                             FinanceService financeService) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.inventoryService = inventoryService;
        this.productionOrderService = productionOrderService;
        this.outsourceOrderService = outsourceOrderService;
        this.recipeService = recipeService;
        this.materialRepo = materialRepo;
        this.materialService = materialService;
        this.financeService = financeService;
        this.writeQueue = writeQueue;
        this.changeLogRepo = changeLogRepo;
        this.jdbc = jdbc;
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;   // v5.66 订单运费聚合

    /** 订单 → Σ公司承担运费（fillAggregation 批量带出） */
    private java.util.List<Object[]> shippingRepoFreight() {
        return jdbc.query("SELECT sales_order_no, SUM(freight) FROM shipping_log WHERE borne = 'COMPANY' GROUP BY sales_order_no",
                (rs, i) -> new Object[]{ rs.getString(1), rs.getBigDecimal(2) });
    }

    public List<SalesOrder> listByStatus(String status) {
        List<SalesOrder> orders = orderRepo.findByStatusOrderByCreateTimeDesc(status);
        fillAggregation(orders);
        return orders;
    }

    public List<SalesOrder> listAll() {
        List<SalesOrder> orders = orderRepo.findAll();
        fillAggregation(orders);
        return orders;
    }

    /**
     * 聚合每张订单的明细摘要(品名/数量) + 产品类型标记(hasB 半成品 / hasC 成品)。
     * v4.8：明细一次批量加载后按订单分组，替代逐单 N+1 查询。
     * hasB/hasC 供生产订单"参照销售订单"按配方类型筛选：GRINDING(制浆)↔B 半成品，TINTING(制漆)↔C 成品。
     */
    private void fillAggregation(List<SalesOrder> orders) {
        if (orders.isEmpty()) return;
        // 只查当前列表涉及订单的明细（此前 findAll 拉全部订单的明细再内存分组）
        List<Long> orderIds = orders.stream().map(o -> o.id).toList();
        java.util.Map<Long, List<SalesOrderItem>> itemsByOrder = itemRepo.findByOrderIdIn(orderIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(i -> i.orderId));
        // v5.66 运费批量聚合（公司承担，按订单号）
        java.util.Map<String, java.math.BigDecimal> freightByOrder = new java.util.HashMap<>();
        for (var row : shippingRepoFreight()) {
            freightByOrder.put(String.valueOf(row[0]), (java.math.BigDecimal) row[1]);
        }
        // 物料编码→category（半成品编码以 PJ 开头，不能按首字符判断 B/C，需查 category）
        java.util.Map<String, String> codeToCategory = materialRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(m -> m.code, m -> m.category != null ? m.category : "", (a, b) -> a));
        for (SalesOrder order : orders) {
            List<SalesOrderItem> items = itemsByOrder.getOrDefault(order.id, java.util.List.of());
            order.materialNames = items.stream()
                    .map(it -> it.materialName)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .reduce((a, b) -> a + "，" + b)
                    .orElse("");
            // 数量摘要：每条明细 "数量 单位"，多明细以逗号分隔
            order.materialQtySummary = items.stream()
                    .map(it -> {
                        String qty = it.qty != null ? it.qty.stripTrailingZeros().toPlainString() : "0";
                        String unit = it.unit != null && !it.unit.isBlank() ? it.unit : "";
                        return unit.isEmpty() ? qty : qty + " " + unit;
                    })
                    .reduce((a, b) -> a + "，" + b)
                    .orElse("");
            order.hasB = items.stream().anyMatch(it -> it.materialCode != null && "B".equals(codeToCategory.get(it.materialCode)));
            order.hasC = items.stream().anyMatch(it -> it.materialCode != null && "C".equals(codeToCategory.get(it.materialCode)));
            // v5.66 累计公司承担运费（批量聚合防 N+1）
            order.freightTotal = freightByOrder.getOrDefault(order.orderNo, java.math.BigDecimal.ZERO);
        }
    }
    public List<SalesOrder> listRecent(int limit) { return orderRepo.findAllByOrderByCreateTimeDesc(PageRequest.of(0, limit)); }
    public Optional<SalesOrder> getById(Long id) { return orderRepo.findById(id); }
    public List<SalesOrderItem> getItems(Long orderId) { return itemRepo.findByOrderId(orderId); }

    // v6.1（高#12）：锁内包事务（executeTx），提交后才放锁——防取号窗口撞号，不再用外层 @Transactional
    public SalesOrder create(SalesOrder order, List<SalesOrderItem> items) {
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("销售明细不能为空");   // v6.1.6：items null 原裸遍历 NPE
            if (order.orderNo == null || order.orderNo.isBlank()) {
                // v5.66.1：单号 = SO-年月日-流水（如 SO-20260826-0001），按天流水、每天从 0001 起
                String day = LocalDate.now().toString().replace("-", "");
                Integer maxSeq = orderRepo.findMaxSeq("SO-" + day + "-%");
                order.orderNo = String.format("SO-%s-%04d", day, (maxSeq == null ? 0 : maxSeq) + 1);
            }
            // v5.27：合同号自动生成且必填（HT + 客户简称拼音首字母 + 日期 + 序号，如 HTQDHB20260808-01）
            if (order.contractNo == null || order.contractNo.isBlank()) {
                String shortCode = com.pengyuan.pims.common.PinyinShortCode.shortCode(order.customerName);
                String datePart = LocalDate.now().toString().replace("-", "");
                String fullPrefix = "HT" + shortCode + datePart;
                Integer maxContractSeq = orderRepo.findMaxContractSeq(fullPrefix + "-%");
                order.contractNo = String.format("%s-%02d", fullPrefix, (maxContractSeq == null ? 0 : maxContractSeq) + 1);
            }
            order.status = "DRAFT";
            // v5.43.1 禁用物料不可销售下单（防绕过前端过滤）
            if (items != null) {
                for (SalesOrderItem it : items) {
                    materialService.assertUsable(it.materialCode, "销售下单");
                }
            }
            order.orderDate = LocalDate.now();
            BigDecimal total = BigDecimal.ZERO;
            SalesOrder saved = orderRepo.save(order);
            for (SalesOrderItem item : items) {
                item.orderId = saved.id;
                // v6.1.7：金额服务端按 单价×数量 重算（不信任前端传值，与 update 同口径）
                item.amount = (item.unitPrice != null && item.qty != null) ? item.unitPrice.multiply(item.qty) : null;
                if (item.amount != null) total = total.add(item.amount);
                itemRepo.save(item);
            }
            saved.totalAmount = total;
            return orderRepo.save(saved);
        });
    }

    /** 确认订单（DRAFT → CONFIRMED，可发货） */
    @Transactional
    public SalesOrder confirm(Long id) {
        SalesOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可确认");
        if (itemRepo.findByOrderId(id).isEmpty())
            throw new IllegalArgumentException("订单无明细，不可确认");
        // v6.3 信用软拦截：确认时复核信用占用并留痕（不阻断——创建/确认两道前端已弹确认，此处落审计标记）
        try {
            var cc = financeService.creditCheck(order.customerId, order.totalAmount);
            order.creditExceeded = Boolean.TRUE.equals(cc.get("exceed"));
            if (Boolean.TRUE.equals(order.creditExceeded)) {
                String snapshot = "信用超额确认：应收欠款 " + cc.get("arBalance") + " + 本单 " + cc.get("orderAmount")
                        + "，超额度 " + cc.get("creditLimit") + "（确认时点快照）";
                log.warn("信用超额订单确认: 单号={} 客户={} 欠款={} 本单={} 额度={}", order.orderNo, order.customerName,
                        cc.get("arBalance"), cc.get("orderAmount"), cc.get("creditLimit"));
                SalesOrderChangeLog lg = new SalesOrderChangeLog();
                lg.orderId = order.id;
                lg.orderNo = order.orderNo;
                lg.detail = snapshot;
                lg.operator = "系统";
                changeLogRepo.save(lg);   // v6.5 B6：超额留数字快照（原只有布尔标记，事后无从还原当时占用）
            }
        } catch (Exception e) {
            log.warn("信用复核跳过（不阻断确认）: {}", e.getMessage());
        }
        order.status = "CONFIRMED";
        order.updateTime = java.time.LocalDateTime.now();
        return orderRepo.save(order);
    }

    /** 删除订单（仅草稿，同时删除明细） */
    /**
     * v5.47 编辑销售订单（仅 DRAFT 可改，与删除同口径）+ 变更留痕：
     * 头字段差异与明细增删改全部记录到 sales_order_change_log；无变化不记。
     */
    @Transactional
    public SalesOrder update(Long id, SalesOrder in, List<SalesOrderItem> items, String operator) {
        return writeQueue.execute(() -> {
            SalesOrder order = orderRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
            if (!"DRAFT".equals(order.status)) throw new IllegalArgumentException("只有草稿状态的订单可编辑（已确认订单如需调整请另立新单）");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("请至少保留一条销售明细");
            for (SalesOrderItem it : items) {
                materialService.assertUsable(it.materialCode, "销售下单");
            }
            StringBuilder diff = new StringBuilder();
            if (in.expectedShipDate != null && !in.expectedShipDate.equals(order.expectedShipDate)) {
                diff.append("交期 ").append(order.expectedShipDate).append("→").append(in.expectedShipDate).append("；");
            }
            if (in.taxRate != null && in.taxRate.compareTo(order.taxRate == null ? java.math.BigDecimal.ZERO : order.taxRate) != 0) {
                diff.append("税率 ").append(order.taxRate).append("%→").append(in.taxRate).append("%；");
            }
            if (in.remark != null && !in.remark.equals(order.remark)) {
                diff.append("备注变更；");
            }
            // v6.1：摘要带单价——此前只比"物料x数量"，仅改单价时摘要相同被判为无变更提前返回，价格修改被静默丢弃
            List<SalesOrderItem> olds = itemRepo.findByOrderId(id);
            String oldSummary = olds.stream().map(i -> i.materialName + "x" + i.qty + "@" + i.unitPrice).reduce((a, b) -> a + ", " + b).orElse("无");
            String newSummary = items.stream().map(i -> i.materialName + "x" + i.qty + "@" + i.unitPrice).reduce((a, b) -> a + ", " + b).orElse("无");
            if (!oldSummary.equals(newSummary)) {
                diff.append("明细【").append(oldSummary).append("】→【").append(newSummary).append("】");
            }
            if (diff.length() == 0) return order;
            order.expectedShipDate = in.expectedShipDate;
            if (in.taxRate != null) order.taxRate = in.taxRate;
            order.remark = in.remark;
            order.updateTime = java.time.LocalDateTime.now();
            itemRepo.deleteAll(olds);
            itemRepo.flush();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (SalesOrderItem it : items) {
                it.id = null;
                it.orderId = id;
                // v6.1.7：金额一律服务端按 单价×数量 重算（原 amount 为空才补，传了就信任前端——一致性缺口）
                it.amount = (it.unitPrice != null && it.qty != null) ? it.unitPrice.multiply(it.qty) : null;
                if (it.amount != null) total = total.add(it.amount);
                itemRepo.save(it);
            }
            order.totalAmount = total;
            SalesOrder saved = orderRepo.save(order);
            SalesOrderChangeLog lg = new SalesOrderChangeLog();
            lg.orderId = id;
            lg.orderNo = order.orderNo;
            lg.detail = diff.toString();
            lg.operator = operator;
            changeLogRepo.save(lg);
            log.info("销售订单编辑留痕: {} {}", order.orderNo, diff);
            return saved;
        });
    }

    /** v5.47 订单变更记录 */
    public List<SalesOrderChangeLog> listChanges(Long orderId) {
        return changeLogRepo.findByOrderIdOrderByCreateTimeDesc(orderId);
    }

    @Transactional
    public void delete(Long id) {
        SalesOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可删除");
        itemRepo.deleteAll(itemRepo.findByOrderId(id));
        orderRepo.delete(order);
    }

    /**
     * v5.27：手工结束订单（DRAFT→CLOSED 不允许，草稿请直接删除；已确认/已发货可结束）
     * 结束后不可再发货、不可转生产/转委外
     */
    @Transactional
    public SalesOrder closeOrder(Long id, String operator, String reason) {
        SalesOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        if ("DRAFT".equals(order.status)) {
            throw new IllegalArgumentException("草稿状态的订单请直接删除，不需要结束");
        }
        if ("CLOSED".equals(order.status)) {
            throw new IllegalArgumentException("订单已结束");
        }
        // v6.8 短交完结正规化：存在未发完明细时原因必填，快照+原因入变更日志（事后可查"100 为什么只发 98"）
        java.math.BigDecimal ordered = java.math.BigDecimal.ZERO, shipped = java.math.BigDecimal.ZERO;
        for (SalesOrderItem it : itemRepo.findByOrderId(id)) {
            ordered = ordered.add(it.qty == null ? java.math.BigDecimal.ZERO : it.qty);
            shipped = shipped.add(it.shippedQty == null ? java.math.BigDecimal.ZERO : it.shippedQty);
        }
        boolean shortShipped = shipped.compareTo(ordered) < 0;
        if (shortShipped && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(String.format(
                    "短交完结必须填写原因：应发 %s 实发 %s，尚差 %s",
                    ordered.stripTrailingZeros().toPlainString(), shipped.stripTrailingZeros().toPlainString(),
                    ordered.subtract(shipped).stripTrailingZeros().toPlainString()));
        }
        order.status = "CLOSED";
        order.updateTime = java.time.LocalDateTime.now();
        orderRepo.save(order);
        if (shortShipped) {
            SalesOrderChangeLog lg = new SalesOrderChangeLog();
            lg.orderId = id;
            lg.orderNo = order.orderNo;
            lg.detail = String.format("短交完结：应发 %s 实发 %s 差 %s，原因：%s",
                    ordered.stripTrailingZeros().toPlainString(), shipped.stripTrailingZeros().toPlainString(),
                    ordered.subtract(shipped).stripTrailingZeros().toPlainString(), reason.trim());
            lg.operator = operator;
            changeLogRepo.save(lg);
        }
        return order;
    }

    /** 确认发货（出库） */
    @Transactional
    public void confirmShip(Long orderId, String operator) {
        SalesOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        if ("DRAFT".equals(order.status)) throw new IllegalArgumentException("订单尚未确认，不能直接发货");
        if ("SHIPPED".equals(order.status)) throw new IllegalArgumentException("订单已发货完成，无需重复发货");
        if ("CLOSED".equals(order.status)) throw new IllegalArgumentException("订单已结束，不能发货");
        List<SalesOrderItem> items = itemRepo.findByOrderId(orderId);
        for (SalesOrderItem item : items) {
            // 防漏账：无价明细随单发货后应收无法计价，必须先维护单价
            if (item.unitPrice == null || item.unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("明细 " + item.materialName + " 单价未维护（≤0），请先编辑订单维护价格再发货");
            }
        }
        for (SalesOrderItem item : items) {
            BigDecimal shipped = item.shippedQty == null ? BigDecimal.ZERO : item.shippedQty;
            if (shipped.compareTo(item.qty) < 0) {
                BigDecimal remain = item.qty.subtract(shipped);
                inventoryService.outbound("SALES_OUT", order.orderNo,
                        item.materialCode, null, order.sourceWarehouseId,
                        remain, operator);
                item.shippedQty = item.qty;
                itemRepo.save(item);
            }
        }
        order.status = "SHIPPED";
        order.updateTime = java.time.LocalDateTime.now();
        orderRepo.save(order);
    }

    /**
     * v5.27：销售订单一键转生产订单
     * 对明细中的半成品(B)/成品(C)各生成一张生产订单（草稿），材料(A/P/F/R/S)跳过；
     * 同一销售订单+同一产品只生成一次（防重复）。
     * @return {created: 生成数, skipped: 跳过数}
     */
    @Transactional
    public java.util.Map<String, Object> createProductionOrders(Long salesOrderId, String operator) {
        SalesOrder order = orderRepo.findById(salesOrderId)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        if ("DRAFT".equals(order.status)) {
            throw new IllegalArgumentException("草稿状态的销售订单不能转生产，请先确认订单");
        }
        if ("CLOSED".equals(order.status)) {
            throw new IllegalArgumentException("订单已结束，不能转生产");
        }
        List<SalesOrderItem> items = itemRepo.findByOrderId(salesOrderId);
        if (items.isEmpty()) throw new IllegalArgumentException("销售订单无明细，无法转生产");

        int created = 0, skipped = 0, noRecipe = 0;
        for (SalesOrderItem it : items) {
            if (it.materialCode == null || it.materialCode.isBlank()) { skipped++; continue; }
            // 按物料 category 判断生产对象：B=半成品(浆) C=成品(漆) 需生产；材料(A/P/F/R/S)不转
            // 注：半成品编码以 PJ 开头(首字符P)，不能按 materialCode 首字符判断，必须查物料 category
            Material mat = materialRepo.findByCode(it.materialCode).orElse(null);
            String cat = (mat != null && mat.category != null) ? mat.category : String.valueOf(it.materialCode.charAt(0));
            if (!"B".equals(cat) && !"C".equals(cat)) { skipped++; continue; }
            // 防重复：同一销售订单+同一产品已生成过则跳过
            if (productionOrderService.existsBySalesOrder(it.materialCode, order.orderNo)) { skipped++; continue; }

            ProductionOrder mo = new ProductionOrder();
            mo.productName = it.materialName;
            mo.productCode = it.materialCode;
            mo.batchQty = it.qty != null ? it.qty : BigDecimal.ZERO;
            mo.unit = (it.unit != null && !it.unit.isBlank()) ? it.unit : "kg";
            mo.salesOrderNo = order.orderNo;
            mo.remark = "来源销售订单 " + order.orderNo
                    + (order.customerName != null ? "（客户：" + order.customerName + "）" : "");
            mo.createdBy = operator;
            // v5.27：自动匹配产品配方（已发布版本），匹配到则关联版本并展开投料明细
            var recipeVersion = recipeService.findReleasedVersionByProductCode(it.materialCode);
            List<ProductionOrderItem> moItems = new java.util.ArrayList<>();
            if (recipeVersion != null) {
                mo.recipeVersionId = recipeVersion.id;
                moItems = expandRecipeItems(recipeVersion.id, it.qty);
            } else {
                noRecipe++;
            }
            productionOrderService.create(mo, moItems);
            created++;
        }
        return java.util.Map.of("created", created, "skipped", skipped, "noRecipe", noRecipe);
    }

    /**
     * v5.27：按配方版本展开为单据明细（半成品保留为一行，不展开原料）
     */
    private List<ProductionOrderItem> expandRecipeItems(Long versionId, BigDecimal qty) {
        List<ProductionOrderItem> items = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> row : recipeService.expandForOrder(versionId, qty)) {
            ProductionOrderItem item = new ProductionOrderItem();
            item.materialCode = (String) row.get("materialCode");
            item.materialName = (String) row.get("materialName");
            item.spec = (String) row.get("spec");
            item.unit = (String) row.get("unit");
            item.qty = (BigDecimal) row.get("qty");
            item.remark = (String) row.get("remark");
            item.nodeType = (String) row.get("nodeType");
            item.refRecipeId = row.get("refRecipeId") != null
                    ? Long.valueOf(row.get("refRecipeId").toString()) : null;
            items.add(item);
        }
        return items;
    }

    /**
     * v5.27：销售订单一键转委外订单
     * 对明细中的半成品(B)/成品(C)各生成一张委外订单（草稿），材料(A/P/F/R/S)跳过；
     * 委外必须指定代工厂（PROCESSOR 供应商）与加工费单价。
     * @return {created: 生成数, skipped: 跳过数}
     */
    @Transactional
    public java.util.Map<String, Object> createOutsourceOrders(Long salesOrderId, Long processorId,
                                                               String processorName, BigDecimal processingFee,
                                                               String operator) {
        if (processorName == null || processorName.isBlank()) {
            throw new IllegalArgumentException("请选择代工厂（转委外必须指定代加工供应商）");
        }
        // v5.27：委外加工费必填（委外入库后按加工费立应付，漏填将无应付可立）
        if (processingFee == null || processingFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("请填写委外加工费（委外入库后按此金额立应付）");
        }
        SalesOrder order = orderRepo.findById(salesOrderId)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        if ("DRAFT".equals(order.status)) {
            throw new IllegalArgumentException("草稿状态的销售订单不能转委外，请先确认订单");
        }
        if ("CLOSED".equals(order.status)) {
            throw new IllegalArgumentException("订单已结束，不能转委外");
        }
        List<SalesOrderItem> items = itemRepo.findByOrderId(salesOrderId);
        if (items.isEmpty()) throw new IllegalArgumentException("销售订单无明细，无法转委外");

        int created = 0, skipped = 0, noRecipe = 0;
        for (SalesOrderItem it : items) {
            if (it.materialCode == null || it.materialCode.isBlank()) { skipped++; continue; }
            // v6.1.1 修复：按物料 category 判 B=半成品 C=成品 可委外（与转生产同口径）——
            // 半成品编码以 PJ 开头，按 materialCode 首字符判断会把半成品全部 skip
            Material mat = materialRepo.findByCode(it.materialCode).orElse(null);
            String cat = (mat != null && mat.category != null) ? mat.category : String.valueOf(it.materialCode.charAt(0));
            if (!"B".equals(cat) && !"C".equals(cat)) { skipped++; continue; }
            // 防重复：同一销售订单+同一产品已生成过则跳过
            if (outsourceOrderService.existsBySalesOrder(it.materialCode, order.orderNo)) { skipped++; continue; }

            OutsourceOrder oo = new OutsourceOrder();
            oo.productName = it.materialName;
            oo.productCode = it.materialCode;
            oo.batchQty = it.qty != null ? it.qty : BigDecimal.ZERO;
            oo.unit = (it.unit != null && !it.unit.isBlank()) ? it.unit : "kg";
            oo.processor = processorName;
            oo.supplierId = processorId;
            oo.processingFee = processingFee;
            oo.salesOrderNo = order.orderNo;
            oo.remark = "来源销售订单 " + order.orderNo
                    + (order.customerName != null ? "（客户：" + order.customerName + "）" : "");
            oo.createdBy = operator;
            // v5.27：自动匹配产品配方（已发布版本），匹配到则关联版本并展开发料明细
            var recipeVersion = recipeService.findReleasedVersionByProductCode(it.materialCode);
            List<OutsourceOrderItem> ooItems = new java.util.ArrayList<>();
            if (recipeVersion != null) {
                oo.recipeVersionId = recipeVersion.id;
                for (java.util.Map<String, Object> row : recipeService.expandForOrder(recipeVersion.id, it.qty)) {
                    OutsourceOrderItem oi = new OutsourceOrderItem();
                    oi.materialCode = (String) row.get("materialCode");
                    oi.materialName = (String) row.get("materialName");
                    oi.spec = (String) row.get("spec");
                    oi.unit = (String) row.get("unit");
                    oi.qty = (BigDecimal) row.get("qty");
                    oi.remark = (String) row.get("remark");
                    oi.nodeType = (String) row.get("nodeType");
                    oi.refRecipeId = row.get("refRecipeId") != null
                            ? Long.valueOf(row.get("refRecipeId").toString()) : null;
                    ooItems.add(oi);
                }
            } else {
                noRecipe++;
            }
            outsourceOrderService.create(oo, ooItems);
            created++;
        }
        return java.util.Map.of("created", created, "skipped", skipped, "noRecipe", noRecipe);
    }
}
