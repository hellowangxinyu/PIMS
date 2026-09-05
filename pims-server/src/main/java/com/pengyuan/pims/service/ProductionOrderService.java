package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.ProductionOrder;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.ProductionOrderItem;
import com.pengyuan.pims.entity.ProductionOutbound;
import com.pengyuan.pims.entity.ProductionInbound;
import com.pengyuan.pims.repository.ProductionOrderItemRepository;
import com.pengyuan.pims.repository.ProductionOrderRepository;
import com.pengyuan.pims.repository.ProductionOutboundRepository;
import com.pengyuan.pims.repository.ProductionInboundRepository;
import com.pengyuan.pims.repository.SalesOrderRepository;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产订单（配方表）服务
 */
@Service
public class ProductionOrderService {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrderService.class);

    private final ProductionOrderRepository orderRepo;
    private final com.pengyuan.pims.repository.ProductionOrderExceptionRepository exceptionRepo;   // v6.8 完工联动
    private final org.springframework.transaction.support.TransactionTemplate txTemplate;   // v6.9.1 建档独立事务
    private final ProductionOrderItemRepository itemRepo;
    private final ProductionOutboundRepository outboundRepo;
    private final ProductionInboundRepository inboundRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final InventoryLedgerRepository inventoryLedgerRepo;
    // v5.24：全局写锁（单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    private final com.pengyuan.pims.repository.MaterialRepository materialRepo;   // v5.72 油尾行校验

    public ProductionOrderService(ProductionOrderRepository orderRepo,
                                  ProductionOrderItemRepository itemRepo,
                                  ProductionOutboundRepository outboundRepo,
                                  ProductionInboundRepository inboundRepo,
                                  SalesOrderRepository salesOrderRepo,
                                  InventoryLedgerRepository inventoryLedgerRepo,
                                  WriteQueue writeQueue,
                                  com.pengyuan.pims.repository.MaterialRepository materialRepo,
                                     com.pengyuan.pims.repository.ProductionOrderExceptionRepository exceptionRepo,
                                     org.springframework.transaction.support.TransactionTemplate txTemplate) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.outboundRepo = outboundRepo;
        this.inboundRepo = inboundRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.inventoryLedgerRepo = inventoryLedgerRepo;
        this.writeQueue = writeQueue;
        this.materialRepo = materialRepo;
        this.exceptionRepo = exceptionRepo;
        this.txTemplate = txTemplate;
    }

    public List<ProductionOrder> listAll() {
        List<ProductionOrder> orders = orderRepo.findByOrderByCreateTimeDesc();
        fillDisplayStatus(orders);
        fillIoRatio(orders);
        return orders;
    }

    public List<ProductionOrder> listByStatus(String status) {
        List<ProductionOrder> orders = orderRepo.findByStatusOrderByCreateTimeDesc(status);
        fillDisplayStatus(orders);
        fillIoRatio(orders);
        return orders;
    }

    /**
     * v5.27：填充展示状态（按单据事件推导，优先级：已发货 > 已入库 > 已投料 > 已排产 > 已确认 > 草稿/已完工）
     * - 已发货：关联销售订单已发货(SHIPPED)
     * - 已入库：存在 DONE 状态生产入库单
     * - 已投料：存在 CONFIRMED 状态生产出库单（已领料）
     */
    private void fillDisplayStatus(List<ProductionOrder> orders) {
        if (orders.isEmpty()) return;
        // 有无领料/入库用 GROUP BY 结果的键集合判断（此前 findAll 两张单据全表加载，列表每次刷新都全量拉回）
        Map<String, Boolean> hasOutbound = new HashMap<>();
        for (Object[] row : outboundRepo.sumConfirmedQtyGroupByOrderNo()) {
            hasOutbound.put((String) row[0], true);
        }
        Map<String, Boolean> hasInbound = new HashMap<>();
        for (Object[] row : inboundRepo.sumDoneQtyGroupByOrderNo()) {
            hasInbound.put((String) row[0], true);
        }
        Map<String, String> soStatus = new HashMap<>();
        for (Object[] row : salesOrderRepo.orderNosAndStatus()) {
            soStatus.put((String) row[0], (String) row[1]);
        }
        for (ProductionOrder o : orders) {
            String display = o.status;
            if (o.salesOrderNo != null && "SHIPPED".equals(soStatus.get(o.salesOrderNo))) {
                display = "SHIPPED";
            } else if ("COMPLETED".equals(o.status)) {
                // 已完工优先于已入库：入库合格自动完工后，状态列显示"已完工"而非"已入库"（仅"已发货"可覆盖）
                display = "COMPLETED";
            } else if (hasInbound.containsKey(o.orderNo)) {
                display = "INBOUND";
            } else if (hasOutbound.containsKey(o.orderNo)) {
                display = "FEED";
            }
            o.displayStatus = display;
        }
    }

    /** 投入产出比阈值(%)：低于此值视为异常订单 */
    private static final BigDecimal IO_RATIO_THRESHOLD = new BigDecimal("95.00");

    /**
     * 计算并填充投入产出比（@Transient 字段，列表返回时实时计算）。
     * 投入 = 订单所有 CONFIRMED 出库单(领料) qty 之和；产出 = 订单所有 DONE 入库单 qty 之和。
     * ioRatio = 产出 / 投入 × 100；≥95% NORMAL，<95% ABNORMAL，投入为0 UNKNOWN。
     * 注：当前投入/产出单位均为 kg，直接按重量 SUM。
     */
    public void fillIoRatio(List<ProductionOrder> orders) {
        if (orders.isEmpty()) return;
        Map<String, BigDecimal> inputMap = new HashMap<>();
        for (Object[] row : outboundRepo.sumConfirmedQtyGroupByOrderNo()) {
            inputMap.put((String) row[0], toBd(row[1]));
        }
        Map<String, BigDecimal> outputMap = new HashMap<>();
        for (Object[] row : inboundRepo.sumDoneQtyGroupByOrderNo()) {
            outputMap.put((String) row[0], toBd(row[1]));
        }
        for (ProductionOrder o : orders) {
            BigDecimal in = inputMap.getOrDefault(o.orderNo, BigDecimal.ZERO);
            BigDecimal out = outputMap.getOrDefault(o.orderNo, BigDecimal.ZERO);
            o.inputQty = in;
            o.outputQty = out;
            if (in.compareTo(BigDecimal.ZERO) == 0) {
                o.ioRatio = null;
                o.ioStatus = "UNKNOWN";
            } else {
                o.ioRatio = out.multiply(BigDecimal.valueOf(100)).divide(in, 2, RoundingMode.HALF_UP);
                o.ioStatus = o.ioRatio.compareTo(IO_RATIO_THRESHOLD) < 0 ? "ABNORMAL" : "NORMAL";
            }
        }
    }

    /** Object → BigDecimal（兼容 SQLite 聚合 SUM 返回 Integer/Long/Double/BigDecimal） */
    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(o.toString());
    }

    public ProductionOrder getById(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("生产订单不存在"));
    }

    public List<ProductionOrderItem> getItems(Long orderId) {
        return itemRepo.findByOrderId(orderId);
    }

    /** v5.27：销售订单转生产防重复（同一销售订单+同一产品是否已生成过） */
    public boolean existsBySalesOrder(String productCode, String salesOrderNo) {
        return orderRepo.existsBySalesOrderNoAndProductCode(salesOrderNo, productCode);
    }

    /**
     * 创建生产订单（含配方明细）
     */
    // v6.1.6 清死注解（private 方法 @Transactional 无效）
    /**
     * v5.72：油尾行校验——油尾只能在生产订单添加，且订单产品必须是 C 类成品漆；
     * 油尾物料本身必须是 C 类成品、主材体系与订单产品一致且（编码相同或色系相同）。
     */
    private void validateTailingItems(ProductionOrder order, List<ProductionOrderItem> items) {
        if (items == null) return;
        boolean hasTail = items.stream().anyMatch(i -> "OIL_TAIL".equals(i.nodeType));
        if (!hasTail) return;
        Material product = order.productCode != null ? materialRepo.findByCode(order.productCode).orElse(null) : null;
        if (product == null || !"C".equals(product.category)) {
            throw new IllegalArgumentException("油尾只能添加在生产成品漆（C 类产品）的生产订单中，当前订单产品不是成品漆");
        }
        for (ProductionOrderItem item : items) {
            if (!"OIL_TAIL".equals(item.nodeType)) continue;
            if (item.materialCode == null || item.materialCode.isBlank())
                throw new IllegalArgumentException("油尾行缺少物料编码");
            Material tail = materialRepo.findByCode(item.materialCode).orElse(null);
            if (tail == null || !"C".equals(tail.category))
                throw new IllegalArgumentException("油尾「" + item.materialName + "」不是成品物料（C 类），不能作为油尾加入");
            String tailMain = tail.mainMaterial != null ? tail.mainMaterial : "";
            String prodMain = product.mainMaterial != null ? product.mainMaterial : "";
            if (!tailMain.equals(prodMain))
                throw new IllegalArgumentException("油尾「" + tail.name + "」体系为" + tailMain + "，与订单产品体系" + prodMain + "不一致，不可加入");
            boolean sameCode = item.materialCode.equals(order.productCode);
            String ts = tail.colorSeries != null ? tail.colorSeries : "";
            String ps = product.colorSeries != null ? product.colorSeries : "";
            if (!sameCode && !( !ts.isBlank() && ts.equals(ps) ))
                throw new IllegalArgumentException("油尾「" + tail.name + "」与产品编码不同且色系不同，不可加入");
        }
    }

    public ProductionOrder create(ProductionOrder order, List<ProductionOrderItem> items) {
        // v5.70.1 防呆：生产订单核心字段必填
        if (order.productName == null || order.productName.isBlank())
            throw new IllegalArgumentException("产品名称不能为空");
        if (order.batchQty == null || order.batchQty.doubleValue() <= 0)
            throw new IllegalArgumentException("生产批量必须大于 0");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("配方明细不能为空（请先绑定配方）");
        validateTailingItems(order, items);   // v5.72：油尾行仅限生产成品漆(C类)的订单
        // v5.24：单号生成+保存整体排队；v6.1.6 改 executeTx——头存了而明细失败时残留半单（原无事务包裹）
        return writeQueue.executeTx(() -> {
            // v5.7：改按年度最大序号+1（count()+1 在删除记录后会错位导致单号重复）
            Integer maxSeq = orderRepo.findMaxOrderSeq("MO-" + LocalDate.now().toString().replace("-", "") + "-%");
            String orderNo = String.format("MO-%s-%04d", LocalDate.now().toString().replace("-", ""),
                    (maxSeq == null ? 0 : maxSeq) + 1);
            order.orderNo = orderNo;
            order.status = "DRAFT";
            order.createTime = LocalDateTime.now();
            orderRepo.save(order);

            if (items != null) {
                for (ProductionOrderItem item : items) {
                    item.id = null;
                    item.orderId = order.id;
                    itemRepo.save(item);
                }
            }
            log.info("生产订单创建: {} 产品={} 批量={}", orderNo, order.productName, order.batchQty);
            return order;
        });
    }

    /**
     * 更新生产订单（含配方明细）—— 仅草稿状态可编辑
     */
    @Transactional
    public ProductionOrder update(Long id, ProductionOrder updated, List<ProductionOrderItem> items) {
        ProductionOrder order = getById(id);
        validateTailingItems(updated, items);   // v5.72：油尾行仅限生产成品漆(C类)的订单
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可编辑");

        order.productName = updated.productName;
        order.productCode = updated.productCode;
        order.batchQty = updated.batchQty;
        order.unit = updated.unit;
        order.remark = updated.remark;
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);

        // 替换明细
        itemRepo.deleteByOrderId(id);
        if (items != null) {
            for (ProductionOrderItem item : items) {
                item.id = null;
                item.orderId = id;
                itemRepo.save(item);
            }
        }
        return order;
    }

    /**
     * v5.27：批量重排排产顺序（拖拽排序后整队列重写顺序号 1..n）
     * @param ids 按新顺序排列的订单ID列表
     */
    @Transactional
    public void reorderSchedule(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        int[] seq = {1};
        for (Long id : ids) {
            orderRepo.findById(id).ifPresent(o -> {
                if ("SCHEDULED".equals(o.status)) {
                    o.scheduleSeq = seq[0]++;
                    orderRepo.save(o);
                }
            });
        }
    }

    /**
     * v5.27：排产（确认 → 已排产 SCHEDULED，分配排产顺序号=当前最大+1，排到最后）
     * 排产前校验原材料库存：配方明细中任一原料库存不足则不允许排产（一次性列出所有不足项）
     */
    @Transactional
    public ProductionOrder schedule(Long id) {
        ProductionOrder order = getById(id);
        if (!"CONFIRMED".equals(order.status))
            throw new IllegalArgumentException("只有已确认的订单可排产");
        checkMaterialStock(order);
        // 分配排产顺序号（当前已排产单子的最大序号+1）
        Integer maxSeq = orderRepo.findMaxScheduleSeq();
        order.scheduleSeq = (maxSeq == null ? 0 : maxSeq) + 1;
        order.status = "SCHEDULED";
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        return order;
    }

    /** v5.27：原材料库存校验（明细用量 vs 库存总量，不足则列出全部缺口） */
    private void checkMaterialStock(ProductionOrder order) {
        List<ProductionOrderItem> items = itemRepo.findByOrderId(order.id);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("该订单没有配方明细（原料清单），无法校验库存，请先补充配方明细再排产");
        }
        List<String> shortages = new java.util.ArrayList<>();
        for (ProductionOrderItem it : items) {
            if (it.materialCode == null || it.materialCode.isBlank()) continue;
            BigDecimal need = it.qty != null ? it.qty : BigDecimal.ZERO;
            BigDecimal stock = inventoryLedgerRepo.sumQtyByMaterialCode(it.materialCode);
            if (stock == null) stock = BigDecimal.ZERO;
            if (need.compareTo(stock) > 0) {
                shortages.add(String.format("%s(%s) 需 %s%s，库存 %s%s",
                        it.materialName == null ? it.materialCode : it.materialName,
                        it.materialCode, need.stripTrailingZeros().toPlainString(),
                        it.unit == null ? "" : it.unit,
                        stock.stripTrailingZeros().toPlainString(),
                        it.unit == null ? "" : it.unit));
            }
        }
        if (!shortages.isEmpty()) {
            throw new IllegalArgumentException("原材料不足，无法排产：\n" + String.join("\n", shortages)
                    + "\n请先补足库存（采购/入库）后再排产");
        }
    }

    /**
     * v5.27：取消排产（已排产 → 已确认，清除顺序号）
     */
    @Transactional
    public ProductionOrder unschedule(Long id) {
        ProductionOrder order = getById(id);
        if (!"SCHEDULED".equals(order.status))
            throw new IllegalArgumentException("只有已排产的订单可取消排产");
        order.status = "CONFIRMED";
        order.scheduleSeq = null;
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        return order;
    }

    /**
     * v5.27：调整排产顺序（上移/下移，与相邻单子交换顺序号）
     * @param direction 1=下移(往后排)，-1=上移(往前排)
     */
    @Transactional
    public void moveSchedule(Long id, int direction) {
        ProductionOrder order = getById(id);
        if (!"SCHEDULED".equals(order.status) || order.scheduleSeq == null)
            throw new IllegalArgumentException("只有已排产的订单可调整顺序");
        List<ProductionOrder> scheduled = orderRepo.findByStatusOrderByScheduleSeqAsc("SCHEDULED");
        int idx = -1;
        for (int i = 0; i < scheduled.size(); i++) {
            if (scheduled.get(i).id.equals(id)) { idx = i; break; }
        }
        int target = idx + direction;
        if (idx < 0 || target < 0 || target >= scheduled.size())
            throw new IllegalArgumentException("已到排产队列边界");
        ProductionOrder other = scheduled.get(target);
        Integer tmp = order.scheduleSeq;
        order.scheduleSeq = other.scheduleSeq;
        other.scheduleSeq = tmp;
        orderRepo.save(order);
        orderRepo.save(other);
    }

    /**
     * 确认生产订单
     */
    @Transactional
    public ProductionOrder confirm(Long id) {
        ProductionOrder order = getById(id);
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可确认");
        // v5.70.1 防呆：无配方明细不能确认
        var confirmItems = itemRepo.findByOrderId(id);
        if (confirmItems == null || confirmItems.isEmpty())
            throw new IllegalArgumentException("生产订单 " + order.orderNo + " 无配方明细，不能确认");
        order.status = "CONFIRMED";
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        return order;
    }

    /**
     * 完工（生产完成）
     */
    @Transactional
    public ProductionOrder complete(Long id, String reason) {
        ProductionOrder order = getById(id);
        // v6.8：SCHEDULED（已排产/已领料）也允许按实际完结——质检不合格不返工、产出短量等场景的正式出口；
        // 带 reason 必填，若投出比异常且尚无处置记录，自动建异常订单记录（PENDING）要求闭环
        boolean scheduled = "SCHEDULED".equals(order.status);
        if (!"CONFIRMED".equals(order.status) && !scheduled)
            throw new IllegalArgumentException("只有已确认或已排产的订单可完工");
        if (scheduled && (reason == null || reason.isBlank()))
            throw new IllegalArgumentException("排产中的订单按实际完结必须填写原因（如：质检不合格客户让步、短量产出完结）");
        order.status = "COMPLETED";
        if (scheduled) {
            // v6.9.1 修复：reason 判空防"按实际完结：null"（旧调用路径无原因时）
            order.remark = (order.remark == null || order.remark.isBlank() ? "" : order.remark + "；")
                    + "按实际完结：" + (reason == null || reason.isBlank() ? "（未填原因）" : reason.trim());
        }
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        // v6.9.1 修复：投出比异常建档挪出主事务（独立事务模板）——
        // 原在同一 @Transactional 内 save，若建档抛错会把整个完工事务标 rollback-only，
        // "不影响完工"的 catch 形同虚设；独立事务下建档失败只丢建档，完工照常提交
        try {
            fillIoRatio(java.util.List.of(order));
            if (order.ioStatus != null && order.ioStatus.contains("ABNORMAL")
                    && exceptionRepo.findByOrderNo(order.orderNo).isEmpty()) {
                final String why = scheduled ? ("按实际完结：" + (reason == null || reason.isBlank() ? "未填原因" : reason.trim()))
                        : "手动完工（投出比异常联动）";
                final var o = order;
                txTemplate.executeWithoutResult(tx -> {
                    var ex = new com.pengyuan.pims.entity.ProductionOrderException();
                    ex.orderNo = o.orderNo;
                    ex.ioRatio = o.ioRatio;
                    ex.inputQty = o.inputQty;
                    ex.outputQty = o.outputQty;
                    ex.reason = why;
                    ex.status = "PENDING";
                    ex.createdBy = "系统";
                    exceptionRepo.save(ex);
                });
                log.warn("订单 {} 完工且投出比异常（{}%），已自动建异常订单记录待处置", order.orderNo, order.ioRatio);
            }
        } catch (Exception e) {
            log.warn("完工联动异常订单建档失败（完工已提交，仅丢失建档）: {}", e.getMessage());
        }
        return order;
    }

    /** 兼容旧调用（无原因完结，仅 CONFIRMED） */
    @Transactional
    public ProductionOrder complete(Long id) {
        return complete(id, null);
    }

    /**
     * 入库合格自动完工：生产入库单质检合格(置 DONE)时触发。
     * 与手动 complete() 区别：放宽到 SCHEDULED 也可完工（手动仅限 CONFIRMED）；已 COMPLETED 幂等跳过。
     * 静默处理：订单不存在/状态不符只记日志不抛异常，避免影响质检主流程。
     */
    @Transactional
    public void autoCompleteByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return;
        ProductionOrder order = orderRepo.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            log.warn("自动完工跳过：订单不存在 orderNo={}", orderNo);
            return;
        }
        if ("COMPLETED".equals(order.status)) return; // 幂等
        if (!"CONFIRMED".equals(order.status) && !"SCHEDULED".equals(order.status)) {
            log.warn("自动完工跳过：订单状态非 CONFIRMED/SCHEDULED orderNo={} status={}", orderNo, order.status);
            return;
        }
        order.status = "COMPLETED";
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        log.info("生产订单自动完工（入库合格触发）：{} 产品={}", order.orderNo, order.productName);
        // v6.9.1：自动完工同套投出比建档——短量产出但质检合格的单不再绕过异常闭环
        try {
            fillIoRatio(java.util.List.of(order));
            if (order.ioStatus != null && order.ioStatus.contains("ABNORMAL")
                    && exceptionRepo.findByOrderNo(order.orderNo).isEmpty()) {
                final var o = order;
                txTemplate.executeWithoutResult(tx -> {
                    var ex = new com.pengyuan.pims.entity.ProductionOrderException();
                    ex.orderNo = o.orderNo;
                    ex.ioRatio = o.ioRatio;
                    ex.inputQty = o.inputQty;
                    ex.outputQty = o.outputQty;
                    ex.reason = "入库合格自动完工（投出比异常联动）";
                    ex.status = "PENDING";
                    ex.createdBy = "系统";
                    exceptionRepo.save(ex);
                });
                log.warn("订单 {} 自动完工且投出比异常（{}%），已建异常订单记录待处置", order.orderNo, order.ioRatio);
            }
        } catch (Exception e) {
            log.warn("自动完工联动建档失败（完工已提交）: {}", e.getMessage());
        }
    }

    /**
     * 启动时补全：存在合格入库单(DONE)但未完工的订单自动完工。
     * （历史数据：上线本功能前已入库但未点完工的订单一次性补齐，使"已入库即进入已完工 Tab"对历史数据同样生效）
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void autoCompleteInboundOrdersOnStartup() {
        Set<String> inboundOrders = new HashSet<>();
        for (Object[] row : inboundRepo.sumDoneQtyGroupByOrderNo()) {
            inboundOrders.add((String) row[0]);
        }
        int count = 0;
        for (ProductionOrder o : orderRepo.findAll()) {
            if (!"COMPLETED".equals(o.status) && inboundOrders.contains(o.orderNo)) {
                autoCompleteByOrderNo(o.orderNo);
                count++;
            }
        }
        if (count > 0) log.info("启动补全：{} 个已入库未完工订单自动完工", count);
    }

    @Transactional
    public void delete(Long id) {
        ProductionOrder order = getById(id);
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可删除");
        itemRepo.deleteByOrderId(id);
        orderRepo.deleteById(id);
    }
}
