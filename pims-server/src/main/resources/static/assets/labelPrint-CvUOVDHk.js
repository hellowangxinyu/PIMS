function y(e,s={}){const o=(e||[]).filter(t=>t&&t.batchNo);if(!o.length)return!1;const c=s.company||"广东芃远新材料有限公司",l=o.map(t=>{const m=t.materialName||t.productName||"-",r=t.materialCode||t.productCode||"-",d=t.qty!=null?t.qty:"-",p=t.unit||"kg",g=v(t.createTime||t.arrivalDate||t.inboundDate),u={PASS:"合 格",CONCESSION:"让步接收",REJECT:"不合格",PENDING:"待检"},f=t.qcStatus?u[t.qcStatus]||t.qcStatus:t.qcResult||"待检",b=t.qcStatus==="PASS"||t.qcResult==="合 格"?"#0a8f3c":t.qcStatus==="REJECT"||t.qcResult==="不合格"?"#d03030":"#333",i=t.qcInspector||t.inspector||"";return`
    <div class="label">
      <div class="company">${n(c)}</div>
      <div class="code-big">${n(r)}</div>
      <div class="name">${n(m)}</div>
      <div class="row"><span>批号：${n(t.batchNo)}</span></div>
      <div class="row"><span>数量：${d} ${p}</span></div>
      <div class="qc" style="color:${b};font-weight:700">质检：${n(f)}</div>
      ${i?`<div class="row"><span>质检员：${n(i)}</span></div>`:""}
      <div class="date">${g}</div>
    </div>`}).join(""),a=window.open("","_blank");return a?(a.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8">
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
</style></head><body>${l}</body></html>`),a.document.close(),a.focus(),setTimeout(()=>{a.print(),a.close()},200),!0):!1}function v(e){return e?String(e).replace("T"," ").substring(0,10):""}function n(e){return String(e??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}export{y as p};
