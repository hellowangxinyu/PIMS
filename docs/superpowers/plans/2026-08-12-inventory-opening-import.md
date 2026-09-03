# 库存期初数据 Excel 导入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 库存中心新增期初数据导入——Excel 批量导入各物料期初库存（每行一个批次），全量校验+单事务落库，配套模板下载。

**Architecture:** 后端新增 OpeningStockService（hutool ExcelReader 解析+全量校验+单事务编排），InventoryService 新增 openingInbound 复用现有台账/批号/流水设施（docType=OPENING）；前端库存页工具栏加「下载期初模板」「导入期初数据」，模板下载走 downloadFile，导入走原生 axios（绕过拦截器自行处理错误清单）。

**Tech Stack:** Spring Boot + JPA + SQLite、hutool-poi（Excel 读写）、Vue3 + Element Plus

**项目约定（必读）：**
- 本项目**无单测目录、非 git 仓库**——验证方式为「构建 → 重启 → curl/Python 接口断言」
- 构建部署流程：`cd pims-web && npm run build` → netstat 找 8080 PID `taskkill //F //PID` → `cd pims-server && mvn clean package -q -DskipTests` → 后台 `java -jar target/pims-server-1.0.0.jar` → 验证
- 登录拿 token：`POST /api/auth/login {"username":"admin","password":"admin123"}` → `data.token`，header `pims-token: <token>`；curl 登录会顶掉浏览器会话（单点登录，正常）
- **SQLite 聚合类型坑**：COALESCE(SUM,0) 原生查询可能返回 Integer，用 Java 循环累加 BigDecimal 更稳
- **测试策略**：本地库已有真实库存，期初导入会被「已有库存」拒绝——这是功能正确。验证用「无库存物料」（或临时清空某物料台账测试后恢复）
- 前端文件下载必须走 `src/utils/download.js` 的 `downloadFile`（绕过 JSON 剥壳拦截器）
- 测试完删除测试数据（测试残留是已知遗留问题，不要再增加）

**Spec:** `docs/superpowers/specs/2026-08-12-inventory-opening-import-design.md`

---

### Task 1: 后端——期初入库方法 + 异常类

**Files:**
- Create: `pims-server/src/main/java/com/pengyuan/pims/common/OpeningImportException.java`
- Modify: `pims-server/src/main/java/com/pengyuan/pims/service/InventoryService.java`

- [ ] **Step 1: 创建 OpeningImportException**

```java
package com.pengyuan.pims.common;

import java.util.List;
import java.util.Map;

/**
 * 期初导入校验失败异常：携带全部错误行（行号+原因），由控制器转成 400 + 错误清单响应。
 */
public class OpeningImportException extends RuntimeException {

    private final List<Map<String, Object>> errors;

    public OpeningImportException(String message, List<Map<String, Object>> errors) {
        super(message);
        this.errors = errors;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}
```

- [ ] **Step 2: InventoryService 新增 openingInbound**

在 InventoryService 类中（reverseInbound 方法附近）新增：

```java
    /**
     * 期初导入入库（每行一个批次）：写台账 + OPENING 异动流水。
     * 与采购入库的差异：qcStatus 直接记 PASS（期初即合格）、无质检单、过期日期可显式指定。
     * 注意：本方法不开启事务，由 OpeningStockService.importOpening 的单一事务包裹，保证整批要么全进要么全不进。
     */
    public void openingInbound(String docNo, String materialCode, String materialName,
                               String warehouseId, String batchNo, BigDecimal qty,
                               BigDecimal unitPrice, LocalDate produceDate, LocalDate expiryDate,
                               String operator) {
        // 显式批号全局防重（含文件内前面行刚插入的批次——同事务内可见）
        if (batchNo != null && !batchNo.isBlank() && ledgerRepo.existsByBatchNo(batchNo)) {
            throw new IllegalArgumentException("批号 " + batchNo + " 已存在，批次号不能重复");
        }
        String finalBatchNo = (batchNo != null && !batchNo.isBlank()) ? batchNo : generateBatchNo();
        writeQueue.execute(() -> {
            InventoryLedger ledger = getOrCreateLedger(materialCode, finalBatchNo, warehouseId, null);
            BigDecimal qtyBefore = ledger.qty;

            ledger.qty = ledger.qty.add(qty);
            ledger.availableQty = ledger.availableQty.add(qty);
            if (materialName != null) ledger.materialName = materialName;
            if (unitPrice != null) {
                ledger.unitPrice = unitPrice;
                ledger.amount = ledger.qty.multiply(unitPrice);
            }
            ledger.inboundDate = LocalDate.now();
            if (produceDate != null) ledger.produceDate = produceDate;
            if (expiryDate != null) {
                ledger.expiryDate = expiryDate;
            } else {
                materialRepo.findByCode(materialCode).ifPresent(mat -> {
                    if (mat.shelfLifeDays != null && mat.shelfLifeDays > 0) {
                        ledger.expiryDate = ledger.inboundDate.plusDays(mat.shelfLifeDays);
                    }
                });
            }
            ledger.qcStatus = "PASS";
            ledger.lastUpdateTime = LocalDateTime.now();
            ledgerRepo.save(ledger);

            saveMovement("OPENING", docNo, materialCode, finalBatchNo, warehouseId,
                    "IN", qty, qtyBefore, ledger.qty, ledger.ownershipType, operator, "期初导入");
            log.info("期初导入: {} {} 批次{} +{} -> 仓{} 余额{}", docNo, materialCode, finalBatchNo, qty, warehouseId, ledger.qty);
        });
    }
```

- [ ] **Step 3: 编译验证**

```bash
cd D:/开发/PIMS/pims-server && mvn clean package -q -DskipTests
```
预期：EXIT 0（此时暂不重启，Task 2 完成后一起重启）。

---

### Task 2: 后端——OpeningStockService（模板生成 + 解析校验 + 导入编排）

**Files:**
- Create: `pims-server/src/main/java/com/pengyuan/pims/service/OpeningStockService.java`

- [ ] **Step 1: 创建 OpeningStockService 完整代码**

```java
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

    public OpeningStockService(MaterialRepository materialRepo, WarehouseRepository warehouseRepo,
                               InventoryLedgerRepository ledgerRepo, InventoryService inventoryService) {
        this.materialRepo = materialRepo;
        this.warehouseRepo = warehouseRepo;
        this.ledgerRepo = ledgerRepo;
        this.inventoryService = inventoryService;
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
            writer.writeRow(Arrays.asList("数量", "是", "必须大于 0；单位自动取物料档案"));
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

    @Transactional
    public int importOpening(MultipartFile file, String operator) {
        List<Row> rows = parseAndValidate(file);
        String docNo = "OPENING-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        for (Row r : rows) {
            inventoryService.openingInbound(docNo, r.materialCode, r.material.name,
                    String.valueOf(r.warehouse.id), r.batchNo, r.qty,
                    r.unitPrice, r.produceDate, r.expiryDate, operator);
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

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        Set<String> seenBatch = new HashSet<>();      // 文件内批号防重
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
            Optional<Material> matOpt = materialRepo.findByCode(code);
            if (matOpt.isEmpty() || !Boolean.TRUE.equals(matOpt.get().enabled)) {
                errors.add(err(rowNo, "物料编码 " + code + " 不存在或已停用"));
                continue;
            }
            r.material = matOpt.get();

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
                if (ledgerRepo.existsByBatchNo(batch)) {
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
            var ledgers = ledgerRepo.findByMaterialCode(code);
            for (var l : ledgers) {
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
```

- [ ] **Step 2: 编译验证**

```bash
cd D:/开发/PIMS/pims-server && mvn clean package -q -DskipTests
```
预期：EXIT 0。

---

### Task 3: 后端——控制器端点

**Files:**
- Modify: `pims-server/src/main/java/com/pengyuan/pims/controller/InventoryController.java`

- [ ] **Step 1: 注入 OpeningStockService 和 UserService**

InventoryController 构造器现有 4 个依赖（service/purchaseRepo/qcRepo/ledgerRepo），追加 `OpeningStockService openingStockService` 和 `UserService userService` 两个字段与构造参数（UserService 已存在，`currentOperatorName()` 取当前登录人姓名）。

- [ ] **Step 2: 新增两个端点**

```java
    /** 下载期初导入模板 */
    @GetMapping("/opening/template")
    @SaCheckPermission(value = "inventory:write")
    public void openingTemplate(HttpServletResponse response) throws IOException {
        openingStockService.downloadTemplate(response);
    }

    /** 期初数据导入（全量校验，任一错整体拒绝；通过则单事务落库） */
    @PostMapping("/opening/import")
    @SaCheckPermission(value = "inventory:write")
    public Result<?> openingImport(@RequestParam("file") MultipartFile file) {
        try {
            int count = openingStockService.importOpening(file, userService.currentOperatorName());
            return Result.ok(Map.of("count", count));
        } catch (OpeningImportException e) {
            return new Result<>(400, e.getMessage(), e.getErrors());
        }
    }
```

补充 import：`jakarta.servlet.http.HttpServletResponse`、`org.springframework.web.multipart.MultipartFile`、`java.io.IOException`、`com.pengyuan.pims.common.OpeningImportException`、`com.pengyuan.pims.service.OpeningStockService`、`com.pengyuan.pims.service.UserService`。

- [ ] **Step 3: 构建、重启、curl 验证**

```bash
cd D:/开发/PIMS/pims-server && mvn clean package -q -DskipTests
# 杀 8080 进程 → 后台启动 jar
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
# 模板下载（应得到 xlsx 二进制）
curl -s -H "pims-token: $TOKEN" -o /tmp/tpl.xlsx http://localhost:8080/api/inventory/opening/template
python -c "import openpyxl;wb=openpyxl.load_workbook('/tmp/tpl.xlsx');print('sheets:',wb.sheetnames)"
# 预期：sheets: ['期初数据', '填写说明']
```

---

### Task 4: 后端——端到端导入验证（单脚本：找物料→导入→验证→防重→清理）

- [ ] **Step 1: 运行端到端验证脚本**

脚本自动找无库存物料、构造 Excel、导入、验证台账/流水、二次导入验证防重：

```bash
python - <<'EOF'
import json, urllib.request, urllib.error, openpyxl, io, uuid
BASE='http://localhost:8080'
def req(method, path, body=None, token=None, headers=None):
    r=urllib.request.Request(BASE+path, method=method)
    for k,v in (headers or {}).items(): r.add_header(k,v)
    if token: r.add_header('pims-token', token)
    data=json.dumps(body).encode('utf-8') if body is not None else None
    if data: r.add_header('Content-Type','application/json; charset=utf-8')
    try:
        with urllib.request.urlopen(r, data) as resp: return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e: return json.loads(e.read().decode('utf-8'))

tok=req('POST','/api/auth/login',{'username':'admin','password':'admin123'})['data']['token']

# 1. 找第一个无库存的启用物料
mats=req('GET','/api/material',token=tok)['data']
code=None
for m in mats:
    if m.get('enabled') is False: continue
    inv=req('GET','/api/inventory/material/%s'%m['code'],token=tok)['data']
    total=sum(x.get('qty',0) or 0 for x in inv) if isinstance(inv,list) else 0
    if total<=0: code=m['code']; print('无库存物料:',code,m.get('name')); break
assert code, '所有物料都有库存，无法测试——先与用户确认'

wh=req('GET','/api/warehouse',token=tok)['data'][0]['name']

# 2. 构造 Excel（一行，批号留空自动生成）
wb=openpyxl.Workbook(); ws=wb.active; ws.title='期初数据'
ws.append(['物料编码','物料名称','仓库名称','数量','单价','批号','生产日期','过期日期'])
ws.append([code,'',wh,100,9.9,'','2026-08-01',''])
buf=io.BytesIO(); wb.save(buf)

# urllib 发 multipart 需手动构造 Request（req 只处理 JSON）
def post_multipart():
    boundary=uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="opening.xlsx"\r\n'
          f'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode() \
         + buf.getvalue() + f'\r\n--{boundary}--\r\n'.encode()
    r=urllib.request.Request(BASE+'/api/inventory/opening/import', method='POST', data=body)
    r.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')
    r.add_header('pims-token', tok)
    try:
        with urllib.request.urlopen(r) as resp: return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e: return json.loads(e.read().decode('utf-8'))

print('3. 首次导入:', post_multipart())   # 预期 code=200 count=1

inv=req('GET','/api/inventory/material/%s'%code,token=tok)['data']
print('4. 台账:', [(x.get('batchNo'),x.get('qty'),x.get('qcStatus'),x.get('produceDate'),x.get('expiryDate')) for x in inv])
mv=req('GET','/api/inventory/movements/%s'%code,token=tok)['data']
print('5. 流水:', [(x.get('docType'),x.get('docNo'),x.get('qty')) for x in (mv if isinstance(mv,list) else [])])

print('6. 二次导入(应拒绝):', post_multipart())  # 预期 code=400，reason 含"已有库存"
EOF
```

预期输出：
- `3. 首次导入: {'code': 200, ..., 'data': {'count': 1}}`
- `4. 台账:` 一条批次，qty=100、qcStatus=PASS、produceDate=2026-08-01、expiryDate 按质保期（物料无质保期则为空）
- `5. 流水:` 一条 `('OPENING', 'OPENING-...', 100)`
- `6. 二次导入: {'code': 400, 'msg': '导入失败，共 1 行错误', 'data': [{'row': 2, 'reason': '物料 XXX 已有库存，不允许期初导入（防重复）'}]}`

- [ ] **Step 2: 清理测试数据（停服后用 sqlite 直删）**

```bash
# 先杀 8080 进程（避免 SQLite 写锁冲突）
PID=$(netstat -ano | grep ":8080" | grep LISTENING | awk '{print $5}' | head -1); [ -n "$PID" ] && taskkill //F //PID $PID
python - <<'EOF'
import sqlite3
con=sqlite3.connect('D:/开发/PIMS/pims-server/data/pims.db')
CODE='<Step 1 输出的物料编码>'
mv=con.execute("DELETE FROM inventory_movement WHERE doc_type='OPENING' AND material_code=?", (CODE,)).rowcount
lg=con.execute("DELETE FROM inventory_ledger WHERE material_code=? AND qty=100 AND qc_status='PASS' AND inbound_date IS NOT NULL", (CODE,)).rowcount
con.commit()
print('清理: 流水', mv, '台账', lg)
EOF
```

（清理 SQL 带 qty=100/qc_status='PASS' 条件限定本次测试数据；服务会在 Task 5 打包后重启。）

---

### Task 5: 前端——工具栏按钮 + 模板下载 + 导入交互

**Files:**
- Modify: `pims-web/src/views/Inventory.vue`

- [ ] **Step 1: 工具栏加两个按钮**

在「导出 Excel」按钮后插入：

```vue
          <el-button size="small" @click="downloadOpeningTpl">下载期初模板</el-button>
          <el-upload
            :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls"
            :on-change="onOpeningFile">
            <el-button size="small" type="primary" :loading="openingImporting">导入期初数据</el-button>
          </el-upload>
```

- [ ] **Step 2: script 逻辑**

```js
import axios from 'axios'
import { ElMessageBox } from 'element-plus'

// ==================== 期初导入 ====================
const openingImporting = ref(false)

function downloadOpeningTpl() {
  downloadFile('/inventory/opening/template', {}, '期初导入模板.xlsx')
}

async function onOpeningFile(uploadFile) {
  const file = uploadFile?.raw
  if (!file) return
  try {
    await ElMessageBox.confirm(
      '期初导入将新增库存批次；校验失败不会写入任何数据。确定导入？',
      '导入期初数据', { type: 'warning', confirmButtonText: '导入', cancelButtonText: '取消' })
  } catch { return }

  openingImporting.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await axios.post('/api/inventory/opening/import', fd, {
      headers: { 'pims-token': localStorage.getItem('pims-token') || '' },
      timeout: 120000
    })
    const d = res.data
    if (d.code === 200) {
      ElMessage.success(`成功导入 ${d.data.count} 个批次`)
      onSearch()
    } else {
      showOpeningErrors(d.msg, d.data)
    }
  } catch (e) {
    const d = e.response?.data
    if (d && d.code !== 200) showOpeningErrors(d.msg, d.data)
    else ElMessage.error(d?.msg || '导入失败，请检查网络')
  } finally { openingImporting.value = false }
}

function showOpeningErrors(msg, errors) {
  const lines = Array.isArray(errors) && errors.length
    ? errors.map(x => `第 ${x.row} 行：${x.reason}`).join('<br/>')
    : '请检查文件内容'
  ElMessageBox.alert(lines, msg || '导入失败', {
    dangerouslyUseHTMLString: true, confirmButtonText: '知道了',
    customStyle: { maxHeight: '60vh', overflow: 'auto' }
  }).catch(() => {})
}
```

注意：`ElMessageBox` 若页面已全局引入则无需重复 import（检查现有 import 行，Inventory.vue 目前只 import 了 api/downloadFile/useColumnResize/SvgLineChart，需补 `ElMessage`、`ElMessageBox`、`axios`）。

- [ ] **Step 3: 前端构建、重新打包、重启**

```bash
cd D:/开发/PIMS/pims-web && npm run build
# 杀进程 → cd pims-server && mvn clean package -q -DskipTests → 后台启动
```

验证产物：`grep -l "导入期初数据" pims-server/src/main/resources/static/assets/Inventory-*.js` 应有命中。

---

### Task 6: 全量回归 + 部署服务器 + 记忆更新

- [ ] **Step 1: 浏览器验证（提醒用户 Ctrl+F5）**

库存中心工具栏：下载期初模板（得到两 sheet 的 xlsx）、导入期初数据（确认框→成功提示/错误清单弹窗）。

- [ ] **Step 2: 部署测试服务器**

```bash
scp D:/开发/PIMS/pims-server/target/pims-server-1.0.0.jar yttuliao@192.168.11.241:/opt/pims/
ssh yttuliao@192.168.11.241 "echo 'yttuliao' | sudo -S systemctl restart pims"
# 验证：curl http://192.168.11.241:8080/ 返回 302
```

- [ ] **Step 3: 更新项目记忆**

新增 `opening-stock-import.md`：模板列/校验规则/OPENING 流水/防重机制/测试方法（本地库有库存需用无库存物料测）；MEMORY.md 加索引行。
