<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">低库存预警</h2>
      <div class="header-actions">
        <el-tooltip placement="top" :content="ruleTip">
          <span class="rule-tip">口径说明 ⓘ</span>
        </el-tooltip>
        <el-button v-if="hasPerm('purchase:write')" type="primary" size="small" :disabled="!selection.length" @click="openPurchase">
          生成采购单<span v-if="selection.length">（已选 {{ selection.length }} 项）</span>
        </el-button>
        <el-button size="small" @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <div v-if="loading" class="report-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else>
      <div class="stat-cards">
        <div class="stat-card warn">
          <div class="stat-num">{{ warningCount }}</div>
          <div class="stat-label">预警材料数（可用天数 &lt; 15 天）</div>
        </div>
        <div class="stat-card red">
          <div class="stat-num">{{ redCount }}</div>
          <div class="stat-label">红色严重预警（可用天数 &lt; 10 天）</div>
        </div>
        <div class="stat-card total">
          <div class="stat-num">{{ totalRawCount }}</div>
          <div class="stat-label">原材料种类（A/P/F/R/S）</div>
        </div>
      </div>

      <div class="chart-card full">
        <div class="chart-card-title">预警明细（仅原材料，按可用天数升序，最紧急在前）</div>
        <p-table v-if="rows.length" :data="pagedRows" size="small" border style="width:100%" @selection-change="onSelectionChange">
          <el-table-column v-if="hasPerm('purchase:write')" type="selection" width="42" />
          <el-table-column prop="materialCode" label="物料编码" min-width="110" />
          <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="brand" label="牌号" min-width="90" show-overflow-tooltip />
          <el-table-column label="大类" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ categoryLabel(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="小类" min-width="90">
            <template #default="{ row }">{{ row.subCategoryName || row.subCategory || '-' }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="60" align="center" />
          <el-table-column label="当前库存" width="110" align="right">
            <template #default="{ row }">{{ fmtNum(row.currentQty) }}</template>
          </el-table-column>
          <!-- v5.34：预警按仓库计算，每行对应一个仓库 -->
          <el-table-column label="仓库" width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.warehouseName || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="安全库存" width="110" align="right">
            <template #default="{ row }">
              <span class="safe-stock" title="安全库存 = 日均用量 × 30 天">{{ fmtNum(row.safeStock) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="月均用量" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.avgMonthlyQty) }}</template>
          </el-table-column>
          <el-table-column label="日均用量" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.avgDailyQty) }}</template>
          </el-table-column>
          <el-table-column label="可用天数" width="110" align="right">
            <template #default="{ row }">
              <span :class="isRed(row) ? 'days-red' : 'days-warn'">{{ row.availableDays }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预警级别" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="isRed(row) ? 'danger' : 'warning'" size="small">{{ isRed(row) ? '严重' : '预警' }}</el-tag>
            </template>
          </el-table-column>
        </p-table>
        <el-empty v-else description="当前无低库存预警材料（所有原材料可用天数均 ≥ 15 天）" :image-size="60" />
        <!-- 分页（v5.2） -->
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[25, 50, 100]"
            :total="rows.length"
            layout="total, sizes, prev, pager, next"
          />
        </div>
      </div>
    </template>

    <!-- 生成采购单弹窗 -->
    <el-dialog title="从低库存预警生成原料采购单" v-model="purchaseVisible" width="min(1250px, 96vw)" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="材料供应商" required>
          <el-select v-model="purchaseForm.supplierId" filterable placeholder="选择材料供应商（MATERIAL）" style="width:100%" @change="onSupplierChange">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <div class="purchase-tip" v-if="purchaseForm.supplierId">
            共 {{ purchaseRows.length }} 种物料，将生成独立采购单（草稿），保存后请到「采购管理 → 原料采购」审核；建议采购量 = 平均月用量
          </div>
        </el-form-item>
      </el-form>
      <p-table :data="purchaseRows" border size="small" style="width:100%" max-height="420">
        <el-table-column prop="materialCode" label="物料编码" min-width="110" />
        <el-table-column prop="materialName" label="品名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="brand" label="牌号" min-width="90" show-overflow-tooltip />
        <el-table-column prop="subCategoryName" label="小类" min-width="85" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <!-- v5.34：目标仓库（发往不同仓库） -->
        <el-table-column label="目标仓库" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.warehouseName || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="95" align="right">
          <template #default="{ row }">{{ fmtNum(row.currentQty) }}</template>
        </el-table-column>
        <el-table-column label="安全库存" width="95" align="right">
          <template #default="{ row }">{{ fmtNum(row.safeStock) }}</template>
        </el-table-column>
        <el-table-column label="建议采购量(kg)" width="130" align="right">
          <template #default="{ row }">
            <span class="gap-pos" title="建议采购量 = 平均月用量">{{ fmtNum(row.gap) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="采购数量(kg)" width="140" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" :precision="3" :step="100" size="small" style="width:120px" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="单价(元/kg)" width="140" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="1" size="small" style="width:120px" controls-position="right" />
            <div v-if="row.lastUnitPrice" class="last-price-tip">上次 {{ fmtNum(row.lastUnitPrice) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="105" align="right">
          <template #default="{ row }">
            <span class="amount-cell">{{ row.qty && row.unitPrice ? fmtMoney(row.qty * row.unitPrice) : '-' }}</span>
          </template>
        </el-table-column>
      </p-table>
      <template #footer>
        <el-button @click="purchaseVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPurchase" :loading="purchaseLoading">生成采购单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtMoney as fmtMoneyBase } from '../utils/fmt'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const loading = ref(false)
const rows = ref([])
const warningCount = ref(0)
const redCount = ref(0)
const totalRawCount = ref(0)
const perms = ref([])

const ruleTip = '仅原材料（大类 A/P/F/R/S），**按仓库分别计算预警**。历史用量 = 各仓库的生产领料/委外发料/其他出库/销售出库的出库量（调拨、盘盈亏不计入）。' +
  '月均用量 = 该仓总用量 ÷ 实际使用月份数；日均用量 = 该仓总用量 ÷ 实际使用天数。' +
  '安全库存 = 该仓日均用量 × 30 天；可用天数 = 该仓当前库存 ÷ 该仓日均用量。' +
  '该仓可用天数 < 15 天进入本表，< 10 天红色严重预警；不同仓库分别预警、分别订货。' +
  '选中行后点击「生成采购单」可直接创建原料采购单（草稿），采购单带目标仓库，到货时默认入库该仓库。'

const CATEGORY_LABELS = { A: '助剂', P: '颜料', F: '填料', R: '树脂', S: '溶剂' }
function categoryLabel(c) { return CATEGORY_LABELS[c] || c || '-' }

function fmtNum(v) {
  if (v === null || v === undefined) return '-'
  const n = Number(v)
  return Number.isInteger(n) ? n.toString() : n.toFixed(2).replace(/\.?0+$/, '')
}

function fmtMoney(v) { return '￥' + fmtMoneyBase(v) }   // v6.6 收口：千分位（utils/fmt）

function isRed(row) { return Number(row.availableDays) < 10 }

function hasPerm(code) { return perms.value.includes(code) }

async function loadData() {
  resetPage()
  loading.value = true
  try {
    const res = await api.get('/report/low-stock')
    rows.value = res.rows || []
    warningCount.value = res.warningCount || 0
    redCount.value = res.redCount || 0
    totalRawCount.value = res.totalRawCount || 0
  } catch (e) { console.error(e) }
  loading.value = false
}

// ============ 生成采购单 ============
const selection = ref([])
const purchaseVisible = ref(false)
const purchaseLoading = ref(false)
const suppliers = ref([])
const purchaseForm = ref({ supplierId: null })
const purchaseRows = ref([])

function onSelectionChange(sel) { selection.value = sel }

async function openPurchase() {
  if (!selection.value.length) { ElMessage.warning('请先勾选需要补货的低库存物料'); return }
  try { suppliers.value = await api.get('/supplier', { params: { type: 'MATERIAL', enabled: true } }) } catch {}
  // 建议采购量 = 平均月用量（v5.0 调整），并默认填入采购数量（可修改）；v5.34 带目标仓库（发往不同仓库）
  purchaseRows.value = selection.value.map(r => ({
    materialCode: r.materialCode,
    materialName: r.materialName,
    brand: r.brand || '',
    category: r.category,
    subCategory: r.subCategory,
    subCategoryName: r.subCategoryName || r.subCategory || '-',
    unit: r.unit || 'kg',
    warehouseId: r.warehouseId || '',
    warehouseName: r.warehouseName || '',
    currentQty: Number(r.currentQty) || 0,
    safeStock: Number(r.safeStock) || 0,
    avgMonthlyQty: Number(r.avgMonthlyQty) || 0,
    avgDailyQty: Number(r.avgDailyQty) || 0,
    availableDays: r.availableDays,
    gap: Number(r.avgMonthlyQty) || 0,
    qty: Number(r.avgMonthlyQty) || null,
    unitPrice: null,
    lastUnitPrice: null
  }))
  // 并行回填上次采购单价（失败静默，单价可手填）
  purchaseRows.value.forEach(async row => {
    try {
      const last = await api.get(`/raw-material-purchase/last/${encodeURIComponent(row.materialName)}`)
      if (last && last.unitPrice) {
        row.lastUnitPrice = Number(last.unitPrice)
        row.unitPrice = Number(last.unitPrice)
      }
    } catch {}
  })
  purchaseForm.value = { supplierId: null }
  purchaseVisible.value = true
}

function onSupplierChange() { /* 供应商选择即生效，无额外联动 */ }

async function submitPurchase() {
  if (!purchaseForm.value.supplierId) { ElMessage.warning('请选择材料供应商'); return }
  const sup = suppliers.value.find(s => s.id === purchaseForm.value.supplierId)
  if (!sup) { ElMessage.warning('供应商不存在'); return }
  for (const row of purchaseRows.value) {
    if (!row.qty || row.qty <= 0) { ElMessage.warning(`请填写「${row.materialName}」的采购数量`); return }
  }
  purchaseLoading.value = true
  let created = 0
  try {
    for (const row of purchaseRows.value) {
      await api.post('/raw-material-purchase', {
        supplierId: sup.id,
        supplierName: sup.name,
        materialCode: row.materialCode,
        materialName: row.materialName,
        brand: row.brand || null,
        category: row.category,
        subCategory: row.subCategory,
        qty: row.qty,
        unitPrice: row.unitPrice != null ? row.unitPrice : 0,
        isFree: false,
        warehouseId: row.warehouseId || null, // v5.34：目标仓库（发往不同仓库）
        remark: '低库存预警生成（' + (row.warehouseName || '未指定仓库') + '）'
      })
      created++
    }
    ElMessage.success(`已生成 ${created} 张原料采购单（草稿），请到「采购管理 → 原料采购」审核`)
    purchaseVisible.value = false
    selection.value = []
    loadData()
  } catch (e) { ElMessage.error(e.response?.data?.msg || '生成采购单失败') }
  finally { purchaseLoading.value = false }
}


// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(rows)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  loadData()
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.rule-tip { font-size: 12px; color: #64748b; cursor: help; border-bottom: 1px dashed #94a3b8; }
.report-loading { padding: 40px 20px; }
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { border-radius: 16px; padding: 18px 20px; background: var(--pims-card-bg); box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.stat-num { font-size: 28px; font-weight: 800; line-height: 1.2; }
.stat-label { font-size: 12px; color: #64748b; margin-top: 4px; }
.stat-card.warn .stat-num { color: #d97706; }
.stat-card.red .stat-num { color: #dc2626; }
.stat-card.total .stat-num { color: #6366f1; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.days-red { color: #dc2626; font-weight: 700; }
.days-warn { color: #d97706; font-weight: 700; }
.safe-stock { color: #2563eb; }
.gap-pos { color: #16a34a; font-weight: 600; }
.text-muted { color: #94a3b8; }
.wh-stock { color: #0c4a6e; font-size: 12px; }
.amount-cell { color: #ea580c; font-weight: 600; }
.last-price-tip { font-size: 11px; color: #94a3b8; line-height: 1; margin-top: 2px; }
.purchase-tip { font-size: 12px; color: #64748b; margin-top: 6px; }
@media (max-width: 768px) {
  .stat-cards { grid-template-columns: 1fr; }
  .report-header { flex-direction: column; align-items: flex-start; }
}
</style>
