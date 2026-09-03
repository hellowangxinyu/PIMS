import { ref, computed } from 'vue'

/**
 * 通用前端分页（v5.2）：列表超过 pageSize 条时自动分页展示，默认每页 25 条
 * 用法：
 *   const list = ref([])
 *   const { page, pageSize, pagedRows, resetPage } = usePaging(list)
 *   // 模板：<el-table :data="pagedRows"> ... </el-table>
 *   //       <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
 *   //                      :total="list.length" layout="total, sizes, prev, pager, next" />
 *   // 刷新/搜索后调用 resetPage() 回到第一页
 */
export function usePaging(rowsRef) {
  const page = ref(1)
  const pageSize = ref(25)

  const pagedRows = computed(() => {
    const rows = rowsRef.value || []
    const start = (page.value - 1) * pageSize.value
    return rows.slice(start, start + pageSize.value)
  })

  const resetPage = () => { page.value = 1 }

  return { page, pageSize, pagedRows, resetPage }
}
