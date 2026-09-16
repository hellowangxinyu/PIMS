<template>
  <div class="page">
    <div class="report-header">
      <h2 class="report-title">库存周转率</h2>
      <span class="report-sub">年化周转 = 期间出库量 ÷ 当前库存 × 年化系数 · 最慢在前（呆滞预警）</span>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="days" @change="fetch">
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
        <el-radio-button :value="180">近半年</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="fetch" :loading="loading">查询</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="table-card" style="margin-bottom:14px">
        <div class="card-title">按物料大类</div>
        <p-table :data="data.byCategory || []" stripe border style="width:100%">
          <el-table-column prop="materialCategory" label="大类" width="120" align="center">
            <template #default="{ row }">{{ catLabel(row.materialCategory) }}</template>
          </el-table-column>
          <el-table-column prop="outQty" label="期间出库量(kg)" width="160" align="right" />
          <el-table-column prop="stockQty" label="当前库存(kg)" width="160" align="right" />
          <el-table-column prop="turnover" label="年化周转次数" width="140" align="center">
            <template #default="{ row }">
              <b v-if="row.turnover != null" :style="num(row.turnover) < 2 ? 'color:#b56a5c' : (num(row.turnover) > 8 ? 'color:#16a34a' : '')">{{ row.turnover }}</b>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="解读" min-width="200">
            <template #default="{ row }">
              <span v-if="row.turnover == null" style="color:#94a3b8">无库存</span>
              <span v-else-if="num(row.turnover) < 2" style="color:#b56a5c">偏慢（资金占用大，建议关注呆滞）</span>
              <span v-else-if="num(row.turnover) > 8" style="color:#16a34a">健康（周转快）</span>
              <span v-else>正常</span>
            </template>
          </el-table-column>
        </p-table>
      </div>

      <div class="table-card">
        <div class="card-title">物料明细（前 300，周转最慢在前；库存>0 且期间零出库 = 呆滞候选）</div>
        <p-table :data="data.details || []" stripe border style="width:100%" max-height="560">
          <el-table-column prop="materialCode" label="物料编码" width="130" show-overflow-tooltip />
          <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="materialCategory" label="大类" width="80" align="center">
            <template #default="{ row }">{{ catLabel(row.materialCategory) }}</template>
          </el-table-column>
          <el-table-column prop="outQty" label="期间出库" width="110" align="right" />
          <el-table-column prop="stockQty" label="当前库存" width="110" align="right" />
          <el-table-column prop="stockAmount" label="库存金额" width="120" align="right">
            <template #default="{ row }">¥{{ Number(row.stockAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
          </el-table-column>
          <el-table-column prop="turnover" label="年化周转" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.turnover == null" size="small" type="info">已清仓</el-tag>
              <el-tag v-else-if="num(row.turnover) === 0" size="small" type="danger">零出库</el-tag>
              <span v-else>{{ row.turnover }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stockDays" label="库存天数" width="100" align="center">
            <template #default="{ row }">{{ row.stockDays != null ? row.stockDays + ' 天' : '—' }}</template>
          </el-table-column>
        </p-table>
      </div>
    </template>
  </div>
</template>

<script setup>
// v6.3 第三批：库存周转率（口径：期间出库÷当前库存年化；数据在后端聚合）
import { ref, onMounted } from 'vue'
import api from '../api'

const days = ref(90)
const data = ref(null)
const loading = ref(false)

function catLabel(c) { return { A: '原料', B: '半成品', C: '成品', P: '包装', F: '辅料', R: '五金', S: '其他' }[c] || c }
function num(v) { return Number(v || 0) }

async function fetch() {
  loading.value = true
  try { data.value = await api.get('/report/turnover', { params: { days: days.value } }) } catch {} finally { loading.value = false }
}

onMounted(fetch)
</script>

<style scoped>
.page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; border: 1px solid var(--pims-card-border, #e2e8f0); }
.card-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
</style>
