<template>
  <div class="page-container">
    <div class="page-header">
      <h2>供应商管理</h2>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索名称" clearable @keyup.enter="fetch" style="width:200px" />
        <el-button @click="fetch">搜索</el-button>
        <el-button @click="downloadTpl" v-if="hasPerm('supplier:write')">下载导入模板</el-button>
        <el-upload v-if="hasPerm('supplier:write')" :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="onImportFile" style="display:inline-block">
          <el-button type="primary" :loading="importing">导入Excel</el-button>
        </el-upload>
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button type="primary" @click="showDialog(null)" v-if="hasPerm('supplier:write')">新增供应商</el-button>
      </div>
    </div>

    <!-- 类型筛选标签页 -->
    <div class="type-tabs">
      <el-radio-group v-model="typeFilter" @change="fetch">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="MATERIAL">材料供应商</el-radio-button>
        <el-radio-button value="FINISHED">成品供应商</el-radio-button>
        <el-radio-button value="PROCESSOR">代工厂</el-radio-button>
      </el-radio-group>
      <span class="type-count">共 {{ list.length }} 条</span>
    </div>
    <div class="table-card">
      <p-table :data="list" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="code" label="编码" :width="cw('编码') || 130" />
        <el-table-column prop="name" label="名称" :width="cw('名称') || undefined" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" :width="cw('类型') || 90" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.type==='FINISHED'?'success':(row.type==='PROCESSOR'?'warning':'primary')">
              {{ row.type === 'FINISHED' ? '成品' : (row.type === 'PROCESSOR' ? '代工厂' : '材料') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" :width="cw('状态') || 80" align="center">
          <template #default="{row}">
            <el-tag v-if="row.blacklisted" size="small" type="danger">已拉黑</el-tag>
            <el-tag v-else-if="!row.enabled" size="small" type="info">已删除</el-tag>
            <el-tag v-else size="small" type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="付款条件" :width="cw('付款条件') || 110" align="center" v-if="hasPerm('supplier:payment')">
          <template #default="{row}">{{ dictLabel('payment_terms', row.paymentTerms) }}</template>
        </el-table-column>
        <el-table-column label="付款方式" :width="cw('付款方式') || 100" align="center" v-if="hasPerm('supplier:payment')">
          <template #default="{row}">
            <el-tag v-if="row.paymentMethod" size="small" :type="row.paymentMethod==='ACCEPTANCE'?'warning':'primary'">{{ dictLabel('payment_method', row.paymentMethod) }}</el-tag>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column label="加工费" :width="cw('加工费') || 90" align="right" v-if="typeFilter==='PROCESSOR'">
          <template #default="{row}">
            <span v-if="row.processingFee != null">￥{{ Number(row.processingFee).toFixed(2) }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center">
          <template #default="{row}">
            <button class="op-btn op-btn-trace" @click="openProfile(row)">360°</button>
            <button v-if="hasPerm('supplier:write')" class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button v-if="row.blacklisted && hasPerm('supplier:write')" class="op-btn op-btn-warn" @click="toggleBlacklist(row, false)">解除拉黑</button>
            <button v-else-if="row.enabled && hasPerm('supplier:write')" class="op-btn op-btn-danger" @click="toggleBlacklist(row, true)">拉黑</button>
            <button v-if="hasPerm('supplier:delete')" class="op-btn op-btn-danger" @click="del(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- v5.49 供应商 360° 抽屉 -->
    <el-drawer v-model="profileVisible" :title="(profile?.supplier?.name || '') + ' · 360° 视图'" size="min(760px, 96vw)">
      <template v-if="profile">
        <div class="p360-cards">
          <div class="p360-card"><div class="k">应付余额</div><div class="v red">¥{{ fmt(profile.apBalance) }}</div><div class="s">立账 ¥{{ fmt(profile.apTotal) }} / 已付 ¥{{ fmt(profile.apPaid) }}</div></div>
          <div class="p360-card"><div class="k">累计付款</div><div class="v">¥{{ fmt(profile.payTotal) }}</div><div class="s">{{ profile.recentPayments.length }} 笔记录</div></div>
          <div class="p360-card"><div class="k">进项发票净额</div><div class="v">¥{{ fmt(profile.invoiceNetTotal) }}</div><div class="s">价税合计（红冲已抵）</div></div>
          <div class="p360-card"><div class="k">采购订单数</div><div class="v">{{ profile.orderCount }}</div><div class="s">未结 {{ profile.apUnpaidCount }} 笔应付</div></div>
        </div>
        <div class="p360-sec">最近采购订单</div>
        <el-table :data="profile.recentOrders" size="small" border>
          <el-table-column prop="orderNo" label="订单号" width="140" />
          <el-table-column prop="totalAmount" label="金额" width="110" align="right" />
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{row}">{{ {DRAFT:'草稿',CONFIRMED:'已确认',CLOSED:'已关闭'}[row.status] || row.status }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="140">
            <template #default="{row}">{{ (row.createTime || '').replace('T', ' ').substring(0, 16) }}</template>
          </el-table-column>
        </el-table>
        <div class="p360-sec">最近付款</div>
        <el-table :data="profile.recentPayments" size="small" border>
          <el-table-column prop="docNo" label="付款单" width="130" />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
          <el-table-column prop="method" label="方式" width="80" align="center">
            <template #default="{row}">{{ {BANK:'银行',CASH:'现金',ACCEPTANCE:'承兑'}[row.method] || row.method }}</template>
          </el-table-column>
          <el-table-column prop="payDate" label="日期" width="105" />
          <el-table-column prop="apDocNo" label="核销" :width="cw('核销') || undefined" min-width="130" show-overflow-tooltip />
        </el-table>
        <div class="p360-sec" v-if="profile.recentReturns?.length">最近退货</div>
        <el-table v-if="profile.recentReturns?.length" :data="profile.recentReturns" size="small" border>
          <el-table-column prop="docNo" label="退货单" width="120" />
          <el-table-column prop="materialName" label="物料" :width="cw('物料') || undefined" min-width="140" />
          <el-table-column prop="qty" label="数量" width="90" align="right" />
          <el-table-column prop="returnAmount" label="金额" width="90" align="right" />
          <el-table-column prop="status" label="状态" width="90" align="center" />
        </el-table>
      </template>
    </el-drawer>
    <el-dialog :title="form.id?'编辑':'新增'" v-model="dialogVisible" width="min(1100px, 96vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="材料供应商" value="MATERIAL" />
            <el-option label="成品供应商" value="FINISHED" />
            <el-option label="代工厂" value="PROCESSOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款条件" v-if="hasPerm('supplier:payment')">
          <el-select v-model="form.paymentTerms" style="width:100%" clearable>
            <el-option v-for="d in dicts.payment_terms" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义天数" v-if="form.paymentTerms === 'CUSTOM_CREDIT' && hasPerm('supplier:payment')">
          <el-input-number v-model="form.creditDays" :min="1" :max="365" style="width:100%" />
        </el-form-item>
        <el-form-item label="付款方式" v-if="hasPerm('supplier:payment')">
          <el-select v-model="form.paymentMethod" style="width:100%" clearable>
            <el-option v-for="d in dicts.payment_method" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="加工费" v-if="form.type === 'PROCESSOR'">
          <el-input-number v-model="form.processingFee" :min="0" :precision="2" :step="0.1" style="width:100%" placeholder="委外加工单价（元/kg）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'
import { useExcelImport } from '../composables/useExcelImport'
// v9.6 导出当前筛选（下载工具绕过 JSON 拦截器）
import { downloadFile } from '../utils/download'
const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/supplier/export', { keyword: keyword.value || undefined }, `供应商-${new Date().toLocaleDateString('sv')}.xlsx`)
  } finally { exporting.value = false }
}


const { importing, downloadTpl, onFile: onImportFile } = useExcelImport('/supplier', '供应商导入模板.xlsx', '供应商', fetch)

const list = ref([])
const keyword = ref('')
const typeFilter = ref('')
const dialogVisible = ref(false)
const profileVisible = ref(false)
const profile = ref(null)
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
async function openProfile(row) {
  profile.value = null
  profileVisible.value = true
  try { profile.value = await api.get(`/supplier/${row.id}/profile`) } catch {}
}
const form = ref({})
const perms = ref([])
const dicts = ref({})

// 表格列宽拖拽与持久化
const { cw, onHeaderDragend } = useColumnResize('supplier')

function hasPerm(code) { return perms.value.includes(code) }

function dictLabel(type, value) {
  const items = dicts.value[type] || []
  const found = items.find(d => d.value === value)
  return found ? found.label : (value || '—')
}

async function fetchDicts() {
  const all = await api.get('/dict')
  const map = {}
  for (const item of all) {
    if (!map[item.type]) map[item.type] = []
    map[item.type].push(item)
  }
  dicts.value = map
}

async function fetch() {
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (typeFilter.value) params.type = typeFilter.value
  list.value = await api.get('/supplier', { params })
}

function showDialog(row) {
  if (row) {
    form.value = { ...row, creditDays: null }
    // 如果是账期但不在字典里，说明是自定义天数
    const pt = row.paymentTerms || ''
    if (pt.startsWith('账期') && !dicts.value.payment_terms?.find(d => d.value === pt)) {
      form.value.paymentTerms = 'CUSTOM_CREDIT'
      form.value.creditDays = parseInt(pt.replace(/[^0-9]/g, '')) || 30
    }
  } else {
    form.value = { type: 'MATERIAL', paymentMethod: 'TRANSFER' }
  }
  dialogVisible.value = true
}

async function save() {
  // 自定义天数时拼接 paymentTerms
  if (form.value.paymentTerms === 'CUSTOM_CREDIT') {
    form.value.paymentTerms = `账期${form.value.creditDays || 30}天`
  }
  if (form.value.id) {
    await api.put(`/supplier/${form.value.id}`, form.value)
  } else {
    await api.post('/supplier', form.value)
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
  fetch()
}

async function del(id) {
  await ElMessageBox.confirm('确定删除？')
  await api.delete(`/supplier/${id}`)
  ElMessage.success('已删除')
  fetch()
}

// v5.27：拉黑 / 解除拉黑（拉黑后采购/委外选择时不可见）
async function toggleBlacklist(row, blacklisted) {
  await ElMessageBox.confirm(blacklisted ? `确定拉黑「${row.name}」？拉黑后下单选择供应商时将不可见` : `确定解除「${row.name}」的拉黑？`)
  await api.post(`/supplier/${row.id}/blacklist`, { blacklisted })
  ElMessage.success(blacklisted ? '已拉黑' : '已解除拉黑')
  fetch()
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await fetchDicts()
  await fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.type-tabs {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
}
.type-count {
  font-size: 13px;
  color: #64748b;
  margin-left: auto;
}
.p360-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 8px; }
.p360-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px 14px; }
.p360-card .k { font-size: 12px; color: #64748b; }
.p360-card .v { font-size: 20px; font-weight: 700; margin: 4px 0 2px; }
.p360-card .v.red { color: #ef4444; }
.p360-card .s { font-size: 11px; color: #94a3b8; }
.p360-sec { font-size: 14px; font-weight: 600; margin: 18px 0 8px; }
</style>
