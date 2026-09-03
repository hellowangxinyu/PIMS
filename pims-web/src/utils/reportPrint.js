/**
 * 通用报表打印（v5.61）—— 准则格式报表（资产负债表/利润表/现金流量表/余额表/明细账）共用
 * 拼 HTML → 隐藏 iframe 打印
 */
export function printTableHtml(title, subtitle, bodyHtml) {
  const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${title}</title>
<style>
  @page { size: A4 portrait; margin: 14mm; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: "SimSun", "宋体", serif; color: #000; font-size: 12px; }
  .head { text-align: center; margin-bottom: 4px; }
  .head h1 { font-size: 18px; letter-spacing: 4px; font-family: "SimHei", "黑体", sans-serif; }
  .sub { text-align: center; font-size: 12px; margin-bottom: 10px; color: #333; }
  table { width: 100%; border-collapse: collapse; }
  th, td { border: 1px solid #000; padding: 4px 6px; }
  th { background: #f0f0f0; font-family: "SimHei", "黑体", sans-serif; font-weight: normal; }
  .amt { text-align: right; font-variant-numeric: tabular-nums; }
  .strong td { font-weight: bold; }
  .indent { padding-left: 18px; }
</style></head><body>
  <div class="head"><h1>${title}</h1></div>
  <div class="sub">${subtitle || ''}</div>
  ${bodyHtml}
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

export function fmtAmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
