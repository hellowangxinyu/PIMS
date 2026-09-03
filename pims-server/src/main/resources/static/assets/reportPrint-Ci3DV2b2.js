function a(e,i,n){const o=`<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${e}</title>
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
  <div class="head"><h1>${e}</h1></div>
  <div class="sub">${i||""}</div>
  ${n}
</body></html>`,t=document.createElement("iframe");t.style.cssText="position:fixed;right:0;bottom:0;width:0;height:0;border:0;",document.body.appendChild(t),t.contentDocument.write(o),t.contentDocument.close(),t.contentWindow.focus(),t.contentWindow.print(),setTimeout(()=>t.remove(),3e3)}function r(e){return Number(e||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}export{r as f,a as p};
