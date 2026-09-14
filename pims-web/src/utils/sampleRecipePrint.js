/**
 * v7.7 打样配方单打印（qcPrint 同模式：纯函数拼 HTML → window.open → print）。
 * @param {Object} f 打样配方（/sample/{id}/formula 返回结构）：formulaNo/sampleNo/customerName/
 *   materialCode/materialName/subCategory/mainMaterial/colorSeries/totalQty/estCost/sampleLocation/items[]
 */
const SUB_NAME = {
  CQ: '清漆', CD: '底漆', CW: '面漆', CB: '背漆',
  BQ: '清浆', BD: '底浆', BW: '白浆', BC: '彩浆', BL: '蓝浆'
}
const MAIN_NAME = { CZ: '聚酯', CF: '氟碳', CE: '环氧', CA: '丙烯酸' }
const COLOR_NAME = { BK: '黑', WH: '白', BU: '蓝', GN: '绿', GY: '灰', RD: '红', YW: '黄' }
const CAT_NAME = { A: '助剂', P: '颜料', F: '填料', R: '树脂', S: '溶剂', B: '半成品(色浆)' }

function esc(s) {
  if (s == null) return ''
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function printSampleFormula(f) {
  if (!f || !f.formulaNo) return false
  const sub = f.subCategory || ''
  const cat3 = [MAIN_NAME[f.mainMaterial] || f.mainMaterial || '', COLOR_NAME[f.colorSeries] || f.colorSeries || '']
    .filter(Boolean).join(' / ')
  const rows = (f.items || []).map((it, i) => `
    <tr>
      <td class="c">${i + 1}</td>
      <td>${esc(it.materialCode)}</td>
      <td>${esc(it.materialName)}</td>
      <td class="c">${CAT_NAME[it.category] || it.category || ''}</td>
      <td class="r">${Number(it.qty ?? 0).toFixed(1)}</td>
    </tr>`).join('')
  const total = (f.items || []).reduce((s, it) => s + (Number(it.qty) || 0), 0)

  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>打样配方单 ${esc(f.formulaNo)}</title>
<style>
  body { font-family: "Microsoft YaHei", SimSun, sans-serif; color: #111; margin: 0; padding: 18px 22px; font-size: 13px; }
  h2 { text-align: center; margin: 0 0 4px; font-size: 20px; letter-spacing: 2px; }
  .sub { text-align: center; color: #555; margin-bottom: 14px; font-size: 12px; }
  .meta { width: 100%; border-collapse: collapse; margin-bottom: 12px; }
  .meta td { border: 1px solid #999; padding: 5px 8px; }
  .meta .k { background: #f3f4f6; width: 90px; text-align: center; white-space: nowrap; }
  table.list { width: 100%; border-collapse: collapse; }
  table.list th, table.list td { border: 1px solid #999; padding: 5px 8px; }
  table.list th { background: #f3f4f6; }
  .c { text-align: center; } .r { text-align: right; }
  .total td { background: #f9fafb; font-weight: 700; }
  .foot { margin-top: 16px; display: flex; justify-content: space-between; }
  .loc { border: 1.5px solid #333; display: inline-block; padding: 3px 14px; font-weight: 700; border-radius: 4px; }
  @media print { body { padding: 0; } }
</style></head><body>
<h2>打样配方单</h2>
<div class="sub">${esc(f.formulaNo)} ｜ ${new Date().toLocaleDateString('zh-CN')}</div>
<table class="meta">
  <tr>
    <td class="k">打样单号</td><td>${esc(f.sampleNo || '')}</td>
    <td class="k">客户/线索</td><td>${esc(f.customerName || '')}</td>
  </tr>
  <tr>
    <td class="k">成品名称</td><td><b>${esc(f.materialName || '')}</b></td>
    <td class="k">成品编码</td><td>${esc(f.materialCode || '（待保存生成）')}</td>
  </tr>
  <tr>
    <td class="k">分类</td><td>${esc(SUB_NAME[sub] || sub || '')} ｜ ${esc(cat3)}</td>
    <td class="k">打样总量</td><td>${Number(f.totalQty ?? total).toFixed(1)} g</td>
  </tr>
  <tr>
    <td class="k">寄样样板</td><td>${esc(f.sampleQty ?? '-')} ${esc(f.sampleUnit || '张')}（${f.sampleSize === 'A4' ? 'A4 大小' : '常规尺寸'}）</td>
  </tr>
  <tr>
    <td class="k">估算成本</td><td>${f.estCost != null ? Number(f.estCost).toFixed(2) + ' 元/kg' : '—'}</td>
    <td class="k">留样位置</td><td>${f.sampleLocation ? esc(f.sampleLocation) : '—'}</td>
  </tr>
</table>
<table class="list">
  <thead><tr><th class="c" style="width:44px">序号</th><th style="width:110px">物料编码</th><th>品名</th><th class="c" style="width:100px">类别</th><th class="r" style="width:110px">用量(g)</th></tr></thead>
  <tbody>${rows}
    <tr class="total"><td class="c">合计</td><td colspan="3"></td><td class="r">${total.toFixed(1)}</td></tr>
  </tbody>
</table>
<div class="foot">
  <span>打样员：${esc(f.assignee || '')}</span>
  <span class="loc">留样：${esc(f.sampleLocation || '未登记')}</span>
  <span>审核：＿＿＿＿＿＿</span>
</div>
</body></html>`

  const win = window.open('', '_blank')
  if (!win) return false
  win.document.write(html)
  win.document.close()
  setTimeout(() => { win.print(); win.close() }, 300)
  return true
}
