<template>
  <div class="page-container">
    <div class="page-header">
      <h2>计价设置</h2>
      <div class="header-actions">
        <span class="hint">会计准则要求：计价方式一经确定不得随意变更，确需变更请在年初进行并留痕披露</span>
      </div>
    </div>

    <div class="table-card">
      <div class="card-title">存货计价方式</div>
      <el-radio-group v-model="method" class="method-group" :disabled="!hasPerm('finance:write')">
        <div class="method-item" v-for="m in METHODS" :key="m.value"
          :class="{ active: method === m.value }" @click="hasPerm('finance:write') && (method = m.value)">
          <el-radio :value="m.value">
            <b>{{ m.label }}</b>
            <el-tag v-if="m.value === 'SPECIFIC'" size="small" type="success" style="margin-left:6px">当前默认</el-tag>
          </el-radio>
          <div class="method-desc">{{ m.desc }}</div>
        </div>
      </el-radio-group>

      <div class="change-area" v-if="hasPerm('finance:write') && method !== current">
        <el-alert type="warning" :closable="false" style="margin-bottom:12px"
          :title="`将从「${labelOf(current)}」变更为「${labelOf(method)}」——变更原因必填，且只影响之后的出库单，历史单据不重算`" />
        <div v-if="config.monthOutboundCount > 0" class="warn-line">
          <el-icon color="#e6a23c"><Warning /></el-icon>
          当月已有 {{ config.monthOutboundCount }} 张出库单按原方式计价，切换后新旧口径将混用，建议月末结账后、下月初切换
        </div>
        <el-input v-model="reason" type="textarea" :rows="2" placeholder="变更原因（必填，将记入变更留痕）" style="margin:8px 0" />
        <el-button type="primary" @click="save" :loading="loading">提交变更</el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="card-title">全月平均月度计算状态（当前期间）</div>
      <div class="monthly-bar" v-if="current === 'MONTHLY_AVG' || method === 'MONTHLY_AVG'">
        <el-date-picker v-model="checkPeriod" type="month" value-format="YYYY-MM" :clearable="false" style="width:130px" />
        <el-button @click="checkMonthly" :loading="checking">查询</el-button>
        <template v-if="monthly">
          <el-tag :type="monthly.done ? 'success' : 'info'">{{ monthly.done ? '已计算' : '未计算' }}</el-tag>
          <span class="hint">该期出库单 {{ monthly.outboundCount }} 张；已算 {{ (monthly.prices || []).length }} 个物料均价</span>
        </template>
      </div>
      <div v-else class="hint">当前计价方式非全月平均，无需月度成本计算</div>
    </div>

    <div class="table-card">
      <div class="card-title">变更留痕</div>
      <el-table :data="logs" border size="small" v-if="logs.length">
        <el-table-column label="变更时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.change_time) }}</template>
        </el-table-column>
        <el-table-column label="原方式" width="110">
          <template #default="{ row }">{{ labelOf(row.old_value) }}</template>
        </el-table-column>
        <el-table-column label="新方式" width="110">
          <template #default="{ row }">{{ labelOf(row.new_value) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="变更原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="changed_by" label="操作人" width="90" />
      </el-table>
      <div v-else class="hint" style="padding:8px 0">从未变更过（自系统启用起保持「个别计价」）</div>
    </div>
  </div>
</template>

<script setup>
import { monthLocal } from '../utils/date'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import api from '../api'

const METHODS = [
  { value: 'SPECIFIC', label: '个别计价（批次计价）', desc: '出库成本 = 所选批次的实际入库价。与批次追溯体系天然配套，出库即知真实成本（系统默认）' },
  { value: 'FIFO', label: '先进先出法', desc: '与个别计价同源（批次价），出库界面自动引导优先选择最早入库批次' },
  { value: 'MOVING_AVG', label: '移动加权平均', desc: '每次出库成本 = 该物料全部在库的加权均价（Σ金额÷Σ数量），实时平滑' },
  { value: 'MONTHLY_AVG', label: '全月平均（月末一次加权平均）', desc: '月中出库按上月均价暂估，月末在「期末结账」页执行存货成本计算后统一回填（结账前必算）' }
]

const perms = ref([])
const config = ref({})
const current = ref('SPECIFIC')
const method = ref('SPECIFIC')
const reason = ref('')
const loading = ref(false)
const logs = ref([])
const checkPeriod = ref(monthLocal())
const checking = ref(false)
const monthly = ref(null)

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function labelOf(v) { return METHODS.find(m => m.value === v)?.label || v || '—' }
function fmtTime(t) {
  if (!t) return ''
  if (typeof t === 'number') return new Date(t).toLocaleString('zh-CN', { hour12: false })
  return String(t).replace('T', ' ').slice(0, 19)
}

async function fetch() {
  try {
    config.value = await api.get('/costing/config')
    current.value = config.value.method
    method.value = config.value.method
  } catch {}
  try { logs.value = await api.get('/costing/log') } catch {}
}

async function save() {
  if (!reason.value.trim()) { ElMessage.warning('请填写变更原因'); return }
  try {
    await ElMessageBox.confirm(`确定变更为「${labelOf(method.value)}」？该操作将留痕且不可静默撤销`, '变更计价方式', { type: 'warning' })
  } catch { return }
  loading.value = true
  try {
    const r = await api.put('/costing/method', { method: method.value, reason: reason.value, force: false })
    if (r.needConfirm) {
      try {
        await ElMessageBox.confirm(r.message, '再次确认', { type: 'warning', confirmButtonText: '仍要切换' })
      } catch { loading.value = false; return }
      const r2 = await api.put('/costing/method', { method: method.value, reason: reason.value, force: true })
      ElMessage.success(`已变更为 ${labelOf(r2.to)}`)
    } else {
      ElMessage.success(`已变更为 ${labelOf(r.to)}`)
    }
    reason.value = ''
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '变更失败')
  } finally { loading.value = false }
}

async function checkMonthly() {
  checking.value = true
  try { monthly.value = await api.get('/costing/monthly-status', { params: { period: checkPeriod.value } }) }
  catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败') }
  finally { checking.value = false }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.hint { font-size: 12px; color: #94a3b8; }
.card-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 14px; }
.method-group { display: block; width: 100%; }
.method-item { border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px 16px; margin-bottom: 10px; cursor: pointer; transition: border-color .2s; }
.method-item.active { border-color: #2a4a3a; background: #f0ede5; }
.method-desc { font-size: 12px; color: #64748b; margin-top: 2px; padding-left: 26px; }
.change-area { border-top: 1px dashed #dcdfe6; padding-top: 14px; margin-top: 4px; }
.warn-line { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #b45309; }
.monthly-bar { display: flex; align-items: center; gap: 10px; }
</style>
