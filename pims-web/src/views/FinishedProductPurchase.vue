<template>
  <div class="page-container">
    <div class="page-header">
      <h2>成品采购</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showForm(null)" v-if="hasPerm('purchase:write')">新增采购</el-button>
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="purchase-tabs">
      <!-- ===== 开立单据 ===== -->
      <el-tab-pane label="开立单据" name="draft">
        <div class="table-card">
          <p-table :data="draftList" stripe border v-loading="loading" @header-dragend="onHeaderDragend">
            <el-table-column prop="orderNo" label="合同号" :width="cw('合同号') || 160" />
            <el-table-column prop="purchaseDate" label="日期" :width="cw('日期') || 100" />
            <el-table-column prop="supplierName" label="供应商" :width="cw('供应商') || 120" />
            <el-table-column prop="materialName" label="品名" :width="cw('品名') || 120" />
            <el-table-column prop="brand" label="牌号" :width="cw('牌号') || 80" />
            <el-table-column prop="qty" label="数量" :width="cw('数量') || 80" />
            <el-table-column prop="unitPrice" label="单价(含税)" :width="cw('单价(含税)') || 95" v-if="hasPerm('purchase:price')" />
            <el-table-column label="不含税单价" :width="cw('不含税单价') || 95" align="right" v-if="hasPerm('purchase:price')">
              <template #default="{row}">{{ fmtTax(netOfTax(row.unitPrice, row.taxRate ?? taxRate)) }}</template>
            </el-table-column>
            <el-table-column label="税额" :width="cw('税额') || 75" align="right" v-if="hasPerm('purchase:price')">
              <template #default="{row}">{{ fmtTax(taxOf(row.unitPrice, row.taxRate ?? taxRate)) }}</template>
            </el-table-column>
            <el-table-column label="税率" :width="cw('税率') || 60" align="center" v-if="hasPerm('purchase:price')">
              <template #default="{row}">{{ (row.taxRate ?? taxRate) }}%</template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="金额" :width="cw('金额') || 100" v-if="hasPerm('purchase:price')" />
            <el-table-column label="赠送" :width="cw('赠送') || 60"><template #default="{row}"><el-tag size="small" :type="row.isFree?'warning':'info'">{{ row.isFree?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="状态" :width="cw('状态') || 80">
              <template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="createdBy" label="制单人" :width="cw('制单人') || 90" />
            <el-table-column label="操作" width="190">
              <template #default="{row}">
                <button class="op-btn" @click="printOrder(row)">打印</button>
                <button v-if="row.status==='DRAFT'" class="op-btn op-btn-primary" @click="showForm(row)">编辑</button>
                <button v-if="row.status==='DRAFT' && hasPerm('purchase:audit')" class="op-btn op-btn-success" @click="audit(row)">审核</button>
              </template>
            </el-table-column>
          </p-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="draftPage"
              v-model:page-size="draftSize"
              :page-sizes="[25, 50, 100]"
              :total="draftTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchDraft"
              @current-change="fetchDraft"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== 历史单据 ===== -->
      <el-tab-pane label="历史单据" name="history">
        <div class="search-card">
          <el-form :inline="true" :model="search" size="small">
            <el-form-item label="合同号"><el-input v-model="search.orderNo" placeholder="合同号" clearable @keyup.enter="onSearch" /></el-form-item>
            <el-form-item label="供应商"><el-input v-model="search.supplierName" placeholder="供应商" clearable @keyup.enter="onSearch" /></el-form-item>
            <el-form-item label="品名"><el-input v-model="search.materialName" placeholder="物料品名" clearable @keyup.enter="onSearch" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="search.status" placeholder="状态" clearable style="width:120px">
                <el-option label="已审核" value="APPROVED" />
                <el-option label="已到货" value="RECEIVED" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker v-model="search.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onSearch">查询</el-button>
              <el-button @click="onReset">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="table-card">
          <p-table :data="historyList" stripe border v-loading="historyLoading" @header-dragend="onHeaderDragend">
            <el-table-column prop="orderNo" label="合同号" :width="cw('合同号') || 160" />
            <el-table-column prop="purchaseDate" label="日期" :width="cw('日期') || 100" />
            <el-table-column prop="supplierName" label="供应商" :width="cw('供应商') || 120" />
            <el-table-column prop="materialName" label="品名" :width="cw('品名') || 120" />
            <el-table-column prop="brand" label="牌号" :width="cw('牌号') || 80" />
            <el-table-column prop="qty" label="数量" :width="cw('数量') || 80" />
            <el-table-column prop="unitPrice" label="单价(含税)" :width="cw('单价(含税)') || 95" v-if="hasPerm('purchase:price')" />
            <el-table-column label="不含税单价" :width="cw('不含税单价') || 95" align="right" v-if="hasPerm('purchase:price')">
              <template #default="{row}">{{ fmtTax(netOfTax(row.unitPrice, row.taxRate ?? taxRate)) }}</template>
            </el-table-column>
            <el-table-column label="税额" :width="cw('税额') || 75" align="right" v-if="hasPerm('purchase:price')">
              <template #default="{row}">{{ fmtTax(taxOf(row.unitPrice, row.taxRate ?? taxRate)) }}</template>
            </el-table-column>
            <el-table-column label="税率" :width="cw('税率') || 60" align="center" v-if="hasPerm('purchase:price')">
              <template #default="{row}">{{ (row.taxRate ?? taxRate) }}%</template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="金额" :width="cw('金额') || 100" v-if="hasPerm('purchase:price')" />
            <el-table-column label="赠送" :width="cw('赠送') || 60"><template #default="{row}"><el-tag size="small" :type="row.isFree?'warning':'info'">{{ row.isFree?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="状态" :width="cw('状态') || 80">
              <template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{row}">
                <button class="op-btn" @click="printOrder(row)">打印</button>
                <button v-if="row.status==='APPROVED' && hasPerm('purchase:reverse-audit')" class="op-btn op-btn-danger" @click="reverseAudit(row)">反审核</button>
              </template>
            </el-table-column>
          </p-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="historyPage"
              v-model:page-size="historySize"
              :page-sizes="[25, 50, 100]"
              :total="historyTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchHistory"
              @current-change="fetchHistory"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 弹窗（v5.0：新建为单据式多物料；编辑保留单行） ===== -->
    <el-dialog :title="form.id?'编辑':'新增成品采购'" v-model="visible" width="min(1280px, 96vw)" top="16px" destroy-on-close>
      <el-form :model="form" label-width="80px" size="small">
        <!-- ===== 新建：一张单据（同一合同号）一个供应商多个成品 ===== -->
        <template v-if="!form.id">
          <!-- v5.77 单据式表头：边框表格布局 -->
          <el-descriptions :column="2" border size="small" class="po-head-table">
            <el-descriptions-item label="采购日期">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-descriptions-item>
            <el-descriptions-item label="供应商 *">
              <el-autocomplete v-model="form.supplierName" :fetch-suggestions="searchSupplier" placeholder="输入搜索（仅成品供应商）" @select="onSelectSupplier" clearable style="width:100%" />
            </el-descriptions-item>
            <el-descriptions-item label="收货仓库 *">
              <el-select v-model="form.warehouseId" placeholder="选择收货地址（打印/收货按此）" style="width:100%">
                <el-option v-for="w in enabledWhs" :key="w.id" :value="w.id" :label="w.name + (w.address ? ' — ' + w.address : '')">
                  <span>{{ w.name }}</span>
                  <span style="float:right;color:#8492a6;font-size:12px;margin-left:16px">{{ w.address || '（未维护地址）' }}</span>
                </el-option>
              </el-select>
            </el-descriptions-item>
            <el-descriptions-item label="税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="17" :precision="2" :step="1" style="width:130px" />
              <span style="margin-left:8px;font-size:12px;color:#888">默认13%可改；单价为含税</span>
            </el-descriptions-item>
          </el-descriptions>
<div class="batch-header">
            <span class="batch-title">采购明细（同一合同号，共 {{ batchItems.length }} 种物料）</span>
            <button class="op-btn op-btn-add" type="button" @click="addBatchRow">+ 添加物料</button>
          </div>
          <p-table :data="batchItems" border size="small" style="width:100%">
            <el-table-column label="成品物料" :width="cw('成品物料') || undefined" min-width="240">
              <template #default="{ row }">
                <el-select v-model="row.materialName" filterable clearable :filter-method="v => row.searchText = v" :disabled="!form.supplierName" placeholder="请先选择供应商" size="small" class="full-width" @change="name => onBatchMaterialChange(row, name)">
                  <el-option v-for="m in rowMaterials(row)" :key="m.code" :label="m.code + ' ' + m.name + (m.brand ? ' [' + m.brand + ']' : '')" :value="m.name">
                    <span>{{ m.code }} {{ m.name }}<span v-if="m.brand"> [{{ m.brand }}]</span></span>
                    <el-tag size="small" type="success" effect="light" style="margin-left:8px">成品</el-tag>
                  </el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="牌号" width="110">
              <template #default="{ row }">{{ row.brand || '-' }}</template>
            </el-table-column>
            <el-table-column label="数量(kg)" width="120">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="0" :precision="3" size="small" style="width:105px" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="单价(含税)" width="118">
              <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:100px" controls-position="right" :disabled="row.isFree" /></template>
            </el-table-column>
            <el-table-column label="不含税单价" width="95" align="right">
              <template #default="{ row }"><span class="amount-cell">{{ fmtTax(netOfTax(row.unitPrice, form.taxRate ?? taxRate)) }}</span></template>
            </el-table-column>
            <el-table-column label="税额" width="85" align="right">
              <template #default="{ row }"><span class="amount-cell">{{ fmtTax(taxOf(row.unitPrice, form.taxRate ?? taxRate)) }}</span></template>
            </el-table-column>
            <el-table-column label="金额(含税)" width="105" align="right">
              <template #default="{ row }"><span class="amount-cell">{{ fmtMoney(row.qty * (row.unitPrice||0)) }}</span></template>
            </el-table-column>
            <el-table-column label="赠送" width="62" align="center">
              <template #default="{ row }"><el-switch v-model="row.isFree" size="small" @change="v => { if (v) row.unitPrice = 0 }" /></template>
            </el-table-column>
            <el-table-column label="" width="46" align="center">
              <template #default="{ $index }"><button class="op-btn op-btn-del" type="button" @click="batchItems.splice($index,1)">✕</button></template>
            </el-table-column>
          </p-table>
          <div class="batch-total">合计：<strong class="amount-cell">￥{{ fmtMoney(batchTotal) }}</strong>　不含税合计：<strong class="amount-cell">￥{{ (batchTotal / (1 + (form.taxRate ?? taxRate) / 100)).toFixed(2) }}</strong>　税额合计：<strong class="amount-cell">￥{{ (batchTotal - batchTotal / (1 + (form.taxRate ?? taxRate) / 100)).toFixed(2) }}</strong>　税率：<strong>{{ form.taxRate ?? taxRate }}%</strong></div>
          <el-form-item label="备注" style="margin-top:12px"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        </template>

        <!-- ===== 编辑：单行（仅草稿） ===== -->
        <template v-else>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="采购日期"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="合同号"><el-input v-model="form.orderNo" disabled /></el-form-item></el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="收货仓库" required>
              <el-select v-model="form.warehouseId" placeholder="选择收货地址（打印/收货按此）" class="full-width">
                <el-option v-for="w in enabledWhs" :key="w.id" :value="w.id" :label="w.name + (w.address ? ' — ' + w.address : '')">
                  <span>{{ w.name }}</span>
                  <span style="float:right;color:#8492a6;font-size:12px;margin-left:16px">{{ w.address || '（未维护地址）' }}</span>
                </el-option>
              </el-select>
            </el-form-item></el-col>
          </el-row>

          <el-form-item label="供应商"><el-autocomplete v-model="form.supplierName" :fetch-suggestions="searchSupplier" placeholder="输入搜索" @select="onSelectSupplier" clearable class="full-width" /></el-form-item>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="品名">
              <el-select v-model="form.materialName" filterable clearable :filter-method="materialFilter" :disabled="!form.supplierName" placeholder="请先选择供应商" class="full-width" @change="onMaterialChange">
                <el-option v-for="m in filteredMaterials" :key="m.code" :label="m.code + ' ' + m.name + (m.brand ? ' [' + m.brand + ']' : '')" :value="m.name">
                  <span>{{ m.code }} {{ m.name }}<span v-if="m.brand"> [{{ m.brand }}]</span></span>
                  <el-tag size="small" type="success" effect="light" style="margin-left:8px">成品</el-tag>
                </el-option>
              </el-select>
            </el-form-item></el-col>
            <el-col :span="12"><el-form-item label="牌号"><el-input v-model="form.brand" placeholder="PPG/阿克苏/宣伟" /></el-form-item></el-col>
          </el-row>
          <!-- 品牌归属：选择物料后只读展示，便于核对供应商一致性 -->
          <el-row :gutter="16" v-if="form.brandOwner">
            <el-col :span="12"><el-form-item label="品牌归属"><el-input v-model="form.brandOwner" disabled /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="form.qty" :min="0" controls-position="right" class="full-width" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="form.unitPrice" :min="0" controls-position="right" class="full-width" :disabled="form.isFree" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="赠送"><el-switch v-model="form.isFree" @change="v=>{if(v)form.unitPrice=0}" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
    <!-- v5.71.2 打印前选择收货地址（多仓多地址场景） -->
    <el-dialog title="选择收货地址" v-model="printWhVisible" width="min(1100px, 96vw)">
      <el-radio-group v-model="printWhId" style="display:flex;flex-direction:column;gap:10px;">
        <el-radio v-for="w in printWhOptions" :key="w.id" :value="String(w.id)" style="align-items:flex-start;height:auto;line-height:1.6;margin-right:0;">
          <div>
            <div style="font-weight:600;">{{ w.name }}</div>
            <div style="font-size:12px;color:#666;">地址：{{ w.address || '（未维护）' }}</div>
            <div style="font-size:12px;color:#666;">联系人：{{ w.contactPerson || '（未维护）' }}　{{ w.contactPhone || '' }}</div>
          </div>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="printWhVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPrint">打印请购单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtMoney as fmtMoneyBase } from '../utils/fmt'
import { statusType } from '../utils/statusTag'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'
import { downloadFile } from '../utils/download'
import { loadTaxRate, netOfTax, taxOf, fmtTax } from '../utils/tax'
import { printPurchaseOrder } from '../utils/purchaseOrderPrint'

// ===== Tab 状态 =====
const activeTab = ref('draft')
const exporting = ref(false)

// v5.23：导出当前 Tab 的筛选数据——开立单据导出全部 DRAFT；历史单据导出当前搜索条件全量
async function doExport() {
  exporting.value = true
  try {
    const params = {}
    if (activeTab.value === 'draft') {
      params.status = 'DRAFT'
    } else {
      if (search.value.orderNo) params.orderNo = search.value.orderNo
      if (search.value.supplierName) params.supplierName = search.value.supplierName
      if (search.value.materialName) params.materialName = search.value.materialName
      if (search.value.status) params.status = search.value.status
      if (search.value.dateRange?.[0]) params.startDate = search.value.dateRange[0]
      if (search.value.dateRange?.[1]) params.endDate = search.value.dateRange[1]
    }
    await downloadFile('/finished-product-purchase/export', params, `成品采购-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// ===== 开立单据（分页） =====
const draftList = ref([])
const draftPage = ref(1)
const draftSize = ref(20)
const draftTotal = ref(0)
const loading = ref(false)

// ===== 历史单据（多条件 + 分页） =====
const historyList = ref([])
const historyPage = ref(1)
const historySize = ref(20)
const historyTotal = ref(0)
const historyLoading = ref(false)
const search = ref({ orderNo: '', supplierName: '', materialName: '', status: '', dateRange: null })

// ===== 弹窗表单 =====
const finishedMaterials = ref([])
const visible = ref(false)
const saving = ref(false)
const form = ref({})
const perms = ref([])
const taxRate = ref(13)
const { cw, onHeaderDragend } = useColumnResize('finished_product_purchase')

// ===== v5.0：新建模式（单据式多物料明细） =====
const batchItems = ref([])
function addBatchRow() {
  batchItems.value.push({ materialName: '', materialCode: '', brand: '', qty: null, unitPrice: null, isFree: false, searchText: '' })
}
const batchTotal = computed(() => batchItems.value.reduce((s, r) => s + (Number(r.qty) || 0) * (Number(r.unitPrice) || 0), 0))

// 行内物料下拉：按当前供应商品牌归属过滤 + 行内关键字搜索
function rowMaterials(row) {
  let base = finishedMaterials.value
  if (form.value.supplierName) base = base.filter(m => m.brandOwner === form.value.supplierName)
  const q = (row.searchText || '').toLowerCase()
  if (!q) return base
  return base.filter(m => m.code.toLowerCase().includes(q) || m.name.toLowerCase().includes(q) || (m.brand && m.brand.toLowerCase().includes(q)))
}
function onBatchMaterialChange(row, name) {
  row.searchText = ''
  const m = finishedMaterials.value.find(m => m.name === name)
  if (!m) return
  row.materialCode = m.code
  row.brand = m.brand || ''
}

function fmtMoney(v) { return '￥' + fmtMoneyBase(v) }   // v6.6 收口：千分位（utils/fmt）

// ===== v5.71 打印请购单（按合同号聚合全部物料行，可直接发给供应商） =====
let supplierPrintCache = null
const whPrintList = ref([])
const enabledWhs = computed(() => whPrintList.value.filter(w => w.enabled !== false))
async function fetchWhsForForm() { try { whPrintList.value = await api.get('/warehouse') } catch {} }
async function printOrder(row) {
  try {
    const res = await api.get('/finished-product-purchase/search', { params: { orderNo: row.orderNo, page: 0, size: 200 } })
    const rows = res.content || []
    if (!rows.length) { ElMessage.warning('未找到该合同号的明细'); return }
    if (!supplierPrintCache) {
      try { supplierPrintCache = await api.get('/supplier', { params: { pageSize: 500 } }) } catch { supplierPrintCache = [] }
    }
    const sup = (Array.isArray(supplierPrintCache) ? supplierPrintCache : (supplierPrintCache.rows || []))
      .find(x => x.id === row.supplierId) || {}
    // v5.71.3 打印优先用单据收货仓库直接出单；仅历史单据未选仓库时弹窗补选
    if (!whPrintList.value.length) {
      try { whPrintList.value = await api.get('/warehouse') } catch { whPrintList.value = [] }
    }
    const list = whPrintList.value.filter(w => w.enabled !== false)
    if (!list.length) { ElMessage.warning('未找到仓库档案，请先在 基础数据→仓库 维护'); return }
    if (row.warehouseId) {
      const wh = list.find(w => String(w.id) === String(row.warehouseId))
      if (wh) {
        if (!wh.address || !wh.address.trim() || !wh.contactPerson || !wh.contactPerson.trim()) {
          ElMessage.warning(`收货仓库「${wh.name}」档案未维护收货地址或收货联系人，请先在 基础数据→仓库 中维护后再打印`)
          return
        }
        printPurchaseOrder(rows, {
          kind: 'FINISHED',
          showPrice: hasPerm('purchase:price'),
          supplier: sup,
          warehouseName: wh.name,
          receiver: { address: wh.address, contact: wh.contactPerson, phone: wh.contactPhone }
        })
        return
      }
    }
    printPending.value = { rows }
    printWhOptions.value = list
    printWhId.value = String((list.find(w => String(w.id) === String(row.warehouseId)) || list[0]).id)
    printWhVisible.value = true
    // 字典码翻译成中文（付款条件/付款方式）
    let dictMap = {}
    try {
      const dicts = await api.get('/dict')
      ;(dicts || []).forEach(d => { if (d.type === 'payment_terms' || d.type === 'payment_method') dictMap[d.value] = d.label })
    } catch {}
    if (sup.paymentTerms && dictMap[sup.paymentTerms]) sup.paymentTerms = dictMap[sup.paymentTerms]
    if (sup.paymentMethod && dictMap[sup.paymentMethod]) sup.paymentMethod = dictMap[sup.paymentMethod]
    printPending.value.sup = sup
  } catch (e) {
    console.error('打印失败详情:', e)
    ElMessage.error((e && e.message ? '打印失败：' + e.message : '') || e?.response?.data?.msg || '打印失败')
  }
}

// v5.71.2 打印弹窗确认：按所选收货仓库出单
const printWhVisible = ref(false)
const printWhOptions = ref([])
const printWhId = ref('')
const printPending = ref(null)
async function confirmPrint() {
  const wh = printWhOptions.value.find(w => String(w.id) === printWhId.value)
  if (!wh) { ElMessage.warning('请选择收货仓库'); return }
  if (!wh.address || !wh.address.trim() || !wh.contactPerson || !wh.contactPerson.trim()) {
    ElMessage.warning(`仓库「${wh.name}」档案未维护收货地址或收货联系人，请先在 基础数据→仓库 中维护后再打印`)
    return
  }
  const { rows, sup } = printPending.value || {}
  if (!rows || !rows.length) return
  printWhVisible.value = false
  printPurchaseOrder(rows, {
    kind: 'FINISHED',
    showPrice: hasPerm('purchase:price'),
    supplier: sup || {},
    warehouseName: wh.name,
    receiver: { address: wh.address, contact: wh.contactPerson, phone: wh.contactPhone }
  })
}

function hasPerm(c) { return perms.value.includes(c) }

// 状态文案/类型映射（含已到货）
function statusText(s) { return { DRAFT: '开立', APPROVED: '已审核', RECEIVED: '已到货' }[s] || '未知' }
// v6.4 状态色统一（utils/statusTag 全局映射）

// ===== 开立单据查询 =====
async function fetchDraft() {
  loading.value = true
  try {
    const res = await api.get('/finished-product-purchase/search', {
      params: { status: 'DRAFT', page: draftPage.value - 1, size: draftSize.value }
    })
    draftList.value = res.content || []
    draftTotal.value = res.totalElements || 0
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载开立单据失败')
  } finally { loading.value = false }
}

// ===== 历史单据查询（多条件） =====
async function fetchHistory() {
  historyLoading.value = true
  try {
    const params = {
      page: historyPage.value - 1,
      size: historySize.value,
      orderNo: search.value.orderNo || undefined,
      supplierName: search.value.supplierName || undefined,
      materialName: search.value.materialName || undefined,
      status: search.value.status || undefined,
      startDate: search.value.dateRange?.[0] || undefined,
      endDate: search.value.dateRange?.[1] || undefined,
    }
    const res = await api.get('/finished-product-purchase/search', { params })
    historyList.value = res.content || []
    historyTotal.value = res.totalElements || 0
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载历史单据失败')
  } finally { historyLoading.value = false }
}

// ===== Tab 切换：懒加载历史 =====
function handleTabChange(name) {
  if (name === 'draft' && draftList.value.length === 0) fetchDraft()
  if (name === 'history' && historyList.value.length === 0) fetchHistory()
}

// ===== 搜索/重置 =====
function onSearch() { historyPage.value = 1; fetchHistory() }
function onReset() {
  search.value = { orderNo: '', supplierName: '', materialName: '', status: '', dateRange: null }
  historyPage.value = 1
  fetchHistory()
}

// ===== 保存/审核/反审核后刷新当前 Tab =====
function reload() {
  if (activeTab.value === 'draft') fetchDraft()
  else fetchHistory()
}

async function loadMaterials() {
  try { finishedMaterials.value = await api.get('/material', { params: { category: 'C', enabled: true } }) } catch {}
}
function showForm(row) {
  form.value = row ? { ...row } : { purchaseDate: new Date().toISOString().slice(0,10), isFree: false , taxRate: taxRate.value }
  // 新建模式初始化一行空明细
  if (!row) { batchItems.value = []; addBatchRow() }
  // 编辑时若单据未带品牌归属，则根据物料名称回填，保证只读展示与供应商过滤生效
  if (row && row.materialName && !form.value.brandOwner) {
    const m = finishedMaterials.value.find(m => m.name === row.materialName)
    if (m) form.value.brandOwner = m.brandOwner || ''
  }
  visible.value = true
}

async function searchSupplier(q, cb) {
  try {
    // 成品采购展示全部成品供应商；品名下拉会根据所选供应商的品牌归属自动过滤物料
    const d = await api.get('/supplier', { params: { keyword: q, type: 'FINISHED', enabled: true } })
    cb(d.map(s => ({ value: s.name, id: s.id })))
  } catch { cb([]) }
}

const materialSearch = ref('')
const filteredMaterials = computed(() => {
  // 第一层过滤：仅展示当前供应商品牌归属下的成品物料
  let base = finishedMaterials.value
  if (form.value.supplierName) {
    base = base.filter(m => m.brandOwner === form.value.supplierName)
  }
  // 第二层过滤：用户输入关键字搜索（编码/品名/牌号）
  const q = materialSearch.value.toLowerCase()
  if (!q) return base
  return base.filter(m => m.code.toLowerCase().includes(q) || m.name.toLowerCase().includes(q) || (m.brand && m.brand.toLowerCase().includes(q)))
})
function materialFilter(val) { materialSearch.value = val }
function onMaterialChange(name) {
  materialSearch.value = ''
  const m = finishedMaterials.value.find(m => m.name === name)
  if (!m) return
  form.value.materialCode = m.code
  form.value.brand = m.brand || ''
  // 回填物料的品牌归属，用于只读展示
  form.value.brandOwner = m.brandOwner || ''
}
function onSelectSupplier(item) {
  form.value.supplierId = item.id
  // 切换供应商后，若已选物料的品牌归属与新供应商不一致，清空物料相关字段，避免误提交
  if (form.value.materialName && form.value.brandOwner && form.value.brandOwner !== item.value) {
    form.value.materialName = ''
    form.value.materialCode = ''
    form.value.brand = ''
    form.value.brandOwner = ''
  }
}

async function save() {
  saving.value = true
  try {
    if (form.value.id) { await api.put(`/finished-product-purchase/${form.value.id}`, form.value) }
    else {
      // v5.0：新建走批量接口（一张单据一个供应商多个物料，共享合同号）
      const validItems = batchItems.value.filter(r => r.materialName)
      if (!validItems.length) { ElMessage.warning('请至少添加一个成品物料'); return }
      if (!form.value.supplierId) { ElMessage.warning('请选择供应商'); return }
      if (!form.value.warehouseId) { ElMessage.warning('请选择收货仓库（打印请购单按此带出收货地址）'); return }
      for (const r of validItems) {
        if (!r.qty || r.qty <= 0) { ElMessage.warning(`请填写「${r.materialName}」的采购数量`); return }
      }
      const res = await api.post('/finished-product-purchase/batch', {
        supplierId: form.value.supplierId,
        warehouseId: form.value.warehouseId,
        taxRate: form.value.taxRate ?? taxRate.value,
        supplierName: form.value.supplierName,
        purchaseDate: form.value.purchaseDate || undefined,
        remark: form.value.remark || undefined,
        items: validItems.map(r => ({ materialCode: r.materialCode, materialName: r.materialName, brand: r.brand, qty: r.qty, unitPrice: r.unitPrice, isFree: r.isFree }))
      })
      const orderNo = res?.[0]?.orderNo
      ElMessage.success(orderNo ? `采购单已创建：合同号 ${orderNo}，共 ${res.length} 种物料` : '保存成功')
    }
    visible.value = false; reload()
  } catch(e) { ElMessage.error(e.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

async function audit(row) {
  await api.post(`/finished-product-purchase/${row.id}/audit`)
  ElMessage.success('审核成功')
  fetchDraft()
}

async function reverseAudit(row) {
  await ElMessageBox.confirm('反审核后单据回到开立状态，确认？')
  await api.post(`/finished-product-purchase/${row.id}/reverse-audit`)
  ElMessage.success('反审核成功')
  fetchHistory()
}

onMounted(async () => {
  loadTaxRate(api).then(r => { taxRate.value = r })
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  fetchDraft()
  loadMaterials()
  fetchWhsForForm()
})
</script>

<style scoped>
.page-container { width: 100%; }
.full-width { width: 100%; }
.search-card { background: #fff; padding: 12px 16px 0; border-radius: 8px; margin-bottom: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.04); }
.pagination-bar { display: flex; justify-content: flex-end; padding: 12px 0 4px; }
.batch-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.batch-title { font-size: 13px; font-weight: 600; color: #374151; }
.batch-total { text-align: right; padding: 8px 4px; font-size: 13px; color: #374151; }
.amount-cell { color: #ea580c; font-weight: 600; }

.po-head-table { margin-bottom: 12px; }
.po-head-table :deep(.el-descriptions__label) { width: 110px; background: #f5f7fa; color: #606266; }
.po-head-table :deep(.el-descriptions__content) { padding: 8px 12px; }
.po-head-table :deep(.el-select-dropdown__item) { max-width: 640px; white-space: normal; height: auto; line-height: 1.5; padding: 4px 12px; }
</style>
