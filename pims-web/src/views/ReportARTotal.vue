<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">应收总表</h2>
      <span class="report-sub">按客户维度汇总，不分订单</span>
    </div>

    <!-- 汇总卡片 -->
    <div class="summary-cards" v-if="hasAmountPerm('finance-ar')">
      <div class="summary-card">
        <div class="sc-label">应收总额</div>
        <div class="sc-value">¥{{ fmt(totalAmount) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">已收总额</div>
        <div class="sc-value" style="color:#16a34a">¥{{ fmt(totalReceived) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">剩余未收</div>
        <div class="sc-value" style="color:#ef4444">¥{{ fmt(totalRemaining) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">整体回款率</div>
        <div class="sc-value">{{ receivedRate }}%</div>
      </div>
    </div>

    <div class="table-card">
      <div class="tab-toolbar">
        <span class="tab-count">共 {{ list.length }} 个客户</span>
      </div>
      <p-table :data="list" stripe border style="width:100%" show-summary
                :summary-method="getSummary">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="customerName" label="客户" min-width="220" show-overflow-tooltip />
        <el-table-column prop="docCount" label="单据数" width="90" align="center" />
        <el-table-column label="单据状态分布" width="220" align="center">
          <template #default="{ row }">
            <span class="status-chip unpaid">未收 {{ row.unpaidCount }}</span>
            <span class="status-chip partial">部分 {{ row.partialCount }}</span>
            <span class="status-chip paid">已结清 {{ row.paidCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="应收总额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="receivedAmount" label="已收金额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.receivedAmount) }}</template>
        </el-table-column>
        <el-table-column prop="remainingAmount" label="剩余未收" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <span :style="{ color: row.remainingAmount > 0 ? '#ef4444' : '#16a34a' }">¥{{ fmt(row.remainingAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="回款率" width="180" align="center" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.receivedRate || 0)" :stroke-width="10"
                         :color="progressColor(row.receivedRate)" />
          </template>
        </el-table-column>
      </p-table>
    </div>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const list = ref([])
const perms = ref([])

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function progressColor(rate) {
  const r = Number(rate || 0)
  if (r >= 100) return '#16a34a'
  if (r >= 50) return '#f59e0b'
  return '#ef4444'
}

// 汇总计算
const totalAmount = computed(() => list.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0))
const totalReceived = computed(() => list.value.reduce((s, r) => s + Number(r.receivedAmount || 0), 0))
const totalRemaining = computed(() => totalAmount.value - totalReceived.value)
const receivedRate = computed(() => {
  if (totalAmount.value <= 0) return '0.00'
  return (totalReceived.value * 100 / totalAmount.value).toFixed(2)
})

// 表尾合计
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const prop = col.property
    if (prop === 'customerName') { sums[idx] = `${data.length} 个客户`; return }
    if (prop === 'docCount') { sums[idx] = data.reduce((s, r) => s + Number(r.docCount || 0), 0); return }
    if (prop === 'totalAmount' || prop === 'receivedAmount' || prop === 'remainingAmount') {
      sums[idx] = '¥' + fmt(data.reduce((s, r) => s + Number(r[prop] || 0), 0))
      return
    }
    sums[idx] = ''
  })
  return sums
}

async function load() {
  try { list.value = await api.get('/finance/ar/total') } catch {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  load()
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.report-sub { font-size: 13px; color: #64748b; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.sc-label { font-size: 13px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 22px; font-weight: 800; color: var(--pims-text); }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.tab-count { font-size: 13px; color: #64748b; }
.status-chip { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; margin-right: 4px; }
.status-chip.unpaid { background: #fee2e2; color: #b91c1c; }
.status-chip.partial { background: #fef3c7; color: #b45309; }
.status-chip.paid { background: #dcfce7; color: #15803d; }
</style>
