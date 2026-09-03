package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存台账（INV-011）
 * 按「物料编码 + 批次 + 物理仓」三元组记录库存余额
 * 所有权与物理位置分离建模
 */
@Entity
@Table(name = "inventory_ledger",
    uniqueConstraints = @UniqueConstraint(columnNames = {"materialCode", "batchNo", "warehouseId", "locationId"}))
public class InventoryLedger {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 30) public String materialCode;

    @Column(length = 100) public String materialName;

    @Column(length = 30) public String batchNo;

    /** 所有权: PENGYUAN(芃远所有) / PROCESSOR_HOLDING(代工厂代持) */
    @Column(nullable = false, length = 20) public String ownershipType = "PENGYUAN";

    @Column(nullable = false, length = 20) public String warehouseId;

    /** 库位ID */
    @Column(length = 20) public String locationId;

    /** 分库名称 */
    @Column(length = 50) public String zoneName;

    /** 库位名称 */
    @Column(length = 50) public String locationName;

    @Column(precision = 14, scale = 3) public BigDecimal qty = BigDecimal.ZERO;

    @Column(length = 10) public String unit;

    @Column(precision = 14, scale = 3) public BigDecimal availableQty = BigDecimal.ZERO;

    /** v5.9：占用/在途功能未启用（始终 0），不随列表接口序列化，减小响应体积 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(precision = 14, scale = 3) public BigDecimal occupiedQty = BigDecimal.ZERO;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(precision = 14, scale = 3) public BigDecimal inTransitQty = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2) public BigDecimal unitPrice;

    @Column(precision = 14, scale = 2) public BigDecimal amount = BigDecimal.ZERO;

    /** 生产日期 */
    public LocalDate produceDate;

    /** 过期日期 */
    public LocalDate expiryDate;

    /** 入库日期（库龄起算） */
    public LocalDate inboundDate;

    /** 质检状态: PASS(合格) / CONCESSION(让步接收)，入库即合格；null 表示历史存量未质检 */
    @Column(length = 20) public String qcStatus;

    /** 检测结果（质检单的 resultRemark） */
    @Column(length = 500) public String qcResult;

    /** 质检单号 */
    @Column(length = 30) public String qcInspectionNo;

    /** 检验员 */
    @Column(length = 50) public String qcInspector;

    /** 检验日期 */
    public LocalDate qcDate;

    public LocalDateTime lastUpdateTime;

    public LocalDateTime createTime = LocalDateTime.now();
}
