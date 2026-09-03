<template>
  <div class="page">
    <div class="report-header">
      <h2 class="report-title">银行对账</h2>
      <span class="report-sub">出纳日记账 × 银行流水 · 自动勾对 · 余额调节表</span>
    </div>

    <div class="toolbar">
      <el-select v-model="accountId" filterable placeholder="选择银行账户" style="width:200px" @change="fetchAll">
        <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
        start-placeholder="开始" end-placeholder="结束" style="width:250px" @change="fetchAll" />
      <el-button @click="dlgAcc = true" v-if="hasPerm('finance:write')">账户管理</el-button>
      <el-upload :show-file-list="false" :http-request="doImport" accept=".xlsx,.xls" v-if="hasPerm('finance:write')">
        <el-button>导入银行流水</el-button>
      </el-upload>
      <a href="/api/bank/statement/template" download style="color:#4f7cff;font-size:13px">下载模板</a>
      <el-button type="primary" @click="autoMatch" :disabled="!accountId" v-if="hasPerm('finance:write')">自动勾对</el-button>
    </div>

    <template v-if="accountId">
      <div class="two-col">
        <div class="table-card">
          <div class="card-title">出纳日记账（系统收付款单）期初 ¥{{ fmt(journal.opening) }} · 期末 ¥{{ fmt(journal.closing) }}</div>
          <p-table :data="journal.rows || []" border size="small" style="width:100%" max-height="420"
                   :row-class-name="r => r.row.matched ? 'row-matched' : ''">
            <el-table-column prop="d" label="日期" width="100">
              <template #default="{ row }">{{ String(row.d || '').slice(0, 10) }}</template>
            </el-table-column>
            <el-table-column prop="docNo" label="单据号" min-width="130" show-overflow-tooltip />
            <el-table-column prop="side" label="收/付" width="60" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.side === 'RECEIPT' ? 'success' : 'danger'">{{ row.side === 'RECEIPT' ? '收' : '付' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="party" label="往来单位" min-width="120" show-overflow-tooltip />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.signed) }}</template>
            </el-table-column>
            <el-table-column prop="balance" label="余额" width="110" align="right" />
            <el-table-column label="勾对" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.matched" size="small" type="success">已勾</el-tag>
                <el-tag v-else size="small" type="warning">未勾</el-tag>
              </template>
            </el-table-column>
          </p-table>
        </div>

        <div class="table-card">
          <div class="card-title">银行流水（网银对账单）<span v-if="stmt.length">共 {{ stmt.length }} 笔</span></div>
          <p-table :data="stmt" border size="small" style="width:100%" max-height="420"
                   :row-class-name="r => r.row.status === 'MATCHED' ? 'row-matched' : ''">
            <el-table-column prop="tx_date" label="日期" width="100">
              <template #default="{ row }">{{ String(row.tx_date || '').slice(0, 10) }}</template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="110" show-overflow-tooltip />
            <el-table-column prop="counterparty" label="对方户名" min-width="110" show-overflow-tooltip />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">
                <span :style="Number(row.amount) < 0 ? 'color:#ef4444' : 'color:#16a34a'">{{ fmt(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="balance" label="余额" width="110" align="right" />
            <el-table-column label="勾对" width="110" align="center">
              <template #default="{ row }">
                <template v-if="row.status === 'MATCHED'">
                  <el-tag size="small" type="success">已勾</el-tag>
                  <el-button v-if="hasPerm('finance:write')" size="small" link @click="unbind(row)">取消</el-button>
                </template>
                <el-button v-else-if="hasPerm('finance:write')" size="small" link type="primary" @click="bindRow(row)">手工勾</el-button>
              </template>
            </el-table-column>
          </p-table>
        </div>
      </div>

      <div class="table-card" style="margin-top:14px" v-if="report">
        <div class="card-title" style="display:flex;justify-content:space-between;align-items:center">
          <span>余额调节表（{{ report.from }} 至 {{ report.to }}）</span>
          <div>
            <el-input-number v-model="bankEndingInput" :precision="2" placeholder="银行期末余额" size="small" style="width:160px;margin-right:8px" />
            <el-button size="small" @click="fetchReport(true)">按输入余额重算</el-button>
            <el-button size="small" @click="printReport">打印</el-button>
          </div>
        </div>
        <table class="recon-table">
          <tr><td>账面余额（系统）</td><td class="num">¥{{ fmt(report.bookClosing) }}</td><td>银行对账单余额</td><td class="num">¥{{ fmt(report.bankEnding) }}</td></tr>
          <tr><td>加：企业已收、银行未记</td><td class="num">¥{{ fmt(report.firmInNotInBank) }}</td><td>加：银行已收、企业未记</td><td class="num">¥{{ fmt(report.bankInNotInFirm) }}</td></tr>
          <tr><td>减：企业已付、银行未记</td><td class="num">¥{{ fmt(report.firmOutNotInBank) }}</td><td>减：银行已付、企业未记</td><td class="num">¥{{ fmt(report.bankOutNotInFirm) }}</td></tr>
          <tr class="final"><td><b>调节后余额</b></td><td class="num"><b>¥{{ fmt(report.adjustedBank) }}</b></td>
            <td><b>调节后余额</b></td><td class="num"><b :style="Number(report.diff) !== 0 ? 'color:#ef4444' : 'color:#16a34a'">¥{{ fmt(report.adjustedBank) }}</b></td></tr>
          <tr><td colspan="4" :style="Number(report.diff) === 0 ? 'color:#16a34a' : 'color:#ef4444'">
            {{ Number(report.diff) === 0 ? '✓ 调节平衡——账实一致' : `✗ 差异 ¥${fmt(report.diff)}：请检查未勾对明细（手续费等银行扣款请补记费用单后勾对）` }}
          </td></tr>
        </table>
        <div v-if="(report.unmatchedStatements || []).length" style="margin-top:10px">
          <div style="font-size:13px;color:#64748b;margin-bottom:6px">未勾对流水（需人工认定）：</div>
          <div v-for="u in report.unmatchedStatements" :key="u.id" class="unmatched-row">
            {{ String(u.tx_date).slice(0,10) }} · {{ u.summary }} · {{ u.counterparty || '—' }} · <b>{{ fmt(u.amount) }}</b>
          </div>
        </div>
      </div>
    </template>

    <div class="empty-tip" v-if="!accountId">先在「账户管理」建银行账户（名称与收付款单的账户字段一致）</div>

    <!-- 账户管理弹窗 -->
    <el-dialog title="银行账户" v-model="dlgAcc" width="560px">
      <p-table :data="accounts" border size="small" style="width:100%">
        <el-table-column prop="name" label="账户名称" min-width="130" />
        <el-table-column prop="bankName" label="开户行" min-width="120" show-overflow-tooltip />
        <el-table-column prop="openingBalance" label="期初余额" width="110" align="right" />
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }"><el-button size="small" link type="primary" @click="accForm = { ...row }">编辑</el-button></template>
        </el-table-column>
      </p-table>
      <el-divider style="margin:12px 0" />
      <el-form :model="accForm" label-width="90px" inline>
        <el-form-item label="账户名称"><el-input v-model="accForm.name" style="width:140px" /></el-form-item>
        <el-form-item label="开户行"><el-input v-model="accForm.bankName" style="width:140px" /></el-form-item>
        <el-form-item label="期初余额"><el-input-number v-model="accForm.openingBalance" :precision="2" style="width:130px" /></el-form-item>
        <el-form-item><el-button type="primary" size="small" @click="saveAcc">保存</el-button></el-form-item>
      </el-form>
    </el-dialog>

    <!-- 手工勾对弹窗 -->
    <el-dialog title="手工勾对：选择系统收/付款单" v-model="dlgBind" width="600px">
      <el-select v-model="bindTarget" filterable placeholder="选择单据（金额需一致）" style="width:100%">
        <el-option v-for="j in bindable" :key="j.side + j.jid" :value="j.side + ':' + j.jid"
          :label="`${String(j.d).slice(0,10)} ${j.side === 'RECEIPT' ? '收款' : '付款'} ¥${fmt(j.signed)} ${j.party || ''} ${j.docNo}`" />
      </el-select>
      <template #footer>
        <el-button @click="dlgBind = false">取消</el-button>
        <el-button type="primary" @click="doBind" :disabled="!bindTarget">勾对</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
// v6.3 出纳银行对账：日记账 vs 银行流水双栏、自动勾对、手工勾对、余额调节表
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const accounts = ref([])
const accountId = ref(null)
const range = ref([monthStart(), today()])
const journal = ref({})
const stmt = ref([])
const report = ref(null)
const bankEndingInput = ref(null)
const dlgAcc = ref(false)
const accForm = ref({})
const dlgBind = ref(false)
const bindTarget = ref('')
const bindRowId = ref(null)
const perms = ref([])
function hasPerm(c) { return perms.value.includes(c) }

function today() { return new Date().toISOString().slice(0, 10) }
function monthStart() { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10) }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const bindable = computed(() => (journal.value.rows || []).filter(r => !r.matched))

async function fetchAccounts() {
  try {
    accounts.value = await api.get('/bank/account')
    if (!accountId.value && accounts.value.length) { accountId.value = accounts.value[0].id; fetchAll() }
  } catch {}
}

async function fetchAll() {
  if (!accountId.value || !range.value || range.value.length !== 2) return
  const [from, to] = range.value
  try {
    [journal.value, stmt.value] = await Promise.all([
      api.get('/bank/journal', { params: { accountId: accountId.value, from, to } }),
      api.get('/bank/statement', { params: { accountId: accountId.value, from, to } })
    ])
    fetchReport()
  } catch {}
}

async function fetchReport(useInput) {
  const [from, to] = range.value
  try {
    report.value = await api.get('/bank/reconcile/report', {
      params: { accountId: accountId.value, from, to, bankEnding: useInput && bankEndingInput.value != null ? bankEndingInput.value : undefined }
    })
  } catch {}
}

async function saveAcc() {
  if (!accForm.value.name) { ElMessage.warning('请填账户名称'); return }
  try {
    await api.post('/bank/account', accForm.value)
    ElMessage.success('已保存')
    accForm.value = {}
    fetchAccounts()
  } catch {}
}

async function doImport(opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  try {
    const r = await api.post(`/bank/statement/import?accountId=${accountId.value}`, fd)
    ElMessage.success(`导入成功：新增 ${r.inserted} 条${r.skipped ? `，防重跳过 ${r.skipped} 条` : ''}`)
    fetchAll()
  } catch {}
}

async function autoMatch() {
  try {
    const r = await api.post(`/bank/reconcile/auto?accountId=${accountId.value}`)
    ElMessage.success(`自动勾对完成：${r.matched} 笔匹配，剩余 ${r.remaining} 笔需人工认定`)
    fetchAll()
  } catch {}
}

function bindRow(row) {
  bindRowId.value = row.id
  bindTarget.value = ''
  dlgBind.value = true
}

async function doBind() {
  const [refType, refId] = bindTarget.value.split(':')
  try {
    await api.post(`/bank/reconcile/${bindRowId.value}/bind?refType=${refType}&refId=${refId}`)
    ElMessage.success('已勾对')
    dlgBind.value = false
    fetchAll()
  } catch {}
}

async function unbind(row) {
  try { await api.post(`/bank/reconcile/${row.id}/unbind`); fetchAll() } catch {}
}

function printReport() {
  const r = report.value
  const win = window.open('', '_blank')
  if (!win) { ElMessage.warning('浏览器拦截了弹窗'); return }
  const rows = (r.unmatchedStatements || []).map(u =>
    `<tr><td>${String(u.tx_date).slice(0,10)}</td><td>${u.summary || ''}</td><td>${u.counterparty || ''}</td><td style="text-align:right">${fmt(u.amount)}</td></tr>`).join('')
  win.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"><title>银行存款余额调节表</title>
  <style>body{font-family:"Microsoft YaHei";font-size:12px;padding:24px}
  h2{text-align:center;letter-spacing:2px}table{width:100%;border-collapse:collapse;margin-top:14px}
  td,th{border:1px solid #333;padding:7px 10px}.num{text-align:right}
  .final td{background:#f3f4f6}</style></head><body>
  <h2>银行存款余额调节表</h2>
  <p>${r.accountName} · ${r.from} 至 ${r.to} · 制表 ${today()}</p>
  <table>
  <tr><td>账面余额（系统）</td><td class="num">¥${fmt(r.bookClosing)}</td><td>银行对账单余额</td><td class="num">¥${fmt(r.bankEnding)}</td></tr>
  <tr><td>加：企业已收、银行未记</td><td class="num">¥${fmt(r.firmInNotInBank)}</td><td>加：银行已收、企业未记</td><td class="num">¥${fmt(r.bankInNotInFirm)}</td></tr>
  <tr><td>减：企业已付、银行未记</td><td class="num">¥${fmt(r.firmOutNotInBank)}</td><td>减：银行已付、企业未记</td><td class="num">¥${fmt(r.bankOutNotInFirm)}</td></tr>
  <tr class="final"><td><b>调节后余额</b></td><td class="num"><b>¥${fmt(r.adjustedBank)}</b></td><td><b>调节后余额</b></td><td class="num"><b>¥${fmt(r.adjustedBank)}</b></td></tr>
  </table>
  ${rows ? `<p style="margin-top:16px"><b>未勾对流水（需人工认定）</b></p><table><tr><th>日期</th><th>摘要</th><th>对方</th><th>金额</th></tr>${rows}</table>` : ''}
  <p style="margin-top:40px">出纳：__________　财务复核：__________　日期：__________</p>
  </body></html>`)
  win.document.close()
  setTimeout(() => { win.print(); win.close() }, 200)
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetchAccounts()
})
</script>

<style scoped>
.page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; align-items: center; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 1100px) { .two-col { grid-template-columns: 1fr } }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px; border: 1px solid var(--pims-card-border, #e2e8f0); }
.card-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; color: #475569; }
:deep(.row-matched) { background: #f0fdf4 !important; }
.recon-table { width: 100%; border-collapse: collapse; margin-top: 4px }
.recon-table td { border: 1px solid #cbd5e1; padding: 8px 12px; font-size: 13px }
.recon-table .num { text-align: right; font-variant-numeric: tabular-nums }
.recon-table .final td { background: #f8fafc }
.unmatched-row { font-size: 12px; color: #475569; padding: 3px 8px; background: #fefce8; border-radius: 4px; margin-bottom: 4px; display: inline-block; margin-right: 8px }
.empty-tip { text-align: center; color: #94a3b8; padding: 60px 0; font-size: 14px; }
</style>
