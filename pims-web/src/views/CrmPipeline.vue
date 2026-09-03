<template>
  <div class="page-container">
    <div class="page-header">
      <h2>商机管道</h2>
      <div class="header-actions">
        <el-radio-group v-model="stageFilter">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OPEN">进行中</el-radio-button>
          <el-radio-button v-for="(label, st) in STAGE_LABEL" :key="st" :value="st">{{ label }}</el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" placeholder="搜商机/公司/产品" clearable style="width:180px" />
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('crm:write')">新增商机</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="summary">
      <div class="summary-card"><div class="sc-label">进行中商机</div><div class="sc-value">{{ summary.openCount }}</div><div class="sc-sub">预计金额 ¥{{ fmt(summary.openAmount) }}</div></div>
      <div class="summary-card" v-for="(label, st) in OPEN_STAGES" :key="st">
        <div class="sc-label">{{ label }}</div>
        <div class="sc-value">{{ summary.byStage[st]?.count || 0 }}</div>
        <div class="sc-sub">¥{{ fmt(summary.byStage[st]?.amount) }}</div>
      </div>
      <div class="summary-card won"><div class="sc-label">成交</div><div class="sc-value">{{ summary.wonCount }}</div><div class="sc-sub">¥{{ fmt(summary.wonAmount) }}</div></div>
      <div class="summary-card lost"><div class="sc-label">流失</div><div class="sc-value">{{ summary.lostCount }}</div></div>
    </div>

    <div class="table-card">
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="title" label="商机名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="companyName" label="客户/公司" min-width="150" show-overflow-tooltip />
        <el-table-column prop="productInterest" label="意向产品" min-width="130" show-overflow-tooltip />
        <el-table-column prop="expectAmount" label="预计金额" width="110" align="right">
          <template #default="{ row }">{{ row.expectAmount ? '¥' + fmt(row.expectAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="expectDate" label="预计成交" width="105" align="center" />
        <el-table-column label="阶段" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="stageTag(row.stage)" size="small">{{ STAGE_LABEL[row.stage] || row.stage }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="85" />
        <el-table-column prop="wonOrderNo" label="成交订单" width="120" />
        <el-table-column label="最近跟进" width="105" align="center">
          <template #default="{ row }">{{ row.lastFollow || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" v-if="hasPerm('crm:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openDialog(row)">编辑</button>
            <button class="op-btn op-btn-success" @click="openFollow(row)">跟进</button>
            <button class="op-btn op-btn-warn" @click="openStage(row)">推进</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 商机弹窗 -->
    <el-dialog :title="editing ? '编辑商机' : '新增商机'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="商机名称" required><el-input v-model="form.title" placeholder="如：华南卷材涂料年度框架" /></el-form-item>
        <el-form-item label="客户/公司" required>
          <el-select v-model="form.customerId" filterable clearable placeholder="选正式客户自动带名" style="width:100%" @change="onCustChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-input v-model="form.companyName" placeholder="或直接填公司名（线索）" style="margin-top:6px" />
        </el-form-item>
        <el-form-item label="意向产品"><el-input v-model="form.productInterest" placeholder="如：聚酯白面漆/背漆" /></el-form-item>
        <el-form-item label="预计金额"><el-input-number v-model="form.expectAmount" :min="0" :precision="2" :step="10000" style="width:100%" /></el-form-item>
        <el-form-item label="预计成交"><el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" placeholder="如：王鹏飞" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 阶段推进弹窗 -->
    <el-dialog :title="'推进阶段 · ' + (stageRow?.title || '')" v-model="stageVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="stageForm" label-width="90px">
        <el-form-item label="目标阶段" required>
          <el-radio-group v-model="stageForm.stage">
            <el-radio v-for="(label, st) in STAGE_LABEL" :key="st" :value="st">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="stageForm.stage === 'WON'" label="成交订单号">
          <el-input v-model="stageForm.wonOrderNo" placeholder="关联销售订单号（如 SO-20260826-0001）" />
        </el-form-item>
        <el-form-item v-if="stageForm.stage === 'LOST'" label="流失原因">
          <el-input v-model="stageForm.lossReason" type="textarea" :rows="2" placeholder="如：价格高于对手 10%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStage" :loading="loading">确认</el-button>
      </template>
    </el-dialog>

    <!-- 跟进抽屉（含时间线） -->
    <el-drawer v-model="followVisible" :title="'跟进 · ' + (followRow?.title || '')" size="min(560px, 94vw)">
      <el-form :model="followForm" label-width="80px" style="margin-bottom:16px">
        <el-form-item label="日期"><el-date-picker v-model="followForm.followDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="方式">
          <el-select v-model="followForm.method" style="width:100%">
            <el-option label="电话" value="PHONE" /><el-option label="拜访" value="VISIT" /><el-option label="微信" value="WECHAT" />
            <el-option label="邮件" value="EMAIL" /><el-option label="会议" value="MEETING" /><el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required><el-input v-model="followForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="followForm.nextDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="到期在工作台提醒" /></el-form-item>
        <el-button type="primary" @click="submitFollow" :loading="loading">添加跟进</el-button>
      </el-form>
      <div class="p360-sec">跟进时间线</div>
      <el-empty v-if="!followList.length" description="暂无跟进记录" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item v-for="f in followList" :key="f.id" :timestamp="f.followDate + ' · ' + (METHOD_LABEL[f.method] || f.method) + (f.nextDate ? ' · 下次 ' + f.nextDate : '')" placement="top">
          <div style="font-weight:600;font-size:13px">{{ f.operator }}</div>
          <div style="font-size:13px;color:#475569;line-height:1.7;white-space:pre-wrap">{{ f.content }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const STAGE_LABEL = { LEAD: '初步接触', QUOTED: '已报价', SAMPLING: '样品测试', NEGOTIATING: '商务谈判', WON: '成交', LOST: '流失' }
const OPEN_STAGES = { LEAD: '初步接触', QUOTED: '已报价', SAMPLING: '样品测试', NEGOTIATING: '商务谈判' }
const METHOD_LABEL = { PHONE: '电话', VISIT: '拜访', WECHAT: '微信', EMAIL: '邮件', MEETING: '会议', OTHER: '其他' }
const OPEN_SET = ['LEAD', 'QUOTED', 'SAMPLING', 'NEGOTIATING']

const list = ref([])
const customers = ref([])
const perms = ref([])
const summary = ref(null)
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const stageFilter = ref('')
const keyword = ref('')
const stageVisible = ref(false)
const stageRow = ref(null)
const stageForm = ref({ stage: '', wonOrderNo: '', lossReason: '' })
const followVisible = ref(false)
const followRow = ref(null)
const followList = ref([])
const followForm = ref({})
const form = ref(emptyForm())

function emptyForm() {
  return { title: '', customerId: null, companyName: '', productInterest: '', expectAmount: null, expectDate: '', owner: '', remark: '' }
}
function hasPerm(c) { return perms.value.includes(c) }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function stageTag(s) { return { LEAD: 'info', QUOTED: 'primary', SAMPLING: 'warning', NEGOTIATING: 'warning', WON: 'success', LOST: 'danger' }[s] || 'info' }

const filtered = computed(() => {
  let arr = list.value
  if (stageFilter.value === 'OPEN') arr = arr.filter(o => OPEN_SET.includes(o.stage))
  else if (stageFilter.value) arr = arr.filter(o => o.stage === stageFilter.value)
  const kw = keyword.value.trim().toLowerCase()
  if (kw) arr = arr.filter(o => [o.title, o.companyName, o.productInterest, o.owner].some(v => v && v.toLowerCase().includes(kw)))
  return arr
})

async function fetch() {
  resetPage()
  try { list.value = await api.get('/crm/opportunity') } catch {}
  try { summary.value = await api.get('/crm/pipeline-summary') } catch {}
  // 补最近跟进日期
  for (const o of list.value) {
    try {
      const fu = await api.get('/crm/follow-up', { params: { opportunityId: o.id } })
      o.lastFollow = fu?.[0]?.followDate || ''
    } catch { o.lastFollow = '' }
  }
}

function onCustChange(id) {
  const c = customers.value.find(x => x.id === id)
  if (c) form.value.companyName = c.name
}

function openDialog(row) {
  editing.value = row
  form.value = row ? { title: row.title, customerId: row.customerId, companyName: row.companyName,
    productInterest: row.productInterest || '', expectAmount: row.expectAmount != null ? Number(row.expectAmount) : null,
    expectDate: row.expectDate || '', owner: row.owner || '', remark: row.remark || '' } : emptyForm()
  visible.value = true
}

async function submit() {
  if (!form.value.title) { ElMessage.warning('请填商机名称'); return }
  if (!form.value.companyName) { ElMessage.warning('请填客户/公司'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/crm/opportunity/${editing.value.id}`, form.value)
    else await api.post('/crm/opportunity', form.value)
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
  finally { loading.value = false }
}

function openStage(row) {
  stageRow.value = row
  const idx = Object.keys(STAGE_LABEL).indexOf(row.stage)
  stageForm.value = { stage: Object.keys(STAGE_LABEL)[Math.min(idx + 1, 4)] || 'WON', wonOrderNo: row.wonOrderNo || '', lossReason: row.lossReason || '' }
  stageVisible.value = true
}

async function submitStage() {
  loading.value = true
  try {
    await api.post(`/crm/opportunity/${stageRow.value.id}/stage`, stageForm.value)
    ElMessage.success('阶段已更新为 ' + (STAGE_LABEL[stageForm.value.stage] || stageForm.value.stage))
    stageVisible.value = false
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
  finally { loading.value = false }
}

async function openFollow(row) {
  followRow.value = row
  followForm.value = { followDate: new Date().toISOString().slice(0, 10), method: 'PHONE', content: '', nextDate: '' }
  await loadFollows(row.id)
  followVisible.value = true
}

async function loadFollows(oppId) {
  try { followList.value = await api.get('/crm/follow-up', { params: { opportunityId: oppId } }) || [] } catch { followList.value = [] }
}

async function submitFollow() {
  if (!followForm.value.content) { ElMessage.warning('请填跟进内容'); return }
  loading.value = true
  try {
    await api.post('/crm/follow-up', { ...followForm.value, opportunityId: followRow.value.id })
    ElMessage.success('跟进已记录')
    followForm.value = { followDate: new Date().toISOString().slice(0, 10), method: 'PHONE', content: '', nextDate: '' }
    loadFollows(followRow.value.id)
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
  finally { loading.value = false }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  try { customers.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 12px 14px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); text-align: center; }
.sc-label { font-size: 12px; color: #64748b; }
.sc-value { font-size: 22px; font-weight: 700; margin-top: 2px; }
.sc-sub { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.summary-card.won .sc-value { color: #16a34a; }
.summary-card.lost .sc-value { color: #ef4444; }
.p360-sec { font-size: 14px; font-weight: 600; margin: 14px 0 8px; }
</style>
