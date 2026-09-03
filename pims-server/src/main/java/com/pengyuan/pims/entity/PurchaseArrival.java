package com.pengyuan.pims.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购到货记录
 */
@Entity @Table(name = "purchase_arrival")
public class PurchaseArrival {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** v5.73 含税单价（到货时从采购单固化；采购录入单价默认含税） */
    @Column(precision = 14, scale = 4)
    public java.math.BigDecimal unitPrice;

    /** v5.75 税率%（默认13，单据级可改；含税单价→不含税=含税÷(1+税率%)） */
    @Column(precision = 5, scale = 2)
    public java.math.BigDecimal taxRate = java.math.BigDecimal.valueOf(13);

    /** v5.96 到货单号 ARR-YYYYMMDD-NNNN（按天流水，与合同号 PREFIX-YYYYMMDD-NNNN 规则一致） */
    @Column(length = 30)
    public String docNo;

    /** RAW / FINISHED */
    @Column(nullable = false, length = 10)
    public String type;

    @Column(nullable = false, length = 30)
    public String refOrderNo;           // 关联采购单号

    public Long supplierId;

    @Column(length = 100)
    public String supplierName;

    /** v5.2：本次到货明细字段（到货录入时落库） */
    @Column(length = 30)
    public String materialCode;

    @Column(length = 100)
    public String materialName;

    @Column(precision = 14, scale = 3)
    public java.math.BigDecimal qty;

    @Column(length = 10)
    public String unit;

    @Column(length = 20)
    public String warehouseId;

    @Column(length = 20)
    public String locationId;

    /** v5.2：分库/库位名称冗余（到货明细展示） */
    @Column(length = 50)
    public String zoneName;

    @Column(length = 50)
    public String locationName;

    public LocalDate arrivalDate;

    @Column(length = 100)
    public String operator;

    @Column(length = 500)
    public String remark;

    @Column(nullable = false, length = 20)
    public String status = "DRAFT";            // DRAFT(开立) / APPROVED(已审核)

    /** v5.27：合格入库后的库存批号；v5.32 改为持久化列——到货可录入批号（供应商批号），质检单/台账沿用，未填时入库自动生成后回填展示 */
    @Column(length = 30)
    public String batchNo;

    public LocalDateTime createTime = LocalDateTime.now();
}
