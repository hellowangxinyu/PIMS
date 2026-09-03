<template>
  <div class="page-container">
    <div class="page-header">
      <h2>打样样品</h2>
      <div class="header-actions">
        <el-select v-model="query.status" clearable placeholder="按状态筛选" size="small" style="width:130px" @change="fetch">
          <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button type="primary" @click="openCreate" v-if="hasPerm('sample:write')">新增打样</el-button>
      </div>
    </div>

    <p-table :data="list" stripe border size="small" @header-dragend="onHeaderDragend">
      <el-table-column prop="sampleNo" label="打样单号" :width="cw('打样单号') || 118" />
      <el-table-column prop="customerName" label="客户/线索" min-width="130" show-overflow-tooltip />
      <el-table-column prop="materialDesc" label="意向产品/颜色" min-width="150" show-overflow-tooltip />
      <el-table-column prop="qty" label="数量" :width="cw('数量') || 70" align="right" />
      <el-table-column prop="applicant" label="申请人" :width="cw('申请人') || 80" />
      <el-table-column label="状态" :width="cw('状态') || 88" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ STATUS[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="colorist" label="调色员" :width="cw('调色员') || 80" />
      <el-table-column label="轮次" :width="cw('轮次') || 60" align="center">
        <template #default="{ row }">{{ (row.adjustCount || 0) + 1 }}</template>
      </el-table-column>
      <el-table-column prop="expressNo" label="快递单号" :width="cw('快递单号') || 110" show-overflow-tooltip />
      <el-table-column prop="wonOrderNo" label="成交订单" :width="cw('成交订单') || 112" />
      <el-table-column prop="applyDate" label="申请日期" :width="cw('申请日期') || 96" />
      <el-table-column label="操作" width="230" v-if="hasPerm('sample:write')">
        <template #default="{ row }">
          <button class="op-btn op-btn-primary" v-if="row.status === 'APPLIED' || row.status === 'ADJUST'" @click="openColoring(row)">调色</button>
          <button class="op-btn op-btn-primary" v-if="row.status === 'COLORING'" @click="openSend(row)">寄样</button>
          <button class="op-btn op-btn-primary" v-if="row.status === 'SENT'" @click="openFeedback(row)">反馈</button>
          <button class="op-btn op-btn-success" v-if="row.status === 'SATISFIED'" @click="openWin(row)">转单</button>
          <button class="op-btn" @click="openDetail(row)">详情</button>
          <button class="op-btn op-btn-warning" v-if="!['WON','LOST'].includes(row.status)" @click="openLose(row)">未成交</button>
          <button class="op-btn op-btn-danger" v-if="row.status === 'APPLIED'" @click="del(row)">删除</button>
        </template>
      </el-table-column>
    </p-table>

    <!-- 新增/编辑 -->
    <el-dialog :title="editing ? '编辑打样 ' + editing.sampleNo : '新增打样申请'" v-model="dialogVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable allow-create default-first-option placeholder="选择正式客户或直接输入线索公司名" style="width:100%" @change="onCustChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="意向产品" required>
          <el-input v-model="form.materialDesc" placeholder="如：氟碳漆 RAL7016 哑光，附着力要求…" />
        </el-form-item>
        <el-form-item label="关联物料">
          <el-select v-model="form.materialCode" filterable clearable placeholder="可选，关联正式物料" style="width:100%">
            <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + m.name" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="打样数量">
          <el-input-number v-model="form.qty" :min="0.001" :precision="3" style="width:160px" />
          <span style="margin-left:8px">{{ form.unit }}</span>
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="form.applicant" placeholder="默认当前登录人" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调色 -->
    <el-dialog :title="'开始调色 ' + (opRow?.sampleNo || '')" v-model="coloringVisible" width="min(1100px, 96vw)">
      <el-form label-width="80px">
        <el-form-item label="调色员"><el-input v-model="coloring.colorist" placeholder="研发调色负责人" /></el-form-item>
        <el-form-item label="调色说明"><el-input v-model="coloring.colorNote" type="textarea" :rows="2" placeholder="基料选择/颜色方向等" /></el-form-item>
      </el-form>
      <div class="flow-tip">首次调色会自动在「研发进度」创建一条分类=调色的跟进条目，研发在自己页面同步进展。</div>
      <template #footer>
        <el-button @click="coloringVisible=false">取消</el-button>
        <el-button type="primary" @click="submitColoring">开始调色</el-button>
      </template>
    </el-dialog>

    <!-- 寄样 -->
    <el-dialog :title="'寄样 ' + (opRow?.sampleNo || '')" v-model="sendVisible" width="min(1100px, 96vw)">
      <el-form label-width="80px">
        <el-form-item label="寄样日期"><el-date-picker v-model="send.sendDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="快递单号"><el-input v-model="send.expressNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible=false">取消</el-button>
        <el-button type="primary" @click="submitSend">确认寄样</el-button>
      </template>
    </el-dialog>

    <!-- 反馈 -->
    <el-dialog :title="'客户反馈 ' + (opRow?.sampleNo || '')" v-model="feedbackVisible" width="min(1100px, 96vw)">
      <el-form label-width="80px">
        <el-form-item label="反馈结果">
          <el-radio-group v-model="feedback.satisfied">
            <el-radio-button :value="true">客户满意</el-radio-button>
            <el-radio-button :value="false">需调整</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="反馈内容"><el-input v-model="feedback.content" type="textarea" :rows="3" placeholder="客户具体意见：颜色偏差/性能/其他要求" /></el-form-item>
        <el-form-item label="反馈日期"><el-date-picker v-model="feedback.feedbackDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <div class="flow-tip" v-if="feedback.satisfied === false">选「需调整」会回到调色中，轮次+1，重新走调色→寄样。</div>
      <template #footer>
        <el-button @click="feedbackVisible=false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">记录反馈</el-button>
      </template>
    </el-dialog>

    <!-- 转单 / 未成交 -->
    <el-dialog :title="(winMode ? '转单 ' : '标记未成交 ') + (opRow?.sampleNo || '')" v-model="winVisible" width="min(1100px, 96vw)">
      <el-form label-width="90px">
        <el-form-item :label="winMode ? '成交订单号' : '未成交原因'" :required="true">
          <el-input v-if="winMode" v-model="win.orderNo" placeholder="如 SO-20260826-0001" />
          <el-input v-else v-model="win.reason" type="textarea" :rows="2" placeholder="如：价格未谈拢/客户选择了竞品" />
        </el-form-item>
      </el-form>
      <div class="flow-tip">{{ winMode ? '转单后研发进度条目自动结案（结果=已成交）。' : '标记后研发进度条目自动结案（结果=未成交）。' }}</div>
      <template #footer>
        <el-button @click="winVisible=false">取消</el-button>
        <el-button type="primary" @click="submitWin">{{ winMode ? '确认转单' : '确认未成交' }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer :title="'打样详情 ' + (viewing?.sampleNo || '')" v-model="detailVisible" size="min(560px, 96vw)">
      <el-descriptions :column="1" border size="small" v-if="viewing">
        <el-descriptions-item label="客户">{{ viewing.customerName }}</el-descriptions-item>
        <el-descriptions-item label="意向产品">{{ viewing.materialDesc }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ viewing.qty }} {{ viewing.unit }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ STATUS[viewing.status] }}（第 {{ (viewing.adjustCount || 0) + 1 }} 轮）</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ viewing.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="调色员">{{ viewing.colorist || '-' }}</el-descriptions-item>
        <el-descriptions-item label="调色说明">{{ viewing.colorNote || '-' }}</el-descriptions-item>
        <el-descriptions-item label="寄样">{{ viewing.sendDate ? viewing.sendDate + ' 快递：' + (viewing.expressNo || '-') : '未寄样' }}</el-descriptions-item>
        <el-descriptions-item label="客户反馈">{{ viewing.feedbackContent ? (viewing.feedbackDate || '') + ' ' + viewing.feedbackContent : '-' }}</el-descriptions-item>
        <el-descriptions-item label="成交订单">{{ viewing.wonOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="未成交原因">{{ viewing.lossReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewing.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const STATUS = { APPLIED: '已申请', COLORING: '调色中', SENT: '已寄样', SATISFIED: '客户满意', ADJUST: '需调整', WON: '已转单', LOST: '未成交' }

const list = ref([])
const customers = ref([])
const materials = ref([])
const query = ref({ status: '' })
const dialogVisible = ref(false)
const editing = ref(null)
const form = ref({})
const opRow = ref(null)
const coloringVisible = ref(false)
const coloring = ref({})
const sendVisible = ref(false)
const send = ref({})
const feedbackVisible = ref(false)
const feedback = ref({})
const winVisible = ref(false)
const winMode = ref(true)
const win = ref({})
const detailVisible = ref(false)
const viewing = ref(null)
const perms = ref([])
const { cw, onHeaderDragend } = useColumnResize('sample_request')

function hasPerm(code) { return perms.value.includes(code) }
// v6.4 状态色统一：全局 + 打样域局部
const statusType = (s) => globalStatusType(s, { APPLIED: 'info', COLORING: 'primary', SENT: 'primary', SATISFIED: 'success', ADJUST: 'warning', WON: 'success', LOST: 'danger' })

async function fetch() {
  const params = {}
  if (query.value.status) params.status = query.value.status
  list.value = await api.get('/sample', { params })
}

function openCreate() {
  editing.value = null
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  form.value = { customerId: null, customerName: '', materialCode: '', materialDesc: '', qty: 1, unit: 'kg', applicant: user.realName || user.username || '', remark: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  form.value = { customerId: row.customerId, customerName: row.customerName, materialCode: row.materialCode, materialDesc: row.materialDesc, qty: Number(row.qty), unit: row.unit, applicant: row.applicant, remark: row.remark }
  dialogVisible.value = true
}

function onCustChange(val) {
  const c = customers.value.find(c => c.name === val)
  form.value.customerName = val
  form.value.customerId = c ? c.id : null
}

async function save() {
  if (!form.value.customerName) { ElMessage.warning('请填写客户/线索公司'); return }
  if (!form.value.materialDesc) { ElMessage.warning('请填写意向产品/颜色要求'); return }
  const body = { ...form.value, qty: Number(form.value.qty) }
  try {
    if (editing.value) await api.put(`/sample/${editing.value.id}`, body)
    else await api.post('/sample', body)
    ElMessage.success('已保存')
    dialogVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openColoring(row) { opRow.value = row; coloring.value = { colorist: row.colorist || '', colorNote: '' }; coloringVisible.value = true }
async function submitColoring() {
  try {
    await api.post(`/sample/${opRow.value.id}/coloring`, coloring.value)
    ElMessage.success('已进入调色中（研发进度已同步）')
    coloringVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openSend(row) { opRow.value = row; send.value = { sendDate: '', expressNo: '' }; sendVisible.value = true }
async function submitSend() {
  try {
    await api.post(`/sample/${opRow.value.id}/send`, send.value)
    ElMessage.success('已寄样')
    sendVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openFeedback(row) { opRow.value = row; feedback.value = { satisfied: true, content: '', feedbackDate: '' }; feedbackVisible.value = true }
async function submitFeedback() {
  try {
    await api.post(`/sample/${opRow.value.id}/feedback`, feedback.value)
    ElMessage.success('已记录反馈')
    feedbackVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openWin(row) { opRow.value = row; winMode.value = true; win.value = { orderNo: '' }; winVisible.value = true }
function openLose(row) { opRow.value = row; winMode.value = false; win.value = { reason: '' }; winVisible.value = true }
async function submitWin() {
  try {
    if (winMode.value) {
      await api.post(`/sample/${opRow.value.id}/win`, win.value)
      ElMessage.success('已转单，研发进度已结案')
    } else {
      await api.post(`/sample/${opRow.value.id}/lose`, win.value)
      ElMessage.success('已标记未成交')
    }
    winVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openDetail(row) { viewing.value = row; detailVisible.value = true }

async function del(row) {
  await ElMessageBox.confirm(`删除打样申请 ${row.sampleNo}？`)
  try { await api.delete(`/sample/${row.id}`); ElMessage.success('已删除'); fetch() } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { customers.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
  try {
    const mats = await api.get('/material', { params: { enabled: true } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name }))
  } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.flow-tip { font-size: 12px; color: #94a3b8; padding: 4px 8px; }
</style>
