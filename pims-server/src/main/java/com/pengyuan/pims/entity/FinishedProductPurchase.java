package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 成品采购单（简化版）
 */
@Entity @Table(name = "finished_product_purchase")
public class FinishedProductPurchase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String orderNo;

    /** v5.75 税率%（默认13，单据级可改；含税单价→不含税=含税÷(1+税率%)） */
    @Column(precision = 5, scale = 2)
    public java.math.BigDecimal taxRate = java.math.BigDecimal.valueOf(13);

    public LocalDate purchaseDate;

    public Long supplierId;

    @Column(length = 100)
    public String supplierName;

    @Column(length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(length = 50)
    public String brand;                       // PPG/阿克苏/宣伟

    @Column(precision = 14, scale = 3)
    public BigDecimal qty;

    @Column(precision = 12, scale = 2)
    public BigDecimal unitPrice;

    @Column(precision = 14, scale = 2)
    public BigDecimal totalAmount;

    public Boolean isFree = false;

    @Column(length = 200)
    public String filePath;

    @Column(length = 20)
    public String status = "DRAFT";            // DRAFT(开立) / APPROVED(已审核) / CLOSED(已关闭)

    @Column(precision = 14, scale = 3)
    public BigDecimal receivedQty = BigDecimal.ZERO;  // 已到货数量

    @Column(length = 30)
    public String warehouseId;                  // 入库仓库ID

    @Column(length = 50)
    public String createdBy;

    @Column(length = 500)
    public String remark;

    public LocalDateTime createTime = LocalDateTime.now();
    public LocalDateTime updateTime;
}
