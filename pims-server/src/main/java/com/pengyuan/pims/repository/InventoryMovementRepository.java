package com.pengyuan.pims.repository;

import com.pengyuan.pims.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** 按物料编码查异动日志 */
    List<InventoryMovement> findByMaterialCodeOrderByCreateTimeDesc(String materialCode);

    /** 按单据号查异动 */
    List<InventoryMovement> findByDocNo(String docNo);

    /** 按物理仓查异动 */
    List<InventoryMovement> findByWarehouseIdOrderByCreateTimeDesc(String warehouseId);

    /** 按物料+批次查异动（追溯链用） */
    List<InventoryMovement> findByMaterialCodeAndBatchNoOrderByCreateTime(
            String materialCode, String batchNo);

    /** v5.7：取指定日期前缀的最大批号序号（与台账合并计算，保证批号全局不重复） */
    @Query(value = "SELECT MAX(CAST(SUBSTR(batch_no, INSTR(batch_no,'-')+1) AS INTEGER)) FROM inventory_movement WHERE batch_no LIKE ?1", nativeQuery = true)
    Integer findMaxBatchSeq(String prefix);

    /** v4.8：按物料+批次查异动（主表 + 归档表合并，2 年前已归档的批次仍可追溯） */
    @Query(value = """
            SELECT * FROM inventory_movement WHERE material_code = ?1 AND batch_no = ?2
            UNION ALL
            SELECT * FROM inventory_movement_archive WHERE material_code = ?1 AND batch_no = ?2
            ORDER BY create_time
            """, nativeQuery = true)
    List<InventoryMovement> findByMaterialCodeAndBatchNoIncludingArchive(
            String materialCode, String batchNo);

    /** v5.26：按物料查异动日志（主表 + 归档表合并，归档后历史异动仍可查看） */
    @Query(value = """
            SELECT * FROM inventory_movement WHERE material_code = ?1
            UNION ALL
            SELECT * FROM inventory_movement_archive WHERE material_code = ?1
            ORDER BY create_time DESC
            """, nativeQuery = true)
    List<InventoryMovement> findByMaterialCodeIncludingArchive(String materialCode);
}
