import { defineComponent, h, cloneVNode } from 'vue'
import { ElTable } from 'element-plus'
import { useRoute } from 'vue-router'

/**
 * PTable = el-table 的包装组件，额外提供「列宽拖拽持久化」。
 *
 * - 拖拽列宽后按「路由 + 列标识」存入 localStorage，刷新/重进页面自动恢复。
 * - 用法与 el-table 完全一致：列定义(<el-table-column>)不变，只把 <el-table> 换成 <p-table>。
 * - 列标识优先用 el-table-column 的 prop，其次 label；无 prop/label 的列(如 selection)跳过。
 * - 其余 props/事件/slot 全部透传给 el-table，行为完全一致。
 */
export default defineComponent({
  name: 'PTable',
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    const route = useRoute()
    const storageKey = () => 'colw:' + route.path
    // 操作列按钮宽度由页面代码控制，不参与持久化（避免旧缓存压住按钮化后的新列宽）
    const isActionCol = (k) => k === '操作'
    const readMap = () => {
      try { return JSON.parse(localStorage.getItem(storageKey()) || '{}') }
      catch { return {} }
    }
    // v6.1 修复：页面绑定的 @header-dragend 必须透传——此前 h(ElTable, {...attrs, onHeaderDragend})
    // 用自身的 handler 覆盖了 attrs 里的同名事件，useColumnResize 的账号级保存从未触发，服务端一直没写入
    const pageOnDragend = attrs.onHeaderDragend
    const onHeaderDragend = (newWidth, oldWidth, column) => {
      const k = column && (column.property || column.label)
      if (k && !isActionCol(k)) {
        const m = readMap()
        m[k] = Math.round(newWidth)
        localStorage.setItem(storageKey(), JSON.stringify(m))
      }
      if (typeof pageOnDragend === 'function') pageOnDragend(newWidth, oldWidth, column)
    }
    // clone 子列节点，把持久化的 width 注入回去（覆盖原 width 属性）
    const injectWidth = (nodes) => {
      const map = readMap()
      return (nodes || []).map(node => {
        if (node && node.props) {
          const k = node.props.prop || node.props.label
          if (k != null && !isActionCol(k) && map[k] != null) {
            return cloneVNode(node, { width: map[k] })
          }
        }
        return node
      })
    }
    return () => h(ElTable, { ...attrs, onHeaderDragend }, {
      ...slots,
      default: slots.default ? () => injectWidth(slots.default()) : undefined
    })
  }
})
