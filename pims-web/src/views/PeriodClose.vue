<template>
  <div class="page-container">
    <div class="page-header">
      <h2>期末结账</h2>
      <div class="header-actions">
        <span class="hint">流程：当月凭证全部记账 → 结转损益（生成结转凭证并记账）→ 结账锁定期间。已结账期间禁止一切凭证操作</span>
        <el-date-picker v-model="costingPeriod" type="month" value-format="YYYY-MM" :clearable="false" style="width:130px" />
        <el-button type="warning" plain @click="costingClose" v-if="hasPerm('finance:write') && costingMethod === 'MONTHLY_AVG'">存货成本计算（全月平均）</el-button>
        <el-button @click="fetch" :loading="loadingList">刷新</el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ periods.length }} 个期间（从最晚期间往前逐月反结账）</span></div>
      <p-table :data="periods" stripe border style="width:100%">
        <el-table-column prop="period" label="期间" width="110" />
        <el-table-column prop="total" label="凭证数" width="90" align="center" />
        <el-table-column prop="draft" label="未记账" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.draft > 0" type="danger" size="small">{{ row.draft }} 张待记账</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="未结转损益" width="140" align="right">
          <template #default="{ row }">
            <span v-if="hasAmountPerm('voucher')">{{ Number(row.plBalance) !== 0 ? '¥' + fmt(row.plBalance) : '已结平' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结账状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.closed ? 'success' : 'info'" size="small">{{ row.closed ? '已结账' : '未结账' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="closedBy" label="结账人" width="90" />
        <el-table-column prop="closeTime" label="结账时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.closeTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" v-if="Number(row.plBalance) !== 0 && !row.closed"
              @click="transfer(row)">结转损益</button>
            <button class="op-btn op-btn-success" v-if="!row.closed" @click="close(row)">结账</button>
            <button class="op-btn op-btn-danger" v-if="row.closed" @click="reopen(row)">反结账</button>
          </template>
        </el-table-column>
      </p-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const periods = ref([])
const perms = ref([])
const loadingList = ref(false)
const costingPeriod = ref(new Date().toISOString().slice(0, 7))
const costingMethod = ref('SPECIFIC')   // v5.63 全月平均结账前置

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function fmtTime(t) {
  if (!t) return ''
  if (typeof t === 'number') { const d = new Date(t); return d.toLocaleString('zh-CN', { hour12: false }) }
  return String(t).replace('T', ' ').slice(0, 19)
}

async function fetch() {
  loadingList.value = true
  try { periods.value = await api.get('/voucher/period-status') } catch {} finally { loadingList.value = false }
}

async function transfer(row) {
  try {
    await ElMessageBox.confirm(
      `将按「年初至 ${row.period} 损益类科目余额」生成结转凭证（草稿，含收入/费用结转及本年利润），生成后请到「会计凭证」页记账。继续？`,
      '结转损益', { type: 'info' })
  } catch { return }
  try {
    const r = await api.post('/voucher/transfer-profit', { period: row.period })
    ElMessage.success(`结转凭证 ${r.voucher.docNo} 已生成（本年利润 ${fmt(r.profit)}），请到会计凭证页记账后重新结账`)
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '结转失败')
  }
}

async function close(row) {
  try {
    await ElMessageBox.confirm(
      `确定结账 ${row.period}？结账后该期间禁止新增/修改/删除/记账凭证，报表以该期末数据封账。继续？`,
      '期末结账', { type: 'warning' })
  } catch { return }
  try {
    await api.put('/voucher/close-period', { period: row.period })
    ElMessage.success(row.period + ' 已结账')
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '结账失败')
  }
}

async function reopen(row) {
  try {
    await ElMessageBox.confirm(`确定反结账 ${row.period}？该期间恢复可操作。`, '反结账', { type: 'warning' })
  } catch { return }
  try {
    await api.put('/voucher/reopen-period', { period: row.period })
    ElMessage.success('已反结账')
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '反结账失败')
  }
}

/** v5.63 全月平均存货成本计算：回填该期出库成本 + 落价格快照（结账前置） */
async function costingClose() {
  try {
    await ElMessageBox.confirm(
      `计算 ${costingPeriod.value} 存货成本？将按全月一次加权平均重算该期全部已确认出库单的成本并生成价格快照。注意：算过之后不能重复重算。`,
      '存货成本计算', { type: 'info' })
  } catch { return }
  try {
    const r = await api.post('/costing/monthly-close', { period: costingPeriod.value })
    ElMessage.success(`${costingPeriod.value} 已计算 ${r.materialCount} 个物料均价并回填出库成本`)
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '计算失败')
  }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { costingMethod.value = (await api.get('/costing/config')).method || 'SPECIFIC' } catch {}   // v5.63
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hint { font-size: 12px; color: #94a3b8; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
</style>
