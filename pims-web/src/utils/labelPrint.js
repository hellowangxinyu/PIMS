/**
 * 入库标签打印（v5.27）：8cm × 10cm 标签纸，每张标签一页
 * 通用字段兼容：品名 materialName/productName，编码 materialCode/productCode，
 * 批号 batchNo，数量 qty，单位 unit，日期 createTime/arrivalDate
 * v7.3 分桶打印：records 数组元素为 {row, split, adjust}——split>1 时该行拆 N 张
 * （每桶可微调、合计守恒由调用方/computeBuckets 保证），标签带「第 N/M 桶」与批量合计行。
 */
export function printLabels(records, opts = {}) {
  const company = opts.company || '广东芃远新材料有限公司'
  // 兼容旧签名：直接传行数组（无 split）
  const items = (records || []).map(item => {
    if (item && item.row) return item
    return { row: item }
  }).filter(it => it.row && it.row.batchNo)
  if (!items.length) return false

  const rowsToPrint = []
  for (const it of items) {
    const r = it.row
    if (it.split && it.split > 1) {
      const n = Math.min(200, Math.max(1, Math.floor(it.split)))
      for (let i = 0; i < n; i++) {
        rowsToPrint.push({
          ...r,
          _bucketQty: it.adjust && it.adjust[i] != null ? it.adjust[i] : r.qty,
          _bucketNo: i + 1, _bucketTotal: n
        })
      }
    } else {
      rowsToPrint.push(r)
    }
  }

  const labels = rowsToPrint.map(r => {
    const name = r.materialName || r.productName || '-'
    const code = r.materialCode || r.productCode || '-'
    const qty = r._bucketQty != null ? r._bucketQty : (r.qty != null ? r.qty : '-')
    const unit = r.unit || 'kg'
    const date = fmt(r.createTime || r.arrivalDate || r.inboundDate)
    // v5.79：编码为主视觉（防错核心），质检结果/质检人随标签
    const qcMap = { PASS: '合 格', CONCESSION: '让步接收', REJECT: '不合格', PENDING: '待检' }
    const qc = r.qcStatus ? (qcMap[r.qcStatus] || r.qcStatus) : (r.qcResult || '待检')
    const qcColor = r.qcStatus === 'PASS' || r.qcResult === '合 格' ? '#0a8f3c'
      : (r.qcStatus === 'REJECT' || r.qcResult === '不合格' ? '#d03030' : '#333')
    const inspector = r.qcInspector || r.inspector || ''
    return `
    <div class="label">
      <div class="company">${esc(company)}</div>
      <div class="code-big">${esc(code)}</div>
      <div class="name">${esc(name)}</div>
      <div class="row"><span>批号：${esc(r.batchNo)}</span></div>
      <div class="row"><span>数量：${qty} ${unit}</span></div>
      ${r._bucketNo ? `<div class="bucket">第 ${r._bucketNo} / ${r._bucketTotal} 桶</div>` : ''}
      <div class="qc" style="color:${qcColor};font-weight:700">质检：${esc(qc)}</div>
      ${inspector ? `<div class="row"><span>质检员：${esc(inspector)}</span></div>` : ''}
      <div class="date">${date}${r._bucketTotal ? ` · 批量合计 ${esc(String(r.qty))} ${unit}` : ''}</div>
    </div>`
  }).join('')

  const win = window.open('', '_blank')
  if (!win) return false
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8">
<title>入库标签打印</title>
<style>
  @page { size: 80mm 100mm; margin: 0; }
  html, body { margin: 0; padding: 0; }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; }
  .label {
    width: 80mm; height: 100mm;
    box-sizing: border-box;
    padding: 4mm;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    text-align: center;
    page-break-after: always;
    break-inside: avoid;
  }
  .label:last-child { page-break-after: auto; }
  .company { font-size: 10pt; font-weight: 700; margin-bottom: 4mm; letter-spacing: 1px; }
  .code-big { font-size: 24pt; font-weight: 900; font-family: 'Consolas', 'Courier New', monospace; letter-spacing: 1px; margin-bottom: 3mm; word-break: break-all; border: 0.6mm solid #000; padding: 2mm 3mm; }
  .name { font-size: 11pt; font-weight: 500; margin-bottom: 4mm; word-break: break-all; }
  .row { font-size: 12pt; margin-bottom: 3mm; }
  .bucket { font-size: 13pt; font-weight: 700; margin: 1mm 0; border: 0.4mm solid #000; padding: 1mm 3mm; border-radius: 2mm; }
  .qc { font-size: 14pt; margin: 2mm 0; }
  .date { font-size: 9pt; margin-top: 3mm; color: #333; }
</style></head><body>${labels}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 200)
  return true
}

/**
 * v7.3 分桶算法（均分+微调+尾差守恒）：
 * - edited 按桶序号（0 起）记锁定（用户改过的桶）；未动桶 =（总量−Σ锁定）÷ 剩余桶数，保留 2 位小数
 * - 尾差归最后一个**未锁定**桶（末桶被手改时顺延到倒数第二个未动桶）
 * - Σ锁定 > 总量 → 返回 null（调用方据此禁用确认：标签是物理事实，合计必须守恒）
 * @param total 总量 @param n 桶数 @param edited {index→qty} 手改桶 @returns 每桶量数组或 null
 */
export function computeBuckets(total, n, edited = {}) {
  total = Number(total) || 0
  n = Math.max(1, Math.floor(n))
  if (Object.keys(edited).length === 0) {
    const avg = Math.round((total / n) * 100) / 100
    const arr = Array(n).fill(avg)
    arr[n - 1] = Math.round((total - avg * (n - 1)) * 100) / 100
    if (arr[n - 1] < 0) return null   // 极端小数均分四舍五入后尾桶可能为负（如 0.07 分 10 桶）——物理不可能，禁打
    return arr
  }
  const lockedSum = Object.values(edited).reduce((a, v) => a + Number(v), 0)
  const freeIdx = []
  for (let i = 0; i < n; i++) if (!(i in edited)) freeIdx.push(i)
  if (lockedSum > total + 1e-9) return null   // 锁定合计超总量——物理不可能，禁打
  const freeCount = freeIdx.length
  const arr = Array(n)
  for (const [i, v] of Object.entries(edited)) arr[Number(i)] = Number(v)
  if (freeCount === 0) return arr              // 全部锁定且 Σ≤total（守恒由禁用守卫保证）
  const freeAvg = Math.round(((total - lockedSum) / freeCount) * 100) / 100
  for (const i of freeIdx) arr[i] = freeAvg
  // 尾差归最后一个未锁定桶
  const lastFree = freeIdx[freeIdx.length - 1]
  const othersSum = arr.reduce((a, v, i) => i === lastFree ? a : a + v, 0)
  arr[lastFree] = Math.round((total - othersSum) * 100) / 100
  if (arr[lastFree] < 0) return null   // 同上：尾差为负=这种分法物理不可能，走禁打而非打负数标签
  return arr
}

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 10)
}
function esc(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
