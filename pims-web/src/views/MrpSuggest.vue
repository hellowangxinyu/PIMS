<template>
  <div class="page">
    <div class="report-header">
      <h2 class="report-title">采购建议（MRP）</h2>
      <span class="report-sub">{{ activeDim === 'recipe' ? '销售订单 → 配方自动展开 → 比对库存/在途 → 缺口清单 → 一键生成请购单' : '历史用量 → 日均用量×(平均到货周期+缓冲) → 低于请购点提醒采购' }}</span>
    </div>

    <!-- v11.1 双维度切换 -->
    <div class="type-tabs" style="margin-bottom:12px">
      <div class="filter-tabs">
        <button :class="['filter-btn', { active: activeDim === 'recipe' }]" @click="activeDim = 'recipe'; result = null; usageResult = null">按订单配方</button>
        <button :class="['filter-btn', { active: activeDim === 'usage' }]" @click="activeDim = 'usage'; result = null; usageResult = null">按历史用量</button>
      </div>
    </div>

    <div class="toolbar" v-if="activeDim === 'recipe'">
      <el-select v-model="selectedOrders" multiple filterable placeholder="选择销售订单（空=全部已确认订单）" style="width:420px" size="default">
        <el-option v-for="o in orders" :key="o.id" :label="`${o.orderNo} · ${o.customerName || ''} · ¥${Number(o.totalAmount || 0).toFixed(0)}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="analyze" :loading="loading">分析缺口</el-button>
      <el-button type="success" @click="createOrder" :disabled="!checked.length" v-if="hasPerm('purchase:write')">
        生成请购单（{{ checked.length }} 项）
      </el-button>
    </div>

    <!-- v11.1 历史用量维度 -->
    <div class="toolbar" v-if="activeDim === 'usage'">
      <span style="font-size:13px;color:#64748b">缓冲天数</span>
      <el-input-number v-model="bufferDays" :min="0" :max="60" :step="1" style="width:130px" controls-position="right" />
      <el-button type="primary" @click="analyzeUsage" :loading="usageLoading">计算请购点</el-button>
      <span v-if="usageResult" style="font-size:13px;color:#64748b">低于请购点物料 {{ usageResult.materialCount }} 种（缓冲 {{ usageResult.bufferDays }} 天）</span>
    </div>

    <template v-if="activeDim === 'usage' && usageResult">
      <template v-if="!isMobile">
      <p-table :data="usageResult.lines || []" stripe border style="width:100%" row-key="materialCode">
        <el-table-column prop="materialCode" label="物料编码" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="日均用量" width="110" align="right">
          <template #default="{ row }">{{ row.avgDailyQty }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="平均到货周期" width="115" align="right">
          <template #default="{ row }">{{ row.avgLeadDays }} 天</template>
        </el-table-column>
        <el-table-column label="现库存" width="100" align="right">
          <template #default="{ row }">{{ row.stockQty }}</template>
        </el-table-column>
        <el-table-column label="在途" width="100" align="right">
          <template #default="{ row }">{{ row.transitQty }}</template>
        </el-table-column>
        <el-table-column label="可覆盖" width="100" align="right">
          <template #default="{ row }"><span :style="row.level === 'RED' ? 'color:#b05a4e;font-weight:700' : ''">{{ row.coverDays }} 天</span></template>
        </el-table-column>
        <el-table-column label="请购点" width="105" align="right">
          <template #default="{ row }">{{ row.targetDays }} 天</template>
        </el-table-column>
        <el-table-column label="建议采购量" min-width="130" align="right">
          <template #default="{ row }"><strong>{{ row.suggested }} {{ row.unit }}</strong></template>
        </el-table-column>
      </p-table>
      </template>
      <!-- 手机卡片 -->
      <div v-if="isMobile" class="m-cards">
        <div v-for="row in usageResult.lines || []" :key="row.materialCode" class="m-card">
          <div class="m-card-head">
            <div>
              <div class="m-card-title">{{ row.materialName }}</div>
              <div class="m-card-sub">{{ row.materialCode }}</div>
            </div>
            <span class="m-status" :style="row.level === 'RED' ? 'background:#b05a4e;color:#fff' : ''">{{ row.coverDays }}天 / {{ row.targetDays }}天</span>
          </div>
          <div class="m-card-row"><span>日均用量</span><span class="m-val num">{{ row.avgDailyQty }} {{ row.unit }}</span></div>
          <div class="m-card-row"><span>库存 / 在途</span><span class="m-val num">{{ row.stockQty }} / {{ row.transitQty }}</span></div>
          <div class="m-card-row"><span>建议采购量</span><span class="m-val num" style="font-weight:700">{{ row.suggested }} {{ row.unit }}</span></div>
        </div>
        <div v-if="!(usageResult.lines || []).length" class="m-empty">没有低于请购点的物料，库存覆盖充足</div>
      </div>
    </template>
    <div v-if="activeDim === 'usage' && usageResult && !(usageResult.lines || []).length && !isMobile" class="m-empty" style="padding:24px 0">没有低于请购点的物料，库存覆盖充足</div>

    <template v-if="activeDim === 'recipe' && result">
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">分析订单</div><div class="kpi-value">{{ result.orders || 0 }} 张</div></div>
        <div class="kpi-card"><div class="kpi-label">缺口物料</div><div class="kpi-value red">{{ (result.lines || []).length }} 种</div></div>
      </div>

      <template v-if="!isMobile">
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
    <!-- v10.2 手机卡片视图：点卡片勾选/取消，与表格同源 checked -->
    <div v-if="isMobile" class="m-cards">
      <div v-for="row in result.lines || []" :key="row.materialCode" class="m-card"
           style="cursor:pointer" @click="toggleMCheck(row)">
        <div class="m-card-head">
          <div>
            <div class="m-card-title">{{ row.materialName }}</div>
            <div class="m-card-sub">{{ row.materialCode }} · {{ row.materialCategory }}</div>
          </div>
          <span class="m-status" :style="checked.some(r => r.materialCode === row.materialCode) ? 'background:var(--pims-primary);color:#fff' : ''">
            {{ checked.some(r => r.materialCode === row.materialCode) ? '已选' : '点选' }}
          </span>
        </div>
        <div class="m-card-row"><span>需求量</span><span class="m-val num">{{ row.need }}</span></div>
        <div class="m-card-row"><span>现库存 / 在途</span><span class="m-val num">{{ row.stock }} / {{ row.transit }}</span></div>
        <div class="m-card-row"><span>缺口</span><span class="m-val num danger">{{ row.gap }}</span></div>
        <div class="m-card-row"><span>建议采购</span><span class="m-val num">{{ row.suggested }}</span></div>
      </div>
      <div v-if="!(result.lines || []).length" class="m-empty">暂无建议</div>
    </div>
    </template>

    <div class="empty-tip" v-if="!result && !loading">选择订单（或不选=全部）后点「分析缺口」</div>
  </div>
</template>

<script setup>
// v6.3 第三批：MRP 简版——需求展开/库存/在途全在后端算，前端只呈现与勾选
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { isMobile } from '../composables/useIsMobile'

const orders = ref([])
const selectedOrders = ref([])
const result = ref(null)
const checked = ref([])
// v11.1 历史用量维度
const activeDim = ref('recipe')
const bufferDays = ref(10)
const usageLoading = ref(false)
const usageResult = ref(null)
async function analyzeUsage() {
  usageLoading.value = true
  try {
    usageResult.value = await api.post('/mrp/suggest-usage', { bufferDays: bufferDays.value })
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '计算失败')
  } finally { usageLoading.value = false }
}
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

// v10.2 手机卡片勾选（与表格 selection-change 同源 checked 数组）
function toggleMCheck(row) {
  const i = checked.value.findIndex(r => r.materialCode === row.materialCode)
  if (i >= 0) checked.value.splice(i, 1)
  else checked.value.push(row)
}


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
