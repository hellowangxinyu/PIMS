<template>
  <div class="page-container">
    <div class="page-header">
      <h2>油尾退回</h2>
      <el-button type="primary" @click="openCreate">新建油尾退回单</el-button>
    </div>
    <div class="tip-bar">
      客户生产完毕后未用完的油漆（已加稀料）退回芃远：原批号带回、入库「油尾库」，不再销售，仅制漆配方消化或报废。
      结算两种方式：<b>折价退回</b>（按折扣价退款冲应收，单价填正数）/<b>付费回收</b>（客户付钱给芃远，单价填负数）。
    </div>

    <div class="table-card">
      <p-table :data="rows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单号" width="130" />
        <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
        <el-table-column prop="refSalesOutboundNo" label="原出库单" width="150" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" width="110" />
        <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="原批号" width="130" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="85" align="right" />
        <el-table-column label="结算方式" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="row.settleType === 'PAID_RECYCLE' ? 'danger' : 'warning'" size="small">{{ settleLabel(row.settleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="85" align="right" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span :class="Number(row.returnAmount) < 0 ? 'amt-neg' : ''">{{ fmtMoney(row.returnAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationName" label="油尾库位" width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="85" />
        <el-table-column prop="createTime" label="时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-success" @click="confirmOne(row)">确认入库</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-danger" @click="rejectOne(row)">驳回</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- 新建油尾退回单 -->
    <el-dialog title="新建油尾退回单" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="原出库单" required>
          <el-select v-model="form.refSalesOutboundNo" filterable placeholder="选择已确认的销售出库单" style="width:100%" @change="onRefChange">
            <el-option v-for="o in outbounds" :key="o.docNo" :label="o.docNo + ' ' + o.materialName + '（' + o.batchNo + '，可退 ' + o.remaining + '）'" :value="o.docNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="form.customerName" disabled />
        </el-form-item>
        <el-form-item label="物料">
          <el-input :value="form.materialCode + ' ' + (form.materialName || '')" disabled />
        </el-form-item>
        <el-form-item label="原批号">
          <el-input v-model="form.batchNo" disabled />
        </el-form-item>
        <el-form-item label="退回数量" required>
          <el-input-number v-model="form.qty" :min="0.001" :precision="3" :step="1" style="width:160px" />
          <span class="hint-text" v-if="form.maxQty">可退 {{ form.maxQty }}</span>
        </el-form-item>
        <el-form-item label="结算方式" required>
          <el-radio-group v-model="form.settleType" @change="onSettleChange">
            <el-radio value="DISCOUNT_RETURN">折价退回（退款冲应收）</el-radio>
            <el-radio value="PAID_RECYCLE">付费回收（客户付钱给芃远）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="结算单价" required>
          <el-input-number v-model="form.unitPrice" :precision="2" :step="1" style="width:160px" />
          <div class="hint-text">
            原销售单价 {{ fmtMoney(form.salePrice) }}；折价退填正数（如 5），付费回收填负数（如 -2）
          </div>
        </el-form-item>
        <el-form-item label="入油尾仓库" required>
          <el-select v-model="form.warehouseId" placeholder="选择仓库（退回的油尾入该仓油尾库）" style="width:100%">
            <el-option v-for="w in tailingOptions" :key="w.warehouseId" :label="w.warehouseName" :value="w.warehouseId" />
          </el-select>
          <div class="hint-text">确认时按产品体系自动入该仓的聚酯油尾库或氟碳油尾库（库位自动分配）</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtMoney as fmtMoneyBase } from '../utils/fmt'
import { statusType } from '../utils/statusTag'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const rows = ref([])
const visible = ref(false)
const saving = ref(false)
const outbounds = ref([])
const tailingOptions = ref([])
const form = ref({})

function settleLabel(s) { return s === 'PAID_RECYCLE' ? '付费回收' : s === 'DISCOUNT_RETURN' ? '折价退回' : '-' }
// v6.4 状态色统一（utils/statusTag 全局映射）
function statusLabel(s) { return { CONFIRMED: '已入油尾库', DRAFT: '草稿', REJECTED: '已驳回' }[s] || '未知' }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
function fmtMoney(v) { return '￥' + fmtMoneyBase(v) }   // v6.6 收口：千分位（utils/fmt）

async function fetchList() {
  try { rows.value = await api.get('/tailing-return') } catch {}
}

async function openCreate() {
  form.value = { refSalesOutboundNo: '', customerName: '', materialCode: '', materialName: '', batchNo: '', qty: null, maxQty: null, salePrice: 0, settleType: 'DISCOUNT_RETURN', unitPrice: 0, warehouseId: '', remark: '' }
  try { outbounds.value = await api.get('/tailing-return/returnable-outbounds') } catch {}
  // v5.38.2：只选仓库；库位按体系自动路由（确认时回写单据）
  try {
    tailingOptions.value = await api.get('/tailing-return/warehouse-options') || []
    if (tailingOptions.value.length) form.value.warehouseId = tailingOptions.value[0].warehouseId
  } catch {}
  visible.value = true
}

function onRefChange(docNo) {
  const o = outbounds.value.find(x => x.docNo === docNo)
  if (!o) return
  form.value.customerName = o.customerName || ''
  form.value.materialCode = o.materialCode
  form.value.materialName = o.materialName
  form.value.batchNo = o.batchNo
  form.value.maxQty = o.remaining
  form.value.qty = o.remaining
  form.value.salePrice = Number(o.salePrice) || 0
  // 默认折价退回按原销售价预填（可改折扣）
  form.value.unitPrice = Number(o.salePrice) || 0
}

function onSettleChange() {
  // 切换结算方式时给个合理默认值
  if (form.value.settleType === 'PAID_RECYCLE' && Number(form.value.unitPrice) >= 0) form.value.unitPrice = -1
  if (form.value.settleType === 'DISCOUNT_RETURN' && Number(form.value.unitPrice) <= 0) form.value.unitPrice = Number(form.value.salePrice) || 1
}

async function submit() {
  if (!form.value.refSalesOutboundNo) { ElMessage.warning('请选择原销售出库单'); return }
  if (!form.value.qty || form.value.qty <= 0) { ElMessage.warning('请输入退回数量'); return }
  if (form.value.maxQty != null && form.value.qty > form.value.maxQty) { ElMessage.warning(`退回数量不能超过可退量 ${form.value.maxQty}`); return }
  if (form.value.settleType === 'DISCOUNT_RETURN' && Number(form.value.unitPrice) <= 0) { ElMessage.warning('折价退回的单价必须大于 0'); return }
  if (form.value.settleType === 'PAID_RECYCLE' && Number(form.value.unitPrice) >= 0) { ElMessage.warning('付费回收的单价必须小于 0（客户付钱给芃远）'); return }
  if (!form.value.warehouseId) { ElMessage.warning('请选择入油尾仓库'); return }
  saving.value = true
  try {
    await api.post('/tailing-return', {
      customerName: form.value.customerName,
      refSalesOutboundNo: form.value.refSalesOutboundNo,
      materialCode: form.value.materialCode,
      materialName: form.value.materialName,
      batchNo: form.value.batchNo,
      unit: 'kg',
      qty: form.value.qty,
      unitPrice: form.value.unitPrice,
      settleType: form.value.settleType,
      warehouseId: form.value.warehouseId,
      remark: form.value.remark
    })
    ElMessage.success('油尾退回单已创建（草稿）')
    visible.value = false
    fetchList()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '创建失败')
  } finally { saving.value = false }
}

async function confirmOne(row) {
  try {
    await ElMessageBox.confirm(
      `确认油尾退回单 ${row.docNo}？确认后货物入库「油尾库」（原批号 ${row.batchNo}），不再销售。\n` +
      (row.settleType === 'PAID_RECYCLE' ? '付费回收：将生成客户应收（客户付钱给芃远）。' : '折价退回：将冲减该销售订单应收。'),
      '确认入库', { type: 'warning' })
    await api.post(`/tailing-return/${row.id}/confirm`)
    ElMessage.success('已确认，货物已入油尾库')
    fetchList()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || '确认失败') }
}

async function rejectOne(row) {
  try {
    await ElMessageBox.confirm(`确认驳回油尾退回单 ${row.docNo}？`, '驳回', { type: 'warning' })
    await api.post(`/tailing-return/${row.id}/reject`)
    ElMessage.success('已驳回')
    fetchList()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || '驳回失败') }
}

onMounted(fetchList)
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.tip-bar { background: #fff7ed; border: 1px solid #fed7aa; border-radius: 8px; padding: 10px 14px; font-size: 13px; color: #9a3412; margin-bottom: 14px; line-height: 1.6; }
.table-card { background: #fff; border-radius: 8px; padding: 12px; }
.hint-text { font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 2px; }
.amt-neg { color: #16a34a; font-weight: 600; }
</style>
