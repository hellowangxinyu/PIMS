package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.ProductionOutbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductionOutboundRepository extends JpaRepository<ProductionOutbound, Long> {
    List<ProductionOutbound> findByOrderByCreateTimeDesc();
    List<ProductionOutbound> findByMaterialCode(String materialCode);
    List<ProductionOutbound> findByProductionOrderNoAndStatus(String productionOrderNo, String status);

    /** v4.8：按生产订单汇总实际材料成本（数据库 GROUP BY，替代全表拉取后内存累加） */
    @Query("SELECT p.productionOrderNo, SUM(p.cost) FROM ProductionOutbound p " +
           "WHERE p.status = 'CONFIRMED' AND p.cost IS NOT NULL GROUP BY p.productionOrderNo")
    List<Object[]> sumCostGroupByProductionOrderNo();

    /** 按生产订单汇总实际投入量（CONFIRMED 出库单领料总重，用于投入产出比；单位继承配方明细，当前均为 kg） */
    @Query("SELECT p.productionOrderNo, COALESCE(SUM(p.qty),0) FROM ProductionOutbound p " +
           "WHERE p.status = 'CONFIRMED' GROUP BY p.productionOrderNo")
    List<Object[]> sumConfirmedQtyGroupByOrderNo();

    /** 已领料（CONFIRMED 出库单）的生产订单号集合（可参照订单过滤，替代逐单查询 N+1） */
    @Query("SELECT DISTINCT p.productionOrderNo FROM ProductionOutbound p WHERE p.status = 'CONFIRMED' AND p.productionOrderNo IS NOT NULL")
    List<String> findConfirmedOrderNos();

    /** 实际领用 vs 配方标准用量（近N个月，按订单+物料）；标准用量取生产订单明细（配方展开量） */
    @Query(value = "SELECT po.production_order_no, po.product_name, po.material_code, po.material_name, SUM(po.qty) AS actual, " +
            "(SELECT SUM(i.qty) FROM production_order_item i JOIN production_order o2 ON i.order_id = o2.id " +
            " WHERE o2.order_no = po.production_order_no AND i.material_code = po.material_code) AS standard " +
            "FROM production_outbound po " +
            "WHERE po.status = 'CONFIRMED' AND po.create_time >= 1000 * (CAST(strftime('%s', ?1) AS INTEGER) - 28800) " +
            "GROUP BY po.production_order_no, po.material_code ORDER BY po.production_order_no DESC", nativeQuery = true)
    List<Object[]> usageVsStandardSince(String sinceDate);

    /** v5.9：关键字分页搜索（docNo/productName/materialCode/materialName/batchNo 模糊匹配） */
    @Query("SELECT o FROM ProductionOutbound o WHERE (:kw = '' OR o.docNo LIKE %:kw% OR o.productName LIKE %:kw% OR o.materialCode LIKE %:kw% OR o.materialName LIKE %:kw% OR o.batchNo LIKE %:kw%) ORDER BY o.createTime DESC")
    org.springframework.data.domain.Page<ProductionOutbound> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);
    /** v5.24：取指定前缀最大单号序号（并发防重 + 删除不错位，替代 count()+1） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(doc_no, -4) AS INTEGER)) FROM production_outbound WHERE doc_no LIKE ?1", nativeQuery = true)
    Integer findMaxSeq(String prefix);

    /**
     * v5.64 领料差异分析行级取数：订单配方明细（计划用量）LEFT JOIN 净领料聚合
     * （RETURN 负数行天然净额、CANCELLED 作废行自动剔除），只含有领料记录的订单。
     * 金额口径：计划金额=计划量×该行净领料加权单价（同价折算纯量差）、实际金额=Σcost。
     * 返回列：orderNo, productName, productCode, materialCode, materialName, unit,
     *         plannedQty, actualQty, plannedAmount, actualAmount
     */
    @Query(value = """
            SELECT po.order_no, po.product_name, COALESCE(po.product_code, po.product_name),
                   i.material_code, i.material_name, i.unit, i.qty,
                   COALESCE(a.actual_qty, 0), (i.qty * COALESCE(a.avg_price, 0)), COALESCE(a.actual_amount, 0)
            FROM production_order_item i
            JOIN production_order po ON po.id = i.order_id
            LEFT JOIN (
                SELECT production_order_no, material_code, SUM(qty) AS actual_qty,
                       SUM(COALESCE(cost, 0)) AS actual_amount,
                       CASE WHEN SUM(qty) > 0 THEN SUM(COALESCE(cost, 0)) / SUM(qty) ELSE NULL END AS avg_price
                FROM production_outbound WHERE status = 'CONFIRMED'
                GROUP BY production_order_no, material_code
            ) a ON a.production_order_no = po.order_no AND a.material_code = i.material_code
            WHERE EXISTS (SELECT 1 FROM production_outbound x
                          WHERE x.production_order_no = po.order_no AND x.status = 'CONFIRMED')
            """, nativeQuery = true)
    java.util.List<Object[]> findMaterialVarianceRows();

}
