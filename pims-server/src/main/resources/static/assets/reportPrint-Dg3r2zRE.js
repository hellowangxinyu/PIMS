function i(t){return String(t??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function r(t,n,o){const a=`<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${i(t)}</title>
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
  <div class="head"><h1>${i(t)}</h1></div>
  <div class="sub">${i(n)}</div>
  ${o}
</body></html>`,e=document.createElement("iframe");e.style.cssText="position:fixed;right:0;bottom:0;width:0;height:0;border:0;",document.body.appendChild(e),e.contentDocument.write(a),e.contentDocument.close(),e.contentWindow.focus(),e.contentWindow.print(),setTimeout(()=>e.remove(),3e3)}function m(t){return Number(t||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}export{i as e,m as f,r as p};
