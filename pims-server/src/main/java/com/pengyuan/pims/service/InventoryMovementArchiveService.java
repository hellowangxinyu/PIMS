package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 库存异动归档（v5.26）
 * 异动表只增不减（每笔出入库一条），长期运行会无限膨胀；
 * 30 天前的异动搬移到 inventory_movement_archive（追溯查询已按主表+归档表合并），
 * 归档保留 12 个月后清理。与操作日志归档同模式。
 */
@Service
public class InventoryMovementArchiveService {

    private static final Logger log = LoggerFactory.getLogger(InventoryMovementArchiveService.class);

    private static final int HOT_DAYS = 30;            // 主表保留 30 天热数据
    private static final int RETENTION_DAYS = 365;     // 归档保留 12 个月

    private final JdbcTemplate jdbc;
    private final WriteQueue writeQueue;

    public InventoryMovementArchiveService(JdbcTemplate jdbc, WriteQueue writeQueue) {
        this.jdbc = jdbc;
        this.writeQueue = writeQueue;
    }

    /** 启动时执行一次（幂等，每日凌晨还会执行） */
    @PostConstruct
    public void init() {
        try {
            archive();
        } catch (Exception e) {
            log.warn("启动异动归档检查失败: {}", e.getMessage());
        }
    }

    /** 每日凌晨 3:30 归档（与操作日志 3:00 错开） */
    @Scheduled(cron = "0 30 3 * * ?")
    public void archiveTask() {
        archive();
    }

    private void archive() {
        // v6.1.6：改 executeTx——INSERT 搬移与 DELETE 热表必须同事务（中途失败会双份/丢异动）
        writeQueue.executeTx(() -> {
            // 归档表幂等建表 + 索引（追溯查询按物料+批次合并，索引必备）
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inventory_movement_archive (
                    id integer PRIMARY KEY,
                    doc_type varchar(20) not null,
                    doc_no varchar(30) not null,
                    material_code varchar(30) not null,
                    batch_no varchar(30),
                    warehouse_id varchar(20) not null,
                    direction varchar(5) not null,
                    qty numeric(14,3) not null,
                    qty_before numeric(14,3),
                    qty_after numeric(14,3),
                    ownership_type varchar(20) not null,
                    location_id varchar(20),   -- v6.1.6：异动表含库位（追溯按库位）——原归档缺此列丢信息
                    operator varchar(50),
                    remark varchar(500),
                    create_time timestamp
                )
                """);
            // v6.1.6：存量归档表补列（新建表已含）
            var archCols = jdbc.queryForList("PRAGMA table_info(inventory_movement_archive)");
            if (!archCols.isEmpty() && archCols.stream().noneMatch(c -> "location_id".equals(c.get("name")))) {
                jdbc.execute("ALTER TABLE inventory_movement_archive ADD COLUMN location_id varchar(20)");
            }
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_archive_batch ON inventory_movement_archive(material_code, batch_no)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_movement_archive_time ON inventory_movement_archive(create_time)");

            // 30 天前的异动搬移到归档表（create_time 为毫秒时间戳）
            long threshold = System.currentTimeMillis() - HOT_DAYS * 86400_000L;
            int moved = jdbc.update("""
                INSERT INTO inventory_movement_archive (id, doc_type, doc_no, material_code, batch_no,
                    warehouse_id, location_id, direction, qty, qty_before, qty_after, ownership_type, operator, remark, create_time)
                SELECT id, doc_type, doc_no, material_code, batch_no,
                    warehouse_id, location_id, direction, qty, qty_before, qty_after, ownership_type, operator, remark, create_time
                FROM inventory_movement WHERE create_time < ?
                """, threshold);
            if (moved > 0) {
                jdbc.update("DELETE FROM inventory_movement WHERE create_time < ?", threshold);
                log.info("库存异动归档: 搬移 {} 条到归档表", moved);
            }

            // 清理超过 12 个月的归档
            long cutoff = System.currentTimeMillis() - RETENTION_DAYS * 86400_000L;
            int cleaned = jdbc.update("DELETE FROM inventory_movement_archive WHERE create_time < ?", cutoff);
            if (cleaned > 0) {
                log.info("库存异动归档: 清理 {} 条过期归档", cleaned);
            }
        });
    }
}
