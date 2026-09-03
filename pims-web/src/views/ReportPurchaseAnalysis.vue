<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">采购分析</h2>
      <span class="hint">比价范围：近12个月非草稿采购记录；执行率 = 已到货数量 ÷ 采购数量</span>
    </div>

    <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">采购总量</div><div class="kpi-value">{{ fmtQty(data.completion?.totalQty) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">已到货量</div><div class="kpi-value">{{ fmtQty(data.completion?.receivedQty) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">到货完成率</div><div class="kpi-value" :class="completionRate < 100 ? 'kpi-orange' : 'kpi-green'">{{ fmt(completionRate) }}%</div></div>
        <div class="kpi-card"><div class="kpi-label">未到齐单数</div><div class="kpi-value kpi-red">{{ data.completion?.incompleteCount || 0 }}</div></div>
      </div>

      <!-- 比价 -->
      <div class="table-card" style="margin-top:16px">
        <div class="tab-toolbar">
          <div>
            <el-select v-model="selectedMaterial" size="small" placeholder="选择物料查看供应商比价" clearable filterable style="width:320px" @change="onMaterialChange">
              <el-option v-for="m in materialOptions" :key="m.materialCode" :label="`${m.materialName}（${m.materialCode}）`" :value="m.materialCode" />
            </el-select>
            <el-input v-model="priceSearch" placeholder="搜索物料/牌号/供应商" size="small" clearable style="width:200px;margin-left:12px" @input="resetPricePage" />
          </div>
          <span class="tab-count">{{ selectedMaterial ? '供应商比价' : '采购价格记录' }} 共 {{ priceFiltered.length }} 条</span>
        </div>

        <!-- 选中物料：供应商价格对比 -->
        <template v-if="selectedMaterial">
          <p-table :data="supplierCompareRows" stripe border size="small" style="width:100%;margin-bottom:12px">
            <el-table-column prop="supplierName" label="供应商" min-width="200" show-overflow-tooltip />
            <el-table-column prop="latestPrice" label="最新单价" width="130" align="right"><template #default="{ row }">¥{{ fmt(row.latestPrice) }}</template></el-table-column>
            <el-table-column prop="avgPrice" label="平均单价(加权)" width="140" align="right"><template #default="{ row }">¥{{ fmt(row.avgPrice) }}</template></el-table-column>
            <el-table-column prop="count" label="采购次数" width="100" align="right" />
          </p-table>
        </template>

        <!-- 明细 -->
        <p-table :data="pricePagedRows" stripe border size="small" style="width:100%">
          <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="编码" min-width="110" show-overflow-tooltip />
          <el-table-column prop="brand" label="牌号" width="100" show-overflow-tooltip />
          <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
          <el-table-column prop="purchaseDate" label="采购日期" width="110" />
          <el-table-column prop="unitPrice" label="单价" width="110" align="right"><template #default="{ row }">¥{{ fmt(row.unitPrice) }}</template></el-table-column>
          <el-table-column prop="qty" label="数量" width="100" align="right" />
          <el-table-column prop="totalAmount" label="金额" width="120" align="right"><template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template></el-table-column>
        </p-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="pricePage" v-model:page-size="pricePageSize" :page-sizes="[25, 50, 100]"
            :total="priceFiltered.length" layout="total, sizes, prev, pager, next" />
        </div>
      </div>

      <!-- 未到齐明细 -->
      <div class="table-card" style="margin-top:16px">
        <div class="tab-toolbar"><span class="tab-count">未到齐采购明细（已审核但到货数量 &lt; 采购数量）共 {{ (data.incompleteList || []).length }} 条</span></div>
        <p-table :data="incompletePagedRows" stripe border size="small" style="width:100%">
          <el-table-column prop="orderNo" label="采购单号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
          <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="编码" min-width="110" show-overflow-tooltip />
          <el-table-column prop="qty" label="采购数量" width="110" align="right" />
          <el-table-column prop="receivedQty" label="已到货" width="110" align="right"><template #default="{ row }"><span class="num-red">{{ fmtQty(row.receivedQty) }}</span></template></el-table-column>
          <el-table-column prop="totalAmount" label="金额" width="120" align="right"><template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template></el-table-column>
          <el-table-column prop="purchaseDate" label="采购日期" width="110" />
        </p-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="incompletePage" v-model:page-size="incompletePageSize" :page-sizes="[25, 50, 100]"
            :total="(data.incompleteList || []).length" layout="total, sizes, prev, pager, next" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const loading = ref(false)
const data = reactive({})
const selectedMaterial = ref('')
const priceSearch = ref('')

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function fmtQty(v) { return Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 }) }
const completionRate = computed(() => Number(data.completion?.rate || 0))

const materialOptions = computed(() => (data.priceCompare || []).map(m => ({ materialCode: m.materialCode, materialName: m.materialName })))
const supplierCompareRows = computed(() => {
  const m = (data.priceCompare || []).find(x => x.materialCode === selectedMaterial.value)
  return m ? m.suppliers : []
})

const priceFiltered = computed(() => {
  const kw = priceSearch.value.trim().toLowerCase()
  let rows = data.priceRows || []
  if (selectedMaterial.value) rows = rows.filter(r => r.materialCode === selectedMaterial.value)
  if (kw) rows = rows.filter(r => [r.materialName, r.materialCode, r.brand, r.supplierName].some(v => v && String(v).toLowerCase().includes(kw)))
  return rows
})
const { page: pricePage, pageSize: pricePageSize, pagedRows: pricePagedRows, resetPage: resetPricePage } = usePaging(priceFiltered)

const incompleteList = computed(() => data.incompleteList || [])
const { page: incompletePage, pageSize: incompletePageSize, pagedRows: incompletePagedRows, resetPage: resetIncompletePage } = usePaging(incompleteList)

function onMaterialChange() { resetPricePage() }

async function loadData() {
  loading.value = true
  try {
    const res = await api.get('/report/purchase-analysis')
    Object.assign(data, res)
    resetPricePage()
    resetIncompletePage()
  } catch (err) { console.error(err) }
  loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.hint { font-size: 12px; color: #94a3b8; }
.report-loading { padding: 40px 20px; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.kpi-card { background: var(--pims-card-bg); border-radius: 16px; padding: 18px 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.kpi-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.kpi-value { font-size: 22px; font-weight: 800; color: var(--pims-text); }
.kpi-green { color: #10b981; } .kpi-orange { color: #f59e0b; } .kpi-red { color: #ef4444; }
.num-red { color: #ef4444; font-weight: 700; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.tab-count { font-size: 13px; color: #64748b; }
.pagination-bar { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
