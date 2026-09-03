<template>
  <div class="page-container">
    <div class="page-header">
      <h2>期初建账</h2>
      <div class="header-actions">
        <span class="hint">录入系统启用时点的各科目存量余额（来自旧账/代账交接），损益类科目无需录入</span>
        <el-button type="primary" @click="save" :loading="loading" :disabled="!balanced || !hasPerm('finance:write')"
          v-if="hasAmountPerm('account-subject')">保存期初余额</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('account-subject')">
      <div class="summary-card"><div class="sc-label">借方期初合计</div><div class="sc-value">¥{{ fmt(drSum) }}</div></div>
      <div class="summary-card"><div class="sc-label">贷方期初合计</div><div class="sc-value">¥{{ fmt(crSum) }}</div></div>
      <div class="summary-card"><div class="sc-label">平衡检查</div>
        <div class="sc-value" :class="balanced ? 'green' : 'red'">{{ balanced ? '借贷平衡 ✓' : '差额 ¥' + fmt(diff) }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ rows.length }} 个科目，只填写有余额的科目即可（其余留空视为 0）</span></div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column label="编码" width="100">
          <template #default="{ row }">
            <span :style="{ paddingLeft: row.parentCode ? '18px' : 0, fontWeight: row.parentCode ? 400 : 600 }">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }"><span :style="{ fontWeight: row.parentCode ? 400 : 600 }">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column label="类别" width="80" align="center">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="余额方向" width="110" align="center">
          <template #default="{ row }">
            <el-radio-group v-model="row.openingDirection" size="small" v-if="Number(row.openingBalance) > 0">
              <el-radio-button value="DR">借</el-radio-button>
              <el-radio-button value="CR">贷</el-radio-button>
            </el-radio-group>
            <span v-else>{{ row.direction === 'DR' ? '借' : '贷' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="期初余额" width="180" align="right" v-if="hasAmountPerm('account-subject')">
          <template #default="{ row }">
            <el-input-number v-if="row.category !== 'PL'" v-model="row.openingBalance" :min="0" :precision="2"
              :controls="false" size="small" style="width:100%" placeholder="0.00" />
            <span v-else>—</span>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[50, 100, 200]" :total="rows.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const rows = ref([])
const perms = ref([])
const loading = ref(false)

const CATEGORIES = { ASSET: '资产', LIABILITY: '负债', EQUITY: '权益', COST: '成本', PL: '损益' }
function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function categoryLabel(c) { return CATEGORIES[c] || c }

const drSum = computed(() => rows.value.filter(r => r.openingDirection === 'DR').reduce((s, r) => s + Number(r.openingBalance || 0), 0))
const crSum = computed(() => rows.value.filter(r => r.openingDirection === 'CR').reduce((s, r) => s + Number(r.openingBalance || 0), 0))
const diff = computed(() => Math.abs(drSum.value - crSum.value))
const balanced = computed(() => diff.value < 0.005)

async function fetch() {
  try {
    const list = await api.get('/account-subject')
    rows.value = list.map(s => ({
      code: s.code, name: s.name, parentCode: s.parentCode, category: s.category, direction: s.direction,
      openingBalance: Number(s.openingBalance) > 0 ? Number(s.openingBalance) : null,
      openingDirection: s.openingDirection || s.direction
    }))
  } catch {}
}

async function save() {
  if (!balanced.value) { ElMessage.warning('期初余额不平衡，无法保存'); return }
  loading.value = true
  try {
    const items = rows.value
      .filter(r => Number(r.openingBalance || 0) > 0)
      .map(r => ({ code: r.code, openingBalance: Number(r.openingBalance), openingDirection: r.openingDirection }))
    await api.put('/account-subject/opening-balance', items)
    ElMessage.success('期初建账已保存')
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(computed(() => rows.value))

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hint { font-size: 12px; color: #94a3b8; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 18px; font-weight: 700; }
.sc-value.red { color: #ef4444; }
.sc-value.green { color: #16a34a; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
</style>
