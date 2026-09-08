<template>
  <el-dialog title="从物料库选择" v-model="visible" width="min(860px, 94vw)" top="6vh" destroy-on-close>
    <div class="mp-filter">
      <el-radio-group v-model="cat" size="small">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="c in catOptions" :key="c.value" :value="c.value">{{ c.label }}</el-radio-button>
      </el-radio-group>
      <el-select v-model="sub" size="small" clearable placeholder="小类" style="width:130px">
        <el-option v-for="sc in filteredSubs" :key="sc.value" :label="sc.label" :value="sc.value" />
      </el-select>
      <el-input v-model="kw" size="small" clearable placeholder="编码 / 品名模糊搜索" style="width:200px" />
    </div>
    <el-table :data="paged" height="380" border size="small" @selection-change="onSel" row-key="code">
      <el-table-column type="selection" width="42" reserve-selection />
      <el-table-column prop="code" label="编码" width="110" />
      <el-table-column prop="name" label="品名" min-width="180" show-overflow-tooltip />
      <el-table-column prop="brand" label="牌号" width="120" show-overflow-tooltip />
      <el-table-column label="大类" width="80" align="center">
        <template #default="{ row }">{{ catLabel(row.category) }}</template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="60" align="center" />
    </el-table>
    <div class="mp-foot">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length"
        layout="total, prev, pager, next" small />
      <div>
        <span class="mp-sel">已选 {{ selected.length }} 项</span>
        <el-button size="small" @click="visible = false">取消</el-button>
        <el-button type="primary" size="small" :disabled="!selected.length" @click="confirm">加入所选</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * v7.7 通用物料选择弹窗：模糊搜索 + 大类/小类两级筛选 + 表格勾选批量加入。
 * 用法：v-model 绑定开关；@picked 接收选中物料数组 [{code,name,brand,category,subCategory,unit}]。
 * 数据源 /material?enabled=true 全量拉取前端过滤（项目惯例，物料量为百级）。
 */
import { ref, computed, watch } from 'vue'
import api from '../api'

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['picked'])
const props = defineProps({
  /** 允许选择的大类（默认原材料+半成品；打样配料排除 C 成品） */
  categories: { type: Array, default: () => ['A', 'P', 'F', 'R', 'S', 'B'] }
})

const materials = ref([])
const dicts = ref({})
const cat = ref('')
const sub = ref('')
const kw = ref('')
const page = ref(1)
const pageSize = 10
const selected = ref([])

const CAT_LABEL = { A: '助剂', P: '颜料', F: '填料', R: '树脂', S: '溶剂', B: '半成品', C: '成品' }
const catOptions = computed(() => props.categories.map(c => ({ value: c, label: CAT_LABEL[c] || c })))
const catLabel = c => CAT_LABEL[c] || c || '—'

// v7.8 修弱级联：选了大类只显示该大类的小类；未选大类时显示可选范围内全部小类
const filteredSubs = computed(() => {
  const scope = cat.value ? [cat.value] : props.categories
  return (dicts.value.material_sub_category || []).filter(d => scope.some(c => d.value.startsWith(c)))
})
watch(cat, v => {
  // 切大类后若已选小类不属于新大类，清掉
  if (sub.value && !sub.value.startsWith(v || '')) sub.value = ''
})

const filtered = computed(() => {
  let list = materials.value.filter(m => props.categories.includes(m.category))
  if (cat.value) list = list.filter(m => m.category === cat.value)
  if (sub.value) list = list.filter(m => (m.subCategory || '').startsWith(sub.value))
  const k = kw.value.trim().toLowerCase()
  if (k) list = list.filter(m => (m.code || '').toLowerCase().includes(k) || (m.name || '').toLowerCase().includes(k))
  return list
})
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

watch(filtered, () => { page.value = 1 })

function onSel(rows) { selected.value = rows }

function confirm() {
  emit('picked', selected.value.map(m => ({
    code: m.code, name: m.name, brand: m.brand,
    category: m.category, subCategory: m.subCategory, unit: m.unit || 'kg'
  })))
  visible.value = false
  selected.value = []
}

watch(visible, async v => {
  if (!v) return
  if (!materials.value.length) {
    try { materials.value = await api.get('/material', { params: { enabled: true } }) } catch { /* ignore */ }
  }
  if (!Object.keys(dicts.value).length) {
    try {
      const all = await api.get('/dict')
      const map = {}
      for (const item of all) {
        if (!map[item.type]) map[item.type] = []
        map[item.type].push(item)
      }
      dicts.value = map
    } catch { /* ignore */ }
  }
})
</script>

<style scoped>
.mp-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.mp-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.mp-sel { font-size: 13px; color: #64748b; margin-right: 12px; }
</style>
