package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.OutsourceOrder;
import com.pengyuan.pims.entity.OutsourceOrderItem;
import com.pengyuan.pims.entity.OutsourceFinishInbound;
import com.pengyuan.pims.repository.OutsourceOrderItemRepository;
import com.pengyuan.pims.repository.OutsourceOrderRepository;
import com.pengyuan.pims.repository.OutsourceFinishInboundRepository;
import com.pengyuan.pims.repository.SalesOrderRepository;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外订单（配方表）服务
 */
@Service
public class OutsourceOrderService {

    private static final Logger log = LoggerFactory.getLogger(OutsourceOrderService.class);

    private final OutsourceOrderRepository orderRepo;
    private final OutsourceOrderItemRepository itemRepo;
    private final OutsourceFinishInboundRepository inboundRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final InventoryLedgerRepository inventoryLedgerRepo;
    // v5.24：全局写锁（单号生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;

    public OutsourceOrderService(OutsourceOrderRepository orderRepo,
                                 OutsourceOrderItemRepository itemRepo,
                                 OutsourceFinishInboundRepository inboundRepo,
                                 SalesOrderRepository salesOrderRepo,
                                 InventoryLedgerRepository inventoryLedgerRepo,
                                 WriteQueue writeQueue) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.inboundRepo = inboundRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.inventoryLedgerRepo = inventoryLedgerRepo;
        this.writeQueue = writeQueue;
    }

    public List<OutsourceOrder> listAll() {
        List<OutsourceOrder> orders = orderRepo.findByOrderByCreateTimeDesc();
        fillDisplayStatus(orders);
        return orders;
    }

    public List<OutsourceOrder> listRecent(int limit) {
        List<OutsourceOrder> orders = orderRepo.findByOrderByCreateTimeDesc(PageRequest.of(0, limit)).getContent();
        fillDisplayStatus(orders);
        return orders;
    }

    public List<OutsourceOrder> listByStatus(String status) {
        List<OutsourceOrder> orders = orderRepo.findByStatusOrderByCreateTimeDesc(status);
        fillDisplayStatus(orders);
        return orders;
    }

    /**
     * v5.27：填充展示状态（优先级：已发货 > 已入库 > 已委外 > 已确认 > 草稿/已完工）
     * - 已发货：关联销售订单已发货(SHIPPED)
     * - 已入库：存在 DONE 状态委外入库单
     */
    private void fillDisplayStatus(List<OutsourceOrder> orders) {
        if (orders.isEmpty()) return;
        // 有无 DONE 入库用 GROUP BY 结果的键集合判断（此前 findAll 委外入库单全表加载）
        Map<String, Boolean> hasInbound = new HashMap<>();
        for (Object[] row : inboundRepo.sumDoneQtyGroupByOrderNo()) {
            hasInbound.put((String) row[0], true);
        }
        Map<String, String> soStatus = new HashMap<>();
        for (Object[] row : salesOrderRepo.orderNosAndStatus()) {
            soStatus.put((String) row[0], (String) row[1]);
        }
        for (OutsourceOrder o : orders) {
            String display = o.status;
            if (o.salesOrderNo != null && "SHIPPED".equals(soStatus.get(o.salesOrderNo))) {
                display = "SHIPPED";
            } else if (hasInbound.containsKey(o.orderNo)) {
                display = "INBOUND";
            }
            o.displayStatus = display;
        }
    }

    public OutsourceOrder getById(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("委外订单不存在"));
    }

    public List<OutsourceOrderItem> getItems(Long orderId) {
        return itemRepo.findByOrderId(orderId);
    }

    /** v5.27：销售订单转委外防重复（同一销售订单+同一产品是否已生成过） */
    public boolean existsBySalesOrder(String productCode, String salesOrderNo) {
        return orderRepo.existsBySalesOrderNoAndProductCode(salesOrderNo, productCode);
    }

    /**
     * 创建委外订单（含配方明细）
     */
    @Transactional
    public OutsourceOrder create(OutsourceOrder order, List<OutsourceOrderItem> items) {
        // v5.70.1 防呆：委外订单核心字段必填
        if (order.processor == null || order.processor.isBlank())
            throw new IllegalArgumentException("代工厂不能为空");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("委外明细不能为空");
        // v5.24：单号生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.execute(() -> {
            // v5.7：改按年度最大序号+1（count()+1 在删除记录后会错位导致单号重复）
            Integer maxSeq = orderRepo.findMaxOrderSeq("OO-" + LocalDate.now().toString().replace("-", "") + "-%");
            String orderNo = String.format("OO-%s-%04d", LocalDate.now().toString().replace("-", ""),
                    (maxSeq == null ? 0 : maxSeq) + 1);
            order.orderNo = orderNo;
            order.status = "DRAFT";
            order.createTime = LocalDateTime.now();
            orderRepo.save(order);

            if (items != null) {
                for (OutsourceOrderItem item : items) {
                    item.id = null;
                    item.orderId = order.id;
                    itemRepo.save(item);
                }
            }
            log.info("委外订单创建: {} 产品={} 批量={} 代工厂={}", orderNo, order.productName, order.batchQty, order.processor);
            return order;
        });
    }

    /**
     * 更新委外订单（含配方明细）—— 仅草稿状态可编辑
     */
    @Transactional
    public OutsourceOrder update(Long id, OutsourceOrder updated, List<OutsourceOrderItem> items) {
        OutsourceOrder order = getById(id);
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可编辑");

        order.productName = updated.productName;
        order.productCode = updated.productCode;
        order.batchQty = updated.batchQty;
        order.unit = updated.unit;
        order.processor = updated.processor;
        order.remark = updated.remark;
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);

        // 替换明细
        itemRepo.deleteByOrderId(id);
        if (items != null) {
            for (OutsourceOrderItem item : items) {
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
                if ("OUTSOURCED".equals(o.status)) {
                    o.scheduleSeq = seq[0]++;
                    orderRepo.save(o);
                }
            });
        }
    }

    /**
     * v5.27：排产（确认 → 已委外 OUTSOURCED，分配排产顺序号=当前最大+1，排到最后）
     * 排产前校验原材料库存（委外发料同样需要原料，不足则不允许排产）
     */
    @Transactional
    public OutsourceOrder schedule(Long id) {
        OutsourceOrder order = getById(id);
        if (!"CONFIRMED".equals(order.status))
            throw new IllegalArgumentException("只有已确认的订单可排产");
        checkMaterialStock(order);
        Integer maxSeq = orderRepo.findMaxScheduleSeq();
        order.scheduleSeq = (maxSeq == null ? 0 : maxSeq) + 1;
        order.status = "OUTSOURCED";
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        return order;
    }

    /** v5.27：原材料库存校验（发料明细用量 vs 库存总量，不足则列出全部缺口） */
    private void checkMaterialStock(OutsourceOrder order) {
        List<OutsourceOrderItem> items = itemRepo.findByOrderId(order.id);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("该订单没有发料明细（原料清单），无法校验库存，请先补充配方明细再排产");
        }
        List<String> shortages = new java.util.ArrayList<>();
        for (OutsourceOrderItem it : items) {
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
     * v5.27：取消排产（已委外 → 已确认，清除顺序号）
     */
    @Transactional
    public OutsourceOrder unschedule(Long id) {
        OutsourceOrder order = getById(id);
        if (!"OUTSOURCED".equals(order.status))
            throw new IllegalArgumentException("只有已委外的订单可取消排产");
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
        OutsourceOrder order = getById(id);
        if (!"OUTSOURCED".equals(order.status) || order.scheduleSeq == null)
            throw new IllegalArgumentException("只有已委外的订单可调整顺序");
        List<OutsourceOrder> scheduled = orderRepo.findByStatusOrderByScheduleSeqAsc("OUTSOURCED");
        int idx = -1;
        for (int i = 0; i < scheduled.size(); i++) {
            if (scheduled.get(i).id.equals(id)) { idx = i; break; }
        }
        int target = idx + direction;
        if (idx < 0 || target < 0 || target >= scheduled.size())
            throw new IllegalArgumentException("已到排产队列边界");
        OutsourceOrder other = scheduled.get(target);
        Integer tmp = order.scheduleSeq;
        order.scheduleSeq = other.scheduleSeq;
        other.scheduleSeq = tmp;
        orderRepo.save(order);
        orderRepo.save(other);
    }

    /**
     * 确认委外订单
     */
    @Transactional
    public OutsourceOrder confirm(Long id) {
        OutsourceOrder order = getById(id);
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可确认");
        // v5.70.1 防呆：无明细不能确认
        var confirmItems = itemRepo.findByOrderId(id);
        if (confirmItems == null || confirmItems.isEmpty())
            throw new IllegalArgumentException("委外订单 " + order.orderNo + " 无明细，不能确认");
        order.status = "CONFIRMED";
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        return order;
    }

    /**
     * 完工（委外加工完成）
     */
    @Transactional
    public OutsourceOrder complete(Long id) {
        OutsourceOrder order = getById(id);
        // v6.1.6：委外发料后状态为 OUTSOURCED（加工中）——完工必须放行，原只认 CONFIRMED 使发料后永远无法完工
        if (!"CONFIRMED".equals(order.status) && !"OUTSOURCED".equals(order.status))
            throw new IllegalArgumentException("只有已确认或加工中（已发料）的订单可完工");
        order.status = "COMPLETED";
        order.updateTime = LocalDateTime.now();
        orderRepo.save(order);
        return order;
    }

    @Transactional
    public void delete(Long id) {
        OutsourceOrder order = getById(id);
        if (!"DRAFT".equals(order.status))
            throw new IllegalArgumentException("只有草稿状态的订单可删除");
        itemRepo.deleteByOrderId(id);
        orderRepo.deleteById(id);
    }
}
