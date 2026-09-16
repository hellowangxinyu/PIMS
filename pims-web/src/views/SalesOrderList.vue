<template>
  <div class="page-container">
    <div class="page-header">
      <h2>销售订单</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate" v-if="hasPerm('sales:write')">新增销售订单</el-button>
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="type-tabs">
        <button class="type-tab" :class="{ active: activeTab === 'ACTIVE' }" @click="activeTab = 'ACTIVE'">进行中 <span class="tab-badge">{{ activeCount }}</span></button>
        <button class="type-tab" :class="{ active: activeTab === 'SHIPPED' }" @click="activeTab = 'SHIPPED'">已发货 <span class="tab-badge">{{ shippedCount }}</span></button>
        <button class="type-tab" :class="{ active: activeTab === 'CLOSED' }" @click="activeTab = 'CLOSED'">已结束 <span class="tab-badge">{{ closedCount }}</span></button>
        <span class="type-count" style="margin-left:auto">共 {{ filteredList.length }} 条</span>
      </div>
      <template v-if="!isMobile">
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="orderNo" label="订单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || custName(row.customerId) }}</template>
        </el-table-column>
        <el-table-column prop="contractNo" label="合同号" width="120" show-overflow-tooltip />
        <el-table-column prop="materialNames" label="品名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialNames || '-' }}</template>
        </el-table-column>
        <el-table-column prop="materialQtySummary" label="数量" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialQtySummary || '-' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">￥{{ fmt(row.totalAmount || 0) }}</template>
        </el-table-column>
        <el-table-column label="运费" width="95" align="right" v-if="hasPerm('finance:amount')">
          <template #default="{ row }">
            <span v-if="Number(row.freightTotal) > 0">￥{{ fmt(row.freightTotal) }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            <el-tag v-if="row.creditExceeded" type="danger" size="small" effect="plain" style="margin-left:4px">超信用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发货仓库" width="110">
          <template #default="{ row }">{{ row.sourceWarehouseId ? whName(row.sourceWarehouseId) : '待排产' }}</template>
        </el-table-column>
        <el-table-column label="期望发货" width="110">
          <template #default="{ row }">{{ row.expectedShipDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column label="操作" width="420" align="center">
          <template #default="{ row }">
            <button class="op-btn op-btn-trace" @click="openItems(row)">明细</button>
            <!-- v5.27：打印合同（合同号自动生成，打印即生成合同） -->
            <button v-if="row.status !== 'DRAFT'" class="op-btn op-btn-primary" @click="printContract(row)">打印合同</button>
            <!-- v5.27：一键转生产订单（半成品/成品明细各生成一张生产单） -->
            <button v-if="row.status === 'CONFIRMED' && hasPerm('production:write')" class="op-btn op-btn-success" @click="toProduction(row)">转生产</button>
            <!-- v5.27：一键转委外订单（需选择代工厂） -->
            <button v-if="row.status === 'CONFIRMED' && hasPerm('outsource:write')" class="op-btn op-btn-primary" @click="openToOutsource(row)">转委外</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="openEdit(row)">编辑</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-success" @click="confirmOrder(row)">确认</button>
            <button class="op-btn op-btn-alt" @click="openChanges(row)">变更</button>
            <button v-if="row.status === 'CONFIRMED'" class="op-btn op-btn-primary" @click="openShip(row)">发货</button>
            <!-- v5.27：结束订单（手工结束，已确认/已发货可结束） -->
            <button v-if="row.status === 'CONFIRMED' || row.status === 'SHIPPED'" class="op-btn op-btn-danger" @click="closeOrder(row)">结束订单</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-danger" @click="deleteOrder(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </template>
    <!-- v10.1 手机卡片视图（桌面分支 v-if=!isMobile 渲染路径不变） -->
    <div v-if="isMobile" class="m-cards">
      <div v-for="row in pagedRows" :key="row.id" class="m-card">
        <div class="m-card-head">
          <div>
            <div class="m-card-title">{{ row.customerName || custName(row.customerId) }}</div>
            <div class="m-card-sub">{{ row.orderNo }}</div>
          </div>
          <span class="m-status">{{ statusLabel(row.status) }}{{ row.creditExceeded ? ' · 超信用' : '' }}</span>
        </div>
        <div class="m-card-row" v-if="row.materialNames"><span>品名</span><span class="m-val">{{ row.materialNames }}</span></div>
        <div class="m-card-row" v-if="row.materialQtySummary"><span>数量</span><span class="m-val">{{ row.materialQtySummary }}</span></div>
        <div class="m-card-row"><span>金额</span><span class="m-val num">￥{{ fmt(row.totalAmount || 0) }}</span></div>
        <div class="m-card-row" v-if="row.expectedShipDate"><span>期望发货</span><span class="m-val">{{ row.expectedShipDate }}</span></div>
        <div class="m-card-actions">
          <el-button size="small" @click="openItems(row)">明细</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" @click="confirmOrder(row)">确认</el-button>
          <el-button v-if="row.status === 'CONFIRMED'" size="small" type="primary" @click="openShip(row)">发货</el-button>
        </div>
      </div>
      <div v-if="!pagedRows.length" class="m-empty">暂无订单</div>
    </div>
      <!-- 分页（v5.2） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="list.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <!-- 新增销售订单 -->
    <el-dialog :title="editingOrder ? '编辑销售订单 ' + editingOrder.orderNo : '新增销售订单'" v-model="createVisible" width="min(860px, 94vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width:100%" @change="onCustChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <!-- v5.27：合同号自动生成（打印合同用）；发货仓库由排产环节确定，下单不选 -->
        <el-form-item label="税率(%)">
          <el-input-number v-model="form.taxRate" :min="0" :max="17" :precision="2" :step="1" style="width:160px" />
          <span style="margin-left:8px;font-size:12px;color:#888">默认13%，单价为含税价</span>
        </el-form-item>
        <el-form-item label="期望发货">
          <el-date-picker v-model="form.expectedShipDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <!-- v5.27：先选销售大类（材料/半成品/成品），明细物料只从该大类选 -->
        <el-form-item label="销售大类" required>
          <el-radio-group v-model="form.saleCategory" @change="onCategoryChange">
            <el-radio-button v-for="cat in CATEGORIES" :key="cat.code" :value="cat.code">{{ cat.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="销售明细" required>
          <div style="width:100%">
            <p-table :data="form.items" border size="small" style="width:100%">
              <el-table-column label="物料" min-width="200">
                <template #default="{ row }">
                  <el-select v-model="row.materialCode" filterable size="small" placeholder="搜索物料" style="width:100%" @change="onItemMatChange(row)">
                    <el-option v-for="m in categoryMaterials" :key="m.code" :label="m.code + ' ' + (m.name || '')" :value="m.code" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.qty" :min="0.001" :precision="3" :step="1" size="small" controls-position="right" style="width:110px" @change="onQtyChange(row)" />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="80">
                <template #default="{ row }"><el-input v-model="row.unit" size="small" placeholder="kg" /></template>
              </el-table-column>
              <el-table-column label="单价" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="1" size="small" controls-position="right" style="width:110px" />
                </template>
              </el-table-column>
              <el-table-column label="金额" width="100" align="right">
                <template #default="{ row }">￥{{ ((Number(row.qty) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="" width="60" align="center">
                <template #default="{ $index }">
                  <button class="op-btn op-btn-danger" @click="form.items.splice($index, 1)">✕</button>
                </template>
              </el-table-column>
            </p-table>
            <el-button size="small" type="primary" style="margin-top:8px" @click="addItem">+ 添加明细</el-button>
            <div class="total-bar">合计金额：￥{{ totalAmount }}</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="loading">保存订单</el-button>
      </template>
    </el-dialog>

    <!-- 查看明细 -->
    <el-dialog :title="'订单明细 ' + (itemsOrder?.orderNo || '')" v-model="itemsVisible" width="min(1100px, 96vw)">      <p-table :data="orderItems" border size="small" style="width:100%">
        <el-table-column prop="materialCode" label="编码" width="120" />
        <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column label="单价(含税)" width="105" align="right">
          <template #default="{ row }">{{ row.unitPrice != null ? '￥' + fmt(row.unitPrice) : '-' }}</template>
        </el-table-column>
        <el-table-column label="不含税单价" width="100" align="right">
          <template #default="{ row }">{{ fmtTax(netOfTax(row.unitPrice, detailTaxRate)) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="85" align="right">
          <template #default="{ row }">{{ fmtTax(taxOf(row.unitPrice, detailTaxRate)) }}</template>
        </el-table-column>
        <el-table-column label="税率" width="65" align="center">
          <template #default="{ row }">{{ detailTaxRate }}%</template>
        </el-table-column>
        <el-table-column label="金额(含税)" width="110" align="right">
          <template #default="{ row }">￥{{ ((Number(row.qty) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="已发" width="90" align="right">
          <template #default="{ row }">{{ row.shippedQty || 0 }}</template>
        </el-table-column>
      </p-table>
    </el-dialog>

    <!-- 参照订单发货 -->
    <el-dialog :title="'发货 ' + (shipOrder?.orderNo || '')" v-model="shipVisible" width="min(880px, 94vw)" destroy-on-close>
      <el-form :model="shipForm" label-width="90px">
        <el-form-item label="客户">
          <el-input :value="shipOrder?.customerName || custName(shipOrder?.customerId)" disabled />
        </el-form-item>
        <el-alert v-if="shipCreditWarn" :title="shipCreditWarn" type="warning" show-icon :closable="false" style="margin-bottom: 12px" />
        <el-form-item label="出库仓库" required>
          <el-select v-model="shipForm.warehouseId" placeholder="选择出库仓库" style="width:100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shipForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="shipItems.length" label="发货明细">
          <p-table :data="shipItems" border size="small" style="width:100%">
            <el-table-column prop="materialCode" label="编码" min-width="110" />
            <el-table-column prop="materialName" label="品名" min-width="120" show-overflow-tooltip />
            <el-table-column label-width="10" align="center">
              <template #header>
                <span>批次（必选）</span>
                <el-tag v-if="costingMethod === 'MOVING_AVG' || costingMethod === 'MONTHLY_AVG'" size="small" type="warning" style="margin-left:4px">成本按均价</el-tag>
              </template>
              <template #default="{ row }">
                <el-select v-model="row.batchNo" size="small" placeholder="选择批次" style="width:100%">
                  <el-option v-for="b in (batchOptions[row.materialCode] || [])" :key="b.batchNo" :label="b.batchNo + (b.places.length ? ' ' + b.places.join('、') : '') + ' (现存量:' + b.qty + ')'" :value="b.batchNo" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="发货数量" width="150" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.shipQty" :min="0.001" :max="Number(row.remainQty)" :precision="3" :step="1" size="small" controls-position="right" style="width:130px" />
              </template>
            </el-table-column>
            <el-table-column label="未发量" width="90" align="right">
              <template #default="{ row }">{{ row.remainQty }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="60" align="center" />
          </p-table>
          <div class="qty-hint">可修改发货数量（不超过未发量），每行必须选择出库批号</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShip" :loading="loading">发货出库</el-button>
      </template>
    </el-dialog>

    <!-- v5.27：转委外弹窗（选择代工厂 + 加工费） -->
    <el-dialog :title="'转委外 ' + (ooOrder?.orderNo || '')" v-model="ooVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="ooForm" label-width="90px">
        <el-form-item label="代工厂" required>
          <el-select v-model="ooForm.processorId" filterable placeholder="请选择代工厂（供应商管理-代工厂档案）" style="width:100%" @change="onOoProcessorChange">
            <el-option v-for="s in processors" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="加工费单价" required>
          <el-input-number v-model="ooForm.processingFee" :min="0.01" :precision="2" :step="1" style="width:100%" />
          <span style="margin-left:8px;font-size:12px;color:#94a3b8">元/{{ ooOrder?.unit || 'kg' }}，委外入库后自动生成应付，必填</span>
        </el-form-item>
        <div class="qty-hint">半成品/成品明细将各生成一张委外订单（草稿），材料明细自动跳过；已生成过的产品自动跳过。</div>
      </el-form>
      <template #footer>
        <el-button @click="ooVisible = false">取消</el-button>
        <el-button type="primary" @click="submitToOutsource" :loading="loading">生成委外订单</el-button>
      </template>
    </el-dialog>

    <!-- v5.47 变更记录弹窗 -->
    <el-drawer v-model="changesVisible" :title="'订单变更记录 ' + (editingOrder?.orderNo || '')" size="min(560px, 92vw)">
      <el-empty v-if="!changes.length" description="该订单暂无变更记录" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item v-for="c in changes" :key="c.id" :timestamp="(c.createTime || '').replace('T', ' ').substring(0, 16)" placement="top">
          <div style="font-weight:600">{{ c.operator }}</div>
          <div style="font-size:13px;color:#475569;line-height:1.7">{{ c.detail }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { statusType } from '../utils/statusTag'
import { isMobile } from '../composables/useIsMobile'
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { loadTaxRate, netOfTax, taxOf, fmtTax } from '../utils/tax'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'

const taxRate = ref(13)
const list = ref([])
const exporting = ref(false)

// v5.23：导出全部销售订单
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/sales-order/export', {}, `销售订单-${todayLocal()}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}
const customers = ref([])
const warehouses = ref([])
const materials = ref([])
const perms = ref([])
const loading = ref(false)

// 新增弹窗
const createVisible = ref(false)
const form = ref({ customerId: null, contractNo: '', sourceWarehouseId: '', expectedShipDate: '', remark: '', saleCategory: '', items: [] })
const totalAmount = computed(() => form.value.items.reduce((s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0).toFixed(2))

// v5.27：销售大类（与质检分类一致：A/P/F/R/S=材料，B=半成品，C=成品）
const CATEGORIES = [
  { code: 'A,P,F,R,S', label: '材料' },
  { code: 'B', label: '半成品' },
  { code: 'C', label: '成品' }
]
// 按所选大类过滤物料（先选大类，再选物料）
const categoryMaterials = computed(() => {
  const cat = form.value.saleCategory
  if (!cat) return []
  return materials.value.filter(m => m.category && cat.split(',').includes(m.category))
})
function onCategoryChange() {
  // 切换大类清空已选明细，避免混入其他大类的物料
  form.value.items = [emptyItem()]
}

// 明细弹窗
const itemsVisible = ref(false)
const itemsOrder = ref(null)
const detailTaxRate = ref(13)
const orderItems = ref([])

// 发货弹窗
const shipVisible = ref(false)
const shipOrder = ref(null)
const shipItems = ref([])
const batchOptions = ref({})
const costingMethod = ref('SPECIFIC')   // v5.63 计价方式（均价提示）
const shipForm = ref({ warehouseId: '', remark: '' })

// v5.27：转委外弹窗
const ooVisible = ref(false)
const ooOrder = ref(null)
const ooForm = ref({ processorId: null, processorName: '', processingFee: null })
const processors = ref([])
async function loadProcessors() { try { processors.value = await api.get('/supplier', { params: { type: 'PROCESSOR', enabled: true } }) } catch {} }
function openToOutsource(row) {
  ooOrder.value = row
  ooForm.value = { processorId: null, processorName: '', processingFee: null }
  ooVisible.value = true
}
function onOoProcessorChange(id) {
  const s = processors.value.find(p => p.id === id)
  ooForm.value.processorName = s ? s.name : ''
}
async function submitToOutsource() {
  if (!ooForm.value.processorId) { ElMessage.warning('请选择代工厂'); return }
  if (!ooForm.value.processingFee || ooForm.value.processingFee <= 0) { ElMessage.warning('请填写加工费单价'); return }
  loading.value = true
  try {
    const r = await api.post(`/sales-order/${ooOrder.value.id}/create-outsource-orders`, {
      processorId: ooForm.value.processorId,
      processorName: ooForm.value.processorName,
      processingFee: ooForm.value.processingFee
    })
    if (r.created > 0) {
      let msg = `已生成 ${r.created} 张委外订单（跳过 ${r.skipped} 项）`
      if (r.noRecipe > 0) msg += `；其中 ${r.noRecipe} 个产品未找到配方，需在订单中补充`
      ElMessage.success(msg)
    } else {
      ElMessage.info(`没有需要生成的委外订单（跳过 ${r.skipped} 项，可能已生成过）`)
    }
    ooVisible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

function hasPerm(c) { return perms.value.includes(c) }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : (id || '-') }
function custName(id) { const c = customers.value.find(c => c.id === id); return c ? c.name : (id || '-') }
// v6.4 状态色统一（utils/statusTag 全局映射）
function statusLabel(s) { return s === 'SHIPPED' ? '已发货' : s === 'CONFIRMED' ? '已确认' : s === 'CLOSED' ? '已结束' : '草稿' }

// v5.27：Tab（进行中=草稿+已确认 / 已发货 / 已结束）
const activeTab = ref('ACTIVE')
const filteredList = computed(() => {
  if (activeTab.value === 'SHIPPED') return list.value.filter(r => r.status === 'SHIPPED')
  if (activeTab.value === 'CLOSED') return list.value.filter(r => r.status === 'CLOSED')
  return list.value.filter(r => r.status === 'DRAFT' || r.status === 'CONFIRMED')
})
const activeCount = computed(() => list.value.filter(r => r.status === 'DRAFT' || r.status === 'CONFIRMED').length)
const shippedCount = computed(() => list.value.filter(r => r.status === 'SHIPPED').length)
const closedCount = computed(() => list.value.filter(r => r.status === 'CLOSED').length)

async function fetch() {
  resetPage()
  try { list.value = await api.get('/sales-order') } catch {} }

// ==================== 新增 ====================
const editingOrder = ref(null)
const changesVisible = ref(false)
const changes = ref([])

function openCreate() {
  editingOrder.value = null
  form.value = { customerId: null, contractNo: '', sourceWarehouseId: '', expectedShipDate: '', remark: '', saleCategory: '', items: [emptyItem()] , taxRate: taxRate.value }
  createVisible.value = true
}
function emptyItem() { return { materialCode: '', materialName: '', qty: 1, unit: 'kg', unitPrice: null } }
function addItem() { form.value.items.push(emptyItem()) }
function onCustChange() {}

// v6.3 数量变化重匹配价格阶梯（量大优惠按数量档）
async function onQtyChange(row) {
  if (!row.materialCode) return
  try {
    const pm = await api.get('/price-policy/match', { params: { materialCode: row.materialCode, qty: row.qty || 1 } })
    if (pm && pm.price != null) {
      if (row.unitPrice == null || Number(row.unitPrice) !== Number(pm.price)) {
        row.unitPrice = Number(pm.price)
        ElMessage.info(`按数量 ${row.qty} 匹配价格政策 ￥${fmt(pm.price)}（${pm.tier}）`)
      }
    }
  } catch {}
}
async function onItemMatChange(row) {
  const m = materials.value.find(m => m.code === row.materialCode)
  if (m) { row.materialName = m.name || ''; if (!row.unit && m.unit) row.unit = m.unit }
  // v6.3 价格政策优先：维护了阶梯价/大类价则带出（量大优惠按当前数量档），否则回落最近成交价
  if (row.materialCode) {
    try {
      const pm = await api.get('/price-policy/match', { params: { materialCode: row.materialCode, qty: row.qty || 1 } })
      if (pm && pm.price != null) {
        row.unitPrice = Number(pm.price)
        ElMessage.info(`已带出价格政策 ￥${fmt(pm.price)}（${pm.tier}），可修改`)
        return
      }
    } catch {}
  }
  // v5.52 带出该客户最近成交价（防报错价），无历史则不动
  if (!form.value.customerId || !row.materialCode) return
  try {
    const r = await api.get('/sales-order/recent-price', { params: { customerId: form.value.customerId, materialCode: row.materialCode } })
    if (r && r.unitPrice != null) {
      row.unitPrice = Number(r.unitPrice)
      ElMessage.info(`已带出最近成交价 ￥${fmt(r.unitPrice)}（${r.orderNo}），可修改`)
    }
  } catch {}
}
// v5.47 编辑订单（仅草稿）：拉明细回填表单，提交走 PUT（后端自动留痕）
async function openEdit(row) {
  try {
    const items = await api.get(`/sales-order/${row.id}/items`) || []
    form.value = {
      customerId: row.customerId, contractNo: row.contractNo, sourceWarehouseId: row.sourceWarehouseId,
      expectedShipDate: row.expectedShipDate || '', remark: row.remark || '', saleCategory: '',
      items: items.map(it => ({ materialCode: it.materialCode, materialName: it.materialName, qty: Number(it.qty), unit: it.unit, unitPrice: it.unitPrice != null ? Number(it.unitPrice) : null }))
    }
    if (!form.value.items.length) form.value.items = [emptyItem()]
    editingOrder.value = row
    createVisible.value = true
  } catch {}
}

// v5.47 变更记录
async function openChanges(row) {
  changes.value = []
  changesVisible.value = true
  try { changes.value = await api.get(`/sales-order/${row.id}/changes`) || [] } catch {}
}

async function submitCreate() {
  if (!editingOrder.value && !form.value.customerId) { ElMessage.warning('请选择客户'); return }
  if (!editingOrder.value && !form.value.saleCategory) { ElMessage.warning('请选择销售大类（材料/半成品/成品）'); return }
  const items = form.value.items.filter(it => it.materialCode)
  if (!items.length) { ElMessage.warning('请至少添加一条销售明细'); return }
  // v5.52 信用额度预警：当前应收欠款+本单金额超额度时二次确认（不拦截，防止误挡正常大单）
  if (!editingOrder.value && form.value.customerId) {
    const amount = items.reduce((s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0)
    const cc = await api.get(`/customer/${form.value.customerId}/credit-check`, { params: { amount } })
    if (cc && cc.exceed) {
      await ElMessageBox.confirm(
        `客户当前应收欠款 ￥${fmt(cc.arBalance)}，加本单 ￥${fmt(cc.orderAmount)}，` +
        `合计 ￥${fmt(cc.projected)} 已超信用额度 ￥${fmt(cc.creditLimit)}，是否继续下单？`,
        '信用额度预警', { type: 'warning', confirmButtonText: '继续下单', cancelButtonText: '取消' })
    }
  }
  // v9.5（ATP 轻量版）可承诺量预警：任一明细的量 > 可用库存−已订未发 时二次确认（不拦截，防误挡换货/预售）
  if (!editingOrder.value) {
    const warns = []
    for (const it of items) {
      try {
        const res = await api.get('/inventory/summary', { params: { view: 'code', keyword: it.materialCode, page: 1, pageSize: 5 } })
        const row = (res.rows || []).find(r => r.materialCode === it.materialCode)
        if (row && Number(row.atp) < (Number(it.qty) || 0)) {
          warns.push(`${it.materialCode}（可承诺 ${row.atp} ${row.unit || ''}，本单需 ${(Number(it.qty) || 0)}）`)
        }
      } catch { /* 库存接口失败不阻断下单 */ }
    }
    if (warns.length) {
      await ElMessageBox.confirm(
        `以下物料库存不足以覆盖已有订单+本单：\n${warns.join('\n')}\n\n确认后可能无法全额发货（超卖风险），是否继续？`,
        '可承诺量预警', { type: 'warning', confirmButtonText: '继续下单', cancelButtonText: '取消' })
    }
  }
  loading.value = true
  try {
    if (editingOrder.value) {
      await api.put(`/sales-order/${editingOrder.value.id}`, {
        expectedShipDate: form.value.expectedShipDate || undefined,
        remark: form.value.remark || undefined,
        items
      })
      ElMessage.success('订单已保存，变更已留痕')
    } else {
      await api.post('/sales-order', {
        customerId: form.value.customerId,
        customerName: custName(form.value.customerId),
        taxRate: form.value.taxRate ?? taxRate.value,
        expectedShipDate: form.value.expectedShipDate || undefined,
        remark: form.value.remark || undefined,
        items
      })
      ElMessage.success('销售订单已创建（草稿），合同号已自动生成')
    }
    createVisible.value = false
    editingOrder.value = null
    fetch()
  } catch {} finally { loading.value = false }
}

// ==================== 确认/删除 ====================
async function confirmOrder(row) {
  try {
    // v6.3 信用软拦截：确认环节复核信用占用，超额弹数字详情二次确认（不阻断，订单落审计标记）
    let creditTip = `确认销售订单 ${row.orderNo}？\n确认后可安排发货。`
    let exceeded = false
    try {
      const cc = await api.get(`/customer/${row.customerId}/credit-check`, { params: { amount: row.totalAmount } })
      if (cc && cc.exceed) {
        exceeded = true
        creditTip = `客户「${row.customerName}」当前应收欠款 ￥${fmt(cc.arBalance)}，加本单 ￥${fmt(cc.orderAmount)}，` +
          `合计 ￥${fmt(cc.projected)} 已超信用额度 ￥${fmt(cc.creditLimit)}。\n\n仍要确认该订单吗？（仅记录不阻断）`
      }
    } catch { /* 信用查询失败不挡确认 */ }
    await ElMessageBox.confirm(creditTip, exceeded ? '信用额度预警' : '确认订单',
      { type: 'warning', confirmButtonText: exceeded ? '仍要确认' : '确认' })
    await api.post(`/sales-order/${row.id}/confirm`)
    ElMessage.success('订单已确认')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// v5.27：一键转生产订单（半成品/成品明细各生成一张生产订单草稿，材料跳过；自动带配方；重复点自动跳过已生成的）
async function toProduction(row) {
  try {
    await ElMessageBox.confirm(`将销售订单 ${row.orderNo} 转为生产订单？\n半成品/成品明细将各生成一张生产订单（草稿），自动匹配已发布配方，材料类明细跳过。`, '转生产', { type: 'warning' })
    const r = await api.post(`/sales-order/${row.id}/create-production-orders`)
    if (r.created > 0) {
      let msg = `已生成 ${r.created} 张生产订单（跳过 ${r.skipped} 项）`
      if (r.noRecipe > 0) msg += `；其中 ${r.noRecipe} 个产品未找到配方，需在订单中补充`
      ElMessage.success(msg)
    } else {
      ElMessage.info(`没有需要生成的生产订单（跳过 ${r.skipped} 项，可能已生成过）`)
    }
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function deleteOrder(row) {
  try {
    await ElMessageBox.confirm(`确认删除销售订单 ${row.orderNo}？`, '删除', { type: 'warning' })
    await api.delete(`/sales-order/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// v5.27：手工结束订单（结束后不可发货、不可转生产/委外）
async function closeOrder(row) {
  try {
    await ElMessageBox.confirm(`确认结束销售订单 ${row.orderNo}？
结束后该订单不可再发货、不可转生产/转委外。`, '结束订单', { type: 'warning' })
    // v6.8：短交完结必须填原因（快照+原因入变更日志）
    let reason = null
    const items = await api.get(`/sales-order/${row.id}/items`)
    const ordered = items.reduce((a, it) => a + Number(it.qty || 0), 0)
    const shipped = items.reduce((a, it) => a + Number(it.shippedQty || 0), 0)
    if (ordered - shipped > 0.0001) {
      const { value } = await ElMessageBox.prompt(
        `本单尚有 ${(ordered - shipped).toFixed(3)} 未发货，请填写短交原因（将记入订单变更日志）：`,
        '短交完结原因', { inputValue: '客户接受短交' })
      reason = value
    }
    await api.post(`/sales-order/${row.id}/close`, null, { params: { reason: reason || undefined } })
    ElMessage.success('订单已结束')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close' && e?.message !== 'cancel') {} }
}

// ==================== 明细 ====================
async function openItems(row) {
  detailTaxRate.value = Number(row.taxRate) || taxRate.value
  itemsOrder.value = row
  orderItems.value = []
  itemsVisible.value = true
  try { orderItems.value = await api.get(`/sales-order/${row.id}/items`) } catch {}
}

// ==================== 打印合同（v5.27，涂料销售合同模板） ====================
function escHtml(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

// 金额转中文大写（含元角分）
function toChineseAmount(n) {
  const digits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖']
  const units = ['', '拾', '佰', '仟']
  const bigUnits = ['', '万', '亿', '万亿']
  n = Math.round(Number(n) * 100) / 100
  const [intStr, decStr = ''] = String(n).split('.')
  // 整数部分按 4 位分段（从低位）
  const segments = []
  let s = intStr
  while (s.length > 4) { segments.unshift(s.slice(-4)); s = s.slice(0, -4) }
  segments.unshift(s)
  // 每段转中文（段内零处理）
  const segTexts = segments.map(seg => {
    let segStr = ''
    let zeroPending = false
    for (let j = 0; j < seg.length; j++) {
      const d = Number(seg[j])
      const pos = seg.length - 1 - j
      if (d === 0) {
        zeroPending = segStr !== ''
      } else {
        if (zeroPending) { segStr += '零'; zeroPending = false }
        segStr += digits[d] + units[pos]
      }
    }
    return segStr
  })
  let result = ''
  for (let i = 0; i < segTexts.length; i++) {
    const st = segTexts[i]
    const lowerHasValue = segTexts.slice(i + 1).some(t => t !== '')
    if (st) {
      result += st + bigUnits[segTexts.length - 1 - i]
      // 段间补零：本段以 0 结尾且还有更低非空段（如 壹佰万零壹、壹拾万零壹拾）
      if (lowerHasValue && segments[i].endsWith('0')) result += '零'
    } else if (lowerHasValue) {
      result += '零'
    }
  }
  result = result || '零'
  // 小数部分（角分）
  const dec = (decStr + '00').slice(0, 2)
  const jiao = Number(dec[0]), fen = Number(dec[1])
  if (jiao > 0) result += '元' + digits[jiao] + '角'
  else if (fen > 0) result += '元零'
  else result += '元整'
  if (fen > 0) result += digits[fen] + '分'
  else if (jiao > 0) result += '整'
  return result
}

// 付款条件 → 合同条款中文
function payTermsText(t) {
  if (!t) return '按双方约定执行'
  const map = {
    PREPAID: '款到发货：需方应在供方发货前支付全部货款',
    COD: '货到付款：货物送达需方指定地点验收合格后付款',
    CREDIT_30: '货到后 30 天内付清货款',
    CREDIT_60: '货到后 60 天内付清货款',
    MONTHLY: '按月结算：次月对账后结清上月货款',
    TWO_MONTH: '按两月结算：货款滚动两月结清',
    THREE_MONTH: '按三月结算：货款滚动三月结清'
  }
  if (map[t]) return map[t]
  if (String(t).includes('天')) return `货到后 ${t} 付清货款`
  return '按双方约定执行'
}

// 生成涂料销售合同打印 HTML（纯函数；模板参照主流涂料行业销售合同条款）
function buildContractHtml(order, items, customer) {
  const now = new Date()
  const nowStr = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0')
  const total = items.reduce((s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0)
  const bodyRows = items.map((it, idx) => {
    const amount = ((Number(it.qty) || 0) * (Number(it.unitPrice) || 0)).toFixed(2)
    return '<tr><td style="text-align:center">' + (idx + 1) + '</td>'
      + '<td>' + escHtml(it.materialName || '-') + '</td>'
      + '<td>' + escHtml(it.materialCode || '-') + '</td>'
      + '<td style="text-align:right">' + (it.qty != null ? Number(it.qty) : '-') + '</td>'
      + '<td style="text-align:center">' + escHtml(it.unit || '') + '</td>'
      + '<td style="text-align:right">' + (it.unitPrice != null ? fmt(it.unitPrice) : '-') + '</td>'
      + '<td style="text-align:right">' + amount + '</td></tr>'
  }).join('')
  const buyer = customer || {}
  const payText = payTermsText(buyer.paymentTerms || order.paymentTerms)
  const deliveryText = order.expectedShipDate ? `供方应于 ${order.expectedShipDate} 前将货物交付至需方指定地点` : '交货时间与地点由双方另行协商确定'
  const remark = order.remark || '无'
  return [
    '<!DOCTYPE html><html><head><meta charset="utf-8"><title>销售合同 ' + escHtml(order.contractNo || '') + '</title><style>',
    'body{font-family:"SimSun","Microsoft YaHei",serif;color:#111;font-size:13px;line-height:1.7;}',
    '.company{text-align:center;font-size:15px;font-weight:700;margin-bottom:2px;}',
    'h1{text-align:center;font-size:24px;letter-spacing:10px;margin:6px 0 2px;}',
    '.contract-no{text-align:center;font-size:14px;font-weight:600;margin-bottom:12px;}',
    'table{width:100%;border-collapse:collapse;}',
    '.parties td{border:1px solid #999;padding:6px 10px;font-size:13px;}',
    '.parties .k{background:#f5f5f5;font-weight:600;width:90px;text-align:center;}',
    '.main th,.main td{border:1px solid #999;padding:5px 8px;font-size:13px;}',
    '.main th{background:#f0f0f0;font-weight:600;}',
    '.total-row td{font-weight:700;}',
    '.clause{margin:10px 0;}',
    '.clause h3{margin:10px 0 4px;font-size:14px;}',
    '.clause p{margin:3px 0;text-align:justify;}',
    '.sign{display:flex;justify-content:space-between;margin-top:46px;}',
    '.sign-box{width:46%;}',
    '.sign-box div{line-height:1.9;font-size:13px;}',
    '.sign-line{margin-top:38px;border-top:1px solid #888;}',
    '.small{font-size:12px;color:#555;}',
    '@media print{ body{margin:12px 16px;} }',    '</style></head><body>',
    '<div class="company">广东芃远新材料有限公司</div>',
    '<h1>涂料产品销售合同</h1>',
    '<div class="contract-no">合同编号：' + escHtml(order.contractNo || '-') + '</div>',
    '<table class="parties">',
    '<tr><td class="k">需方（甲方）</td><td>' + escHtml(buyer.name || order.customerName || '') + '</td><td class="k">供方（乙方）</td><td>广东芃远新材料有限公司</td></tr>',
    '<tr><td class="k">地址</td><td>' + escHtml(buyer.address || '') + '</td><td class="k">地址</td><td>广东省</td></tr>',
    '<tr><td class="k">联系人</td><td>' + escHtml(buyer.contactPerson || '') + '</td><td class="k">联系人</td><td></td></tr>',
    '<tr><td class="k">电话</td><td>' + escHtml(buyer.contactPhone || '') + '</td><td class="k">电话</td><td></td></tr>',
    '<tr><td class="k">法定代表人</td><td>' + escHtml(buyer.legalPerson || '') + '</td><td class="k">法定代表人</td><td></td></tr>',
    '<tr><td class="k">签订日期</td><td>' + escHtml(order.orderDate || nowStr) + '</td><td class="k">签订地点</td><td>广东省</td></tr>',
    '</table>',
    '<div class="clause"><h3>一、产品名称、规格、数量及金额</h3>',
    '<table class="main"><thead><tr><th style="width:40px">序号</th><th>产品名称</th><th>型号规格</th><th style="width:70px">数量</th><th style="width:50px">单位</th><th style="width:80px">单价(元)</th><th style="width:100px">金额(元)</th></tr></thead><tbody>',
    bodyRows,
    '<tr class="total-row"><td colspan="6" style="text-align:right">合计金额（大写）：' + toChineseAmount(total) + '</td><td style="text-align:right">' + total.toFixed(2) + '</td></tr>',
    '</tbody></table></div>',
    '<div class="clause"><h3>二、质量标准</h3>',
    '<p>1、产品质量按国家相关标准及双方确认的技术要求执行，供方随货提供产品检验报告及合格证；</p>',
    '<p>2、产品保质期自生产之日起 12 个月，须在保质期内使用完毕；超过保质期或储存不当造成的质量问题，供方不承担责任。</p></div>',
    '<div class="clause"><h3>三、包装与储存</h3>',
    '<p>1、产品采用适合运输的密封包装（铁桶/塑料桶），包装物不回收；</p>',
    '<p>2、产品应储存于阴凉、干燥、通风处，避免阳光直射，储存温度 5-35℃。</p></div>',
    '<div class="clause"><h3>四、交货时间、地点与运输</h3>',
    '<p>1、' + deliveryText + '；</p>',
    '<p>2、运输方式及费用由双方协商确定，运输途中的毁损、灭失风险由承运方承担。</p></div>',
    '<div class="clause"><h3>五、验收</h3>',
    '<p>需方收到货物后 7 日内完成验收，如有数量或质量问题应书面通知供方，逾期未提出异议视为验收合格；验收合格后货物风险转移至需方。</p></div>',
    '<div class="clause"><h3>六、付款方式</h3>',
    '<p>' + payText + '；需方逾期付款的，供方有权暂停后续供货，并按日万分之五收取逾期付款违约金。</p></div>',
    '<div class="clause"><h3>七、违约责任</h3>',
    '<p>任何一方违约给对方造成损失的，应承担赔偿责任；供方逾期交货的，按逾期部分货款的日万分之五向需方支付违约金。</p></div>',
    '<div class="clause"><h3>八、争议解决</h3>',
    '<p>因本合同发生争议，双方应友好协商解决；协商不成的，任何一方均可向供方所在地人民法院提起诉讼。</p></div>',
    '<div class="clause"><h3>九、其他约定</h3>',
    '<p>1、' + escHtml(remark) + '；</p>',
    '<p>2、本合同一式两份，甲乙双方各执一份，自双方签字盖章之日起生效，传真件、扫描件与原件具有同等法律效力。</p></div>',
    '<div class="sign">',
    '<div class="sign-box"><div>甲方（盖章）：' + escHtml(buyer.name || order.customerName || '') + '</div><div>代表签字：</div><div>日期：' + now.getFullYear() + ' 年 ' + (now.getMonth() + 1) + ' 月 ' + now.getDate() + ' 日</div></div>',
    '<div class="sign-box"><div>乙方（盖章）：广东芃远新材料有限公司</div><div>代表签字：</div><div>日期：' + now.getFullYear() + ' 年 ' + (now.getMonth() + 1) + ' 月 ' + now.getDate() + ' 日</div></div>',
    '</div>',
    '</body></html>'
  ].join('')
}

// 打开合同打印窗口（仿生产/委外打印：新窗口渲染 + window.print + 自动关闭）
async function printContract(row) {
  try {
    const items = await api.get(`/sales-order/${row.id}/items`)
    const customer = customers.value.find(c => c.id === row.customerId) || {}
    const win = window.open('', '_blank')
    if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
    win.document.write(buildContractHtml(row, items || [], customer))
    win.document.close()
    win.focus()
    setTimeout(() => { win.print(); win.close() }, 200)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载订单数据失败')
  }
}

// ==================== 发货 ====================
const shipCreditWarn = ref('')
async function openShip(row) {
  shipOrder.value = row
  shipItems.value = []
  batchOptions.value = {}
  shipForm.value = { warehouseId: String(row.sourceWarehouseId || ''), remark: '' }
  shipVisible.value = true
  // v6.3 信用软拦截：发货前再提示一次（不阻断，确认/创建环节已各拦过一道）
  shipCreditWarn.value = ''
  try {
    const cc = await api.get(`/customer/${row.customerId}/credit-check`, { params: { amount: row.totalAmount } })
    if (cc && cc.exceed) {
      shipCreditWarn.value = `信用预警：该客户应收欠款 ￥${fmt(cc.arBalance)} + 本单 ￥${fmt(cc.orderAmount)} ` +
        `已超额度 ￥${fmt(cc.creditLimit)}，请知悉后发货`
    }
  } catch {}
  try {
    const items = await api.get(`/sales-order/${row.id}/items`)
    shipItems.value = items
      .map(it => ({ ...it, remainQty: Number((Number(it.qty) - Number(it.shippedQty || 0)).toFixed(3)), batchNo: null, shipQty: Number((Number(it.qty) - Number(it.shippedQty || 0)).toFixed(3)) }))
      .filter(it => it.remainQty > 0)
    if (!shipItems.value.length) { ElMessage.warning('该订单已全部发货'); shipVisible.value = false; return }
    loadBatchOptions()
  } catch {}
}

async function loadBatchOptions() {
  const wh = shipForm.value.warehouseId
  const opts = {}
  for (const item of shipItems.value) {
    try {
      const records = await api.get(`/inventory/material/${item.materialCode}`)
      // 同批号多库位聚合：汇总现存量，展示分库/库位（不显示价格）
      const map = {}
      records.filter(r => Number(r.qty) > 0 && (!wh || String(r.warehouseId) === String(wh)) && r.batchNo).forEach(r => {
        if (!map[r.batchNo]) map[r.batchNo] = { batchNo: r.batchNo, qty: 0, places: [] }
        map[r.batchNo].qty = Number(map[r.batchNo].qty) + Number(r.qty)
        const place = [r.zoneName, r.locationName].filter(Boolean).join('/')
        if (place && !map[r.batchNo].places.includes(place)) map[r.batchNo].places.push(place)
      })
      opts[item.materialCode] = Object.values(map)
    } catch { opts[item.materialCode] = [] }
  }
  batchOptions.value = opts
}

async function submitShip() {
  if (!shipForm.value.warehouseId) { ElMessage.warning('请选择出库仓库'); return }
  const missing = shipItems.value.filter(it => !it.batchNo)
  if (missing.length) { ElMessage.warning(`物料 ${missing.map(m => m.materialCode).join('、')} 未选择出库批号`); return }
  loading.value = true
  try {
    const overrides = {}
    shipItems.value.forEach(it => { overrides[it.materialCode] = { qty: Number(it.shipQty), batchNo: it.batchNo } })
    await api.post('/outbound/sales/from-order', overrides, {
      params: { orderId: shipOrder.value.id, warehouseId: shipForm.value.warehouseId, remark: shipForm.value.remark || undefined }
    })
    ElMessage.success('已生成销售出库单，待仓管审核')
    shipVisible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(filteredList)

onMounted(async () => {
  loadTaxRate(api).then(r => { taxRate.value = r })
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { costingMethod.value = (await api.get('/costing/config')).method || 'SPECIFIC' } catch {}   // v5.63
  try { customers.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  try {
    const mats = await api.get('/material', { params: { enabled: true } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name, unit: m.unit, category: m.category }))
  } catch {}
  loadProcessors()
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.type-tabs { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-tab { padding: 6px 16px; font-size: 13px; font-weight: 600; border: 1px solid #e2e8f0; border-radius: 6px; cursor: pointer; background: #fff; color: #64748b; transition: all 0.2s; }
.type-tab:hover { color: #334155; border-color: #cbd5e1; }
.type-tab.active { background: #4a6785; color: #fff; border-color: #4a6785; }
.tab-badge { display: inline-block; min-width: 18px; padding: 0 5px; margin-left: 4px; font-size: 11px; line-height: 16px; border-radius: 9px; background: rgba(0,0,0,0.08); color: inherit; }
.type-tab.active .tab-badge { background: rgba(255,255,255,0.25); }
.type-count { font-size: 13px; color: #64748b; }
.total-bar { margin-top: 8px; text-align: right; font-size: 13px; font-weight: 600; color: #a8744f; }
.qty-hint { font-size: 12px; color: #94a3b8; margin-top: 6px; }
</style>
