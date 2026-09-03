<template>
  <div class="page-container">
    <div class="page-header">
      <h2>会计科目</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('finance:write')">新增科目</el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="type-tabs">
        <span class="type-count">共 {{ filtered.length }} 个科目（明细科目以缩进展示，类别/方向/编码不可改）</span>
      </div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column label="编码" width="100">
          <template #default="{ row }">
            <span :style="{ paddingLeft: row.parentCode ? '18px' : 0, fontWeight: row.parentCode ? 400 : 600 }">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <span :style="{ fontWeight: row.parentCode ? 400 : 600 }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类别" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="categoryType(row.category)">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">{{ row.direction === 'DR' ? '借' : '贷' }}</template>
        </el-table-column>
        <el-table-column label="期初余额" width="130" align="right" v-if="hasAmountPerm('account-subject')">
          <template #default="{ row }">
            <span v-if="Number(row.openingBalance) > 0">{{ row.openingDirection === 'DR' ? '借 ' : '贷 ' }}{{ fmt(row.openingBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openDialog(row)">改名</button>
            <button class="op-btn" :class="row.status === 'ENABLED' ? 'op-btn-danger' : 'op-btn-primary'" @click="toggle(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[50, 100, 200]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 业务转凭证默认科目映射 -->
    <div class="table-card" style="margin-top:16px">
      <div class="type-tabs"><span class="type-count">业务单据生成凭证时的默认科目映射（收款/付款方式、费用类型 → 会计科目）</span></div>
      <div class="mapping-grid">
        <div v-for="g in mappingGroups" :key="g.title" class="mapping-group">
          <div class="mg-title">{{ g.title }}</div>
          <div v-for="m in g.items" :key="m.key" class="mg-row">
            <span class="mg-label">{{ m.label }}</span>
            <el-select v-model="mappingValues[m.key]" filterable size="small" style="flex:1" clearable placeholder="人工选择">
              <el-option v-for="s in enabledSubjects" :key="s.code" :label="s.code + ' ' + s.name" :value="s.code" />
            </el-select>
            <el-button size="small" @click="saveMapping(m.key)" v-if="hasPerm('finance:write')">保存</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog :title="editing ? '科目改名' : '新增科目'" v-model="dialogVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码" required v-if="!editing">
          <el-input v-model="form.code" placeholder="如 1122 或 6602.09" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <template v-if="!editing">
          <el-form-item label="上级科目">
            <el-select v-model="form.parentCode" clearable filterable style="width:100%" placeholder="留空为一级科目">
              <el-option v-for="s in topSubjects" :key="s.code" :label="s.code + ' ' + s.name" :value="s.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="类别" required v-if="!form.parentCode">
            <el-select v-model="form.category" style="width:100%">
              <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="余额方向" required v-if="!form.parentCode">
            <el-radio-group v-model="form.direction">
              <el-radio value="DR">借方</el-radio>
              <el-radio value="CR">贷方</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-alert v-if="form.parentCode" type="info" :closable="false" title="明细科目自动继承上级的类别与方向" />
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const list = ref([])
const perms = ref([])
const dialogVisible = ref(false)
const loading = ref(false)
const editing = ref(null)
const form = ref({ code: '', name: '', parentCode: null, category: 'ASSET', direction: 'DR' })
const mappingValues = ref({})

const CATEGORIES = [
  { value: 'ASSET', label: '资产' }, { value: 'LIABILITY', label: '负债' }, { value: 'EQUITY', label: '权益' },
  { value: 'COST', label: '成本' }, { value: 'PL', label: '损益' }
]

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function categoryLabel(c) { return CATEGORIES.find(x => x.value === c)?.label || c }
function categoryType(c) {
  return { ASSET: 'primary', LIABILITY: 'warning', EQUITY: 'success', COST: 'info', PL: 'danger' }[c] || 'info'
}

const filtered = computed(() => list.value)
const enabledSubjects = computed(() => list.value.filter(s => s.status === 'ENABLED'))
const topSubjects = computed(() => list.value.filter(s => !s.parentCode && s.status === 'ENABLED'))

// 映射组定义（key 对应后端 account_mapping.map_key）
const mappingGroups = computed(() => [
  { title: '收款方式 → 资金科目', items: [
    { key: 'biz:method:RECEIPT:CASH', label: '现金收款' }, { key: 'biz:method:RECEIPT:BANK', label: '银行转账收款' },
    { key: 'biz:method:RECEIPT:ACCEPTANCE', label: '承兑收款' }, { key: 'biz:method:RECEIPT:WECHAT', label: '微信收款' },
    { key: 'biz:method:RECEIPT:OTHER', label: '其他方式收款' }
  ]},
  { title: '付款方式 → 资金科目', items: [
    { key: 'biz:method:PAYMENT:CASH', label: '现金付款' }, { key: 'biz:method:PAYMENT:BANK', label: '银行转账付款' },
    { key: 'biz:method:PAYMENT:ACCEPTANCE', label: '承兑付款' }, { key: 'biz:method:PAYMENT:WECHAT', label: '微信付款' },
    { key: 'biz:method:PAYMENT:OTHER', label: '其他方式付款' }
  ]},
  { title: '支出费用类型 → 费用科目', items: [
    { key: 'biz:expense:FREIGHT', label: '运费' }, { key: 'biz:expense:PACKAGING', label: '包装费' },
    { key: 'biz:expense:UTILITIES', label: '水电费' }, { key: 'biz:expense:OFFICE', label: '办公费' },
    { key: 'biz:expense:TRAVEL', label: '差旅费' }, { key: 'biz:expense:MAINTENANCE', label: '维修费' },
    { key: 'biz:expense:TESTING', label: '检测费' }, { key: 'biz:expense:OTHER', label: '其他支出' }
  ]},
  { title: '其他收入类型 → 收入科目', items: [
    { key: 'biz:income:SCRAP_SALE', label: '废料回收' }, { key: 'biz:income:RENT', label: '租金收入' },
    { key: 'biz:income:INTEREST', label: '利息收入' }, { key: 'biz:income:SUBSIDY', label: '政府补贴' },
    { key: 'biz:income:OTHER', label: '其他收入' }
  ]}
])

async function fetch() {
  try { list.value = await api.get('/account-subject') } catch {}
  try {
    const ms = await api.get('/account-subject/mapping')
    const vals = {}
    for (const m of ms) vals[m.mapKey] = m.subjectCode
    mappingValues.value = vals
  } catch {}
}

function openDialog(row) {
  editing.value = row
  form.value = row
    ? { code: row.code, name: row.name, parentCode: row.parentCode, category: row.category, direction: row.direction }
    : { code: '', name: '', parentCode: null, category: 'ASSET', direction: 'DR' }
  dialogVisible.value = true
}

async function submit() {
  loading.value = true
  try {
    if (editing.value) await api.put(`/account-subject/${editing.value.id}`, { name: form.value.name })
    else await api.post('/account-subject', form.value)
    ElMessage.success('已保存')
    dialogVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function toggle(row) {
  const action = row.status === 'ENABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}科目 ${row.code} ${row.name}？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await api.put(`/account-subject/${row.id}/toggle`)
    ElMessage.success(`已${action}`)
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}

async function saveMapping(key) {
  try {
    await api.put('/account-subject/mapping', { mapKey: key, subjectCode: mappingValues.value[key] || '' })
    ElMessage.success('映射已保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

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
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.mapping-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
.mapping-group { border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px; }
.mg-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.mg-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.mg-label { width: 100px; font-size: 13px; color: #606266; flex-shrink: 0; }
</style>
