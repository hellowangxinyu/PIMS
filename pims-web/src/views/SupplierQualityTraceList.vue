<template>
  <div class="page-container">
    <div class="page-header">
      <h2>质量追溯（供应商）</h2>
      <div class="header-actions">
        <el-button @click="openTemplates" v-if="hasPerm('strace:write')">函件模板</el-button>
        <el-button type="primary" @click="openCreate" v-if="hasPerm('strace:write')">发起追溯</el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="status-tabs">
        <button class="status-tab" :class="{ active: statusTab === 'PROCESSING' }" @click="statusTab = 'PROCESSING'">追溯中 <span class="tab-badge">{{ processingCount }}</span></button>
        <button class="status-tab" :class="{ active: statusTab === 'RESOLVED' }" @click="statusTab = 'RESOLVED'">已完结 <span class="tab-badge">{{ resolvedCount }}</span></button>
      </div>
      <div class="filter-bar">
        <el-select v-model="filterSupplierId" filterable clearable placeholder="按供应商筛选" style="width:200px">
          <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </div>
      <p-table :data="filteredRows" stripe border highlight-current-row @header-dragend="onHeaderDragend">
        <el-table-column prop="traceNo" label="单号" :width="cw('单号') || 110" />
        <el-table-column label="供应商" :width="cw('供应商') || undefined" min-width="150" show-overflow-tooltip>
          <template #default="{row}">{{ row.supplierName }}</template>
        </el-table-column>
        <el-table-column prop="purchaseOrderNo" label="采购单号" :width="cw('采购单号') || 120" show-overflow-tooltip />
        <el-table-column label="物料" :width="cw('物料') || undefined" min-width="170" show-overflow-tooltip>
          <template #default="{row}">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批号" :width="cw('批号') || 100" />
        <el-table-column prop="category" label="问题类型" :width="cw('问题类型') || 95" />
        <el-table-column prop="createdBy" label="制单人" :width="cw('制单人') || 85" />
        <el-table-column label="损失金额" :width="cw('损失金额') || 95" align="right">
          <template #default="{row}">{{ row.lossAmount != null ? Number(row.lossAmount).toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column label="处理结果" :width="cw('处理结果') || 130" show-overflow-tooltip>
          <template #default="{row}">
            <template v-if="row.resultType">{{ row.resultType }}<span v-if="row.compensationAmount != null && Number(row.compensationAmount) > 0">（¥{{ Number(row.compensationAmount).toFixed(2) }}）</span></template>
            <template v-else>—</template>
          </template>
        </el-table-column>
        <el-table-column label="状态" :width="cw('状态') || 80" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.status === 'PROCESSING' ? 'danger' : 'success'">
              {{ row.status === 'PROCESSING' ? '处理中' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="305" align="center" v-if="hasPerm('strace:write') || hasPerm('strace:read')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" v-if="row.status === 'PROCESSING' && hasPerm('strace:write')" @click="openEdit(row)">编辑</button>
            <button class="op-btn op-btn-success" v-if="row.status === 'PROCESSING' && hasPerm('strace:write')" @click="openResolve(row)">处理完毕</button>
            <button class="op-btn op-btn-warn" @click="openPrint(row)">打印函件</button>
            <button class="op-btn" @click="openTrace(row)">追溯</button>
            <button class="op-btn" @click="openDetail(row)">详情</button>
            <button class="op-btn op-btn-danger" v-if="row.status === 'PROCESSING' && hasPerm('strace:write')" @click="delRow(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- 登记/编辑：选批次 → 自动带出采购入库信息 → 品控补充质量问题与损失 -->
    <el-dialog :title="form.id ? '编辑追溯单 ' + form.traceNo : '发起质量追溯'" v-model="formVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item label="追溯批次" required>
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <el-button @click="openBatchPicker">选择批次</el-button>
            <template v-if="form.batchNo">
              <el-tag type="info">{{ form.materialCode }} {{ form.materialName }}</el-tag>
              <el-tag type="danger">批号：{{ form.batchNo }}</el-tag>
            </template>
            <span v-else class="form-tip">必须精确到批号——选中后系统自动带出原始采购入库信息</span>
          </div>
        </el-form-item>

        <div v-if="form.batchNo" class="brought-box">
          <div class="brought-title">系统带出的原始采购入库信息（快照存单，无需手填）</div>
          <el-alert v-if="!form.supplierId" type="warning" :closable="false" style="margin-bottom:8px"
            :title="form.purchaseOrderNo
              ? `已带出单据 ${form.purchaseOrderNo}，但该单未登记供应商（委外订单开单时未选代工厂）——请在下方手动选择供应商`
              : '该批次反查不到采购/委外单据，请在下方手动选择供应商'" />
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item :label="form.orderCategory === 'OUTSOURCE' ? '委外单号' : '采购单号'">{{ form.purchaseOrderNo || '—' }}</el-descriptions-item>
            <el-descriptions-item :label="form.orderCategory === 'OUTSOURCE' ? '入库日期' : '到货日期'">{{ fmtDate(form.arrivalDate) }}</el-descriptions-item>
            <el-descriptions-item label="入库数量">{{ form.purchaseQty != null ? form.purchaseQty : '—' }}</el-descriptions-item>
            <el-descriptions-item :label="form.orderCategory === 'OUTSOURCE' ? '加工费单价' : '采购单价'">{{ form.purchaseUnitPrice != null ? '¥' + Number(form.purchaseUnitPrice).toFixed(2) : '—' }}</el-descriptions-item>
            <el-descriptions-item :label="form.orderCategory === 'OUTSOURCE' ? '加工费金额' : '采购金额'">{{ form.purchaseAmount != null ? '¥' + Number(form.purchaseAmount).toFixed(2) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="质检单号">{{ form.qcInspectionNo || '—' }}
              <el-tag v-if="form.qcStatus" size="small" :type="qcStatusMap[form.qcStatus]?.t || 'info'" style="margin-left:4px">{{ qcStatusMap[form.qcStatus]?.l || form.qcStatus }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-form-item label="供应商" required style="margin-top:10px">
          <el-select v-model="form.supplierId" filterable placeholder="自动带出；反查不到时手动选择" style="width:100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="问题类型" required>
              <el-select v-model="form.category" style="width:100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发现日期">
              <el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="损失金额">
              <el-input-number v-model="form.lossAmount" :min="0" :precision="2" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="问题描述" required>
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="质量问题的具体表现、影响范围、检验情况等" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存并发函</el-button>
      </template>
    </el-dialog>

    <!-- 选择批次（必须精确到批号） -->
    <el-dialog title="选择批次（按物料编码 / 批号 / 品名搜索）" v-model="batchVisible" width="min(860px, 94vw)" destroy-on-close>
      <div style="display:flex;gap:8px;margin-bottom:10px">
        <el-input v-model="batchKeyword" placeholder="物料编码 / 批号 / 品名（含规格）" style="width:300px" clearable @keyup.enter="searchBatches" />
        <el-button type="primary" @click="searchBatches">搜索</el-button>
      </div>
      <p-table :data="batchRows" stripe border size="small" highlight-current-row @current-change="r => batchSelected = r" empty-text="输入关键字搜索库存台账批次">
        <el-table-column prop="materialCode" label="物料编码" width="120" />
        <el-table-column prop="materialName" label="物料名称" :width="cw('物料名称') || undefined" min-width="160" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批号" width="110" />
        <el-table-column label="数量" width="90" align="right">
          <template #default="{row}">{{ row.qty }}{{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="单价" width="90" align="right">
          <template #default="{row}">{{ row.unitPrice != null ? Number(row.unitPrice).toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column label="入库日期" width="100">
          <template #default="{row}">{{ fmtDate(row.inboundDate) }}</template>
        </el-table-column>
        <el-table-column label="质检" width="90">
          <template #default="{row}">
            <el-tag v-if="row.qcStatus" size="small" :type="qcStatusMap[row.qcStatus]?.t || 'info'">{{ qcStatusMap[row.qcStatus]?.l || row.qcStatus }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </p-table>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!batchSelected" @click="confirmBatch">选定该批次</el-button>
      </template>
    </el-dialog>

    <!-- 处理完毕（采购）：必须有处理结果 -->
    <el-dialog :title="'处理完毕 ' + resolveForm.traceNo" v-model="resolveVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item label="处理结果" required>
          <el-select v-model="resolveForm.resultType" style="width:100%">
            <el-option v-for="r in resultTypes" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="赔付金额" :required="resolveForm.resultType === '赔款'">
          <el-input-number v-model="resolveForm.compensationAmount" :min="0" :precision="2" :controls="false" style="width:100%"
            :placeholder="resolveForm.resultType === '赔款' ? '赔款必填' : '折让/赔付金额（如适用）'" />
        </el-form-item>
        <el-form-item label="处理说明" required>
          <el-input v-model="resolveForm.resultRemark" type="textarea" :rows="4" placeholder="与供应商沟通的过程、达成的协议、执行方式等" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="处理人">
              <el-input v-model="resolveForm.handler" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="处理日期">
              <el-date-picker v-model="resolveForm.resolveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="form-tip" style="margin:0 0 8px 90px">处理完毕后单据永久留存，不可编辑、不可删除。</div>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" @click="doResolve" :loading="saving">确认处理完毕</el-button>
      </template>
    </el-dialog>

    <!-- 打印损失沟通函：选模板 → 预览 → 打印 -->
    <el-dialog :title="'打印损失沟通函 ' + printRow?.traceNo" v-model="printVisible" width="min(860px, 94vw)" destroy-on-close @opened="renderPreview">
      <div style="display:flex;gap:8px;align-items:center;margin-bottom:10px">
        <span style="font-size:13px">函件模板：</span>
        <el-select v-model="printTplId" style="width:260px" @change="renderPreview">
          <el-option v-for="t in templates" :key="t.id" :label="t.name + (t.isDefault ? '（默认）' : '')" :value="t.id" />
        </el-select>
        <el-button type="primary" @click="doPrint">打印</el-button>
        <span class="form-tip">已打印 {{ printRow?.printCount || 0 }} 次</span>
      </div>
      <iframe ref="printFrame" style="width:100%;height:62vh;border:1px solid #dcdfe6;border-radius:4px" />
    </el-dialog>

    <!-- 函件模板管理 -->
    <el-dialog title="损失沟通函模板" v-model="tplListVisible" width="min(1100px, 96vw)">
      <el-table :data="templates" stripe border size="small">
        <el-table-column prop="name" label="模板名称" :width="cw('模板名称') || undefined" min-width="150">
          <template #default="{row}">{{ row.name }} <el-tag v-if="row.isDefault" size="small" type="success" style="margin-left:4px">默认</el-tag></template>
        </el-table-column>
        <el-table-column label="正文预览" :width="cw('正文预览') || undefined" min-width="200" show-overflow-tooltip>
          <template #default="{row}">{{ (row.bodyText || '').slice(0, 60) }}…</template>
        </el-table-column>
        <el-table-column label="操作" width="235" align="center">
          <template #default="{row}">
            <button class="op-btn" @click="openTplPreview(row)">预览</button>
            <button class="op-btn op-btn-primary" @click="openTplEdit(row)">编辑</button>
            <button class="op-btn op-btn-success" v-if="!row.isDefault" @click="setTplDefault(row)">设默认</button>
            <button class="op-btn op-btn-danger" @click="delTpl(row)">删除</button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="tplListVisible = false">关闭</el-button>
        <el-button type="primary" @click="openTplEdit(null)">新增模板</el-button>
      </template>
    </el-dialog>

    <!-- 模板编辑（四段正文 + 占位符插入） -->
    <el-dialog :title="tplForm.id ? '编辑模板：' + tplForm.name : '新增函件模板'" v-model="tplFormVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item label="模板名称" required>
          <el-input v-model="tplForm.name" />
        </el-form-item>
        <el-form-item label="开头语">
          <el-input v-model="tplForm.openingText" type="textarea" :rows="2" @focus="lastFocus = 'openingText'" />
        </el-form-item>
        <el-form-item label="问题与损失" required>
          <el-input v-model="tplForm.bodyText" type="textarea" :rows="5" @focus="lastFocus = 'bodyText'" />
        </el-form-item>
        <el-form-item label="处理要求">
          <el-input v-model="tplForm.requireText" type="textarea" :rows="3" @focus="lastFocus = 'requireText'" />
        </el-form-item>
        <el-form-item label="结尾语">
          <el-input v-model="tplForm.closingText" type="textarea" :rows="2" @focus="lastFocus = 'closingText'" />
        </el-form-item>
        <el-form-item label="占位符">
          <div class="ph-list">
            <el-tag v-for="p in placeholders" :key="p[0]" size="small" class="ph-tag" @click="insertPh(p[0])">{{ p[1] }}</el-tag>
          </div>
          <div class="form-tip">点击插入到最近聚焦的段落；函件抬头、批次采购信息表与落款由系统固定渲染，此处只控制措辞。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplFormVisible = false">取消</el-button>
        <el-button @click="openTplPreview(tplForm)">预览</el-button>
        <el-button type="primary" @click="saveTpl" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板预览：示例数据填充渲染，看版式与措辞效果 -->
    <el-dialog title="函件预览（示例数据）" v-model="previewVisible" width="min(860px, 94vw)">
      <div class="form-tip" style="margin-bottom:8px">预览使用示例数据渲染版式；正式打印时以追溯单实际数据填充占位符。</div>
      <iframe ref="previewFrameRef" :srcdoc="previewHtml" style="width:100%;height:68vh;border:1px solid #dcdfe6;border-radius:4px" />
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="printPreview">打印预览页</el-button>
      </template>
    </el-dialog>

    <!-- 批次追溯流水 -->
    <el-drawer v-model="traceVisible" :title="'批次追溯 ' + traceRow?.materialCode + ' / ' + traceRow?.batchNo" size="60%">
      <el-table :data="traceMovements" stripe border size="small">
        <el-table-column label="时间" width="150">
          <template #default="{row}">{{ fmtTs(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="业务类型" width="100">
          <template #default="{row}">{{ docTypeMap[row.docType] || row.docType }}</template>
        </el-table-column>
        <el-table-column prop="docNo" label="单据号" :width="cw('单据号') || undefined" min-width="130" show-overflow-tooltip />
        <el-table-column label="方向" width="70" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.direction === 'IN' ? 'success' : 'danger'">{{ row.direction === 'IN' ? '入库' : '出库' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column prop="remark" label="备注" :width="cw('备注') || undefined" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-drawer>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="'追溯单详情 ' + detailRow?.traceNo" size="50%">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="供应商" :span="2">{{ detailRow?.supplierName }}</el-descriptions-item>
        <el-descriptions-item :label="detailRow?.orderCategory === 'OUTSOURCE' ? '委外单号' : '采购单号'">{{ detailRow?.purchaseOrderNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="detailRow?.orderCategory === 'OUTSOURCE' ? '入库日期' : '到货日期'">{{ fmtDate(detailRow?.arrivalDate) }}</el-descriptions-item>
        <el-descriptions-item label="物料" :span="2">{{ detailRow?.materialCode }} {{ detailRow?.materialName }}</el-descriptions-item>
        <el-descriptions-item label="批号">{{ detailRow?.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="入库数量">{{ detailRow?.purchaseQty ?? '—' }}</el-descriptions-item>
        <el-descriptions-item :label="detailRow?.orderCategory === 'OUTSOURCE' ? '加工费单价' : '采购单价'">{{ detailRow?.purchaseUnitPrice != null ? '¥' + Number(detailRow.purchaseUnitPrice).toFixed(2) : '—' }}</el-descriptions-item>
        <el-descriptions-item :label="detailRow?.orderCategory === 'OUTSOURCE' ? '加工费金额' : '采购金额'">{{ detailRow?.purchaseAmount != null ? '¥' + Number(detailRow.purchaseAmount).toFixed(2) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="质检单号">{{ detailRow?.qcInspectionNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="质检状态">
          <el-tag v-if="detailRow?.qcStatus" size="small" :type="qcStatusMap[detailRow.qcStatus]?.t || 'info'">{{ qcStatusMap[detailRow.qcStatus]?.l || detailRow.qcStatus }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="问题类型">{{ detailRow?.category || '—' }}</el-descriptions-item>
        <el-descriptions-item label="发现日期">{{ fmtDate(detailRow?.issueDate) }}</el-descriptions-item>
        <el-descriptions-item label="问题描述" :span="2">{{ detailRow?.description }}</el-descriptions-item>
        <el-descriptions-item label="损失金额">{{ detailRow?.lossAmount != null ? '¥' + Number(detailRow.lossAmount).toFixed(2) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="detailRow?.status === 'PROCESSING' ? 'danger' : 'success'">{{ detailRow?.status === 'PROCESSING' ? '处理中' : '已处理' }}</el-tag>
        </el-descriptions-item>
        <template v-if="detailRow?.status === 'RESOLVED'">
          <el-descriptions-item label="处理结果">{{ detailRow.resultType }}</el-descriptions-item>
          <el-descriptions-item label="赔付金额">{{ detailRow.compensationAmount != null ? '¥' + Number(detailRow.compensationAmount).toFixed(2) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理说明" :span="2">{{ detailRow.resultRemark }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detailRow.handler || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理日期">{{ fmtDate(detailRow?.resolveDate) }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="备注" :span="2">{{ detailRow?.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ fmtTs(detailRow?.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="打印次数">{{ detailRow?.printCount || 0 }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'
import { buildLossLetterHtml } from '../utils/lossLetterPrint'

const { cw, onHeaderDragend } = useColumnResize('supplier_quality_trace')

const list = ref([])
const suppliers = ref([])
const templates = ref([])
const perms = ref([])
const saving = ref(false)

const filterSupplierId = ref(null)
// v5.59.3 双 tab：追溯中 / 已完结（计数随供应商筛选联动），列表前端过滤
const statusTab = ref('PROCESSING')
const supervisedList = computed(() => filterSupplierId.value ? list.value.filter(t => t.supplierId === filterSupplierId.value) : list.value)
const processingCount = computed(() => supervisedList.value.filter(t => t.status === 'PROCESSING').length)
const resolvedCount = computed(() => supervisedList.value.filter(t => t.status === 'RESOLVED').length)
const filteredRows = computed(() => supervisedList.value.filter(t => t.status === statusTab.value))

// v5.59.1 问题类型/处理结果类型从数据字典维护（系统设置→数据字典），字典为空时兜底默认项
const DEFAULT_CATEGORIES = ['色差', '性能不达标', '结块沉淀', '包装破损', '杂质超标', '批次不稳', '其他']
const DEFAULT_RESULT_TYPES = ['协商折让', '赔款', '补货', '换货', '供应商拒绝赔付', '免赔', '其他']
const categories = ref([...DEFAULT_CATEGORIES])
const resultTypes = ref([...DEFAULT_RESULT_TYPES])

async function loadDicts() {
  try {
    const cats = await api.get('/dict', { params: { type: 'strace_category' } })
    if (cats.length) categories.value = cats.map(c => c.label || c.value)
    const rts = await api.get('/dict', { params: { type: 'strace_result_type' } })
    if (rts.length) resultTypes.value = rts.map(r => r.label || r.value)
  } catch { /* 字典拉取失败用兜底默认项 */ }
}
const qcStatusMap = {
  PASS: { l: '合格', t: 'success' },
  CONCESSION: { l: '让步接收', t: 'warning' },
  REJECT: { l: '不合格', t: 'danger' },
  EXPIRED: { l: '已过期', t: 'danger' },
  TAILING: { l: '油尾', t: 'warning' }
}

function hasPerm(code) { return perms.value.includes(code) }
function fmtDate(v) { return v ? String(v).slice(0, 10) : '—' }
// 时间兼容两种形态：ISO 字符串（JPA 实体返回，如 2026-08-06T10:30:00）与毫秒整数
function fmtTs(v) {
  if (!v) return '—'
  const s = String(v)
  if (/^\d{10,}$/.test(s)) {
    const d = new Date(Number(s))
    const p = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  }
  return s.replace('T', ' ').substring(0, 16)
}

const docTypeMap = {
  PURCHASE_IN: '采购入库', OUTSOURCE_OUT: '委外出库', OUTSOURCE_IN: '委外入库',
  SALES_OUT: '销售出库', PRODUCTION_OUT: '生产领料', OTHER_OUT: '其他出库', REWORK_OUT: '返工领料',   // v6.9.1
  OTHER_IN: '其他入库', TRANSFER: '调拨', ADJUSTMENT: '盘点调整'
}

// ==================== 列表 ====================

async function fetch() {
  list.value = await api.get('/quality-trace')
}

// ==================== 登记/编辑 ====================

const formVisible = ref(false)
const form = ref({})

function openCreate() {
  form.value = { issueDate: todayLocal() }
  formVisible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  formVisible.value = true
}

async function save() {
  if (!form.value.batchNo) { ElMessage.warning('必须选择批次（精确到批号）'); return }
  if (!form.value.supplierId) { ElMessage.warning('请确认供应商（自动带出或手动选择）'); return }
  if (!form.value.description) { ElMessage.warning('请填写问题描述'); return }
  saving.value = true
  try {
    const sup = suppliers.value.find(s => s.id === form.value.supplierId)
    form.value.supplierName = sup ? sup.name : form.value.supplierName
    if (form.value.id) await api.put(`/quality-trace/${form.value.id}`, form.value)
    else await api.post('/quality-trace', form.value)
    ElMessage.success('保存成功')
    formVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { saving.value = false }
}

// ==================== 选择批次 + 带出采购信息 ====================

const batchVisible = ref(false)
const batchKeyword = ref('')
const batchRows = ref([])
const batchSelected = ref(null)

function openBatchPicker() {
  batchKeyword.value = ''
  batchRows.value = []
  batchSelected.value = null
  batchVisible.value = true
}

async function searchBatches() {
  batchRows.value = await api.get('/quality-trace/batches', { params: { keyword: batchKeyword.value } })
}

async function confirmBatch() {
  const b = batchSelected.value
  if (!b) return
  form.value.materialCode = b.materialCode
  form.value.materialName = b.materialName
  form.value.batchNo = b.batchNo
  batchVisible.value = false
  try {
    const info = await api.get('/quality-trace/purchase-info', { params: { materialCode: b.materialCode, batchNo: b.batchNo } })
    form.value.purchaseOrderNo = info.purchaseOrderNo || null
    form.value.orderCategory = info.orderCategory || null
    form.value.arrivalDate = info.arrivalDate ? String(info.arrivalDate).slice(0, 10) : null
    form.value.purchaseQty = info.inboundQty ?? null
    form.value.purchaseUnitPrice = info.unitPrice ?? null
    form.value.purchaseAmount = info.amount ?? null
    form.value.qcInspectionNo = info.qcInspectionNo || null
    form.value.qcStatus = info.qcStatus || null
    if (info.supplierId) form.value.supplierId = info.supplierId
    if (info.supplierName) {
      ElMessage.success(`已带出采购信息：${info.purchaseOrderNo || '无单号'} / ${info.supplierName}`)
    } else if (info.purchaseOrderNo) {
      ElMessage.warning(`已带出单据 ${info.purchaseOrderNo}，但该单未登记供应商（委外订单需开单时选代工厂），请手动选择`)
    } else {
      ElMessage.warning('该批次反查不到采购/委外单据，请手动选择供应商')
    }
  } catch (e) {
    ElMessage.warning('采购信息带出失败，可手动补充')
  }
}

// ==================== 处理完毕 ====================

const resolveVisible = ref(false)
const resolveForm = ref({})

function openResolve(row) {
  resolveForm.value = { id: row.id, traceNo: row.traceNo, resultType: null, resultRemark: '', compensationAmount: null, handler: '', resolveDate: todayLocal() }
  resolveVisible.value = true
}

async function doResolve() {
  if (!resolveForm.value.resultType) { ElMessage.warning('请选择处理结果类型——必须有处理结果才能处理完毕'); return }
  if (!resolveForm.value.resultRemark) { ElMessage.warning('请填写处理说明'); return }
  if (resolveForm.value.resultType === '赔款' && !(Number(resolveForm.value.compensationAmount) > 0)) {
    ElMessage.warning('处理结果为「赔款」时必须填写赔付金额'); return
  }
  saving.value = true
  try {
    await api.post(`/quality-trace/${resolveForm.value.id}/resolve`, resolveForm.value)
    ElMessage.success('处理完毕，记录已留存')
    resolveVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败')
  } finally { saving.value = false }
}

async function delRow(row) {
  try {
    await ElMessageBox.confirm(`确定删除追溯单 ${row.traceNo}？（仅处理中可删除，处理完毕后永久留存）`)
    await api.delete(`/quality-trace/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

// ==================== 打印损失沟通函 ====================

const printVisible = ref(false)
const printRow = ref(null)
const printTplId = ref(null)
const printFrame = ref(null)

async function openPrint(row) {
  printRow.value = row
  if (!templates.value.length) templates.value = await api.get('/quality-trace/templates')
  const def = templates.value.find(t => t.isDefault) || templates.value[0]
  printTplId.value = def ? def.id : null
  printVisible.value = true
}

function renderPreview() {
  const tpl = templates.value.find(t => t.id === printTplId.value)
  if (printFrame.value && tpl) printFrame.value.srcdoc = buildLossLetterHtml(printRow.value, tpl)
}

function doPrint() {
  const tpl = templates.value.find(t => t.id === printTplId.value)
  if (!tpl || !printFrame.value) return
  api.post('/print-count', { docType: 'SUPPLIER_QUALITY_TRACE', docNo: printRow.value.traceNo })
    .then(n => { printRow.value.printCount = n }).catch(() => {})
  printFrame.value.contentWindow.focus()
  printFrame.value.contentWindow.print()
}

// ==================== 函件模板管理 ====================

const tplListVisible = ref(false)
const tplFormVisible = ref(false)
const tplForm = ref({})
const lastFocus = ref('bodyText')
const placeholders = [
  ['traceNo', '单号'], ['supplierName', '供应商'], ['purchaseOrderNo', '采购单号'], ['materialCode', '物料编码'],
  ['materialName', '物料名称'], ['batchNo', '批号'], ['purchaseQty', '入库数量'], ['purchaseUnitPrice', '采购单价'],
  ['purchaseAmount', '采购金额'], ['arrivalDate', '到货日期'], ['qcInspectionNo', '质检单号'], ['qcResult', '质检结果'],
  ['category', '问题类型'], ['description', '问题描述'], ['lossAmount', '损失金额'], ['issueDate', '发现日期']
]

async function openTemplates() {
  templates.value = await api.get('/quality-trace/templates')
  tplListVisible.value = true
}

function openTplEdit(row) {
  tplForm.value = row ? { ...row } : { name: '', openingText: '', bodyText: '', requireText: '', closingText: '' }
  lastFocus.value = 'bodyText'
  tplFormVisible.value = true
}

function insertPh(key) {
  tplForm.value[lastFocus.value] = (tplForm.value[lastFocus.value] || '') + `{{${key}}}`
}

// ==================== 模板预览（示例数据渲染） ====================

const previewVisible = ref(false)
const previewHtml = ref('')
const previewFrameRef = ref(null)

const SAMPLE_TRACE = {
  traceNo: 'ZS-2026-0001', supplierName: '示例：青岛海博化工产品有限公司', purchaseOrderNo: 'RAWX20260805-01',
  materialCode: 'AC0002', materialName: '示例：分散剂 2500', batchNo: 'B20260805-001',
  arrivalDate: '2026-08-05', purchaseQty: 100, purchaseUnitPrice: 385, purchaseAmount: 38500,
  qcInspectionNo: 'QC-IN-2026-0026', qcStatus: 'REJECT', category: '杂质超标',
  issueDate: '2026-08-22', lossAmount: 1500.5,
  description: '示例：该批次分散剂研磨细度超标，调色后出现明显颗粒，影响生产 2 个批次。'
}

function openTplPreview(tpl) {
  if (!tpl || !tpl.bodyText) { ElMessage.warning('请先填写模板正文再预览'); return }
  previewHtml.value = buildLossLetterHtml(SAMPLE_TRACE, tpl)
  previewVisible.value = true
}

function printPreview() {
  const frame = previewFrameRef.value
  if (!frame) return
  frame.contentWindow.focus()
  frame.contentWindow.print()
}

async function saveTpl() {
  if (!tplForm.value.name) { ElMessage.warning('请填写模板名称'); return }
  if (!tplForm.value.bodyText) { ElMessage.warning('请填写问题与损失正文'); return }
  saving.value = true
  try {
    if (tplForm.value.id) await api.put(`/quality-trace/templates/${tplForm.value.id}`, tplForm.value)
    else await api.post('/quality-trace/templates', tplForm.value)
    ElMessage.success('已保存')
    tplFormVisible.value = false
    templates.value = await api.get('/quality-trace/templates')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { saving.value = false }
}

async function setTplDefault(row) {
  try {
    await api.put(`/quality-trace/templates/${row.id}/default`)
    templates.value = await api.get('/quality-trace/templates')
    ElMessage.success(`已将「${row.name}」设为默认模板`)
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}

async function delTpl(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」？`)
    await api.delete(`/quality-trace/templates/${row.id}`)
    templates.value = await api.get('/quality-trace/templates')
    ElMessage.success('已删除')
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

// ==================== 追溯/详情 ====================

const traceVisible = ref(false)
const traceRow = ref(null)
const traceMovements = ref([])

async function openTrace(row) {
  traceRow.value = row
  traceVisible.value = true
  try { traceMovements.value = await api.get(`/quality-trace/${row.id}/trace`) } catch (e) { traceMovements.value = [] }
}

const detailVisible = ref(false)
const detailRow = ref(null)

function openDetail(row) { detailRow.value = row; detailVisible.value = true }

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  await fetch()
  await loadDicts()
  try { suppliers.value = await api.get('/supplier', { params: { enabled: true } }) } catch {}
  try { templates.value = await api.get('/quality-trace/templates') } catch {}
})
</script>

<style scoped>
.page-container { width: 100% }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px }
.status-tabs { display: flex; gap: 0; margin-bottom: 12px; background: #f1f5f9; border-radius: 8px; padding: 3px; width: fit-content; }
.status-tab { padding: 6px 20px; font-size: 13px; font-weight: 600; border: none; border-radius: 6px; cursor: pointer; background: transparent; color: #64748b; transition: all 0.2s; }
.status-tab:hover { color: #334155; }
.status-tab.active { background: #fff; color: #4a6785; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.tab-badge { display: inline-block; min-width: 18px; padding: 0 5px; margin-left: 4px; font-size: 11px; line-height: 16px; border-radius: 9px; background: rgba(0,0,0,0.08); color: inherit; }
.status-tab.active .tab-badge { background: rgba(29,78,216,0.12); }
.form-tip { font-size: 12px; color: #94a3b8; line-height: 1.5 }
.brought-box { border: 1px dashed #c0c4cc; border-radius: 6px; padding: 10px; margin-bottom: 4px; background: #fafbfc }
.brought-title { font-size: 12px; color: #64748b; margin-bottom: 8px }
.ph-list { display: flex; flex-wrap: wrap; gap: 6px }
.ph-tag { cursor: pointer; }
.ph-tag:hover { border-color: #409eff; color: #409eff }
</style>
