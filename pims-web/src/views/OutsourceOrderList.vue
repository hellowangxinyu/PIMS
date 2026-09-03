<template>
  <div class="page-container">
    <div class="page-header">
      <h2>委外订单（配方表）</h2>
      <el-button type="primary" @click="openCreate">新建委外订单</el-button>
    </div>
    <div class="table-card">
      <div class="type-tabs">
        <span class="type-count">共 {{ list.length }} 条记录</span>
      </div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="orderNo" label="订单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编码" min-width="120" />
        <el-table-column prop="batchQty" label="批量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="salesOrderNo" label="来源销售单" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.salesOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="processor" label="代工厂" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.displayStatus || row.status)" size="small">{{ statusLabel(row.displayStatus || row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column prop="printCount" label="打印次数" width="90" align="center" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="viewDetail(row)">配方</button>
            <button v-if="!isOutbound(row) && (row.status === 'CONFIRMED' || row.status === 'OUTSOURCED')" class="op-btn op-btn-success" @click="printAndOutbound(row)">打印并出库</button>
            <button v-if="isOutbound(row) && row.status !== 'DRAFT'" class="op-btn op-btn-primary" @click="printOutsource(row)">打印</button>
            <button v-if="row.status !== 'DRAFT'" class="op-btn op-btn-primary" @click="viewOutbounds(row)">出库记录</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="openEdit(row)">编辑</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-success" @click="confirm(row)">确认并出库</button>
            <button v-if="row.status === 'CONFIRMED' || row.status === 'OUTSOURCED'" class="op-btn op-btn-warn" @click="complete(row)">完工</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
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

    <!-- 新建/编辑弹窗 -->
    <el-dialog :title="editId ? '编辑委外订单' : '新建委外订单'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <!-- 参照配方（仅新建时） -->
        <el-form-item v-if="!editId" label="配方类型">
          <el-radio-group v-model="recipeTypeFilter" @change="onRecipeTypeChange">
            <el-radio-button value="TINTING">制漆配方（调色）</el-radio-button>
            <el-radio-button value="GRINDING">制浆配方（研磨）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!editId" label="参照配方">
          <el-select v-model="form.recipeVersionId" filterable clearable placeholder="选择已发布的配方版本" style="width:100%" @change="onRecipeChange">
            <el-option v-for="r in filteredRecipes" :key="r.versionId" :label="r.recipeNo + ' ' + r.productName + ' (' + r.versionNo + ')'" :value="r.versionId" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称" required>
          <el-input v-model="form.productName" placeholder="如：白色外墙乳胶漆" />
        </el-form-item>
        <el-form-item label="产品编码">
          <el-select v-model="form.productCode" filterable allow-create clearable default-first-option
                     placeholder="选择或输入成品/半成品编码（必填）" style="width:100%" @change="onProductCodeChange">
            <el-option v-for="m in productMaterials" :key="m.code" :label="m.code + ' ' + (m.name || '') + (m.category === 'B' ? '（半成品）' : '')" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生产批量" required>
              <el-input-number v-model="form.batchQty" :min="0.001" :precision="3" :step="1" style="width:100%" />
              <div v-if="recipeBaseQty" class="batch-hint">配方基准: {{ recipeBaseQty }} {{ form.unit }}，按比例自动计算投料量</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input value="kg（公斤）" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- v5.27：来源销售订单（销售订单一键转委外后自动带入；也可手动关联） -->
        <el-form-item label="来源销售订单">
          <el-select v-model="form.salesOrderNo" filterable clearable placeholder="可选：关联销售订单（选择后自动带出产品）" style="width:100%" @change="onSalesOrderChange">
            <el-option v-for="so in confirmedSalesOrders" :key="so.orderNo" :label="so.orderNo + ' ' + (so.customerName || '') + '（' + (so.contractNo || '-') + '）'" :value="so.orderNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="代工厂" required>
          <!-- v5.27：代工厂必选，从供应商档案（类型=代工厂）中选择，选中即记录名称+档案ID -->
          <el-select v-model="form.supplierId" filterable clearable placeholder="请选择代工厂（先在供应商管理中维护代工厂档案）" style="width:100%" @change="onProcessorChange">
            <el-option v-for="s in processorOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="加工费单价" required>
          <el-input-number v-model="form.processingFee" :min="0" :precision="2" :step="1" style="width:100%" />
          <span style="margin-left:8px;font-size:12px;color:#94a3b8">元/{{ form.unit || 'kg' }}（从代工厂档案带出，可改），入库后自动生成应付</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">配方明细（原料清单）<span v-if="recalculating" style="margin-left:8px;color:#6366f1;font-size:12px">正在按比例重算...</span></el-divider>
        <div class="formula-toolbar">
          <el-button size="small" type="primary" @click="addItem">+ 添加原料</el-button>
        </div>
        <p-table :data="form.items" border size="small" style="width:100%">
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <!-- v5.6：半成品为常备库存保留为一行，不可改选物料，仅可改用量 -->
              <template v-if="row.nodeType === 'SUB_RECIPE'">
                <el-tag size="small" type="warning" class="sub-tag">半成品</el-tag>
                <span class="sub-code">{{ row.materialCode || row.materialName }}</span>
              </template>
              <el-select v-else v-model="row.materialCode" filterable placeholder="搜索物料" style="width:100%" @change="c => onItemMatChange(row, c)">
                <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + (m.name||'')" :value="m.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="品名" min-width="120">
            <template #default="{ row }">
              <span :class="{ 'sub-name': row.nodeType === 'SUB_RECIPE' }">{{ row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用量" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="0.001" :precision="3" :step="1" size="small" style="width:100%" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="{ row }"><span>{{ row.unit }}</span></template>
          </el-table-column>
          <el-table-column label="" width="50" align="center">
            <template #default="{ $index }">
              <button class="op-btn op-btn-danger" @click="form.items.splice($index, 1)">✕</button>
            </template>
          </el-table-column>
        </p-table>
        <div class="sub-tip">半成品（如研磨浆）为常备库存物料，直接领用半成品出库，可点击「溯源」追溯其配方原料</div>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看配方弹窗（v5.6：半成品行显示标签并可溯源） -->
    <el-dialog title="配方明细" v-model="detailVisible" width="min(1100px, 96vw)" destroy-on-close>
      <p style="margin:0 0 12px;font-weight:600">{{ detailOrder.productName }}（{{ detailOrder.orderNo }}）批量: {{ detailOrder.batchQty }} {{ detailOrder.unit }} | 代工厂: {{ detailOrder.processor }}</p>
      <p-table :data="detailItems" border size="small">
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.nodeType === 'SUB_RECIPE'" type="warning" size="small">半成品</el-tag>
            <span v-else class="text-muted">原料</span>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="140" />
        <el-table-column prop="spec" label="规格" min-width="100" />
        <el-table-column prop="qty" label="用量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <button v-if="row.nodeType === 'SUB_RECIPE'" class="op-btn op-btn-primary" @click="traceDetail(row)">溯源</button>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
      </p-table>
    </el-dialog>

    <!-- 半成品溯源弹窗（traceRecipe 层级） -->
    <el-dialog :title="'半成品溯源 - ' + (traceTitle || '')" v-model="traceVisible" width="min(1100px, 96vw)" destroy-on-close>
      <p-table :data="traceRows" border size="small">
        <el-table-column label="层级" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.nodeType === 'SUB_RECIPE'" type="warning" size="small">半成品</el-tag>
            <span v-else class="text-muted">原料</span>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="150" />
        <el-table-column prop="qty" label="用量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
      </p-table>
      <p class="trace-tip">半成品由以下物料组成（按当前订单批量折算）</p>
    </el-dialog>

    <!-- 出库记录明细弹窗（委外出库作为订单记录，先进先出·代工厂仓） -->
    <el-dialog :title="'出库记录 - ' + (outboundOrder?.orderNo || '')" v-model="outboundVisible" width="min(820px, 94vw)" destroy-on-close>
      <p-table :data="outboundRows" border size="small">
        <el-table-column prop="docNo" label="出库单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" min-width="110" />
        <el-table-column prop="materialName" label="品名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" min-width="110" />
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column label="出库仓库" width="120">
          <template #default="{ row }">{{ whName(row.fromWarehouseId) }}</template>
        </el-table-column>
        <el-table-column label="出库库位" min-width="120">
          <template #default="{ row }">
            <span v-if="row.locationName">{{ row.zoneName ? row.zoneName + ' / ' : '' }}{{ row.locationName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="hasFinanceAmount" label="成本" width="100" align="right">
          <template #default="{ row }">￥{{ fmtMoney(row.cost) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
      </p-table>
      <p v-if="!outboundRows.length" class="text-muted" style="text-align:center;margin:16px 0">该订单尚未出库</p>
    </el-dialog>

    <!-- 出库前库存检查预览（确认后才自动出库·代工厂仓） -->
    <el-dialog title="出库前库存检查" v-model="stockDialogVisible" width="min(1100px, 96vw)" destroy-on-close>
      <p-table :data="stockRows" border size="small" max-height="380">
        <el-table-column prop="materialCode" label="物料编码" width="110" />
        <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
        <el-table-column label="需求量" width="100" align="right">
          <template #default="{ row }">{{ row.need }} kg</template>
        </el-table-column>
        <el-table-column label="可用库存" width="110" align="right">
          <template #default="{ row }">{{ row.available }} kg</template>
        </el-table-column>
        <el-table-column label="缺口" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.shortage > 0" class="stock-short">-{{ row.shortage }} kg</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enough ? 'success' : 'danger'" size="small">{{ row.enough ? '足够' : '不足' }}</el-tag>
          </template>
        </el-table-column>
      </p-table>
      <p v-if="hasStockShortage" class="stock-warn">⚠ 有物料库存不足，无法出库，请先向代工厂仓补货（出库将按先进先出自动分配批次）</p>
      <p v-else class="stock-ok">✓ 库存充足，确认后将按先进先出自动分配批次并扣减库存</p>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="hasStockShortage" :loading="printing" @click="confirmOutbound">确认出库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const list = ref([])
const materials = ref([])
const warehouses = ref([])
const productMaterials = computed(() => materials.value.filter(m => m.category === 'C' || m.category === 'B'))
const visible = ref(false)
const detailVisible = ref(false)
const loading = ref(false)
const editId = ref(null)
const form = ref(emptyForm())
const detailOrder = ref({})
const detailItems = ref([])

const releasedRecipes = ref([])
const recipeTypeFilter = ref('TINTING')
const filteredRecipes = computed(() => releasedRecipes.value.filter(r => r.recipeType === recipeTypeFilter.value))
const recipeBaseQty = ref(null)
const recalculating = ref(false)

function emptyForm() { return { productName: '', productCode: '', batchQty: 100, unit: 'kg', processor: '', supplierId: null, processingFee: null, remark: '', items: [], recipeVersionId: null, salesOrderNo: '' } }

function onRecipeTypeChange() {
  form.value.recipeVersionId = null
  recipeBaseQty.value = null
  form.value.items = []
}
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
// v5.27：状态展示（displayStatus 推导：OUTSOURCED=已委外 INBOUND=已入库 SHIPPED=已发货）
function statusType(s) {
  return { CONFIRMED: 'success', COMPLETED: 'info', OUTSOURCED: 'primary', INBOUND: 'success', SHIPPED: 'info' }[s] || 'warning'
}
function statusLabel(s) {
  return { CONFIRMED: '已确认', COMPLETED: '已完工', OUTSOURCED: '已委外', INBOUND: '已入库', SHIPPED: '已发货', DRAFT: '草稿' }[s] || s
}

// v5.27：来源销售订单（下拉选已确认的销售订单，选择后自动带出产品）
const confirmedSalesOrders = ref([])
async function loadConfirmedSalesOrders() {
  try { confirmedSalesOrders.value = await api.get('/sales-order', { params: { status: 'CONFIRMED' } }) } catch {}
}
async function onSalesOrderChange(orderNo) {
  if (!orderNo) return
  const so = confirmedSalesOrders.value.find(s => s.orderNo === orderNo)
  if (!so) return
  try {
    const items = await api.get(`/sales-order/${so.id}/items`)
    // 取第一条需委外加工的明细（半成品 B / 成品 C），自动填充产品
    const prod = items.find(it => /^[BC]/.test(it.materialCode || ''))
    if (!prod) { ElMessage.warning('该销售订单没有半成品/成品明细，无法自动带出产品，请手动填写'); return }
    form.value.productName = prod.materialName || ''
    form.value.productCode = prod.materialCode || ''
    form.value.batchQty = Number(prod.qty) || form.value.batchQty
    form.value.remark = (form.value.remark ? form.value.remark + '；' : '') + '来源销售订单 ' + orderNo
    ElMessage.success(`已带出 ${prod.materialName || prod.materialCode}，批量 ${prod.qty} ${prod.unit || 'kg'}`)
  } catch {}
}

const suppliers = ref([])
// v5.27：代工厂下拉 = 供应商档案中类型为代工厂且未拉黑/删除的
async function fetchSuppliers() { try { suppliers.value = await api.get('/supplier', { params: { type: 'PROCESSOR', enabled: true } }) } catch {} }
// 选代工厂时从档案自动带出加工费
function onProcessorChange(supplierId) {
  const s = suppliers.value.find(x => x.id === supplierId)
  form.value.processor = s ? s.name : ''
  if (s && s.processingFee != null) {
    form.value.processingFee = Number(s.processingFee)
  } else {
    form.value.processingFee = null
  }
}
// 老单据兼容：历史手输代工厂不在档案列表中时，附加占位选项便于回显
const processorOptions = computed(() => {
  const list = [...suppliers.value]
  const f = form.value
  if (f.processor && !list.some(s => s.id === f.supplierId)) {
    list.unshift({ id: f.supplierId || -1, name: f.processor + '（未在代工厂档案中）' })
  }
  return list
})
async function fetch() {
  resetPage()
  try { list.value = await api.get('/outsource-order') } catch {} }

function openCreate() { editId.value = null; form.value = emptyForm(); visible.value = true }
async function openEdit(row) {
  editId.value = row.id
  try {
    const data = await api.get(`/outsource-order/${row.id}`)
    form.value = {
      productName: data.order.productName,
      productCode: data.order.productCode || '',
      batchQty: Number(data.order.batchQty),
      unit: data.order.unit || '',
      processor: data.order.processor || '',
      supplierId: data.order.supplierId || null,
      processingFee: data.order.processingFee != null ? Number(data.order.processingFee) : null,
      remark: data.order.remark || '',
      items: data.items.map(i => ({ materialCode: i.materialCode, materialName: i.materialName || '', spec: i.spec || '', unit: i.unit || '', qty: Number(i.qty) }))
    }
    visible.value = true
  } catch {}
}

function onProductCodeChange(code) {
  const m = materials.value.find(m => m.code === code)
  if (m && m.name) form.value.productName = m.name
}
async function onRecipeChange(versionId) {
  if (!versionId) { recipeBaseQty.value = null; return }
  const r = releasedRecipes.value.find(r => r.versionId === versionId)
  if (r) {
    form.value.productName = r.productName
    form.value.productCode = r.productCode || ''
    form.value.batchQty = Number(r.batchQty) || 100
    form.value.unit = r.unit || 'kg'
    recipeBaseQty.value = Number(r.batchQty) || 100
    await recalcItems()
  }
}

// 批量变更时自动重算投料量
let recalcTimer = null
watch(() => form.value.batchQty, (newQty, oldQty) => {
  if (!form.value.recipeVersionId || !newQty || newQty === oldQty) return
  clearTimeout(recalcTimer)
  recalcTimer = setTimeout(() => recalcItems(), 400)
})

async function recalcItems() {
  if (!form.value.recipeVersionId || !form.value.batchQty) return
  recalculating.value = true
  try {
    // v5.6：/plan 保留半成品为一行（半成品为常备库存，不展开原料）
    const expanded = await api.get(`/recipe/version/${form.value.recipeVersionId}/plan`, { params: { qty: form.value.batchQty } })
    form.value.items = expanded.map(row => ({
      materialCode: row.materialCode,
      materialName: row.materialName || '',
      spec: row.spec || '',
      unit: row.unit || '',
      qty: Number(row.qty),
      nodeType: row.nodeType,
      refRecipeId: row.refRecipeId
    }))
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  } finally { recalculating.value = false }
}

// v5.6：半成品溯源——按半成品引用的子配方版本 trace（保留层级，含子配方原料）
const traceVisible = ref(false)
const traceTitle = ref('')
const traceRows = ref([])

async function traceDetail(row) {
  if (!row.refRecipeId) { ElMessage.warning('该半成品未关联子配方，无法溯源'); return }
  traceTitle.value = row.materialName || row.materialCode || ''
  traceRows.value = []
  traceVisible.value = true
  try {
    const data = await api.get(`/recipe/${row.refRecipeId}/trace`)
    const flatten = (nodes, depth) => {
      if (!Array.isArray(nodes)) return []
      return nodes.flatMap(n => {
        const base = [{ nodeType: n.nodeType, materialCode: n.materialCode, materialName: n.materialName, qty: n.qty, unit: n.unit, depth }]
        return base.concat(flatten(n.children, depth + 1))
      })
    }
    traceRows.value = flatten(data.materials, 0)
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  }
}

function addItem() { form.value.items.push({ materialCode: '', materialName: '', spec: '', unit: '', qty: 1 }) }
function onItemMatChange(row, code) {
  const m = materials.value.find(m => m.code === code)
  if (m) { row.materialName = m.name || ''; row.spec = m.spec || ''; row.unit = m.unit || '' }
}

async function submit() {
  if (!form.value.productName) { ElMessage.warning('请输入产品名称'); return }
  if (!form.value.productCode || !form.value.productCode.trim()) { ElMessage.warning('请填写产品编码（选择成品/半成品物料或输入编码）'); return }
  if (!form.value.batchQty || form.value.batchQty <= 0) { ElMessage.warning('请填写生产批量'); return }
  if (!form.value.items.length) { ElMessage.warning('请至少添加一项原料'); return }
  if (!form.value.processingFee || form.value.processingFee <= 0) { ElMessage.warning('请填写加工费单价'); return }
  if (!form.value.supplierId) { ElMessage.warning('请选择代工厂'); return }
  // v5.27：同步代工厂名称（后端要求 processor 必填，与所选档案一致）
  const selProcessor = suppliers.value.find(s => s.id === form.value.supplierId)
  if (selProcessor) form.value.processor = selProcessor.name
  loading.value = true
  try {
    const payload = { ...form.value }
    if (!payload.salesOrderNo) delete payload.salesOrderNo
    if (editId.value) {
      await api.put(`/outsource-order/${editId.value}`, payload)
    } else {
      await api.post('/outsource-order', payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

// 确认订单 = 确认 + 自动出库（与生产订单逻辑一致：先弹库存检查，确认后按先进先出从代工厂仓出库）
const stockFromConfirm = ref(false)
async function confirm(row) {
  if (printing.value) return
  try {
    await ElMessageBox.confirm(`确认委外订单 ${row.orderNo}？\n确认后将按先进先出从代工厂「${row.processor || '-'}」仓库自动出库。`, '确认并自动出库', { type: 'warning' })
  } catch { return }
  try {
    stockRows.value = await api.get(`/outsource-order/${row.id}/stock-check`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '库存检查失败')
    return
  }
  stockOrder.value = row
  stockFromConfirm.value = true
  stockDialogVisible.value = true
}

// v5.27：排产 / 取消排产（委外：已委外）
async function schedule(row) {
  try {
    await ElMessageBox.confirm(`排产委外订单 ${row.orderNo}？\n排产后状态为「已委外」。`, '排产', { type: 'warning' })
    await api.post(`/outsource-order/${row.id}/schedule`)
    ElMessage.success('已排产（委外）')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}
async function unschedule(row) {
  try {
    await ElMessageBox.confirm(`取消排产 ${row.orderNo}？`, '取消排产', { type: 'warning' })
    await api.post(`/outsource-order/${row.id}/unschedule`)
    ElMessage.success('已取消排产')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function complete(row) {
  try {
    await ElMessageBox.confirm(`确认订单 ${row.orderNo} 已完工？`, '完工', { type: 'info' })
    await api.post(`/outsource-order/${row.id}/complete`)
    ElMessage.success('已完工')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`删除订单 ${row.orderNo}？`, '删除', { type: 'warning' })
    await api.delete(`/outsource-order/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function viewDetail(row) {
  detailOrder.value = row
  try { detailItems.value = await api.get(`/outsource-order/${row.id}/items`) } catch {}
  detailVisible.value = true
}

// ==================== 委外订单打印（v5.25，委外加工单，发料明细保密只显编码） ====================

const printing = ref(false)

function escHtml(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

// 按订单配方的类型取标准工艺模板（含工艺/检测计划/包装要求）
// 按订单配方的绑定路线取工艺（含工艺/检测计划/包装要求），无绑定回退类型默认
async function loadProcessTpl(order) {
  try {
    const r = releasedRecipes.value.find(x => x.versionId === order.recipeVersionId)
    if (r?.processTemplateId) return await api.get(`/process/route/${r.processTemplateId}`)
    return await api.get(`/process/template/${r?.recipeType || 'GRINDING'}`)
  } catch { return null }
}

// 步骤描述里的 {{N}} 替换为订单明细第 N 项物料（投料顺序）
function renderStepDesc(desc, items) {
  if (!desc) return ''
  return desc.replace(/\{\{(\d+)\}\}/g, (m, n) => {
    const it = items[parseInt(n) - 1]
    return it ? (it.materialName || it.materialCode || m) : m
  })
}

// 工艺 + 检测计划 + 包装要求 打印段
function buildProcessHtml(processTpl, items) {
  if (!processTpl || !processTpl.stages || !processTpl.stages.length) return ''
  let html = ''
  if (processTpl.packingRequirement) {
    html += '<div class="outbound-title">包装要求</div>'
      + '<div class="p-pack">' + escHtml(processTpl.packingRequirement) + '</div>'
  }
  processTpl.stages.forEach(stg => {
    html += '<div class="p-stage"><div class="p-stage-h"><span class="p-no">' + escHtml(stg.stageNo || '') + '</span><b>' + escHtml(stg.stageName || '') + '</b>'
      + (stg.roleHint ? '<span class="p-role">（' + escHtml(stg.roleHint) + '）</span>' : '') + '</div>'
    ;(stg.steps || []).forEach(stp => {
      html += '<div class="p-step"><b>' + escHtml(stp.stepCode || '') + '</b>. ' + escHtml(renderStepDesc(stp.description, items))
        + (stp.params ? ' <span class="p-param">[' + escHtml(stp.params) + ']</span>' : '') + '</div>'
    })
    ;(stg.qcItems || []).forEach(qc => {
      const times = qc.testTimes || 1
      let rows = ''
      for (let i = 0; i < times; i++) {
        rows += '<tr><td class="center">' + (i + 1) + '</td><td>' + escHtml(qc.name || '') + '</td><td class="center">' + escHtml(qc.standard || '') + '</td><td></td><td></td></tr>'
      }
      html += '<table class="qc-table"><thead><tr><th style="width:40px">序号</th><th style="width:90px">检测项</th><th style="width:90px">标准</th><th>实测值</th><th style="width:80px">检测人</th></tr></thead><tbody>' + rows + '</tbody></table>'
    })
    html += '</div>'
  })
  return html
}

// 生成委外订单打印 HTML（纯函数；明细只显示物料编码+用量——配方保密，防外泄给代工厂；不显示加工费）
function buildOutsourcePrintHtml(order, items, outbounds, processTpl) {
  const now = new Date()
  const nowStr = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0')
  // 配方编号映射（recipeVersionId → recipeNo，已发布版本才有；兜底显示版本ID）
  const r = releasedRecipes.value.find(x => x.versionId === order.recipeVersionId)
  const recipeNo = r ? r.recipeNo : (order.recipeVersionId != null ? String(order.recipeVersionId) : '-')
  const statusText = statusLabel(order.status)
  const bodyRows = (items || []).map((it, idx) => {
    const isSub = it.nodeType === 'SUB_RECIPE'
    return '<tr' + (isSub ? ' class="sub-row"' : '') + '>'
      + '<td class="center">' + (idx + 1) + '</td>'
      + '<td>' + escHtml(it.materialCode || '-') + '</td>'
      + (isSub ? '<td class="center"><span class="sub-tag">半成品</span></td>' : '<td class="center"></td>')
      + '<td class="num">' + escHtml(it.qty) + '</td>'
      + '<td class="center">' + escHtml(it.unit || 'kg') + '</td></tr>'
  }).join('')
  return [
    '<!DOCTYPE html><html><head><meta charset="utf-8"><title>委外加工单 ' + escHtml(order.orderNo) + '</title><style>',
    'body{font-family:"Microsoft YaHei","SimSun",sans-serif;color:#111;margin:28px 34px;}',
    '.company{text-align:center;font-size:24px;font-weight:bold;letter-spacing:6px;margin:0 0 2px;}',
    'h1{text-align:center;font-size:20px;margin:0 0 2px;letter-spacing:2px;}',
    '.sub{text-align:center;font-size:12px;color:#555;margin-bottom:16px;}',
    'table{width:100%;border-collapse:collapse;}',
    'td,th{border:1px solid #888;padding:6px 10px;font-size:13px;}',
    '.info td.k{width:110px;color:#555;background:#f5f5f5;text-align:center;}',
    'table.main th{background:#f0f0f0;font-size:12px;}',
    '.main td{height:28px;}',
    '.sub-row td{background:#f2f5fa;font-weight:600;}',
    '.sub-tag{color:#64748b;font-size:11px;font-weight:400;}',
    '.num{text-align:right;}.center{text-align:center;}',
    '.sign{display:flex;justify-content:space-between;margin-top:60px;font-size:13px;}',
    '.sign span{border-top:1px solid #888;padding-top:6px;min-width:160px;text-align:center;display:inline-block;}',
    '.outbound-title{font-size:14px;font-weight:bold;margin:22px 0 8px;}',
    '.p-stage{border:1px solid #aaa;border-radius:4px;padding:10px 12px;margin-bottom:10px;page-break-inside:avoid;}',
    '.p-stage-h{font-size:14px;margin-bottom:6px;padding-bottom:4px;border-bottom:1px dashed #ccc;}',
    '.p-stage-h .p-no{display:inline-block;background:#1d4ed8;color:#fff;border-radius:3px;padding:1px 8px;margin-right:8px;font-size:12px;}',
    '.p-role{color:#64748b;font-weight:400;font-size:12px;}',
    '.p-step{font-size:13px;line-height:1.7;}',
    '.p-param{color:#1d4ed8;font-size:12px;}',
    '.qc-table{width:100%;border-collapse:collapse;margin-top:8px;font-size:12px;}',
    '.qc-table th,.qc-table td{border:1px solid #999;padding:3px 6px;}',
    '.qc-table th{background:#f5f5f5;}',
    '.p-pack{font-size:13px;line-height:1.8;border:1px dashed #1d4ed8;border-radius:4px;padding:8px 12px;margin-bottom:10px;background:#f8faff;}',
    '@media print{ body{margin:10px 14px;} }',
    '</style></head><body>',
    '<div class="company">广东芃远新材料有限公司</div>',
    '<h1>委外加工单</h1>',
    '<div class="sub">Outsourcing Order　·　订单号：' + escHtml(order.orderNo) + '　·　打印日期：' + nowStr + '</div>',
    '<table class="info">',
    '<tr><td class="k">订单号</td><td>' + escHtml(order.orderNo) + '</td><td class="k">产品名称</td><td>' + escHtml(order.productName || '-') + '</td></tr>',
    '<tr><td class="k">产品编码</td><td>' + escHtml(order.productCode || '-') + '</td><td class="k">配方编号</td><td>' + escHtml(recipeNo) + '</td></tr>',
    '<tr><td class="k">代工厂</td><td>' + escHtml(order.processor || '-') + '</td><td class="k">生产批量</td><td>' + escHtml(order.batchQty) + ' ' + escHtml(order.unit || 'kg') + '</td></tr>',
    '<tr><td class="k">状态</td><td>' + escHtml(statusText) + '</td><td class="k">制单人</td><td>' + escHtml(order.createdBy || '-') + '</td></tr>',
    '<tr><td class="k">创建时间</td><td>' + escHtml(order.createTime ? String(order.createTime).replace('T', ' ').substring(0, 16) : '-') + '</td><td class="k">备注</td><td>' + escHtml(order.remark || '-') + '</td></tr>',
    '</table>',
    '<table class="main"><thead><tr><th style="width:50px">序号</th><th>物料编码</th><th>类型</th><th>用量</th><th style="width:70px">单位</th></tr></thead><tbody>',
    bodyRows,
    '</tbody></table>',
    buildProcessHtml(processTpl, items),
    buildOutboundDetailHtml(outbounds),
    '<div class="sign"><span>发料人</span><span>代工厂签收</span><span>日期</span></div>',
    '</body></html>'
  ].join('')
}

// 出库批次明细 HTML（生产出库作为记录明细，先进先出自动分配，按库位拆行；不显示成本——生产人员不需要看价格）
function buildOutboundDetailHtml(outbounds) {
  if (!outbounds || !outbounds.length) return ''
  const obRows = outbounds.map((ob, i) => '<tr>'
    + '<td class="center">' + (i + 1) + '</td>'
    + '<td>' + escHtml(ob.materialCode || '-') + '</td>'
    + '<td>' + escHtml(ob.batchNo || '-') + '</td>'
    + '<td class="num">' + escHtml(ob.qty) + '</td>'
    + '<td class="center">' + escHtml(ob.unit || 'kg') + '</td>'
    + '<td>' + escHtml(whName(ob.fromWarehouseId)) + '</td>'
    + '<td>' + escHtml((ob.zoneName ? ob.zoneName + '/' : '') + (ob.locationName || '-')) + '</td></tr>').join('')
  return '<div class="outbound-title">出库批次明细（先进先出·代工厂仓，按库位拆行）</div>'
    + '<table class="main"><thead><tr><th style="width:40px">序号</th><th>物料编码</th><th>批号</th><th>数量</th><th style="width:45px">单位</th><th>出库仓库</th><th>库位</th></tr></thead><tbody>'
    + obRows + '</tbody></table>'
}

// 打开打印窗口（仿配方/质检打印：新窗口渲染 + window.print + 自动关闭）
async function printOutsource(row) {
  if (printing.value) return
  printing.value = true
  try {
    // v5.26：记录打印次数（失败不阻断打印）
    api.post('/print-count', { docType: 'OUTSOURCE_ORDER', docNo: row.orderNo })
      .then(n => { row.printCount = n }).catch(() => {})
    const data = await api.get(`/outsource-order/${row.id}`)
    let outbounds = []
    try { outbounds = await api.get(`/outsource-order/${row.id}/outbounds`) } catch {}
    const processTpl = await loadProcessTpl(data.order)
    const win = window.open('', '_blank')
    if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
    win.document.write(buildOutsourcePrintHtml(data.order, data.items || [], outbounds, processTpl))
    win.document.close()
    win.focus()
    setTimeout(() => { win.print(); win.close() }, 200)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载订单数据失败')
  } finally { printing.value = false }
}

// 已出库判断：displayStatus 推导 FEED/INBOUND/SHIPPED 代表已出库
function isOutbound(row) {
  const ds = row.displayStatus || row.status
  return ['FEED', 'INBOUND', 'SHIPPED'].includes(ds)
}
function fmtMoney(v) { return v == null ? '-' : Number(v).toFixed(2) }
function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : (id || '-') }
// 有「查看金额」权限才显示单价/成本（生产人员不显示价格）
const hasFinanceAmount = computed(() => {
  try { return (JSON.parse(localStorage.getItem('user') || '{}').permissions || []).includes('finance:amount') } catch { return false }
})

// 出库记录弹窗
const outboundVisible = ref(false)
const outboundOrder = ref(null)
const outboundRows = ref([])
async function viewOutbounds(row) {
  outboundOrder.value = row
  try { outboundRows.value = await api.get(`/outsource-order/${row.id}/outbounds`) } catch { outboundRows.value = [] }
  outboundVisible.value = true
}

// 打印并出库：先弹库存检查预览 → 用户确认后才自动出库（先进先出·代工厂仓）→ 打印
const stockDialogVisible = ref(false)
const stockRows = ref([])
const stockOrder = ref(null)
const hasStockShortage = computed(() => stockRows.value.some(r => !r.enough))

async function printAndOutbound(row) {
  if (printing.value) return
  try {
    stockRows.value = await api.get(`/outsource-order/${row.id}/stock-check`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '库存检查失败')
    return
  }
  stockOrder.value = row
  stockFromConfirm.value = false
  stockDialogVisible.value = true
}

async function confirmOutbound() {
  const row = stockOrder.value
  if (!row || printing.value) return
  stockDialogVisible.value = false
  printing.value = true
  try {
    // 从「确认」进来：先确认订单再自动出库；从「打印并出库」进来：直接出库
    if (stockFromConfirm.value) {
      await api.post(`/outsource-order/${row.id}/confirm`)
    }
    const outbounds = await api.post(`/outsource-order/${row.id}/auto-outbound`)
    ElMessage.success(stockFromConfirm.value
      ? `订单已确认并自动出库 ${outbounds.length} 行（先进先出·代工厂仓）`
      : `已自动出库 ${outbounds.length} 行（先进先出·代工厂仓）`)
    fetch()
    api.post('/print-count', { docType: 'OUTSOURCE_ORDER', docNo: row.orderNo }).then(n => { row.printCount = n }).catch(() => {})
    const data = await api.get(`/outsource-order/${row.id}`)
    const processTpl = await loadProcessTpl(data.order)
    const win = window.open('', '_blank')
    if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
    win.document.write(buildOutsourcePrintHtml(data.order, data.items || [], outbounds, processTpl))
    win.document.close()
    win.focus()
    setTimeout(() => { win.print(); win.close() }, 200)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '出库失败，请检查代工厂仓库存是否充足')
  } finally { printing.value = false }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(list)

onMounted(async () => {
  try {
    const mats = await api.get('/material', { params: { enabled: true } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name, spec: m.spec, unit: m.unit }))
  } catch {}
  try {
    releasedRecipes.value = await api.get('/recipe/released')
  } catch {}
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  loadConfirmedSalesOrders()
  fetch(); fetchSuppliers()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.formula-toolbar { margin-bottom: 10px; }
.batch-hint { font-size: 12px; color: #6366f1; margin-top: 4px; line-height: 1.4; }
.sub-tag { margin-right: 4px; }
.sub-code { font-weight: 600; color: #b45309; }
.sub-name { font-weight: 600; color: #92400e; }
.sub-tip { margin-top: 8px; font-size: 12px; color: #64748b; background: #fffbeb; border-radius: 6px; padding: 6px 10px; }
.text-muted { color: #9ca3af; font-size: 12px; }
.trace-tip { font-size: 12px; color: #64748b; margin: 10px 0 0; }
.stock-short { color: #dc2626; font-weight: 600; }
.stock-warn { margin: 12px 0 0; font-size: 13px; color: #dc2626; font-weight: 600; }
.stock-ok { margin: 12px 0 0; font-size: 13px; color: #16a34a; }
</style>
