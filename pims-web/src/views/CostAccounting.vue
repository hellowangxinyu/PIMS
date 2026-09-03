<template>
  <div class="page-container">
    <div class="page-header">
      <h2>成本核算</h2>
      <div class="header-actions">
        <el-radio-group v-model="orderType" @change="fetch">
          <el-radio-button value="PRODUCTION">生产订单</el-radio-button>
          <el-radio-button value="OUTSOURCE">委外订单</el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" placeholder="搜索订单号/产品" clearable style="width:190px" />
      </div>
    </div>

    <div class="caliber-tip">
      实际成本 = {{ orderType === 'PRODUCTION' ? '领料成本（确认出库） + 人工费 + 制造费用（后两项手工补录）' : '发料成本（确认出库） + 加工费（委外入库立账应付）' }}；
      理论成本 = 配方 BOM 用量 × 库存加权均价，按订单批量折算；差异% =（实际 − 理论）÷ 理论 × 100。
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('cost')">
      <div class="summary-card"><div class="sc-label">订单总成本</div><div class="sc-value">¥{{ fmt(totalCost) }}</div></div>
      <div class="summary-card"><div class="sc-label">材料成本</div><div class="sc-value">¥{{ fmt(totalMaterial) }}</div></div>
      <div class="summary-card" v-if="orderType === 'OUTSOURCE'"><div class="sc-label">加工费</div><div class="sc-value">¥{{ fmt(totalFee) }}</div></div>
      <div class="summary-card" v-if="orderType === 'PRODUCTION'"><div class="sc-label">人工 + 制费</div><div class="sc-value">¥{{ fmt(totalLabor) }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ filtered.length }} 张订单</span></div>
      <p-table :data="pagedRows" stripe border style="width:100%" :default-sort="{ prop: 'createTime', order: 'descending' }">
        <el-table-column prop="orderNo" label="订单号" min-width="130" show-overflow-tooltip sortable />
        <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="batchQty" label="批量" width="90" align="right" sortable>
          <template #default="{ row }">{{ row.batchQty }}{{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="materialCost" label="材料成本" width="110" align="right" v-if="hasAmountPerm('cost')" sortable>
          <template #default="{ row }">¥{{ fmt(row.materialCost) }}</template>
        </el-table-column>
        <el-table-column v-if="orderType === 'OUTSOURCE'" prop="outsourceFee" label="加工费" width="100" align="right" sortable>
          <template #default="{ row }">¥{{ fmt(row.outsourceFee) }}</template>
        </el-table-column>
        <template v-if="orderType === 'PRODUCTION'">
          <el-table-column prop="laborFee" label="人工费" width="90" align="right" v-if="hasAmountPerm('cost')">
            <template #default="{ row }">¥{{ fmt(row.laborFee) }}</template>
          </el-table-column>
          <el-table-column prop="overheadFee" label="制费" width="90" align="right" v-if="hasAmountPerm('cost')">
            <template #default="{ row }">¥{{ fmt(row.overheadFee) }}</template>
          </el-table-column>
        </template>
        <el-table-column prop="totalCost" label="总成本" width="110" align="right" v-if="hasAmountPerm('cost')" sortable>
          <template #default="{ row }"><b>¥{{ fmt(row.totalCost) }}</b></template>
        </el-table-column>
        <el-table-column prop="outputQty" label="产出量" width="90" align="right" sortable>
          <template #default="{ row }">
            <span v-if="Number(row.outputQty) > 0">{{ row.outputQty }}{{ row.unit }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="unitCost" label="单位成本" width="100" align="right" v-if="hasAmountPerm('cost')">
          <template #default="{ row }">
            <span v-if="row.unitCost">¥{{ fmt(row.unitCost) }}/{{ row.unit }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="theoreticalCost" label="理论成本" width="105" align="right" v-if="hasAmountPerm('cost')">
          <template #default="{ row }">
            <span v-if="row.theoreticalCost">¥{{ fmt(row.theoreticalCost) }}</span>
            <span v-else style="color:#94a3b8">无配方</span>
          </template>
        </el-table-column>
        <el-table-column prop="costDiff" label="差异%" width="90" align="right" v-if="hasAmountPerm('cost')" sortable>
          <template #default="{ row }">
            <span v-if="row.costDiff !== null && row.costDiff !== undefined" :style="diffColor(row.costDiff)">{{ row.costDiff > 0 ? '+' : '' }}{{ row.costDiff }}%</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="105" sortable prop="createTime">
          <template #default="{ row }">{{ fmtMs(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" v-if="orderType === 'PRODUCTION' && hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openFees(row)">补录人工制费</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 人工/制费补录弹窗 -->
    <el-dialog :title="feeRow ? `人工制费补录 · ${feeRow.orderNo}` : ''" v-model="feeVisible" width="min(1100px, 96vw)" destroy-on-close>
      <div class="caliber-tip small">人工费与制造费用系统无自动数据源，按工资分摊、水电折旧等口径手工录入，计入订单总成本。</div>
      <el-form :model="feeForm" label-width="90px">
        <el-form-item label="人工费">
          <el-input-number v-model="feeForm.laborFee" :min="0" :precision="2" :step="100" style="width:100%" />
        </el-form-item>
        <el-form-item label="制造费用">
          <el-input-number v-model="feeForm.overheadFee" :min="0" :precision="2" :step="100" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFees" :loading="loading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const list = ref([])
const perms = ref([])
const loading = ref(false)
const orderType = ref('PRODUCTION')
const keyword = ref('')
const feeVisible = ref(false)
const feeRow = ref(null)
const feeForm = ref({ laborFee: 0, overheadFee: 0 })

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function fmtMs(v) {
  if (v == null) return ''
  if (typeof v === 'number' || /^\d{10,}$/.test(String(v))) return new Date(Number(v)).toISOString().slice(0, 10)
  return String(v).replace('T', ' ').substring(0, 10)
}
function statusLabel(s) { return { DRAFT: '草稿', CONFIRMED: '已确认', COMPLETED: '已完工' }[s] || s }
function diffColor(d) {
  const n = Number(d)
  if (n > 20) return 'color:#ef4444;font-weight:600'
  if (n < -20) return 'color:#f59e0b;font-weight:600'
  return 'color:#16a34a'
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter(r => [r.orderNo, r.productName].some(v => v && String(v).toLowerCase().includes(kw)))
})

const totalCost = computed(() => filtered.value.reduce((s, r) => s + Number(r.totalCost || 0), 0))
const totalMaterial = computed(() => filtered.value.reduce((s, r) => s + Number(r.materialCost || 0), 0))
const totalFee = computed(() => filtered.value.reduce((s, r) => s + Number(r.outsourceFee || 0), 0))
const totalLabor = computed(() => filtered.value.reduce((s, r) => s + Number(r.laborFee || 0) + Number(r.overheadFee || 0), 0))

async function fetch() {
  try { list.value = await api.get('/cost/order', { params: { type: orderType.value } }) } catch {}
}

function openFees(row) {
  feeRow.value = row
  feeForm.value = { laborFee: Number(row.laborFee || 0), overheadFee: Number(row.overheadFee || 0) }
  feeVisible.value = true
}

async function saveFees() {
  loading.value = true
  try {
    await api.put(`/cost/order/${feeRow.value.orderNo}/fees`, null,
      { params: { laborFee: feeForm.value.laborFee, overheadFee: feeForm.value.overheadFee } })
    ElMessage.success('已保存')
    feeVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
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
.caliber-tip { font-size: 12px; color: #64748b; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 14px; margin-bottom: 14px; line-height: 1.7; }
.caliber-tip.small { margin-bottom: 14px; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 20px; font-weight: 700; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
</style>
