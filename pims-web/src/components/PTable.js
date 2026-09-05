import { defineComponent, h, cloneVNode, withDirectives, resolveDirective, ref, onUnmounted } from 'vue'
import { ElTable } from 'element-plus'
import { useRoute } from 'vue-router'

/**
 * PTable = el-table 的包装组件，额外提供「列宽拖拽持久化（账号级云端）」。
 *
 * - 拖拽列宽后按「路由 + 列标识」存本机 localStorage，并 debounce 上行服务端账号级配置
 *   （sys_config，key = pims.ui.cols.{用户名}.{路由}）——换电脑/换浏览器登录即恢复。
 * - 恢复策略：本机有缓存用本机（最近使用优先），本机无缓存时拉服务端回填本地。
 * - 用法与 el-table 完全一致：列定义(<el-table-column>)不变，只把 <el-table> 换成 <p-table>。
 * - 列标识优先用 el-table-column 的 prop，其次 label；无 prop/label 的列(如 selection)跳过。
 * - 其余 props/事件/slot 全部透传给 el-table，行为完全一致。
 * - 后端 /api/ui-config 现成（账号隔离 + 防越权），本组件零后端依赖。
 *
 * v7.2 让位机制：页面绑定了自己的 @header-dragend（= useColumnResize 的 18 个页面，cw() 体系）
 * 时，PTable 完全不碰列宽存储——注入跳过、colw: 双写跳过、服务端 fetch 也不发（省一次请求，
 * "让位=完全不碰存储"语义）。页面自管列宽，两套体系分治互不打架——
 * 此前 PTable 的 cloneVNode 注入覆盖页面 cw() 绑定 + fetch 路由竞态跨页污染，
 * 导致"拖拽后切页回来宽度复原"。
 */
export default defineComponent({
  name: 'PTable',
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    const route = useRoute()
    // v7.2：让位判定 setup 一次求值（页面事件绑定在挂载期不变）
    const deferToPage = typeof attrs.onHeaderDragend === 'function'

    const storageKey = () => 'colw:' + route.path
    // v6.7：服务端 key 与 useColumnResize 同规范（pims.ui.cols.{用户名}.{表标识}，此处表标识=路由路径）；
    // token 走 HttpOnly Cookie 自动携带，localStorage 旧 token 仅作过渡兜底
    const sKey = () => 'pims.ui.cols.'
      + ((() => { try { return JSON.parse(localStorage.getItem('user') || '{}').username } catch { return '' } })() || 'anonymous')
      + '.' + route.path
    // 操作列按钮宽度由页面代码控制，不参与持久化（避免旧缓存压住按钮化后的新列宽）
    const isActionCol = (k) => k === '操作'
    const readMap = () => {
      try { return JSON.parse(localStorage.getItem(storageKey()) || '{}') }
      catch { return {} }
    }

    // v6.7：colMap 响应式持有——服务端拉回后触发重渲染（原 render 每次 readMap 无法响应异步恢复）
    const colMap = ref(readMap())

    // v7.2：路由竞态根治——fetch 发出前捕获 path/键快照 + alive 标志（卸载置 false）；
    // 响应晚到（组件已卸载或已切页）直接丢弃，杜绝「A 页数据写进 B 页键」的跨页污染
    let alive = true
    onUnmounted(() => { alive = false })

    if (!deferToPage) {
      const pathAtMount = route.path
      const localAtMount = storageKey()
      const serverKeyAtMount = sKey()
      // 服务端恢复：本机无缓存时拉账号级配置回填本地 + colMap（换电脑场景）
      fetch('/api/ui-config?key=' + encodeURIComponent(serverKeyAtMount), {
        headers: { 'pims-token': localStorage.getItem('pims-token') || '' }
      }).then(r => r.json()).then(j => {
        if (!alive || route.path !== pathAtMount) return   // 已卸载/已离开挂载时路由——丢弃
        if (j && j.code === 200 && j.data && !Object.keys(readMap()).length) {
          localStorage.setItem(localAtMount, j.data)
          try { colMap.value = JSON.parse(j.data) } catch { /* 坏数据忽略 */ }
        }
      }).catch(() => {})
    }

    // 上行 debounce（按服务端 key 分计时器，多表格互不挤占）
    const pushTimers = {}
    const pushToServer = () => {
      const key = sKey()
      clearTimeout(pushTimers[key])
      pushTimers[key] = setTimeout(() => {
        fetch('/api/ui-config', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'pims-token': localStorage.getItem('pims-token') || '' },
          body: JSON.stringify({ key, value: localStorage.getItem(storageKey()) })
        }).catch(() => {})
      }, 800)
    }

    // v6.1 修复：页面绑定的 @header-dragend 必须透传——此前 h(ElTable, {...attrs, onHeaderDragend})
    // 用自身的 handler 覆盖了 attrs 里的同名事件，useColumnResize 的账号级保存从未触发，服务端一直没写入
    const pageOnDragend = attrs.onHeaderDragend
    const onHeaderDragend = (newWidth, oldWidth, column) => {
      if (!deferToPage) {   // v7.2：让位页面只透传，不写 colw:（页面 cw() 体系自己保存）
        const k = column && (column.property || column.label)
        if (k && !isActionCol(k)) {
          const m = readMap()
          m[k] = Math.round(newWidth)
          localStorage.setItem(storageKey(), JSON.stringify(m))
          colMap.value = m          // v6.7：同步响应式（即时生效）
          pushToServer()            // v6.7：debounce 上行账号级
        }
      }
      if (typeof pageOnDragend === 'function') pageOnDragend(newWidth, oldWidth, column)
    }
    // clone 子列节点，把持久化的 width 注入回去（覆盖原 width 属性）；v6.7 读响应式 colMap
    // v7.2：让位页面原样返回——页面 :width="cw()" 全权裁决，PTable 不再覆盖
    const injectWidth = (nodes) => {
      if (deferToPage) return nodes || []
      const map = colMap.value
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
    // v6.4.1 修复：解构必须放 render 内（attrs 是响应式代理，setup 里一次性解构会冻结 data 为
    // 初始空数组——"供应商/物料全没了"实为全站表格行不渲染，数据一直在库）
    return () => {
      // v6.6：透传 loading 到 el-table 的 v-loading 指令（页面 <p-table :loading="x"> 一处生效）
      const { size, loading, ...rest } = attrs
      const table = h(ElTable, { size: size || 'small', ...rest, onHeaderDragend }, {
        ...slots,
        default: slots.default ? () => injectWidth(slots.default()) : undefined
      })
      if (loading != null) {
        const vLoading = resolveDirective('loading')
        return withDirectives(table, [[vLoading, !!loading]])
      }
      return table
    }
  }
})
