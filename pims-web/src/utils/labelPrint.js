/**
 * 入库标签打印（v5.27）：8cm × 10cm 标签纸，每张标签一页
 * 通用字段兼容：品名 materialName/productName，编码 materialCode/productCode，
 * 批号 batchNo，数量 qty，单位 unit，日期 createTime/arrivalDate
 * @param {Array} rows 选中的入库/到货记录
 * @param {Object} opts { company: 公司名（默认广东芃远新材料有限公司） }
 */
export function printLabels(rows, opts = {}) {
  const list = (rows || []).filter(r => r && r.batchNo)
  if (!list.length) return false
  const company = opts.company || '广东芃远新材料有限公司'
  const labels = list.map(r => {
    const name = r.materialName || r.productName || '-'
    const code = r.materialCode || r.productCode || '-'
    const qty = r.qty != null ? r.qty : '-'
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
      <div class="qc" style="color:${qcColor};font-weight:700">质检：${esc(qc)}</div>
      ${inspector ? `<div class="row"><span>质检员：${esc(inspector)}</span></div>` : ''}
      <div class="date">${date}</div>
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
  .qc { font-size: 14pt; margin: 2mm 0; }
  .date { font-size: 9pt; margin-top: 5mm; color: #333; }
</style></head><body>${labels}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 200)
  return true
}

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 10)
}
function esc(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
