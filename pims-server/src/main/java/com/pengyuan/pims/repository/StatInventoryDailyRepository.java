package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.StatInventoryDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StatInventoryDailyRepository extends JpaRepository<StatInventoryDaily, Long> {

    /** 按物料查日汇总（倒序） */
    List<StatInventoryDaily> findByMaterialCodeOrderByStatDateDesc(String materialCode);

    /** 按仓库查日汇总（倒序） */
    List<StatInventoryDaily> findByWarehouseIdOrderByStatDateDesc(String warehouseId);

    /** 按月聚合：查某月所有物料的出入库汇总 */
    @Query(value = "SELECT stat_date, material_code, warehouse_id, in_qty, out_qty, in_amount " +
            "FROM stat_inventory_daily WHERE stat_date LIKE ?1 || '%' ORDER BY stat_date DESC", nativeQuery = true)
    List<StatInventoryDaily> findByMonth(String month);

    /** 查某物料某月汇总（聚合） */
    @Query(value = "SELECT COALESCE(SUM(in_qty),0), COALESCE(SUM(out_qty),0), COALESCE(SUM(in_amount),0) " +
            "FROM stat_inventory_daily WHERE material_code = ?1 AND stat_date LIKE ?2 || '%'", nativeQuery = true)
    Object[] sumByMaterialAndMonth(String materialCode, String month);

    /** 按月汇总全部物料的出入库量 */
    @Query(value = "SELECT substr(stat_date,1,7) AS period, COALESCE(SUM(in_qty),0), COALESCE(SUM(out_qty),0) " +
            "FROM stat_inventory_daily WHERE stat_date >= ?1 GROUP BY period ORDER BY period", nativeQuery = true)
    List<Object[]> monthlyInOutSince(String sinceDate);

    /** 物料吞吐量TOP10（按出入库总量排序） */
    @Query(value = "SELECT material_code, COALESCE(SUM(in_qty + out_qty),0) AS throughput " +
            "FROM stat_inventory_daily WHERE stat_date >= ?1 GROUP BY material_code ORDER BY throughput DESC LIMIT 10", nativeQuery = true)
    List<Object[]> topMaterialsSince(String sinceDate);
}
