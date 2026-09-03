<template>
  <div class="page-container">
    <div class="page-header">
      <h2>客户管理</h2>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索名称" clearable @keyup.enter="fetch" />
        <el-button @click="fetch">搜索</el-button>
        <el-button type="primary" @click="showDialog(null)" v-if="hasPerm('customer:write')">新增客户</el-button>
      </div>
    </div>
    <div class="table-card">
      <p-table :data="list" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="code" label="编码" :width="cw('编码') || 130" />
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" :width="cw('联系人') || 100" />
        <el-table-column prop="contactPhone" label="电话" :width="cw('电话') || 130" />
        <el-table-column prop="abcLevel" label="等级" :width="cw('等级') || 80" align="center" />
        <el-table-column label="状态" :width="cw('状态') || 80" align="center">
          <template #default="{row}">
            <el-tag v-if="row.blacklisted" size="small" type="danger">已拉黑</el-tag>
            <el-tag v-else-if="!row.enabled" size="small" type="info">已删除</el-tag>
            <el-tag v-else size="small" type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收款条件" :width="cw('收款条件') || 110" align="center">
          <template #default="{row}">{{ row.paymentTerms ? dictLabel('payment_terms', row.paymentTerms) : '—' }}</template>
        </el-table-column>
        <el-table-column label="收款方式" :width="cw('收款方式') || 100" align="center">
          <template #default="{row}">
            <el-tag v-if="row.paymentMethod" size="small" :type="row.paymentMethod==='ACCEPTANCE'?'warning':'primary'">{{ dictLabel('payment_method', row.paymentMethod) }}</el-tag>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center">
          <template #default="{row}">
            <button class="op-btn op-btn-trace" @click="openProfile(row)">360°</button>
            <button v-if="hasPerm('customer:write')" class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button v-if="row.blacklisted && hasPerm('customer:write')" class="op-btn op-btn-warn" @click="toggleBlacklist(row, false)">解除拉黑</button>
            <button v-else-if="row.enabled && hasPerm('customer:write')" class="op-btn op-btn-danger" @click="toggleBlacklist(row, true)">拉黑</button>
            <button v-if="hasPerm('customer:delete')" class="op-btn op-btn-danger" @click="del(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- v5.47 客户 360° 抽屉 -->
    <el-drawer v-model="profileVisible" :title="(profile?.customer?.name || '') + ' · 360° 视图'" size="min(760px, 96vw)">
      <template v-if="profile">
        <div class="p360-cards">
          <div class="p360-card"><div class="k">应收余额</div><div class="v red">¥{{ fmt(profile.arBalance) }}</div><div class="s">立账 ¥{{ fmt(profile.arTotal) }} / 已收 ¥{{ fmt(profile.arReceived) }}</div></div>
          <div class="p360-card"><div class="k">累计收款</div><div class="v">¥{{ fmt(profile.receiptTotal) }}</div><div class="s">{{ profile.recentReceipts.length }} 笔记录</div></div>
          <div class="p360-card"><div class="k">开票净额</div><div class="v">¥{{ fmt(profile.invoiceNetTotal) }}</div><div class="s">价税合计（红冲已抵）</div></div>
          <div class="p360-card">
            <div class="k">信用额度</div>
            <div class="v" :class="{ red: profile.customer.creditLimit > 0 && Number(profile.arBalance) > Number(profile.customer.creditLimit || 0) }">
              {{ profile.customer.creditLimit > 0 ? '¥' + fmt(profile.customer.creditLimit) : '不限额' }}
            </div>
            <div class="s">{{ profile.customer.creditLimit > 0 ? (Number(profile.arBalance) > Number(profile.customer.creditLimit) ? '已超限' : '余量 ¥' + fmt(Number(profile.customer.creditLimit) - Number(profile.arBalance))) : '下单不预警' }}</div>
          </div>
        </div>
        <div class="p360-sec">最新成交价 <span class="p360-hint">（下单选物料自动带出）</span></div>
        <el-table :data="recentPrices" size="small" border max-height="240">
          <el-table-column prop="materialCode" label="编码" width="110" />
          <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
          <el-table-column label="最新单价" width="110" align="right">
            <template #default="{row}">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="来源订单" width="120" />
          <el-table-column prop="orderDate" label="订单日期" width="105" />
        </el-table>
        <div class="p360-sec">最近订单</div>
        <el-table :data="profile.recentOrders" size="small" border>
          <el-table-column prop="orderNo" label="订单号" width="120" />
          <el-table-column prop="materialNames" label="物料" min-width="160" show-overflow-tooltip />
          <el-table-column prop="totalAmount" label="金额" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{row}">{{ {DRAFT:'草稿',CONFIRMED:'已确认',SHIPPED:'已发货',CLOSED:'已关闭'}[row.status] || row.status }}</template>
          </el-table-column>
        </el-table>
        <div class="p360-sec">最近收款</div>
        <el-table :data="profile.recentReceipts" size="small" border>
          <el-table-column prop="docNo" label="收款单" width="130" />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
          <el-table-column prop="method" label="方式" width="80" align="center">
            <template #default="{row}">{{ {BANK:'银行',CASH:'现金',ACCEPTANCE:'承兑'}[row.method] || row.method }}</template>
          </el-table-column>
          <el-table-column prop="receiptDate" label="日期" width="105" />
          <el-table-column prop="arDocNo" label="核销" min-width="130" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
    <el-dialog :title="form.id?'编辑客户':'新增客户'" v-model="dialogVisible" width="min(1100px, 96vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <!-- v5.27：合同需方（甲方）信息，打印销售合同时直接取用 -->
        <el-form-item label="法定代表人"><el-input v-model="form.legalPerson" placeholder="合同甲方代表" /></el-form-item>
        <el-form-item label="开户银行"><el-input v-model="form.bankName" placeholder="如：中国工商银行济南分行" /></el-form-item>
        <el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="form.taxNo" placeholder="纳税人识别号" /></el-form-item>
        <el-form-item label="等级"><el-select v-model="form.abcLevel"><el-option v-for="d in dicts.customer_level || []" :key="d.value" :label="d.label" :value="d.value" /></el-select></el-form-item>
        <el-form-item label="收款条件">
          <el-select v-model="form.paymentTerms" style="width:100%" clearable placeholder="账期缓冲，影响应收到期日">
            <el-option v-for="d in dicts.payment_terms" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.paymentTerms === 'CUSTOM_CREDIT'" label="自定义天数">
          <el-input-number v-model="form.creditDays" :min="1" :max="365" style="width:100%" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-select v-model="form.paymentMethod" style="width:100%" clearable placeholder="电汇或承兑">
            <el-option v-for="d in dicts.payment_method" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <!-- v5.52 信用额度：空/0=不限额，下单时超额弹预警 -->
        <el-form-item label="信用额度">
          <el-input-number v-model="form.creditLimit" :min="0" :precision="2" :step="10000" style="width:100%" placeholder="留空不限额" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
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

const list = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const profileVisible = ref(false)
const profile = ref(null)
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
async function openProfile(row) {
  profile.value = null
  profileVisible.value = true
  loadRecentPrices(row.id)
  try { profile.value = await api.get(`/customer/${row.id}/profile`) } catch {}
}
const form = ref({})
const perms = ref([])
const dicts = ref({})
const recentPrices = ref([])

// v5.52 360° 成交价：打开抽屉时拉该客户各物料最新价
async function loadRecentPrices(customerId) {
  recentPrices.value = []
  try { recentPrices.value = await api.get('/sales-order/recent-prices', { params: { customerId } }) || [] } catch {}
}

// 字典值转标签
function dictLabel(type, value) {
  const d = dicts.value[type]?.find(d => d.value === value)
  return d ? d.label : (value || '')
}

// 表格列宽拖拽与持久化
const { cw, onHeaderDragend } = useColumnResize('customer')

function hasPerm(code) { return perms.value.includes(code) }

async function fetch() {
  const params = keyword.value ? { keyword: keyword.value } : {}
  list.value = await api.get('/customer', { params })
}

function showDialog(row) {
  form.value = row ? { ...row, creditDays: null } : { paymentMethod: 'TRANSFER' }
  // 自定义账期（如「账期45天」）不在字典里，转为 CUSTOM_CREDIT + 天数
  const pt = form.value.paymentTerms || ''
  if (pt.startsWith('账期') && !dicts.value.payment_terms?.find(d => d.value === pt)) {
    form.value.paymentTerms = 'CUSTOM_CREDIT'
    form.value.creditDays = parseInt(pt.replace(/[^0-9]/g, '')) || 30
  }
  dialogVisible.value = true
}

async function save() {
  // 自定义天数时拼接为「账期N天」
  if (form.value.paymentTerms === 'CUSTOM_CREDIT') {
    form.value.paymentTerms = `账期${form.value.creditDays || 30}天`
  }
  if (form.value.id) {
    await api.put(`/customer/${form.value.id}`, form.value)
  } else {
    await api.post('/customer', form.value)
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
  fetch()
}

async function del(id) {
  await ElMessageBox.confirm('确定删除？')
  await api.delete(`/customer/${id}`)
  ElMessage.success('已删除')
  fetch()
}

// v5.27：拉黑 / 解除拉黑（拉黑后销售下单选择客户时不可见）
async function toggleBlacklist(row, blacklisted) {
  await ElMessageBox.confirm(blacklisted ? `确定拉黑「${row.name}」？拉黑后下单选择客户时将不可见` : `确定解除「${row.name}」的拉黑？`)
  await api.post(`/customer/${row.id}/blacklist`, { blacklisted })
  ElMessage.success(blacklisted ? '已拉黑' : '已解除拉黑')
  fetch()
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  try {
    const ds = await api.get('/dict')
    const grouped = {}
    ds.forEach(d => { if (!grouped[d.type]) grouped[d.type] = []; grouped[d.type].push(d) })
    dicts.value = grouped
  } catch {}
  await fetch()
})
</script>

<style scoped>
.page-container {
  width: 100%;
}
.p360-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 8px; }
.p360-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px 14px; }
.p360-card .k { font-size: 12px; color: #64748b; }
.p360-card .v { font-size: 20px; font-weight: 700; margin: 4px 0 2px; }
.p360-card .v.red { color: #ef4444; }
.p360-card .s { font-size: 11px; color: #94a3b8; }
.p360-sec { font-size: 14px; font-weight: 600; margin: 18px 0 8px; }
.p360-hint { font-size: 12px; font-weight: 400; color: #94a3b8; }
</style>
