package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {

    /** 按物料编码+批次+物理仓+库位查唯一台账记录 */
    Optional<InventoryLedger> findByMaterialCodeAndBatchNoAndWarehouseIdAndLocationId(
            String materialCode, String batchNo, String warehouseId, String locationId);

    /** 按物料编码+物理仓+库位查台账（批次为NULL） */
    Optional<InventoryLedger> findByMaterialCodeAndBatchNoIsNullAndWarehouseIdAndLocationId(
            String materialCode, String warehouseId, String locationId);

    /** 按物料编码+批次+物理仓查唯一台账记录（无库位） */
    Optional<InventoryLedger> findByMaterialCodeAndBatchNoAndWarehouseIdAndLocationIdIsNull(
            String materialCode, String batchNo, String warehouseId);

    /** 按物料编码+物理仓查台账（批次和库位为NULL） */
    Optional<InventoryLedger> findByMaterialCodeAndBatchNoIsNullAndWarehouseIdAndLocationIdIsNull(
            String materialCode, String warehouseId);

    /** 按物料编码查所有仓库的库存 */
    List<InventoryLedger> findByMaterialCode(String materialCode);

    /** 按物料集合批量查台账（出库/预检批量路径一次往返，替代逐物料查询的 N+1） */
    List<InventoryLedger> findByMaterialCodeIn(Iterable<String> materialCodes);

    /** v5.27：按质检单号查入库台账（退货参照到货单时反查库存批号） */
    List<InventoryLedger> findByQcInspectionNo(String qcInspectionNo);

    /** 有质检单号的台账行（批量预取分组用，替代逐单反查的 N+1） */
    List<InventoryLedger> findByQcInspectionNoIsNotNull();

    /** v5.18：按物料编码查有单价的入库记录（按入库时间升序，价格走势图数据源之一） */
    List<InventoryLedger> findByMaterialCodeAndUnitPriceIsNotNullOrderByCreateTimeAsc(String materialCode);

    /** v5.7：批号全局查重（生成新批号前兜底防重复） */
    boolean existsByBatchNo(String batchNo);

    /** 全部已有批号集合（期初导入批量防重，替代逐行 existsByBatchNo 的 N+1） */
    @Query("select l.batchNo from InventoryLedger l where l.batchNo is not null")
    List<String> findAllBatchNos();

    /** v5.7：取指定日期前缀的最大批号序号（服务重启后恢复当日序号，保证批号不重复） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(batch_no, INSTR(batch_no,'-')+1) AS INTEGER)) FROM inventory_ledger WHERE batch_no LIKE ?1", nativeQuery = true)
    Integer findMaxBatchSeq(String prefix);

    /** 按物料+物理仓查库存（批次选择用，v5.4） */
    List<InventoryLedger> findByMaterialCodeAndWarehouseId(String materialCode, String warehouseId);

    /** 按物理仓查所有物料库存 */
    List<InventoryLedger> findByWarehouseId(String warehouseId);

    /** 按物料编码+批次查所有物理仓 */
    List<InventoryLedger> findByMaterialCodeAndBatchNo(String materialCode, String batchNo);

    /**
     * 按物料+批次+物理仓查所有库位的台账行（用于未指定库位时的 FIFO 出库扣减）。
     * 按入库日期升序、id 升序，保证先入库的先扣减。
     */
    List<InventoryLedger> findByMaterialCodeAndBatchNoAndWarehouseIdOrderByInboundDateAscIdAsc(
            String materialCode, String batchNo, String warehouseId);

    /** 芃远所有权总库存 = 所有仓的 qty 之和（ownershipType=PENGYUAN）；v5.30 排除质检不合格（不合格品库隔离） */
    @Query("SELECT COALESCE(SUM(l.qty), 0) FROM InventoryLedger l WHERE l.materialCode = ?1 AND l.ownershipType = 'PENGYUAN' " +
            "AND (l.qcStatus IS NULL OR l.qcStatus NOT IN ('REJECT','TAILING','EXPIRED'))")
    java.math.BigDecimal sumQtyByMaterialCode(String materialCode);

    /** 低库存预警：当前库存 <= 安全库存阈值（v5.30 排除质检不合格行） */
    @Query("SELECT l FROM InventoryLedger l WHERE l.ownershipType = 'PENGYUAN' AND l.qty <= 0 " +
            "AND (l.qcStatus IS NULL OR l.qcStatus NOT IN ('REJECT','TAILING','EXPIRED'))")
    List<InventoryLedger> findLowStock();

    /** 过期预警数据：在库且有过期日期、非不合格/油尾的台账行（v5.55 SQL 预过滤，替代 findAll 全表加载后内存过滤） */
    @Query("SELECT l FROM InventoryLedger l WHERE l.qty > 0 AND l.expiryDate IS NOT NULL " +
            "AND (l.qcStatus IS NULL OR l.qcStatus NOT IN ('REJECT','TAILING'))")
    List<InventoryLedger> findExpiryCandidates();

    /** 零/负库存台账行（含 qty 为 NULL 的历史行，仪表盘 0 库存补充行用，替代全表加载） */
    List<InventoryLedger> findByQtyLessThanEqualOrQtyIsNull(java.math.BigDecimal qty);

    /** 撞键检查：同物料+批次+仓+库位已有其他行占用（batch_no 为 NULL 按 IS NULL 匹配，替代 findAll 内存比对，命中 idx_ledger_lookup） */
    @Query(value = "SELECT COUNT(*) FROM inventory_ledger WHERE material_code = ?1 AND warehouse_id = ?2 " +
            "AND location_id = ?3 AND id <> ?4 AND ((?5 IS NULL AND batch_no IS NULL) OR batch_no = ?5)", nativeQuery = true)
    long countKeyConflict(String materialCode, String warehouseId, String locationId, Long excludeId, String batchNo);

    /** 按仓库聚合当前库存总量（v5.30 排除质检不合格行） */
    @Query(value = "SELECT warehouse_id, COALESCE(SUM(qty),0) FROM inventory_ledger " +
            "WHERE qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED') " +
            "GROUP BY warehouse_id ORDER BY SUM(qty) DESC", nativeQuery = true)
    List<Object[]> sumByWarehouse();

    // ==================== v5.9 报表聚合（替代 findAll 全内存聚合） ====================

    /** 按物料聚合当前库存量（低库存报表用，替代全表拉取；v5.30 排除质检不合格行） */
    @Query(value = "SELECT material_code, COALESCE(SUM(qty),0), MAX(unit) FROM inventory_ledger " +
            "WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED')) GROUP BY material_code", nativeQuery = true)
    List<Object[]> sumQtyGroupByMaterial();

    /** v5.33：按物料+仓库聚合当前库存量（低库存预警显示库存所在仓库；v5.30 排除质检不合格行） */
    @Query(value = "SELECT material_code, warehouse_id, COALESCE(SUM(qty),0) FROM inventory_ledger " +
            "WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED')) " +
            "GROUP BY material_code, warehouse_id", nativeQuery = true)
    List<Object[]> sumQtyGroupByMaterialAndWarehouse();

    /** 批次库龄分层（0-30/31-90/91-180/180+，数量+金额）；inbound_date 为毫秒，julianday 换算天数 */
    @Query(value = "SELECT CASE WHEN d <= 30 THEN 0 WHEN d <= 90 THEN 1 WHEN d <= 180 THEN 2 ELSE 3 END AS bucket, " +
            "COALESCE(SUM(qty),0), COALESCE(SUM(amount),0) FROM (" +
            "SELECT qty, amount, CAST(julianday('now','localtime') - julianday(date(inbound_date/1000,'unixepoch','+8 hours')) AS INTEGER) AS d " +
            "FROM inventory_ledger WHERE qty > 0 AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED'))) " +
            "GROUP BY bucket ORDER BY bucket", nativeQuery = true)
    List<Object[]> ageDistGroup();

    /** 库存金额按物料大类（LEFT JOIN material 取 category，无档案归'其他'） */
    @Query(value = "SELECT COALESCE(m.category, 'OTHER'), COALESCE(SUM(l.qty),0), COALESCE(SUM(l.amount),0) " +
            "FROM inventory_ledger l LEFT JOIN material m ON l.material_code = m.code " +
            "WHERE l.qty > 0 AND (l.qc_status IS NULL OR l.qc_status NOT IN ('REJECT','TAILING','EXPIRED')) GROUP BY COALESCE(m.category, 'OTHER')", nativeQuery = true)
    List<Object[]> categoryAmountGroup();

    /** 呆滞批次 TOP20：库龄天数 × 库存金额 降序 */
    @Query(value = "SELECT material_code, material_name, batch_no, d AS days, qty, amount, warehouse_id FROM (" +
            "SELECT material_code, material_name, batch_no, qty, COALESCE(amount, qty * unit_price) AS amount, warehouse_id, " +
            "CAST(julianday('now','localtime') - julianday(date(inbound_date/1000,'unixepoch','+8 hours')) AS INTEGER) AS d " +
            "FROM inventory_ledger WHERE qty > 0 AND amount > 0 AND inbound_date IS NOT NULL " +
            "AND (qc_status IS NULL OR qc_status NOT IN ('REJECT','TAILING','EXPIRED'))) " +
            "WHERE d > 0 ORDER BY d * amount DESC LIMIT 20", nativeQuery = true)
    List<Object[]> dormantTop20();

    /** v5.9：台账关键字分页搜索（品名/编码/批号 + 仓库过滤；v5.30 全局视图排除质检不合格行；v5.37 保留 EXPIRED 供台账追踪） */
    @Query("SELECT l FROM InventoryLedger l WHERE (:kw = '' OR l.materialCode LIKE %:kw% OR l.materialName LIKE %:kw% OR l.batchNo LIKE %:kw%) " +
            "AND (:wh = '' OR l.warehouseId = :wh) " +
            "AND (:wh <> '' OR l.qcStatus IS NULL OR l.qcStatus NOT IN ('REJECT','TAILING')) " +
            "ORDER BY l.materialCode, l.batchNo")
    org.springframework.data.domain.Page<InventoryLedger> searchByKeyword(
            @org.springframework.data.repository.query.Param("kw") String kw,
            @org.springframework.data.repository.query.Param("wh") String wh,
            org.springframework.data.domain.Pageable pageable);

    /** v5.59 质量追溯选批次：关键字搜台账（编码/品名/批号）。与 searchByKeyword 不同——不过滤仓库、
     *  不排除质检不合格/油尾行：品控追溯的对象批次恰恰常是 REJECT/EXPIRED 的 */
    @Query("SELECT l FROM InventoryLedger l WHERE (:kw = '' OR l.materialCode LIKE %:kw% OR l.materialName LIKE %:kw% OR l.batchNo LIKE %:kw%) " +
            "ORDER BY l.materialCode, l.batchNo")
    org.springframework.data.domain.Page<InventoryLedger> searchForQualityTrace(
            @org.springframework.data.repository.query.Param("kw") String kw,
            org.springframework.data.domain.Pageable pageable);

    // ==================== v5.22 库存查询双视图（按编码聚合 / 按批次聚合） ====================    // 返回列：material_code, material_name, unit, batch_cnt, qty, available_qty, unit_price, amount, last_inbound_date, max_expiry_date
    // 列下标:    0             1              2    3          4    5               6           7       8                    9

    /** 按编码聚合（跨批次/库位合计总量）：编码/品名/单位/批次数量/库存量/可用量/均价/总价/最新入库日期 */
    // v5.32：文本聚合列包 COALESCE(...,'')——sqlite-jdbc 按首行值推断表达式列类型，首行为 NULL 时按数值读，遇文本值炸 Bad value for type BigDecimal（CAST 无效，必须保证首行非 NULL 文本）
    @Query(value = "SELECT l.material_code, COALESCE(MAX(l.material_name),''), COALESCE(MAX(l.unit),''), COUNT(DISTINCT l.batch_no), " +
            "COALESCE(SUM(l.qty),0), COALESCE(SUM(l.available_qty),0), " +
            "CASE WHEN SUM(l.qty)=0 THEN 0 ELSE COALESCE(SUM(l.amount), SUM(l.qty*l.unit_price))/SUM(l.qty) END, " +
            "COALESCE(SUM(l.amount), SUM(l.qty*l.unit_price)), MAX(l.inbound_date) " +
            "FROM inventory_ledger l " +
            "WHERE (:kw = '' OR l.material_code LIKE %:kw% OR l.material_name LIKE %:kw%) " +
            "AND (:wh = '' OR l.warehouse_id = :wh) " +
            "AND (:wh <> '' OR l.qc_status IS NULL OR l.qc_status NOT IN ('REJECT','TAILING','EXPIRED')) " +
            "GROUP BY l.material_code HAVING SUM(l.qty) > 0 ORDER BY l.material_code",
            countQuery = "SELECT COUNT(*) FROM (SELECT 1 FROM inventory_ledger l " +
                    "WHERE (:kw = '' OR l.material_code LIKE %:kw% OR l.material_name LIKE %:kw%) " +
                    "AND (:wh = '' OR l.warehouse_id = :wh) " +
                    "AND (:wh <> '' OR l.qc_status IS NULL OR l.qc_status NOT IN ('REJECT','TAILING','EXPIRED')) " +
                    "GROUP BY l.material_code HAVING SUM(l.qty) > 0)",
            nativeQuery = true)
    org.springframework.data.domain.Page<Object[]> sumByCode(
            @org.springframework.data.repository.query.Param("kw") String kw,
            @org.springframework.data.repository.query.Param("wh") String wh,
            org.springframework.data.domain.Pageable pageable);

    /** 按编码+批次聚合（同批次跨库位合计）：编码/品名/批号/单位/库存量/可用量/单价/总价/最早入库日期/最晚过期日期/库位/质检状态/质检单号/检测结果/检验员/检验日期 */
    // v5.32：文本聚合列包 COALESCE(...,'')（同上，MAX(qc_status) 首行 NULL 时被按数值读，遇 'PASS' 直接炸）
    @Query(value = "SELECT l.material_code, COALESCE(MAX(l.material_name),''), l.batch_no, COALESCE(MAX(l.unit),''), " +
            "COALESCE(SUM(l.qty),0), COALESCE(SUM(l.available_qty),0), MAX(l.unit_price), " +
            "COALESCE(SUM(l.amount), SUM(l.qty*l.unit_price)), MIN(l.inbound_date), MAX(l.expiry_date), " +
            "COALESCE(GROUP_CONCAT(DISTINCT COALESCE(NULLIF(l.location_name,''),'')),''), " +
            "COALESCE(MAX(l.qc_status),''), " +
            "COALESCE(MAX(l.qc_inspection_no),''), COALESCE(MAX(l.qc_result),''), COALESCE(MAX(l.qc_inspector),''), MAX(l.qc_date) " +
            "FROM inventory_ledger l " +
            "WHERE (:kw = '' OR l.material_code LIKE %:kw% OR l.material_name LIKE %:kw% OR l.batch_no LIKE %:kw%) " +
            "AND (:wh = '' OR l.warehouse_id = :wh) " +
            "AND (:wh <> '' OR l.qc_status IS NULL OR l.qc_status NOT IN ('REJECT','TAILING','EXPIRED')) " +
            "GROUP BY l.material_code, l.batch_no HAVING SUM(l.qty) > 0 ORDER BY l.material_code, l.batch_no",
            countQuery = "SELECT COUNT(*) FROM (SELECT 1 FROM inventory_ledger l " +
                    "WHERE (:kw = '' OR l.material_code LIKE %:kw% OR l.material_name LIKE %:kw% OR l.batch_no LIKE %:kw%) " +
                    "AND (:wh = '' OR l.warehouse_id = :wh) " +
                    "AND (:wh <> '' OR l.qc_status IS NULL OR l.qc_status NOT IN ('REJECT','TAILING','EXPIRED')) " +
                    "GROUP BY l.material_code, l.batch_no HAVING SUM(l.qty) > 0)",
            nativeQuery = true)
    org.springframework.data.domain.Page<Object[]> sumByBatch(
            @org.springframework.data.repository.query.Param("kw") String kw,
            @org.springframework.data.repository.query.Param("wh") String wh,
            org.springframework.data.domain.Pageable pageable);
}
