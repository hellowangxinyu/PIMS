<template>
  <el-dialog title="业务单据生成凭证" v-model="visible" width="min(960px, 96vw)" destroy-on-close :close-on-click-modal="false">
    <el-alert v-if="preview" type="info" :closable="false" style="margin-bottom:12px"
      :title="`来源：${sourceLabel(form.source)} ${form.refDocNo}${form.red ? '（红字冲销）' : ''} —— 分录已按默认科目映射带出，可调整后保存`" />
    <div class="form-head">
      <div class="fh-item"><span class="fh-label">日期</span>
        <el-date-picker v-model="form.voucherDate" type="date" value-format="YYYY-MM-DD" style="width:150px" /></div>
      <div class="fh-item"><span class="fh-label">附单据</span>
        <el-input-number v-model="form.attachmentCount" :min="0" :max="999" style="width:100px" /></div>
      <div class="fh-item" style="flex:1"><span class="fh-label">备注</span>
        <el-input v-model="form.remark" placeholder="可选" /></div>
    </div>

    <el-table :data="form.entries" border size="small" style="width:100%">
      <el-table-column label="摘要" min-width="180">
        <template #default="{ row }"><el-input v-model="row.digest" size="small" /></template>
      </el-table-column>
      <el-table-column label="会计科目" min-width="220">
        <template #default="{ row }">
          <el-select v-model="row.subjectCode" filterable placeholder="编码/名称搜索" size="small" style="width:100%">
            <el-option v-for="s in subjects" :key="s.code" :label="s.code + ' ' + s.name" :value="s.code" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="借方" width="140" align="right">
        <template #default="{ row }">
          <el-input-number v-model="row.debit" :precision="2" :controls="false" size="small" style="width:100%" />
        </template>
      </el-table-column>
      <el-table-column label="贷方" width="140" align="right">
        <template #default="{ row }">
          <el-input-number v-model="row.credit" :precision="2" :controls="false" size="small" style="width:100%" />
        </template>
      </el-table-column>
      <el-table-column label="" width="50" align="center">
        <template #default="{ $index }">
          <el-button link type="danger" size="small" @click="form.entries.splice($index, 1)" :disabled="form.entries.length <= 2">✕</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="entry-footer">
      <el-button size="small" @click="form.entries.push({ subjectCode: '', digest: '', debit: null, credit: null, auxType: null, auxName: '' })">+ 增加一行</el-button>
      <div class="total-bar">
        <span>借方合计：<b :class="{ red: !balanced }">¥{{ fmt(formDebit) }}</b></span>
        <span>贷方合计：<b :class="{ red: !balanced }">¥{{ fmt(formCredit) }}</b></span>
        <el-tag v-if="balanced" type="success" size="small">借贷平衡</el-tag>
        <el-tag v-else type="danger" size="small">差额 ¥{{ fmt(Math.abs(formDebit - formCredit)) }}</el-tag>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="save" :loading="loading" :disabled="!balanced || formDebit === 0">保存为草稿</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { todayLocal } from '../utils/date'
/**
 * 业务单据生成凭证弹窗（v5.61）：收款单/付款单/费用单/发票列表页共用。
 * 用法：<voucher-generate-dialog ref="genDlg" @saved="fetch" /> + genDlg.value.open('RECEIPT', row.id)
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['saved'])
const visible = ref(false)
const loading = ref(false)
const preview = ref(false)
const subjects = ref([])
const generatedKeys = ref(new Set())
const form = ref({ voucherDate: '', attachmentCount: 1, remark: '', source: 'MANUAL', refDocNo: '', red: false, entries: [] })

/** 该业务单据是否已生成过凭证（sourceType + 单号在凭证表 source/refDocNo 中已存在） */
function hasGenerated(sourceType, docNo) {
  return generatedKeys.value.has(sourceType + ':' + docNo)
}

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function sourceLabel(s) {
  return { RECEIPT: '收款单', PAYMENT: '付款单', EXPENSE: '费用单', INVOICE: '发票', TRANSFER: '结转损益', MANUAL: '手工' }[s] || s
}

const formDebit = computed(() => form.value.entries.reduce((s, e) => s + Number(e.debit || 0), 0))
const formCredit = computed(() => form.value.entries.reduce((s, e) => s + Number(e.credit || 0), 0))
const balanced = computed(() => Math.abs(formDebit.value - formCredit.value) < 0.005 && formDebit.value !== 0)

async function open(sourceType, refId) {
  try {
    const p = await api.post('/voucher/generate', { sourceType, refId })
    form.value = {
      voucherDate: p.voucherDate || todayLocal(),
      attachmentCount: 1,
      remark: p.digest || p.remark || '',
      source: p.source, refDocNo: p.refDocNo, red: !!p.red,
      entries: (p.entries || []).map(e => ({
        subjectCode: e.subjectCode || '', digest: e.digest || '', debit: Number(e.debit) || null, credit: Number(e.credit) || null,
        auxType: e.auxType, auxName: e.auxName || ''
      }))
    }
    preview.value = true
    visible.value = true
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '生成预览失败')
  }
}

async function save() {
  if (!balanced.value) { ElMessage.warning('借贷不平衡'); return }
  loading.value = true
  try {
    await api.post('/voucher', form.value)
    ElMessage.success('凭证已保存为草稿，请在「会计凭证」页记账')
    generatedKeys.value.add(form.value.source + ':' + form.value.refDocNo)
    visible.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

onMounted(async () => {
  try { subjects.value = (await api.get('/account-subject')).filter(s => s.status === 'ENABLED') } catch {}
  try {
    const vs = await api.get('/voucher')
    generatedKeys.value = new Set(vs.filter(v => v.source && v.refDocNo).map(v => v.source + ':' + v.refDocNo))
  } catch {}
})

defineExpose({ open, hasGenerated })
</script>

<style scoped>
.form-head { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; flex-wrap: wrap; }
.fh-item { display: flex; align-items: center; gap: 6px; }
.fh-label { font-size: 13px; color: #606266; white-space: nowrap; }
.entry-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.total-bar { display: flex; align-items: center; gap: 18px; font-size: 13px; color: #303133; }
.total-bar .red { color: #b56a5c; }
</style>
