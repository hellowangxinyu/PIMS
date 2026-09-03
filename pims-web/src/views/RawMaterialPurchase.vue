<template>
  <div class="page-container">
    <div class="page-header">
      <h2>原料采购</h2>
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
            <el-table-column prop="materialCode" label="编码" :width="cw('编码') || 110" />
            <el-table-column prop="materialName" label="品名" :width="cw('品名') || 120" />
            <!-- v5.34：目标仓库（低库存预警生成采购单时带出，发往不同仓库） -->
            <el-table-column label="目标仓库" :width="cw('目标仓库') || 110">
              <template #default="{row}">{{ whName(row.warehouseId) }}</template>
            </el-table-column>
            <el-table-column prop="brand" label="牌号" :width="cw('牌号') || 120" />
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
            <el-form-item label="编码"><el-input v-model="search.materialCode" placeholder="物料编码" clearable @keyup.enter="onSearch" /></el-form-item>
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
            <el-table-column prop="materialCode" label="编码" :width="cw('编码') || 110" />
            <el-table-column prop="materialName" label="品名" :width="cw('品名') || 120" />
            <!-- v5.34：目标仓库（低库存预警生成采购单时带出，发往不同仓库） -->
            <el-table-column label="目标仓库" :width="cw('目标仓库') || 110">
              <template #default="{row}">{{ whName(row.warehouseId) }}</template>
            </el-table-column>
            <el-table-column prop="brand" label="牌号" :width="cw('牌号') || 120" />
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
    <el-dialog :title="form.id?'编辑原料采购':'新增原料采购'" v-model="visible" width="min(1280px, 96vw)" top="16px" @close="resetForm" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <!-- ===== 新建：一张单据（同一合同号）一个供应商多个物料 ===== -->
        <template v-if="!form.id">
          <!-- v5.77 单据式表头：边框表格布局，一行单据信息一目了然 -->
          <el-descriptions :column="2" border size="small" class="po-head-table">
            <el-descriptions-item label="采购日期">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-descriptions-item>
            <el-descriptions-item label="原料供应商 *">
              <el-autocomplete v-model="form.supplierName" :fetch-suggestions="searchSupplier" placeholder="输入搜索（仅材料供应商）" @select="onSelectSupplier" clearable style="width:100%" />
            </el-descriptions-item>
            <el-descriptions-item label="收货仓库 *">
              <el-select v-model="form.warehouseId" placeholder="选择收货地址（打印按此带出）" style="width:100%">
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
            <el-table-column label="物料" min-width="220">
              <template #default="{ row }">
                <el-autocomplete v-model="row.materialCode" :fetch-suggestions="searchMaterial" placeholder="编码或品名搜索" @select="it => onBatchMaterialSelect(row, it)" clearable size="small" class="full-width">
                  <template #default="{ item }"><span>{{ item.label }}</span></template>
                </el-autocomplete>
              </template>
            </el-table-column>
            <el-table-column label="品名" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialName || '-' }}</template>
            </el-table-column>
            <el-table-column label="牌号" width="110">
              <template #default="{ row }"><el-input v-model="row.brand" placeholder="选物料带出" size="small" /></template>
            </el-table-column>
            <el-table-column label="数量(kg)" width="125">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="0" :precision="3" size="small" style="width:105px" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="单价(含税)" width="120">
              <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:100px" controls-position="right" :disabled="row.isFree" /></template>
            </el-table-column>
            <el-table-column label="不含税单价" width="95" align="right">
              <template #default="{ row }"><span class="amount-cell">{{ fmtTax(netOfTax(row.unitPrice, form.taxRate ?? taxRate)) }}</span></template>
            </el-table-column>
            <el-table-column label="税额" width="85" align="right">
              <template #default="{ row }"><span class="amount-cell">{{ fmtTax(taxOf(row.unitPrice, form.taxRate ?? taxRate)) }}</span></template>
            </el-table-column>
            <el-table-column label="金额(含税)" width="105" align="right">
              <template #default="{ row }"><span class="amount-cell">{{ fmt(row.qty * (row.unitPrice||0)) }}</span></template>
            </el-table-column>
            <el-table-column label="赠送" width="65" align="center">
              <template #default="{ row }"><el-switch v-model="row.isFree" size="small" @change="v => { if (v) row.unitPrice = 0 }" /></template>
            </el-table-column>
            <el-table-column label="" width="48" align="center">
              <template #default="{ $index }"><button class="op-btn op-btn-del" type="button" @click="batchItems.splice($index,1)">✕</button></template>
            </el-table-column>
          </p-table>
          <div class="batch-total">含税合计：<strong class="amount-cell">￥{{ fmt(batchTotal) }}</strong>　不含税合计：<strong class="amount-cell">￥{{ fmt(batchTotal / (1 + (form.taxRate ?? taxRate) / 100)) }}</strong>　税额合计：<strong class="amount-cell">￥{{ fmt(batchTotal - batchTotal / (1 + (form.taxRate ?? taxRate) / 100)) }}</strong>　税率：<strong>{{ form.taxRate ?? taxRate }}%</strong></div>
          <el-form-item label="备注" style="margin-top:12px"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        </template>

        <!-- ===== 编辑：单行（仅草稿） ===== -->
        <template v-else>
          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="采购日期"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="合同号"><el-input v-model="form.orderNo" disabled /></el-form-item></el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="收货仓库" required>
              <el-select v-model="form.warehouseId" placeholder="选择收货地址（打印/收货按此）" class="full-width">
                <el-option v-for="w in enabledWhs" :key="w.id" :value="w.id" :label="w.name + (w.address ? ' — ' + w.address : '')">
                  <span>{{ w.name }}</span>
                  <span style="float:right;color:#8492a6;font-size:12px;margin-left:16px">{{ w.address || '（未维护地址）' }}</span>
                </el-option>
              </el-select>
            </el-form-item></el-col>
          </el-row>


          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="原料供应商">
                <el-autocomplete v-model="form.supplierName" :fetch-suggestions="searchSupplier" placeholder="输入搜索" @select="onSelectSupplier" clearable class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="品名">
                <el-autocomplete v-model="form.materialCode" :fetch-suggestions="searchMaterial" placeholder="输入编码或品名搜索" @select="onSelectMaterial" clearable class="full-width">
                  <template #default="{ item }"><span>{{ item.label }}</span></template>
                </el-autocomplete>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8"><el-form-item label="大类"><el-input v-model="form.category" disabled /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="小类"><el-input v-model="form.subCategory" disabled /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="品名"><el-input v-model="form.materialName" disabled /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8"><el-form-item label="牌号"><el-input v-model="form.brand" disabled /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="form.qty" :min="0" :precision="3" controls-position="right" class="full-width" @change="calcAmount" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="form.unitPrice" :min="0" :precision="2" controls-position="right" class="full-width" :disabled="form.isFree" @change="calcAmount" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="20" v-if="hasPerm('purchase:price')">
            <el-col :span="8"><el-form-item label="上次单价"><el-input-number v-model="form.lastUnitPrice" :min="0" :precision="2" disabled class="full-width" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="赠送"><el-switch v-model="form.isFree" @change="onFreeChange" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="金额"><el-input :model-value="fmt(form.totalAmount)" disabled /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="20" v-if="hasPerm('purchase:price')">
            <el-col :span="8"><el-form-item label="增幅%"><el-input :model-value="form.increaseRate ? form.increaseRate.toFixed(2)+'%' : '-'" disabled /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="增值"><el-input :model-value="form.increaseAmount||'-'" disabled /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="20" v-if="!hasPerm('purchase:price')">
            <el-col :span="8"><el-form-item label="赠送"><el-switch v-model="form.isFree" @change="onFreeChange" /></el-form-item></el-col>
          </el-row>

          <el-form-item label="合同上传" v-if="form.id">
            <div class="upload-area">
              <el-upload :action="`/api/raw-material-purchase/${form.id}/upload`" :headers="uploadHeaders" accept=".pdf" :on-success="onUploaded" :show-file-list="false">
                <el-button size="small">选择PDF</el-button>
              </el-upload>
              <span v-if="form.filePath" class="upload-hint">已上传 ✓</span>
            </div>
          </el-form-item>

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
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'
import { downloadFile } from '../utils/download'
import { loadTaxRate, netOfTax, taxOf, fmtTax } from '../utils/tax'
import { printPurchaseOrder } from '../utils/purchaseOrderPrint'

// 原料采购允许的物料大类：A(助剂)/P(颜料)/F(填料)/R(树脂)/S(溶剂)，排除 B(半成品) 与 C(成品)
const RAW_MATERIAL_CATEGORIES = ['A', 'P', 'F', 'R', 'S']

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
      if (search.value.materialCode) params.materialCode = search.value.materialCode
      if (search.value.materialName) params.materialName = search.value.materialName
      if (search.value.status) params.status = search.value.status
      if (search.value.dateRange?.[0]) params.startDate = search.value.dateRange[0]
      if (search.value.dateRange?.[1]) params.endDate = search.value.dateRange[1]
    }
    await downloadFile('/raw-material-purchase/export', params, `原料采购-${new Date().toISOString().slice(0, 10)}.xlsx`)
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
const search = ref({ orderNo: '', supplierName: '', materialCode: '', materialName: '', status: '', dateRange: null })

// ===== 弹窗表单 =====
const visible = ref(false)
const saving = ref(false)
const form = ref({})
const perms = ref([])
const taxRate = ref(13)
const uploadHeaders = { 'pims-token': localStorage.getItem('pims-token') || '' }
const materials = ref([])
const { cw, onHeaderDragend } = useColumnResize('raw_material_purchase')

// ===== v5.0：新建模式（单据式多物料明细） =====
const batchItems = ref([])

// v5.34：目标仓库名称映射（低库存预警生成采购单时带目标仓库，列表显示发往哪个仓库）
const warehouseMap = ref({})
const whList = ref([])
async function fetchWarehouses() {
  try {
    const whs = await api.get('/warehouse')
    whList.value = whs
    const m = {}
    whs.forEach(w => { m[String(w.id)] = w.name })
    warehouseMap.value = m
  } catch {}
}
function whName(id) { return (id && warehouseMap.value[String(id)]) || '-' }
const enabledWhs = computed(() => whList.value.filter(w => w.enabled !== false))
function addBatchRow() {
  batchItems.value.push({ materialCode: '', materialName: '', brand: '', category: '', subCategory: '', qty: null, unitPrice: null, isFree: false })
}
const batchTotal = computed(() => batchItems.value.reduce((s, r) => s + (Number(r.qty) || 0) * (Number(r.unitPrice) || 0), 0))

// 明细行选择物料：带出品类信息 + 回填上次采购单价（未选供应商时顺带回填供应商）
async function onBatchMaterialSelect(row, item) {
  const m = item.material
  if (!m) return
  row.materialCode = m.code
  row.materialName = m.name
  row.brand = m.brand || ''
  row.category = m.category || ''
  row.subCategory = m.subCategory || ''
  try {
    const last = await api.get(`/raw-material-purchase/last/${encodeURIComponent(m.name)}`)
    if (last) {
      if (!form.value.supplierId && last.supplierId) {
        form.value.supplierId = last.supplierId
        form.value.supplierName = last.supplierName
      }
      row.unitPrice = row.unitPrice ?? (last.unitPrice || last.lastUnitPrice)
    }
  } catch {}
}

function hasPerm(c) { return perms.value.includes(c) }

// 状态文案/类型映射（含已到货）
function statusText(s) { return { DRAFT: '开立', APPROVED: '已审核', RECEIVED: '已到货' }[s] || '未知' }
// v6.4 状态色统一（utils/statusTag 全局映射）

// ===== 开立单据查询 =====
async function fetchDraft() {
  loading.value = true
  try {
    const res = await api.get('/raw-material-purchase/search', {
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
      materialCode: search.value.materialCode || undefined,
      materialName: search.value.materialName || undefined,
      status: search.value.status || undefined,
      startDate: search.value.dateRange?.[0] || undefined,
      endDate: search.value.dateRange?.[1] || undefined,
    }
    const res = await api.get('/raw-material-purchase/search', { params })
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
  search.value = { orderNo: '', supplierName: '', materialCode: '', materialName: '', status: '', dateRange: null }
  historyPage.value = 1
  fetchHistory()
}

// ===== 保存/审核/反审核后刷新当前 Tab =====
function reload() {
  if (activeTab.value === 'draft') fetchDraft()
  else fetchHistory()
}

async function fetchMaterials() {
  try { materials.value = await api.get('/material', { params: { enabled: true } }) } catch {}
}

function showForm(row) {
  form.value = row ? { ...row } : { purchaseDate: new Date().toISOString().slice(0,10), isFree: false, taxRate: taxRate.value }
  // 新建模式初始化一行空明细
  if (!row) { batchItems.value = []; addBatchRow() }
  visible.value = true
}

function resetForm() { form.value = {} }

async function searchSupplier(q, cb) {
  try {
    // 原料采购只显示材料供应商
    const data = await api.get('/supplier', { params: { keyword: q, type: 'MATERIAL', enabled: true } })
    cb(data.map(s => ({ value: s.name, id: s.id })))
  } catch { cb([]) }
}

function searchMaterial(q, cb) {
  const kw = (q || '').toLowerCase()
  const filtered = materials.value
    .filter(m => m.enabled !== false)
    // 仅显示原料大类（A/P/F/R/S），排除半成品(B)与成品(C)
    .filter(m => RAW_MATERIAL_CATEGORIES.includes(m.category))
    .filter(m => !kw || m.code.toLowerCase().includes(kw) || m.name.toLowerCase().includes(kw))
    .map(m => ({ value: m.code, label: `${m.code} - ${m.name}`, material: m }))
  cb(filtered)
}

function onSelectSupplier(item) {
  form.value.supplierId = item.id
}

async function onSelectMaterial(item) {
  const m = item.material
  if (!m) return
  form.value.materialCode = m.code
  form.value.materialName = m.name
  form.value.brand = m.brand || ''
  form.value.category = m.category || ''
  form.value.subCategory = m.subCategory || ''
  // 查上次采购单价
  try {
    const last = await api.get(`/raw-material-purchase/last/${encodeURIComponent(m.name)}`)
    if (last) {
      if (!form.value.supplierId) { form.value.supplierId = last.supplierId; form.value.supplierName = last.supplierName }
      form.value.lastUnitPrice = last.unitPrice || last.lastUnitPrice
      form.value.unitPrice = form.value.unitPrice || form.value.lastUnitPrice
      calcAmount()
      ElMessage.success('已回填上次采购信息')
    }
  } catch {}
}

function onFreeChange(v) { if (v) { form.value.unitPrice = 0; calcAmount() } }

function calcAmount() {
  if (form.value.qty && form.value.unitPrice != null) {
    form.value.totalAmount = form.value.qty * form.value.unitPrice
    if (form.value.lastUnitPrice && form.value.lastUnitPrice > 0) {
      form.value.increaseAmount = form.value.unitPrice - form.value.lastUnitPrice
      form.value.increaseRate = (form.value.increaseAmount / form.value.lastUnitPrice) * 100
    }
  }
}

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
// ===== v5.71 打印请购单（按合同号聚合全部物料行，可直接发给供应商） =====
let supplierPrintCache = null
async function printOrder(row) {
  try {
    // 该合同号下全部物料行（批量采购一张单多个物料）
    const res = await api.get('/raw-material-purchase/search', { params: { orderNo: row.orderNo, page: 0, size: 200 } })
    const rows = res.content || []
    if (!rows.length) { ElMessage.warning('未找到该合同号的明细'); return }
    // 供应商档案（联系人/电话/付款条件）——懒加载一次
    if (!supplierPrintCache) {
      try { supplierPrintCache = await api.get('/supplier', { params: { pageSize: 500 } }) } catch { supplierPrintCache = [] }
    }
    const sup = (Array.isArray(supplierPrintCache) ? supplierPrintCache : (supplierPrintCache.rows || []))
      .find(x => x.id === row.supplierId) || {}
    // 字典码翻译成中文（付款条件/付款方式）
    let dictMap = {}
    try {
      const dicts = await api.get('/dict')
      ;(dicts || []).forEach(d => { if (d.type === 'payment_terms' || d.type === 'payment_method') dictMap[d.value] = d.label })
    } catch {}
    if (sup.paymentTerms && dictMap[sup.paymentTerms]) sup.paymentTerms = dictMap[sup.paymentTerms]
    if (sup.paymentMethod && dictMap[sup.paymentMethod]) sup.paymentMethod = dictMap[sup.paymentMethod]
    // v5.71.3 打印优先用单据收货仓库直接出单；仅历史单据未选仓库时弹窗补选
    if (!whList.value.length) await fetchWarehouses()
    const list = whList.value.filter(w => w.enabled !== false)
    if (!list.length) { ElMessage.warning('未找到仓库档案，请先在 基础数据→仓库 维护'); return }
    if (row.warehouseId) {
      const wh = list.find(w => String(w.id) === String(row.warehouseId))
      if (wh) {
        if (!wh.address || !wh.address.trim() || !wh.contactPerson || !wh.contactPerson.trim()) {
          ElMessage.warning(`收货仓库「${wh.name}」档案未维护收货地址或收货联系人，请先在 基础数据→仓库 中维护后再打印`)
          return
        }
        printPurchaseOrder(rows, {
          kind: 'RAW',
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
    kind: 'RAW',
    showPrice: hasPerm('purchase:price'),
    supplier: sup || {},
    warehouseName: wh.name,
    receiver: { address: wh.address, contact: wh.contactPerson, phone: wh.contactPhone }
  })
}

function onUploaded() { ElMessage.success('合同已上传'); reload() }

async function save() {
  saving.value = true
  try {
    if (form.value.id) {
      await api.put(`/raw-material-purchase/${form.value.id}`, form.value)
    } else {
      // v5.0：新建走批量接口（一张单据一个供应商多个物料，共享合同号）
      const validItems = batchItems.value.filter(r => r.materialCode)
      if (!validItems.length) { ElMessage.warning('请至少添加一个物料'); return }
      if (!form.value.supplierId) { ElMessage.warning('请选择供应商'); return }
      if (!form.value.warehouseId) { ElMessage.warning('请选择收货仓库（打印请购单按此带出收货地址）'); return }
      for (const r of validItems) {
        if (!r.qty || r.qty <= 0) { ElMessage.warning(`请填写「${r.materialName || r.materialCode}」的采购数量`); return }
      }
      const res = await api.post('/raw-material-purchase/batch', {
        supplierId: form.value.supplierId,
        supplierName: form.value.supplierName,
        warehouseId: form.value.warehouseId,
        taxRate: form.value.taxRate ?? taxRate.value,
        purchaseDate: form.value.purchaseDate || undefined,
        remark: form.value.remark || undefined,
        items: validItems
      })
      const orderNo = res?.[0]?.orderNo
      ElMessage.success(orderNo ? `采购单已创建：合同号 ${orderNo}，共 ${res.length} 种物料` : '保存成功')
    }
    visible.value = false
    reload()
  } catch (e) { ElMessage.error(e.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

async function audit(row) {
  await api.post(`/raw-material-purchase/${row.id}/audit`)
  ElMessage.success('审核成功')
  fetchDraft()
}

async function reverseAudit(row) {
  await ElMessageBox.confirm('反审核后单据回到开立状态，确认？')
  await api.post(`/raw-material-purchase/${row.id}/reverse-audit`)
  ElMessage.success('反审核成功')
  fetchHistory()
}

onMounted(async () => {
  loadTaxRate(api).then(r => { taxRate.value = r })
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  fetchMaterials()
  fetchDraft()
  fetchWarehouses()
})
</script>

<style scoped>
.page-container { width: 100%; }
.full-width { width: 100%; }
.upload-area { display: flex; align-items: center; gap: 8px; }
.upload-hint { color: #10b981; font-size: 12px; font-weight: 500; }
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
