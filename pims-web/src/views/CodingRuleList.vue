<template>
  <div class="page-container">
    <div class="page-header">
      <h2>编码规则</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showDialog(null)" v-if="hasPerm('material:write')">新增规则</el-button>
      </div>
    </div>
    <div class="format-tip">
      <b>原料编码（6 位，不变）</b>：小类代码(2位) + 序号(4位)——两位字母只用来区分类别（首位恒为大类：A助剂/P颜料/F填料/R树脂/S溶剂）。
      <b>防投错料</b>：数字 0001-9999 全局连续、每个数字只使用一次——AC0001 之后无论什么类别都是 0002，工人只看数字也不会拿错料；
      新增原料自动取全局下一号 <b>{{ nextGlobalSeq }}</b>。
    </div>
    <div class="format-tip" style="background:#f0f5f0;border-color:#c4d6c8;">
      <b>半成品/成品编码（v5.86 定稿）</b>：与原料 6 位体系彻底分开，看码即知属性——<br>
      · <b>半成品（色浆）8 位</b> = 色浆小类(2) + 主材(1) + 序号(5)：如 <b>BWFT00010</b> = 白浆·丙烯酸系<br>
      · <b>成品 9 位</b> = 漆型(2) + 主材(1) + 色系(1) + 序号(5)：如 <b>CWTH00010</b> = 面漆·聚酯·白<br>
      · 主材位：<b>T聚酯</b> / F氟碳 / E环氧 / A丙烯酸；色系位：K黑 / H白 / U蓝 / N绿 / Y灰 / R红 / W黄<br>
      <b>序号规则（B/C 各自一套全局流水）</b>：①半成品一套、成品一套，体系内每个数字只用一次（不同小类/主材共号池，看数字绝不重复）；②序号 5 位（上限 99999，扣易错约 6.6 万可用）；③自动跳过易错号段——三连同号（0111）、三连递增（0123/1123）、三连递减（0987）一律跳号。<br>
      <b>易混字符约束（原料/半成品/成品统一）</b>：字母段禁用 <b>I / L / O / Z</b>（与 1/0/2 手写极易混；蓝浆 BL 新码用 BU、聚酯主材位用 T）。历史编码一律不变。<br>
      原料仍为全局连续号（0001→0002→…不跳号，工人只看数字防投错料）。
    </div>

    <!-- 按大类分组 -->
    <div v-for="group in grouped" :key="group.categoryCode" class="rule-group">
      <div class="rule-group-header">
        <h3>{{ group.category }}（{{ group.categoryCode }}）<el-tag v-if="isNewSystem(group.categoryCode)" size="small" type="success" style="margin-left:6px">新编码体系</el-tag></h3>
        <el-tag size="small" type="info">{{ group.items.length }} 条</el-tag>
      </div>
      <!-- 原料类（A/P/F/R/S）：6 位全局连续体系，展示流水游标 -->
      <p-table v-if="!isNewSystem(group.categoryCode)" :data="group.items" stripe border size="small" @header-dragend="onHeaderDragend">
        <el-table-column prop="subCategory" label="小类" :width="cw('小类') || 120" />
        <el-table-column prop="subCategoryCode" label="小类代码" :width="cw('小类代码') || 100" />
        <el-table-column label="最近发放序号" :width="cw('最近发放序号') || 110" align="center">
          <template #default="{row}">{{ row.currentSeq || 0 }}</template>
        </el-table-column>
        <el-table-column label="该小类最近编码" :width="cw('该小类最近编码') || 130">
          <template #default="{row}">{{ row.currentSeq ? row.subCategoryCode + String(row.currentSeq).padStart(4,'0') : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" v-if="hasPerm('material:write')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="del(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <!-- 半成品/成品（B/C）：v5.65 属性语义码——序号按属性前缀分组自动生成，不走本表流水，不展示旧游标 -->
      <p-table v-else :data="group.items" stripe border size="small">
        <el-table-column prop="subCategory" label="小类" width="120" />
        <el-table-column prop="subCategoryCode" label="小类代码" width="100" />
        <el-table-column label="编码规则" :width="cw('编码规则') || undefined" min-width="260">
          <template #default="{row}">
            <span v-if="group.categoryCode === 'C'">{{ row.subCategoryCode }} + 主材(1) + 色系(1) + 序号(5)，如 {{ row.subCategoryCode }}TH00010</span>
            <span v-else>{{ subPrefix(row) }} + 主材(1) + 序号(5)，如 {{ subPrefix(row) }}F00010</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" v-if="hasPerm('material:write')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="del(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <el-dialog :title="form.id?'编辑编码规则':'新增编码规则'" v-model="dialogVisible" width="min(1100px, 96vw)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="大类">
          <el-select v-model="form.category" style="width:100%" @change="onCategoryChange">
            <el-option v-for="d in dicts.material_category" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="大类代码">
          <el-input v-model="form.categoryCode" :disabled="true" />
        </el-form-item>
        <el-form-item label="小类">
          <el-select v-model="form.subCategory" style="width:100%" @change="onSubCategoryChange">
            <el-option v-for="d in filteredSubCategories" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="小类代码">
          <el-input v-model="form.subCategoryCode" :disabled="true" />
          <div class="form-tip">编码 = 小类代码 + 4 位序号，自动生成无需配置</div>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({})
const perms = ref([])
const dicts = ref({})
const { cw, onHeaderDragend } = useColumnResize('coding_rule')

function hasPerm(code) { return perms.value.includes(code) }
// v5.65：B/C 类走新属性编码体系（序号按属性前缀分组自动生成，不在此表管理流水）
function isNewSystem(categoryCode) { return categoryCode === 'B' || categoryCode === 'C' }
// 含易混字符 L 的历史小类，新码前缀自动替换（蓝浆 BL→BU）
function subPrefix(row) { return row.subCategoryCode === 'BL' ? 'BU' : row.subCategoryCode }

async function fetchDicts() {
  const all = await api.get('/dict')
  const map = {}
  for (const item of all) {
    if (!map[item.type]) map[item.type] = []
    map[item.type].push(item)
  }
  dicts.value = map
}

const filteredSubCategories = computed(() => {
  if (!form.value.category) return []
  const catDict = dicts.value.material_category?.find(d => d.label === form.value.category)
  if (!catDict) return []
  // 小类代码以大类代码开头
  const code = catDict.value
  return dicts.value.material_sub_category?.filter(d => d.value.startsWith(code)) || []
})

// 全局下一号 = 所有规则行 currentSeq 最大值 + 1（数字 0001-9999 全局连续单次使用）
const nextGlobalSeq = computed(() => {
  const max = Math.max(0, ...list.value.map(r => r.currentSeq || 0))
  return String(max + 1).padStart(4, '0')
})

const grouped = computed(() => {
  const map = {}
  for (const item of list.value) {
    if (!map[item.categoryCode]) map[item.categoryCode] = { category: item.category, categoryCode: item.categoryCode, items: [] }
    map[item.categoryCode].items.push(item)
  }
  return Object.values(map).sort((a, b) => a.categoryCode.localeCompare(b.categoryCode))
})

async function fetch() {
  list.value = await api.get('/coding-rule')
}

function onCategoryChange() {
  form.value.categoryCode = dicts.value.material_category?.find(d => d.label === form.value.category)?.value || ''
  form.value.subCategory = ''
  form.value.subCategoryCode = ''
}

function onSubCategoryChange() {
  const sub = dicts.value.material_sub_category?.find(d => d.label === form.value.subCategory)
  if (sub) {
    form.value.subCategoryCode = sub.value
  }
}

function showDialog(row) {
  if (row) {
    form.value = { ...row }
  } else {
    form.value = { category: '', categoryCode: '', subCategory: '', subCategoryCode: '', numberStart: 0 }
  }
  dialogVisible.value = true
}

async function save() {
  if (form.value.id) {
    await api.put(`/coding-rule/${form.value.id}`, form.value)
  } else {
    await api.post('/coding-rule', form.value)
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
  fetch()
}

async function del(id) {
  await ElMessageBox.confirm('确定删除？')
  await api.delete(`/coding-rule/${id}`)
  ElMessage.success('已删除')
  fetch()
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await fetchDicts()
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.rule-group { margin-bottom: 24px; }
.rule-group-header {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 10px;
}
.rule-group-header h3 {
  margin: 0; font-size: 15px; font-weight: 600;
  color: var(--pims-text);
}
.format-tip { font-size: 12px; color: #64748b; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 14px; margin-bottom: 16px; line-height: 1.8; }
.form-tip { font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 2px; }
</style>
