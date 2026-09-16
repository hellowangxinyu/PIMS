<template>
  <div class="page">
    <div class="report-header">
      <h2 class="report-title">采购建议（MRP）</h2>
      <span class="report-sub">销售订单 → 配方自动展开 → 比对库存/在途 → 缺口清单 → 一键生成请购单</span>
    </div>

    <div class="toolbar">
      <el-select v-model="selectedOrders" multiple filterable placeholder="选择销售订单（空=全部已确认订单）" style="width:420px" size="default">
        <el-option v-for="o in orders" :key="o.id" :label="`${o.orderNo} · ${o.customerName || ''} · ¥${Number(o.totalAmount || 0).toFixed(0)}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="analyze" :loading="loading">分析缺口</el-button>
      <el-button type="success" @click="createOrder" :disabled="!checked.length" v-if="hasPerm('purchase:write')">
        生成请购单（{{ checked.length }} 项）
      </el-button>
    </div>

    <template v-if="result">
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">分析订单</div><div class="kpi-value">{{ result.orders || 0 }} 张</div></div>
        <div class="kpi-card"><div class="kpi-label">缺口物料</div><div class="kpi-value red">{{ (result.lines || []).length }} 种</div></div>
      </div>

      <p-table :data="result.lines || []" stripe border style="width:100%" @selection-change="onCheck" row-key="materialCode">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="materialCode" label="物料编码" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="materialCategory" label="大类" width="80" align="center">
          <template #default="{ row }">{{ catLabel(row.materialCategory) }}</template>
        </el-table-column>
        <el-table-column prop="need" label="需求量" width="110" align="right" />
        <el-table-column prop="stock" label="现库存" width="110" align="right" />
        <el-table-column prop="transit" label="在途" width="100" align="right" />
        <el-table-column prop="gap" label="缺口" width="110" align="right">
          <template #default="{ row }"><b style="color:#b56a5c">{{ row.gap }}</b></template>
        </el-table-column>
        <el-table-column prop="suggested" label="建议采购(+5%)" width="130" align="right" />
        <el-table-column prop="orders" label="需求来源订单" min-width="170" show-overflow-tooltip />
      </p-table>
    </template>

    <div class="empty-tip" v-if="!result && !loading">选择订单（或不选=全部）后点「分析缺口」</div>
  </div>
</template>

<script setup>
// v6.3 第三批：MRP 简版——需求展开/库存/在途全在后端算，前端只呈现与勾选
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const orders = ref([])
const selectedOrders = ref([])
const result = ref(null)
const checked = ref([])
const loading = ref(false)
const perms = ref([])
function hasPerm(c) { return perms.value.includes(c) }
function catLabel(c) { return { A: '原料', B: '半成品', C: '成品', P: '包装', F: '辅料', R: '五金', S: '其他' }[c] || c }

async function analyze() {
  loading.value = true
  try {
    result.value = await api.post('/mrp/suggest', selectedOrders.value.length ? { orderIds: selectedOrders.value } : {})
    if (!(result.value.lines || []).length) ElMessage.success('所选订单原料库存与在途全部覆盖，无采购缺口')
  } catch {} finally { loading.value = false }
}

function onCheck(rows) { checked.value = rows }

async function createOrder() {
  try {
    await ElMessageBox.confirm(`将勾选的 ${checked.value.length} 项生成一张请购单（草稿，走审核流），确认？`, '生成请购单', { type: 'warning' })
    const r = await api.post('/mrp/create-order', { lines: checked.value })
    ElMessage.success(`已生成请购单 ${r.orderNo}（${r.itemCount} 项），请到「请购单」查看审核`)
    analyze()
  } catch (e) {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { orders.value = await api.get('/sales-order') } catch {}
})
</script>

<style scoped>
.page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; align-items: center; }
.kpi-row { display: flex; gap: 12px; margin-bottom: 14px; }
.kpi-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 12px 18px; border: 1px solid var(--pims-card-border, #e2e8f0); }
.kpi-label { font-size: 12px; color: #64748b; margin-bottom: 4px; }
.kpi-value { font-size: 18px; font-weight: 700; }
.kpi-value.red { color: #b56a5c; }
.empty-tip { text-align: center; color: #94a3b8; padding: 60px 0; font-size: 14px; }
</style>
