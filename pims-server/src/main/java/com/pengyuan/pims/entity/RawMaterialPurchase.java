package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 原料采购明细
 * 一个合同（orderNo）可包含多个物料，同一 orderNo 对应多条记录
 */
@Entity @Table(name = "raw_material_purchase")
public class RawMaterialPurchase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 合同号（同一合同号可有多行明细，不唯一） */
    @Column(nullable = false, length = 30)
    public String orderNo;

    /** v5.75 税率%（默认13，单据级可改；含税单价→不含税=含税÷(1+税率%)） */
    @Column(precision = 5, scale = 2)
    public java.math.BigDecimal taxRate = java.math.BigDecimal.valueOf(13);

    public LocalDate purchaseDate;             // 采购日期

    @Column(length = 100)
    public String manufacturer;                // 生产厂家

    public Long supplierId;                    // 原料供应商ID

    @Column(length = 50)
    public String supplierName;                // 原料供应商名称（冗余）

    @Column(length = 30)
    public String category;                    // 物料大类代码

    @Column(length = 30)
    public String subCategory;                 // 物料小类代码

    @Column(length = 30)
    public String materialCode;                // 物料编码

    @Column(length = 100)
    public String materialName;                // 品名

    @Column(length = 50)
    public String brand;                       // 牌号

    @Column(precision = 14, scale = 3)
    public BigDecimal qty;                     // 采购数量

    @Column(precision = 12, scale = 2)
    public BigDecimal unitPrice;              // 采购单价

    @Column(precision = 12, scale = 2)
    public BigDecimal lastUnitPrice;          // 上次采购单价

    @Column(precision = 8, scale = 2)
    public BigDecimal increaseRate;           // 增幅(%)

    @Column(precision = 12, scale = 2)
    public BigDecimal increaseAmount;         // 增值

    @Column(precision = 14, scale = 2)
    public BigDecimal totalAmount;            // 采购金额 = qty * unitPrice

    public Boolean isFree = false;             // 是否赠送

    @Column(length = 200)
    public String filePath;                    // 合同PDF路径

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
