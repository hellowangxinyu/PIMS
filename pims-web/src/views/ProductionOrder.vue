<template>
  <div class="page-container">
    <div class="page-header">
      <h2>生产订单（配方表）</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">新建生产订单</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="type-tabs">
        <button class="type-tab" :class="{ active: activeTab === 'ACTIVE' }" @click="activeTab = 'ACTIVE'">未完工 <span class="tab-badge">{{ activeCount }}</span></button>
        <button class="type-tab" :class="{ active: activeTab === 'COMPLETED' }" @click="activeTab = 'COMPLETED'">已完工 <span class="tab-badge">{{ completedCount }}</span></button>
      </div>
      <p-table :data="pagedRows" stripe border style="width:100%" :row-class-name="rowClass">
        <el-table-column prop="orderNo" label="订单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编码" min-width="120" />
        <el-table-column prop="salesOrderNo" label="来源" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.salesOrderNo ? '销售单 ' + row.salesOrderNo : '备料生产' }}</template>
        </el-table-column>
        <el-table-column prop="batchQty" label="批量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.displayStatus || row.status)" size="small">{{ statusLabel(row.displayStatus || row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 已完工 Tab：投入产出比（产出÷投入，<95% 为异常，整行标红） -->
        <el-table-column v-if="activeTab === 'COMPLETED'" label="投入量" width="95" align="right">
          <template #default="{ row }">{{ fmtNum(row.inputQty) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'COMPLETED'" label="产出量" width="95" align="right">
          <template #default="{ row }">{{ fmtNum(row.outputQty) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'COMPLETED'" label="投入产出比" width="115" align="center">
          <template #default="{ row }">
            <span v-if="row.ioStatus === 'UNKNOWN'">—</span>
            <el-tag v-else :type="row.ioStatus === 'ABNORMAL' ? 'danger' : 'success'" size="small">{{ row.ioRatio }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column prop="printCount" label="打印次数" width="90" align="center" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="380" align="center">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="viewDetail(row)">配方</button>
            <!-- 未出库：打印即出库（先进先出自动分配批次）；已出库：纯打印 -->
            <button v-if="!isOutbound(row) && (row.status === 'CONFIRMED' || row.status === 'SCHEDULED')" class="op-btn op-btn-success" @click="printAndOutbound(row)">打印并出库</button>
            <button v-if="isOutbound(row) && row.status !== 'DRAFT'" class="op-btn op-btn-primary" @click="printOrder(row)">打印</button>
            <button v-if="row.status !== 'DRAFT'" class="op-btn op-btn-primary" @click="viewOutbounds(row)">出库记录</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="openEdit(row)">编辑</button>
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-success" @click="confirm(row)">确认并出库</button>
            <button v-if="row.status === 'CONFIRMED' || row.status === 'SCHEDULED'" class="op-btn op-btn-warn" @click="complete(row)">完工</button>
            <button v-if="activeTab === 'COMPLETED' && row.ioStatus === 'ABNORMAL'" class="op-btn op-btn-danger" @click="handleAbnormal(row)">处理异常</button>
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
          :total="filteredList.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog :title="editId ? '编辑生产订单' : '新建生产订单'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <!-- 参照配方（仅新建时） -->
        <el-form-item v-if="!editId" label="配方类型">
          <el-radio-group v-model="recipeTypeFilter" @change="onRecipeTypeChange">
            <el-radio-button value="TINTING">制漆配方（调色）</el-radio-button>
            <el-radio-button value="GRINDING">制浆配方（研磨）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- v5.27：生产来源必选——销售订单 或 备料生产（自产，不挂销售单） -->
        <el-form-item v-if="!editId" label="生产来源" required>
          <el-radio-group v-model="form.sourceType" @change="onSourceTypeChange">
            <el-radio-button value="SALES">销售订单</el-radio-button>
            <el-radio-button value="STOCK">备料生产</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- v5.27：来源销售订单（选择后自动带出产品；备料生产不显示） -->
        <el-form-item v-if="!editId && form.sourceType === 'SALES'" label="来源销售订单" required>
          <el-select v-model="form.salesOrderNo" filterable clearable placeholder="选择销售订单（自动带出产品）" style="width:100%" @change="onSalesOrderChange">
            <el-option v-for="so in filteredSalesOrders" :key="so.orderNo" :label="so.orderNo + ' ' + (so.customerName || '') + '（' + (so.contractNo || '-') + '）'" :value="so.orderNo" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editId" label="参照配方">
          <el-select v-model="form.recipeVersionId" filterable clearable placeholder="选择已发布的配方版本" style="width:100%" @change="onRecipeChange">
            <el-option v-for="r in filteredRecipes" :key="r.versionId" :label="r.recipeNo + ' ' + r.productName + ' (' + r.versionNo + ')'" :value="r.versionId" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称" required>
          <el-input v-model="form.productName" placeholder="如：白色外墙乳胶漆" />
        </el-form-item>
        <el-form-item label="产品编码" required>
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
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">配方明细（原料清单）<span v-if="recalculating" style="margin-left:8px;color:#6366f1;font-size:12px">正在按比例重算...</span></el-divider>
        <div class="formula-toolbar">
          <el-button size="small" type="primary" @click="addItem">+ 添加原料</el-button>
          <!-- v5.72：油尾只能在生产订单添加，且仅限生产成品漆（C 类产品）的订单 -->
          <el-button v-if="isProductFinished" size="small" type="warning" @click="openTailDialog">+ 添加油尾</el-button>
          <span v-if="isProductFinished" class="form-tip" style="margin-left:4px">油尾：用油尾库的同体系成品漆当原料消化</span>
        </div>
        <p-table :data="form.items" border size="small" style="width:100%">
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <!-- v5.6：半成品为常备库存保留为一行，不可改选物料，仅可改用量 -->
              <template v-if="row.nodeType === 'SUB_RECIPE'">
                <el-tag size="small" type="warning" class="sub-tag">半成品</el-tag>
                <span class="sub-code">{{ row.materialCode || row.materialName }}</span>
              </template>
              <template v-else-if="row.nodeType === 'OIL_TAIL'">
                <el-tag size="small" type="danger" class="sub-tag">油尾</el-tag>
                <span class="sub-code">{{ row.materialCode || row.materialName }}</span>
              </template>
              <el-select v-else v-model="row.materialCode" filterable placeholder="搜索物料" style="width:100%" @change="c => onItemMatChange(row, c)">
                <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + (m.name||'')" :value="m.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="品名" min-width="120">
            <template #default="{ row }">
              <span :class="{ 'sub-name': row.nodeType === 'SUB_RECIPE' || row.nodeType === 'OIL_TAIL' }">{{ row.materialName }}</span>
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
    <!-- v5.72：添加油尾——油尾库有库存的成品（仅生产成品漆的订单） -->
    <el-dialog title="添加油尾" v-model="tailVisible" width="min(1100px, 96vw)">
      <el-form label-width="90px">
        <el-form-item label="油尾物料" required>
          <el-select v-model="tailForm.materialCode" filterable placeholder="选择油尾库的成品物料" style="width:100%">
            <el-option v-for="t in tailOptions" :key="t.materialCode" :label="t.materialCode + ' ' + (t.materialName||'') + '（' + (t.mainMaterial || '-') + '，库存 ' + t.qty + '）'" :value="t.materialCode" />
          </el-select>
          <div class="form-tip">只能选与订单产品「主材体系一致且编码或色系相同」的油尾，保存时后端校验</div>
        </el-form-item>
        <el-form-item label="用量(kg)" required>
          <el-input-number v-model="tailForm.qty" :min="0.001" :precision="3" :step="1" style="width:180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tailVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddTail">加入明细</el-button>
      </template>
    </el-dialog>

    <el-dialog title="配方明细" v-model="detailVisible"  width="min(1100px, 96vw)" destroy-on-close>
      <p style="margin:0 0 12px;font-weight:600">{{ detailOrder.productName }}（{{ detailOrder.orderNo }}）批量: {{ detailOrder.batchQty }} {{ detailOrder.unit }}</p>
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

    <!-- 出库记录明细弹窗（生产出库作为订单的记录明细，先进先出自动分配批次） -->
    <el-dialog :title="'出库记录 - ' + (outboundOrder?.orderNo || '')" v-model="outboundVisible" width="min(1100px, 96vw)" destroy-on-close>
      <p-table :data="outboundRows" border size="small">
        <el-table-column prop="docNo" label="出库单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" min-width="110" />
        <el-table-column prop="materialName" label="品名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" min-width="110" />
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column label="出库仓库" width="110">
          <template #default="{ row }">{{ whName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="出库库位" min-width="120">
          <template #default="{ row }">
            <span v-if="row.locationName">{{ row.zoneName ? row.zoneName + ' / ' : '' }}{{ row.locationName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column v-if="hasFinanceAmount" label="单价" width="90" align="right">
          <template #default="{ row }">￥{{ fmtMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column v-if="hasFinanceAmount" label="成本" width="100" align="right">
          <template #default="{ row }">￥{{ fmtMoney(row.cost) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
      </p-table>
      <p v-if="!outboundRows.length" class="text-muted" style="text-align:center;margin:16px 0">该订单尚未出库</p>
    </el-dialog>

    <!-- 出库前库存检查预览（确认后才自动出库） -->
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
      <p v-if="hasStockShortage" class="stock-warn">⚠ 有物料库存不足，无法出库，请先补货（出库将按先进先出自动分配批次）</p>
      <p v-else class="stock-ok">✓ 库存充足，确认后将按先进先出自动分配批次并扣减库存</p>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="hasStockShortage" :loading="printing" @click="confirmOutbound">确认出库</el-button>
      </template>
    </el-dialog>

    <!-- 异常订单处理弹窗 -->
    <el-dialog :title="'异常处理 — ' + (abnormalForm.orderNo || '')" v-model="abnormalVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="abnormalForm" label-width="90px">
        <el-form-item label="投出比">
          <el-tag type="danger" size="small">{{ abnormalForm.ioRatio }}%</el-tag>
          <span style="margin-left:8px;color:#64748b;font-size:12px">投入 {{ fmtNum(abnormalForm.inputQty) }} kg / 产出 {{ fmtNum(abnormalForm.outputQty) }} kg</span>
        </el-form-item>
        <el-form-item label="异常原因" required>
          <el-select v-model="abnormalForm.reason" filterable allow-create default-first-option placeholder="选择或输入异常原因" style="width:100%">
            <el-option label="投料过多" value="投料过多" />
            <el-option label="计量误差" value="计量误差" />
            <el-option label="挥发损耗" value="挥发损耗" />
            <el-option label="设备异常" value="设备异常" />
            <el-option label="原料问题" value="原料问题" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置措施">
          <el-input v-model="abnormalForm.measure" type="textarea" :rows="2" placeholder="如：补料/报废/记录存档/调整工艺" />
        </el-form-item>
        <el-form-item label="处置结果">
          <el-radio-group v-model="abnormalForm.status">
            <el-radio-button value="PROCESSING">处理中</el-radio-button>
            <el-radio-button value="CLOSED">已闭环</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="abnormalForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="abnormalVisible = false">取消</el-button>
        <el-button type="primary" :loading="abnormalSubmitting" @click="submitAbnormal">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtMoney as fmtMoneyBase } from '../utils/fmt'
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
const list = ref([])
const materials = ref([])
const warehouses = ref([])
const productMaterials = computed(() => materials.value.filter(m => m.category === 'C' || m.category === 'B'))
function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : (id || '-') }
// 有「查看金额」权限才显示单价/成本（生产人员不显示价格）
const hasFinanceAmount = computed(() => {
  try { return (JSON.parse(localStorage.getItem('user') || '{}').permissions || []).includes('finance:amount') } catch { return false }
})
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
const recipeBaseQty = ref(null) // 配方基准批量
const recalculating = ref(false)

function emptyForm() { return { productName: '', productCode: '', batchQty: 100, unit: 'kg', remark: '', items: [], recipeVersionId: null, salesOrderNo: '', sourceType: 'STOCK' } }

// v5.27：来源销售订单（下拉选已确认的销售订单，选择后自动带出产品）
const confirmedSalesOrders = ref([])
// 按配方类型筛选销售订单：制浆(GRINDING)只显示含半成品(B)的、制漆(TINTING)只显示含成品(C)的
const filteredSalesOrders = computed(() => {
  const wantB = recipeTypeFilter.value === 'GRINDING'
  return confirmedSalesOrders.value.filter(so => wantB ? so.hasB : so.hasC)
})
async function loadConfirmedSalesOrders() {
  try { confirmedSalesOrders.value = await api.get('/sales-order', { params: { status: 'CONFIRMED' } }) } catch {}
}
async function onSalesOrderChange(orderNo) {
  if (!orderNo) return
  const so = confirmedSalesOrders.value.find(s => s.orderNo === orderNo)
  if (!so) return
  try {
    const items = await api.get(`/sales-order/${so.id}/items`)
    // 按当前配方类型 + 物料 category 匹配：制浆(GRINDING)带出半成品(B)、制漆(TINTING)带出成品(C)
    // 注：半成品编码以 PJ 开头(首字符P)，必须按物料 category 判断，不能用编码首字符
    const wantCat = recipeTypeFilter.value === 'GRINDING' ? 'B' : 'C'
    const prod = items.find(it => {
      const m = materials.value.find(mm => mm.code === it.materialCode)
      return (m ? m.category : (it.materialCode || '').charAt(0)) === wantCat
    })
    if (!prod) { ElMessage.warning('该销售订单没有半成品/成品明细，无法自动带出产品，请手动填写'); return }
    form.value.productName = prod.materialName || ''
    form.value.productCode = prod.materialCode || ''
    form.value.batchQty = Number(prod.qty) || form.value.batchQty
    form.value.remark = (form.value.remark ? form.value.remark + '；' : '') + '来源销售订单 ' + orderNo
    // 自动匹配该产品的已发布配方（同配方类型），匹配到则按销售数量展开投料明细
    const matched = filteredRecipes.value.find(r => r.productCode === prod.materialCode)
    if (matched) {
      form.value.recipeVersionId = matched.versionId
      recipeBaseQty.value = Number(matched.batchQty) || 100
      await recalcItems()
      ElMessage.success(`已带出 ${prod.materialName || prod.materialCode}，批量 ${prod.qty} ${prod.unit || 'kg'}，已按配方展开投料明细`)
    } else {
      ElMessage.success(`已带出 ${prod.materialName || prod.materialCode}，批量 ${prod.qty} ${prod.unit || 'kg'}（未匹配到已发布配方，请手动选择参照配方）`)
    }
  } catch {}
}

// v5.27：生产来源必选——销售订单 或 备料生产；切换时清掉自动带出的内容
function onSourceTypeChange() {
  form.value.salesOrderNo = ''
  if (form.value.sourceType === 'STOCK') {
    // 备料生产：不挂销售单，产品/批量由手工选择配方或物料填写
    form.value.recipeVersionId = null
    form.value.items = []
  }
}
function onRecipeTypeChange() {
  form.value.recipeVersionId = null
  recipeBaseQty.value = null
  form.value.items = []
  // 切换配方类型后清空已选销售订单（下拉已按新类型过滤，避免残留无效选择）
  form.value.salesOrderNo = ''
}
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
// v5.27：状态展示（displayStatus 推导：SCHEDULED=已排产 FEED=已投料 INBOUND=已入库 SHIPPED=已发货）
// v6.4 状态色统一：全局 + 生产域局部
const statusType = (s) => globalStatusType(s, { COMPLETED: 'success', FEED: 'primary', SHIPPED: 'success', INBOUND: 'success' })
function statusLabel(s) {
  return { CONFIRMED: '已确认', COMPLETED: '已完工', SCHEDULED: '已排产', FEED: '已投料', INBOUND: '已入库', SHIPPED: '已发货', DRAFT: '草稿' }[s] || s
}

// 状态分组：未完工(草稿+已确认) / 已完工，默认只显示未完工
const activeTab = ref('ACTIVE')
const activeCount = computed(() => list.value.filter(r => r.status !== 'COMPLETED').length)
const completedCount = computed(() => list.value.filter(r => r.status === 'COMPLETED').length)
const filteredList = computed(() => activeTab.value === 'ACTIVE'
  ? list.value.filter(r => r.status !== 'COMPLETED')
  : list.value.filter(r => r.status === 'COMPLETED'))

async function fetch() {
  resetPage()
  try { list.value = await api.get('/production-order') } catch {} }

function openCreate() { editId.value = null; form.value = emptyForm(); visible.value = true }
async function openEdit(row) {
  editId.value = row.id
  try {
    const data = await api.get(`/production-order/${row.id}`)
    form.value = {
      productName: data.order.productName,
      productCode: data.order.productCode || '',
      batchQty: Number(data.order.batchQty),
      unit: data.order.unit || '',
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
    // 配方未绑定产品编码时明确提示，避免"编码没带出来"的困惑
    if (!r.productCode) {
      ElMessage.warning(`配方 ${r.recipeNo} 未绑定产品编码，请手动选择产品编码（或到配方管理中为该配方绑定产品物料）`)
    }
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

// v5.72：产品是否 C 类成品漆（只有成品漆订单可加油尾）
const isProductFinished = computed(() => {
  const m = materials.value.find(x => x.code === form.value.productCode)
  return m != null && m.category === 'C'
})
// v5.72：添加油尾弹窗——从油尾库存选择（/recipe/tailing-options 复用：油尾库有库存的 C 类成品）
const tailVisible = ref(false)
const tailOptions = ref([])
const tailForm = ref({ materialCode: '', qty: 1 })
async function openTailDialog() {
  if (!isProductFinished.value) { ElMessage.warning('油尾只能添加在生产成品漆（C 类产品）的订单中'); return }
  try { tailOptions.value = await api.get('/recipe/tailing-options') } catch { tailOptions.value = [] }
  tailForm.value = { materialCode: '', qty: 1 }
  tailVisible.value = true
}
function confirmAddTail() {
  const t = tailOptions.value.find(x => x.materialCode === tailForm.value.materialCode)
  if (!t) { ElMessage.warning('请选择油尾物料'); return }
  if (!tailForm.value.qty || tailForm.value.qty <= 0) { ElMessage.warning('请填写油尾用量'); return }
  if (tailForm.value.qty > Number(t.qty)) { ElMessage.warning(`油尾库存仅 ${t.qty} kg，不能超过`); return }
  if (form.value.items.some(i => i.nodeType === 'OIL_TAIL' && i.materialCode === t.materialCode)) {
    ElMessage.warning('该油尾物料已添加，请直接修改用量'); return
  }
  form.value.items.push({ materialCode: t.materialCode, materialName: t.materialName, spec: '', unit: 'kg', qty: tailForm.value.qty, nodeType: 'OIL_TAIL', remark: '油尾' })
  tailVisible.value = false
}

function addItem() { form.value.items.push({ materialCode: '', materialName: '', spec: '', unit: '', qty: 1 }) }
function onItemMatChange(row, code) {
  const m = materials.value.find(m => m.code === code)
  if (m) { row.materialName = m.name || ''; row.spec = m.spec || ''; row.unit = m.unit || '' }
}

async function submit() {
  // v5.27：生产来源必选——销售订单 或 备料生产（编辑不改来源，跳过）
  if (!editId.value) {
    if (!form.value.sourceType) { ElMessage.warning('请选择生产来源：销售订单 或 备料生产'); return }
    if (form.value.sourceType === 'SALES' && !form.value.salesOrderNo) { ElMessage.warning('请选择来源销售订单'); return }
  }
  if (!form.value.productName) { ElMessage.warning('请输入产品名称'); return }
  if (!form.value.productCode || !form.value.productCode.trim()) { ElMessage.warning('请填写产品编码（选择成品/半成品物料或输入编码）'); return }
  if (!form.value.batchQty || form.value.batchQty <= 0) { ElMessage.warning('请填写生产批量'); return }
  if (!form.value.items.length) { ElMessage.warning('请至少添加一项原料'); return }
  loading.value = true
  try {
    const payload = { ...form.value }
    if (payload.sourceType === 'STOCK') delete payload.salesOrderNo
    else if (!payload.salesOrderNo) delete payload.salesOrderNo
    if (editId.value) {
      await api.put(`/production-order/${editId.value}`, payload)
    } else {
      await api.post('/production-order', payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

// 确认订单 = 确认 + 自动出库（先弹库存检查，确认后按先进先出出库）
const stockFromConfirm = ref(false)
async function confirm(row) {
  if (printing.value) return
  try {
    await ElMessageBox.confirm(`确认生产订单 ${row.orderNo}？\n确认后将按先进先出自动分配批次出库并扣减库存。`, '确认并自动出库', { type: 'warning' })
  } catch { return }
  try {
    stockRows.value = await api.get(`/production-order/${row.id}/stock-check`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '库存检查失败')
    return
  }
  stockOrder.value = row
  stockFromConfirm.value = true
  stockDialogVisible.value = true
}

// v5.27：排产 / 取消排产
async function schedule(row) {
  try {
    await ElMessageBox.confirm(`排产生产订单 ${row.orderNo}？\n排产后状态为「已排产」。`, '排产', { type: 'warning' })
    await api.post(`/production-order/${row.id}/schedule`)
    ElMessage.success('已排产')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}
async function unschedule(row) {
  try {
    await ElMessageBox.confirm(`取消排产 ${row.orderNo}？`, '取消排产', { type: 'warning' })
    await api.post(`/production-order/${row.id}/unschedule`)
    ElMessage.success('已取消排产')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function complete(row) {
  try {
    await ElMessageBox.confirm(`确认订单 ${row.orderNo} 已完工？`, '完工', { type: 'info' })
    // v6.8：排产中按实际完结必须填原因（投出比异常将自动建异常订单记录）
    let reason = null
    if (row.status === 'SCHEDULED') {
      const { value } = await ElMessageBox.prompt(
        '该订单仍在排产/生产中，按实际完结必须填写原因（如：质检不合格客户让步、短量产出）：',
        '按实际完结原因', { inputValue: '按实际量完结' })
      reason = value
    }
    await api.post(`/production-order/${row.id}/complete`, null, { params: { reason: reason || undefined } })
    ElMessage.success('已完工')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close' && e?.message !== 'cancel') {} }
}

// 投入产出比相关数量格式化（kg，去尾零）
function fmtNum(v) {
  if (v === null || v === undefined) return '-'
  const n = Number(v)
  return Number.isInteger(n) ? n.toString() : n.toFixed(3).replace(/\.?0+$/, '')
}
// 异常行整行红色高亮（仅已完工 Tab 下 ioStatus=ABNORMAL）
function rowClass({ row }) {
  if (activeTab.value === 'COMPLETED' && row.ioStatus === 'ABNORMAL') return 'row-abnormal'
  return ''
}
// 异常订单处置弹窗
const abnormalVisible = ref(false)
const abnormalSubmitting = ref(false)
const abnormalForm = ref({ orderNo: '', ioRatio: null, inputQty: null, outputQty: null, reason: '', measure: '', status: 'PROCESSING', remark: '', handler: '' })
function curUserName() { try { const u = JSON.parse(localStorage.getItem('user') || '{}'); return u.realName || u.username || '' } catch { return '' } }
async function handleAbnormal(row) {
  abnormalForm.value = { orderNo: row.orderNo, ioRatio: row.ioRatio, inputQty: row.inputQty, outputQty: row.outputQty, reason: '', measure: '', status: 'PROCESSING', remark: '', handler: curUserName() }
  // 回填已有处置记录
  try {
    const ex = await api.get(`/abnormal-order/${row.orderNo}`)
    if (ex) {
      abnormalForm.value.reason = ex.reason || ''
      abnormalForm.value.measure = ex.measure || ''
      abnormalForm.value.status = ex.status || 'PROCESSING'
      abnormalForm.value.remark = ex.remark || ''
    }
  } catch {}
  abnormalVisible.value = true
}
async function submitAbnormal() {
  if (!abnormalForm.value.reason) { ElMessage.warning('请选择/填写异常原因'); return }
  abnormalSubmitting.value = true
  try {
    await api.post('/abnormal-order/handle', {
      orderNo: abnormalForm.value.orderNo,
      reason: abnormalForm.value.reason,
      measure: abnormalForm.value.measure,
      status: abnormalForm.value.status,
      remark: abnormalForm.value.remark,
      handler: abnormalForm.value.handler
    })
    ElMessage.success('已提交异常处理')
    abnormalVisible.value = false
    fetch()
  } catch (e) { /* api 已提示 */ }
  abnormalSubmitting.value = false
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`删除订单 ${row.orderNo}？`, '删除', { type: 'warning' })
    await api.delete(`/production-order/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function viewDetail(row) {
  detailOrder.value = row
  try { detailItems.value = await api.get(`/production-order/${row.id}/items`) } catch {}
  detailVisible.value = true
}

// ==================== 生产订单打印（v5.25，生产工单/投料单） ====================

const printing = ref(false)

function escHtml(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

// 已出库判断：displayStatus 推导 FEED(已投料)/INBOUND(已入库)/SHIPPED(已发货) 均代表已出库
function isOutbound(row) {
  const ds = row.displayStatus || row.status
  return ['FEED', 'INBOUND', 'SHIPPED'].includes(ds)
}

// 出库记录弹窗
const outboundVisible = ref(false)
const outboundOrder = ref(null)
const outboundRows = ref([])
async function viewOutbounds(row) {
  outboundOrder.value = row
  try { outboundRows.value = await api.get(`/production-order/${row.id}/outbounds`) } catch {}
  outboundVisible.value = true
}

function fmtMoney(v) { return v == null ? '-' : fmtMoneyBase(v) }   // v6.6 收口：千分位

// 打印并出库：先弹库存检查预览 → 用户确认后才自动出库（先进先出）→ 打印（含批次明细）
const stockDialogVisible = ref(false)
const stockRows = ref([])
const stockOrder = ref(null)
const hasStockShortage = computed(() => stockRows.value.some(r => !r.enough))

async function printAndOutbound(row) {
  if (printing.value) return
  try {
    stockRows.value = await api.get(`/production-order/${row.id}/stock-check`)
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
      await api.post(`/production-order/${row.id}/confirm`)
    }
    const outbounds = await api.post(`/production-order/${row.id}/auto-outbound`)
    ElMessage.success(stockFromConfirm.value
      ? `订单已确认并自动出库 ${outbounds.length} 行（先进先出）`
      : `已自动出库 ${outbounds.length} 行批次明细（先进先出）`)
    fetch()
    api.post('/print-count', { docType: 'PRODUCTION_ORDER', docNo: row.orderNo }).then(n => { row.printCount = n }).catch(() => {})
    const data = await api.get(`/production-order/${row.id}`)
    const processTpl = await loadProcessTpl(data.order)
    const win = window.open('', '_blank')
    if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
    win.document.write(buildProductionPrintHtml(data.order, data.items || [], outbounds, processTpl))
    win.document.close()
    win.focus()
    setTimeout(() => { win.print(); win.close() }, 200)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '出库失败，请检查库存是否充足')
  } finally { printing.value = false }
}

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

// 工艺 + 检测计划 + 包装要求 打印段（投料单带上配方树的所有要求）
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

// 生成生产订单打印 HTML（纯函数，便于测试与验证；车间投料单，显示品名+编码+用量，无价格）
function buildProductionPrintHtml(order, items, outbounds, processTpl) {
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
      + '<td>' + (isSub ? '<strong>' + escHtml(it.materialName || '-') + '</strong> <span class="sub-tag">半成品</span>' : escHtml(it.materialName || '-')) + '</td>'
      + '<td>' + escHtml(it.spec || '') + '</td>'
      + '<td class="num">' + escHtml(it.qty) + '</td>'
      + '<td class="center">' + escHtml(it.unit || 'kg') + '</td></tr>'
  }).join('')
  // 出库批次明细（生产出库作为订单的记录明细，先进先出自动分配；不显示单价/成本——车间生产人员不需要看价格）
  let outboundHtml = ''
  if (outbounds && outbounds.length) {
    const obRows = outbounds.map((ob, i) => '<tr>'
      + '<td class="center">' + (i + 1) + '</td>'
      + '<td>' + escHtml(ob.materialCode || '-') + '</td>'
      + '<td>' + escHtml(ob.materialName || '-') + '</td>'
      + '<td>' + escHtml(ob.batchNo || '-') + '</td>'
      + '<td class="num">' + escHtml(ob.qty) + '</td>'
      + '<td class="center">' + escHtml(ob.unit || 'kg') + '</td>'
      + '<td>' + escHtml(whName(ob.warehouseId)) + '</td>'
      + '<td>' + escHtml((ob.zoneName ? ob.zoneName + '/' : '') + (ob.locationName || '-')) + '</td></tr>').join('')
    outboundHtml = '<div class="outbound-title">出库批次明细（先进先出自动分配，按库位拆行）</div>'
      + '<table class="main"><thead><tr><th style="width:40px">序号</th><th>物料编码</th><th>品名</th><th>批号</th><th>数量</th><th style="width:45px">单位</th><th>出库仓库</th><th>库位</th></tr></thead><tbody>'
      + obRows
      + '</tbody></table>'
  }
  return [
    '<!DOCTYPE html><html><head><meta charset="utf-8"><title>生产订单 ' + escHtml(order.orderNo) + '</title><style>',
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
    '<h1>生产订单·投料单</h1>',
    '<div class="sub">Production Order　·　订单号：' + escHtml(order.orderNo) + '　·　打印日期：' + nowStr + '</div>',
    '<table class="info">',
    '<tr><td class="k">订单号</td><td>' + escHtml(order.orderNo) + '</td><td class="k">产品名称</td><td>' + escHtml(order.productName || '-') + '</td></tr>',
    '<tr><td class="k">产品编码</td><td>' + escHtml(order.productCode || '-') + '</td><td class="k">配方编号</td><td>' + escHtml(recipeNo) + '</td></tr>',
    '<tr><td class="k">生产批量</td><td>' + escHtml(order.batchQty) + ' ' + escHtml(order.unit || 'kg') + '</td><td class="k">状态</td><td>' + escHtml(statusText) + '</td></tr>',
    '<tr><td class="k">制单人</td><td>' + escHtml(order.createdBy || '-') + '</td><td class="k">创建时间</td><td>' + escHtml(order.createTime ? String(order.createTime).replace('T', ' ').substring(0, 16) : '-') + '</td></tr>',
    '<tr><td class="k">备注</td><td colspan="3">' + escHtml(order.remark || '-') + '</td></tr>',
    '</table>',
    '<table class="main"><thead><tr><th style="width:50px">序号</th><th>物料编码</th><th>品名</th><th>规格</th><th>用量</th><th style="width:70px">单位</th></tr></thead><tbody>',
    bodyRows,
    '</tbody></table>',
    buildProcessHtml(processTpl, items),
    outboundHtml,
    '<div class="sign"><span>投料人</span><span>车间主管</span><span>日期</span></div>',
    '</body></html>'
  ].join('')
}

// 打开打印窗口（仿配方/质检打印：新窗口渲染 + window.print + 自动关闭）
async function printOrder(row) {
  if (printing.value) return
  printing.value = true
  try {
    // v5.26：记录打印次数（失败不阻断打印）
    api.post('/print-count', { docType: 'PRODUCTION_ORDER', docNo: row.orderNo })
      .then(n => { row.printCount = n }).catch(() => {})
    const data = await api.get(`/production-order/${row.id}`)
    // 已出库的订单打印时附上出库批次明细（生产出库作为记录明细）
    let outbounds = []
    try { outbounds = await api.get(`/production-order/${row.id}/outbounds`) } catch {}
    const processTpl = await loadProcessTpl(data.order)
    const win = window.open('', '_blank')
    if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
    win.document.write(buildProductionPrintHtml(data.order, data.items || [], outbounds, processTpl))
    win.document.close()
    win.focus()
    setTimeout(() => { win.print(); win.close() }, 200)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载订单数据失败')
  } finally { printing.value = false }
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

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(filteredList)

onMounted(async () => {
  try {
    // v5.72.1：物料默认分页只有 268 条，C 类成品不全（isProductFinished 判断失效、下拉搜不到）——拉全量
    const mats = await api.get('/material', { params: { enabled: true, page: 1, pageSize: 1000 } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name, spec: m.spec, unit: m.unit, category: m.category }))
  } catch {}
  try {
    releasedRecipes.value = await api.get('/recipe/released')
  } catch {}
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  // v5.27：来源销售订单下拉（已确认的销售订单）
  loadConfirmedSalesOrders()
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
.type-tab.active { background: #1d4ed8; color: #fff; border-color: #1d4ed8; }
.tab-badge { display: inline-block; min-width: 18px; padding: 0 5px; margin-left: 4px; font-size: 11px; line-height: 16px; border-radius: 9px; background: rgba(0,0,0,0.08); color: inherit; }
.type-tab.active .tab-badge { background: rgba(255,255,255,0.25); }
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
/* 异常订单整行红色高亮（已完工 Tab，投出比<95%） */
:deep(.el-table .row-abnormal > td) { background: #fef2f2 !important; }
:deep(.el-table .row-abnormal:hover > td) { background: #fee2e2 !important; }
</style>
