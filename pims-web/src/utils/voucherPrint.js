/**
 * 记账凭证打印（v5.61）—— 三栏式通用记账凭证
 * 照 statementPrint.js 模式：拼 HTML → 隐藏 iframe 打印
 */
function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// v8.3（C3）：摘要/科目/备注/制单人来自单据录入，拼 HTML 前必须转义（同源 XSS）
function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

export function printVoucher(v) {
  const rows = (v.entries || []).map(e => `
    <tr>
      <td class="digest">${esc(e.digest)}</td>
      <td class="subject">${esc(e.subjectCode)} ${esc(e.subjectName)}${e.auxName ? '（' + esc(e.auxName) + '）' : ''}</td>
      <td class="amt">${Number(e.debit || 0) !== 0 ? fmt(e.debit) : ''}</td>
      <td class="amt">${Number(e.credit || 0) !== 0 ? fmt(e.credit) : ''}</td>
    </tr>`).join('')

  const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${esc(v.docNo)}</title>
<style>
  @page { size: A4 landscape; margin: 12mm; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: "SimSun", "宋体", serif; color: #000; font-size: 12px; }
  .head { text-align: center; margin-bottom: 6px; }
  .head h1 { font-size: 20px; letter-spacing: 8px; font-family: "SimHei", "黑体", sans-serif; }
  .meta { display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 12px; }
  table { width: 100%; border-collapse: collapse; }
  th, td { border: 1px solid #000; padding: 4px 6px; }
  th { background: #f0f0f0; font-family: "SimHei", "黑体", sans-serif; font-weight: normal; }
  .digest { width: 30%; }
  .subject { width: 34%; }
  .amt { width: 18%; text-align: right; font-family: "SimSun", serif; }
  .total td { font-weight: bold; border-top: 2px solid #000; }
  .sign { display: flex; justify-content: space-between; margin-top: 14px; font-size: 12px; }
  .sign span { margin-right: 18px; }
</style></head><body>
  <div class="head"><h1>记 账 凭 证</h1></div>
  <div class="meta">
    <span>凭证号：${esc(v.docNo)}</span>
    <span>日期：${esc(v.voucherDate)}</span>
    <span>附单据 ${v.attachmentCount || 0} 张</span>
  </div>
  <table>
    <thead><tr><th>摘要</th><th>会计科目</th><th>借方金额</th><th>贷方金额</th></tr></thead>
    <tbody>
      ${rows}
      <tr class="total">
        <td colspan="2" style="text-align:right">合　计</td>
        <td class="amt">${fmt(v.totalDebit)}</td>
        <td class="amt">${fmt(v.totalCredit)}</td>
      </tr>
    </tbody>
  </table>
  <div class="sign">
    <span>制单：${esc(v.createdBy)}</span>
    <span>记账：${esc(v.postedBy)}</span>
    <span>审核：</span>
    <span>出纳：</span>
  </div>
  <div style="margin-top:6px;font-size:11px;color:#333;">备注：${esc(v.remark)}</div>
</body></html>`

  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
  document.body.appendChild(iframe)
  iframe.contentDocument.write(html)
  iframe.contentDocument.close()
  iframe.contentWindow.focus()
  iframe.contentWindow.print()
  setTimeout(() => iframe.remove(), 3000)
}
