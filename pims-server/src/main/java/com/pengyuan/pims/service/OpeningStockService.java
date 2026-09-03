package com.pengyuan.pims.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelWriter;
import com.pengyuan.pims.common.OpeningImportException;
import com.pengyuan.pims.entity.Material;
import com.pengyuan.pims.entity.Warehouse;
import com.pengyuan.pims.repository.InventoryLedgerRepository;
import com.pengyuan.pims.repository.MaterialRepository;
import com.pengyuan.pims.repository.WarehouseRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 期初库存导入：Excel 模板下载、全量校验、单事务落库（docType=OPENING）。
 * 校验失败整体拒绝并返回全部错误行；校验通过则整批一个事务写入，要么全进要么全不进。
 */
@Service
public class OpeningStockService {

    private static final Logger log = LoggerFactory.getLogger(OpeningStockService.class);

    private static final String[] HEADERS = {"物料编码", "物料名称", "仓库名称", "数量", "单价", "批号", "生产日期", "过期日期"};

    private final MaterialRepository materialRepo;
    private final WarehouseRepository warehouseRepo;
    private final InventoryLedgerRepository ledgerRepo;
    private final InventoryService inventoryService;
    private final com.pengyuan.pims.common.WriteQueue writeQueue;

    public OpeningStockService(MaterialRepository materialRepo, WarehouseRepository warehouseRepo,
                               InventoryLedgerRepository ledgerRepo, InventoryService inventoryService,
                               com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.materialRepo = materialRepo;
        this.warehouseRepo = warehouseRepo;
        this.ledgerRepo = ledgerRepo;
        this.inventoryService = inventoryService;
        this.writeQueue = writeQueue;
    }

    // ==================== 模板下载 ====================

    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encoded = URLEncoder.encode("期初导入模板", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded + ".xlsx");
        try (ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true)) {
            writer.renameSheet("期初数据");
            writer.writeRow(Arrays.asList(HEADERS), true);
            writer.writeRow(Arrays.asList("AC10001", "示例树脂（请删除本行）", "原料仓", 100, 12.5, "", "2026-08-01", ""));
            writer.writeRow(Arrays.asList("PM20001", "示例粉料（请删除本行）", "原料仓", 50, "", "PC20260801", "", "2027-08-01"));

            writer.setSheet("填写说明");
            writer.writeRow(Arrays.asList("列名", "必填", "说明"));
            writer.writeRow(Arrays.asList("物料编码", "是", "必须存在于物料档案且启用"));
            writer.writeRow(Arrays.asList("物料名称", "否", "仅供填报人阅读，系统以编码为准"));
            writer.writeRow(Arrays.asList("仓库名称", "是", "须与系统仓库档案名称完全一致"));
            writer.writeRow(Arrays.asList("数量", "是", "必须大于 0；单位不用填写"));
            writer.writeRow(Arrays.asList("单价", "否", "不能为负，用于计算库存金额"));
            writer.writeRow(Arrays.asList("批号", "否", "填写则必须全局不重复；留空系统自动生成"));
            writer.writeRow(Arrays.asList("生产日期", "否", "格式 yyyy-MM-dd"));
            writer.writeRow(Arrays.asList("过期日期", "否", "格式 yyyy-MM-dd；留空按物料质保期推算"));
            writer.writeRow(Arrays.asList("注意", "", "已有库存的物料不允许导入（防重复）；同一物料可多行=多个批次"));
            writer.flush(response.getOutputStream());
        }
    }

    // ==================== 导入（全量校验 + 单事务） ====================

    /** 解析行（内部结构；public 字段，JDK 17+ 无需 getter） */
    public static class Row {
        public int rowNo;                 // Excel 行号（从 2 起，1 是表头）
        public String materialCode;
        public Material material;
        public Warehouse warehouse;
        public BigDecimal qty;
        public BigDecimal unitPrice;      // 可空
        public String batchNo;            // 可空=自动生成
        public LocalDate produceDate;     // 可空
        public LocalDate expiryDate;      // 可空
    }

    // v6.1.5：整批 executeTx 锁内包事务——保住"全量校验单事务、要么全进要么全不进"的设计，
    // 同时消除与已迁 executeTx 的 openingInbound 混用导致的外层事务先于锁（旧时序）
    public int importOpening(MultipartFile file, String operator) {
        return writeQueue.executeTx(() -> importOpeningLocked(file, operator));
    }

    private int importOpeningLocked(MultipartFile file, String operator) {
        List<Row> rows = parseAndValidate(file);
        String docNo = "OPENING-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // v5.55：物料档案一次预取 + 仓库默认库位按仓缓存（行内不再重复回查）
        Map<String, Material> matByCode = new HashMap<>();
        for (Material m : materialRepo.findByEnabledTrue()) matByCode.put(m.code, m);
        Map<Long, String> defaultLocByWh = new HashMap<>();
        for (Row r : rows) {
            inventoryService.openingInbound(docNo, r.materialCode, r.material.name,
                    String.valueOf(r.warehouse.id), r.batchNo, r.qty,
                    r.unitPrice, r.produceDate, r.expiryDate, operator,
                    r.material, defaultLocByWh.computeIfAbsent(r.warehouse.id,
                            wid -> inventoryService.resolveDefaultLocation(String.valueOf(wid))));
        }
        log.info("期初导入完成：{} 共 {} 行，操作人 {}", docNo, rows.size(), operator);
        return rows.size();
    }

    /** 解析 Excel 并全量校验，任一错误抛 OpeningImportException（携带全部错误行） */
    private List<Row> parseAndValidate(MultipartFile file) {
        List<Map<String, Object>> raw;
        try (ExcelReader reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream())) {
            raw = reader.readAll();
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败：" + e.getMessage());
        }
        if (raw == null || raw.isEmpty()) {
            throw new OpeningImportException("文件为空，没有可导入的数据", List.of());
        }
        // 表头校验（第一行 key 必须含全部必需列）
        Map<String, Object> first = raw.get(0);
        for (String required : new String[]{"物料编码", "仓库名称", "数量"}) {
            if (!first.containsKey(required)) {
                throw new OpeningImportException("模板格式不正确（缺少列：" + required + "），请下载最新模板", List.of());
            }
        }

        // 仓库名 → 仓库 映射（仅启用仓库）
        Map<String, Warehouse> whByName = new LinkedHashMap<>();
        for (Warehouse w : warehouseRepo.findByEnabledTrue()) {
            whByName.put(w.name.trim(), w);
        }
        // v5.55：批量预取（替代逐行 findByCode / existsByBatchNo / findByMaterialCode 的 N+1）
        Map<String, Material> matByCode = new LinkedHashMap<>();
        for (Material m : materialRepo.findByEnabledTrue()) matByCode.put(m.code, m);
        Set<String> existingBatches = new HashSet<>(ledgerRepo.findAllBatchNos());
        Set<String> fileCodes = new LinkedHashSet<>();
        for (Map<String, Object> m : raw) {
            String c = str(m.get("物料编码"));
            if (!c.isBlank()) fileCodes.add(c);
        }
        Map<String, List<com.pengyuan.pims.entity.InventoryLedger>> ledgersByCode = fileCodes.isEmpty()
                ? Map.of()
                : ledgerRepo.findByMaterialCodeIn(fileCodes).stream()
                        .collect(java.util.stream.Collectors.groupingBy(l -> l.materialCode));

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        Set<String> seenBatch = new HashSet<>();               // 文件内批号防重
        Map<String, BigDecimal> importQtyByCode = new HashMap<>(); // 文件内同物料累计数量

        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> m = raw.get(i);
            int rowNo = i + 2; // 第 1 行是表头
            if (isEmptyRow(m)) continue;

            Row r = new Row();
            r.rowNo = rowNo;

            // 物料编码
            String code = str(m.get("物料编码"));
            if (code.isBlank()) {
                errors.add(err(rowNo, "物料编码不能为空"));
                continue;
            }
            r.materialCode = code;
            Material mat = matByCode.get(code);
            if (mat == null) {
                errors.add(err(rowNo, "物料编码 " + code + " 不存在或已停用"));
                continue;
            }
            r.material = mat;

            // 仓库名称
            String whName = str(m.get("仓库名称"));
            Warehouse wh = whByName.get(whName);
            if (wh == null) {
                errors.add(err(rowNo, "仓库「" + whName + "」不存在，可用仓库：" + String.join("、", whByName.keySet())));
                continue;
            }
            r.warehouse = wh;

            // 数量
            BigDecimal qty = toBd(m.get("数量"));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(err(rowNo, "数量必须大于 0"));
                continue;
            }
            r.qty = qty;

            // 单价（可空）
            Object up = m.get("单价");
            if (up != null && !str(up).isBlank()) {
                BigDecimal price = toBd(up);
                if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(err(rowNo, "单价不能为负"));
                    continue;
                }
                r.unitPrice = price;
            }

            // 批号（可空=自动生成）
            String batch = str(m.get("批号"));
            if (!batch.isBlank()) {
                if (existingBatches.contains(batch)) {
                    errors.add(err(rowNo, "批号 " + batch + " 已存在"));
                    continue;
                }
                if (!seenBatch.add(batch)) {
                    errors.add(err(rowNo, "批号 " + batch + " 在文件内重复"));
                    continue;
                }
                r.batchNo = batch;
            }

            // 日期（可空）
            try {
                r.produceDate = toDate(m.get("生产日期"));
                r.expiryDate = toDate(m.get("过期日期"));
            } catch (Exception e) {
                errors.add(err(rowNo, "生产日期/过期日期格式错误，应为 yyyy-MM-dd"));
                continue;
            }

            // 已有库存拒绝（数据库现有 + 文件内累计）
            BigDecimal dbQty = BigDecimal.ZERO;
            for (var l : ledgersByCode.getOrDefault(code, List.of())) {
                if (l.qty != null) dbQty = dbQty.add(l.qty);
            }
            BigDecimal inFileQty = importQtyByCode.getOrDefault(code, BigDecimal.ZERO);
            if (dbQty.add(inFileQty).compareTo(BigDecimal.ZERO) > 0) {
                errors.add(err(rowNo, "物料 " + code + " 已有库存，不允许期初导入（防重复）"));
                continue;
            }
            importQtyByCode.put(code, inFileQty.add(qty));

            rows.add(r);
        }

        if (!errors.isEmpty()) {
            throw new OpeningImportException("导入失败，共 " + errors.size() + " 行错误", errors);
        }
        if (rows.isEmpty()) {
            throw new OpeningImportException("没有可导入的有效数据行", List.of());
        }
        return rows;
    }

    // ==================== 辅助 ====================

    private Map<String, Object> err(int rowNo, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", rowNo);
        m.put("reason", reason);
        return m;
    }

    private boolean isEmptyRow(Map<String, Object> m) {
        for (Object v : m.values()) {
            if (v != null && !str(v).isBlank()) return false;
        }
        return true;
    }

    private String str(Object v) {
        if (v == null) return "";
        if (v instanceof Number) {
            // Excel 数字单元格（如物料编码被识别为数字）去掉小数尾巴
            double d = ((Number) v).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return v.toString().trim();
    }

    private BigDecimal toBd(Object v) {
        if (v == null || str(v).isBlank()) return null;
        try {
            return new BigDecimal(str(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Excel 日期单元格可能是 Date/数字/字符串，统一转 LocalDate */
    private LocalDate toDate(Object v) {
        if (v == null || str(v).isBlank()) return null;
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = str(v);
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/M/d"));
        }
    }
}
