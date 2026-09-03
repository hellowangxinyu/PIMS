/**
 * 表格列宽拖拽与持久化（v6.0 双层存储：服务器账号级 + 本地 localStorage 兜底）
 * 原理：reactive 对象绑定 el-table-column 的 :width；拖拽后实时保存。
 * 持久化升级：换电脑重新登录也能恢复——localStorage 只在本机浏览器，
 * 增加服务端 sys_config 存储（key = pims.ui.cols.{username}.{tableKey}），登录账号级全局生效。
 * 写入策略：本地即时写；服务端 debounce 800ms 合并写，避免拖拽过程高频请求。
 */
import { reactive } from 'vue'

const SAVE_DEBOUNCE_MS = 800
const pendingTimers = {}   // serverKey -> timer

function userName() {
  try {
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    return u.username || 'anonymous'
  } catch { return 'anonymous' }
}

function serverKey(tableKey) {
  return `pims.ui.cols.${userName()}.${tableKey}`
}

/** 从服务端拉取（返回 Promise<string|null>） */
async function fetchFromServer(sKey) {
  try {
    const token = localStorage.getItem('pims-token') || ''
    const r = await fetch(`/api/ui-config?key=${encodeURIComponent(sKey)}`, {
      headers: { 'pims-token': token }
    })
    if (!r.ok) return null
    const j = await r.json()
    return j && j.code === 200 ? (j.data ?? null) : null
  } catch { return null }
}

/** debounce 写服务端 */
function pushToServer(sKey, value) {
  clearTimeout(pendingTimers[sKey])
  pendingTimers[sKey] = setTimeout(async () => {
    try {
      const token = localStorage.getItem('pims-token') || ''
      await fetch('/api/ui-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'pims-token': token },
        body: JSON.stringify({ key: sKey, value })
      })
    } catch { /* 离线忽略，本地仍有 */ }
  }, SAVE_DEBOUNCE_MS)
}

/**
 * @param tableKey - 表格唯一标识（原 localStorage key 后缀，保持兼容）
 */
export function useColumnResize(tableKey) {
  const storageKey = `pims_table_cols_${tableKey}`

  const colWidths = reactive(loadFromStorage())

  function loadFromStorage() {
    try {
      const saved = localStorage.getItem(storageKey)
      return saved ? JSON.parse(saved) : {}
    } catch {
      return {}
    }
  }

  function saveToStorage() {
    try {
      const obj = {}
      for (const key of Object.keys(colWidths)) {
        obj[key] = colWidths[key]
      }
      const json = JSON.stringify(obj)
      localStorage.setItem(storageKey, json)          // 本地兜底（未登录/离线可用）
      pushToServer(serverKey(tableKey), json)         // v6.0 服务端账号级
    } catch (e) { /* ignore */ }
  }

  /** v6.0 服务端恢复：本机无缓存时从账号配置拉（换电脑场景），拉到后回填本地；
   *  v6.1 补：本机有、服务端无（事件被 PTable 截胡的历史时段未上行）时自动上行一次，存量不用重拖 */
  async function restoreFromServer() {
    const local = loadFromStorage()
    if (Object.keys(local).length) {
      const remote = await fetchFromServer(serverKey(tableKey))
      if (!remote) saveToStorage()   // 本机已有但服务端为空 → 上行（saveToStorage 内含 pushToServer）
      return                          // 本机已有配置，本机优先
    }
    const remote = await fetchFromServer(serverKey(tableKey))
    if (remote) {
      try {
        const obj = JSON.parse(remote)
        for (const k of Object.keys(obj)) colWidths[k] = obj[k]
        localStorage.setItem(storageKey, remote)     // 回填本地，后续离线也可用
      } catch { /* 坏数据忽略 */ }
    }
  }
  restoreFromServer()

  function cw(label) {
    return colWidths[label] || undefined
  }

  function onHeaderDragend(newWidth, oldWidth, column) {
    if (column && column.label) {
      colWidths[column.label] = Math.round(newWidth)
      saveToStorage()
    }
  }

  return { colWidths, cw, onHeaderDragend }
}
