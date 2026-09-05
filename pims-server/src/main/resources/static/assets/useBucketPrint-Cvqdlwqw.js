import{aE as w,aB as $,e as y,h as o,am as N}from"./vendor-Byv5cfwf.js";function q(s,d={}){const i=d.company||"广东芃远新材料有限公司",p=(s||[]).map(e=>e&&e.row?e:{row:e}).filter(e=>e.row&&e.row.batchNo);if(!p.length)return!1;const l=[];for(const e of p){const c=e.row;if(e.split&&e.split>1){const f=Math.min(200,Math.max(1,Math.floor(e.split)));for(let t=0;t<f;t++)l.push({...c,_bucketQty:e.adjust&&e.adjust[t]!=null?e.adjust[t]:c.qty,_bucketNo:t+1,_bucketTotal:f})}else l.push(c)}const r=l.map(e=>{const c=e.materialName||e.productName||"-",f=e.materialCode||e.productCode||"-",t=e._bucketQty!=null?e._bucketQty:e.qty!=null?e.qty:"-",u=e.unit||"kg",g=M(e.createTime||e.arrivalDate||e.inboundDate),h={PASS:"合 格",CONCESSION:"让步接收",REJECT:"不合格",PENDING:"待检"},x=e.qcStatus?h[e.qcStatus]||e.qcStatus:e.qcResult||"待检",k=e.qcStatus==="PASS"||e.qcResult==="合 格"?"#0a8f3c":e.qcStatus==="REJECT"||e.qcResult==="不合格"?"#d03030":"#333",a=e.qcInspector||e.inspector||"";return`
    <div class="label">
      <div class="company">${b(i)}</div>
      <div class="code-big">${b(f)}</div>
      <div class="name">${b(c)}</div>
      <div class="row"><span>批号：${b(e.batchNo)}</span></div>
      <div class="row"><span>数量：${t} ${u}</span></div>
      ${e._bucketNo?`<div class="bucket">第 ${e._bucketNo} / ${e._bucketTotal} 桶</div>`:""}
      <div class="qc" style="color:${k};font-weight:700">质检：${b(x)}</div>
      ${a?`<div class="row"><span>质检员：${b(a)}</span></div>`:""}
      <div class="date">${g}${e._bucketTotal?` · 批量合计 ${b(String(e.qty))} ${u}`:""}</div>
    </div>`}).join(""),n=window.open("","_blank");return n?(n.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8">
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
</style></head><body>${r}</body></html>`),n.document.close(),n.focus(),setTimeout(()=>{n.print(),n.close()},200),!0):!1}function C(s,d,i={}){if(s=Number(s)||0,d=Math.max(1,Math.floor(d)),Object.keys(i).length===0){const t=Math.round(s/d*100)/100,u=Array(d).fill(t);return u[d-1]=Math.round((s-t*(d-1))*100)/100,u}const p=Object.values(i).reduce((t,u)=>t+Number(u),0),l=[];for(let t=0;t<d;t++)t in i||l.push(t);if(p>s+1e-9)return null;const r=l.length,n=Array(d);for(const[t,u]of Object.entries(i))n[Number(t)]=Number(u);if(r===0)return n;const e=Math.round((s-p)/r*100)/100;for(const t of l)n[t]=e;const c=l[l.length-1],f=n.reduce((t,u,g)=>g===c?t:t+u,0);return n[c]=Math.round((s-f)*100)/100,n}function M(s){return s?String(s).replace("T"," ").substring(0,10):""}function b(s){return String(s??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}function z(){function s(i){return new Promise(p=>{const l=Number(i.qty||0),r=y(1),n=N({}),e=y([]),c=y(!1),f=()=>{const a=C(l,r.value,{...n});if(a==null){c.value=!0;return}c.value=!1,e.value=a};f();const t=()=>{for(const a of Object.keys(n))delete n[a];f()},u=(a,m)=>{m===""||m==null?delete n[a]:n[a]=Number(m),f()},g=()=>e.value.reduce((a,m)=>a+(Number(m)||0),0),h=()=>!c.value&&e.value.length>0&&Math.abs(g()-l)<.005,x=()=>o("div",{style:"min-width:420px"},[o("div",{style:"margin-bottom:10px;color:#64748b;font-size:13px"},`${i.materialName||i.productName||""} ${i.batchNo||""} · 总量 ${l} ${i.unit||"kg"}`),o("div",{style:"display:flex;align-items:center;gap:8px;margin-bottom:10px"},[o("span",{style:"font-size:13px"},"分桶数："),o("div",{class:"el-input-number el-input-number--small",style:"width:120px"},[o("input",{class:"el-input__inner",type:"number",min:1,max:200,value:r.value,onInput:a=>{r.value=Math.max(1,Math.min(200,Number(a.target.value)||1)),t()}})]),o("span",{style:"font-size:12px;color:#94a3b8"},"改桶数会重置每桶的微调")]),c.value||e.value.length>1?o("table",{class:"el-table__inner-wrapper",style:"width:100%;border-collapse:collapse;margin-bottom:8px;font-size:12px"},[o("thead",null,o("tr",null,[o("th",{style:"border:1px solid #e2e8f0;padding:4px 8px;background:#f8fafc"},"桶号"),o("th",{style:"border:1px solid #e2e2e0;padding:4px 8px;background:#f8fafc"},"数量"),o("th",{style:"border:1px solid #e2e8f0;padding:4px 8px;background:#f8fafc"},"说明")])),o("tbody",null,e.value.map((a,m)=>o("tr",{key:m},[o("td",{style:"border:1px solid #e2e8f0;padding:3px 8px;text-align:center"},`第 ${m+1} / ${e.value.length} 桶`),o("td",{style:"border:1px solid #e2e8f0;padding:2px 6px"},o("input",{type:"number",step:"0.01",value:a,style:"width:100%;border:1px solid #dcdfe6;border-radius:4px;padding:2px 6px;font-size:12px",onInput:v=>u(m,v.target.value)})),o("td",{style:"border:1px solid #e2e8f0;padding:3px 8px;color:#94a3b8;font-size:11px"},m in n?"已手动调整（锁定）":"自动均分（可改）")])))]):null,c.value||e.value.length>1?o("div",{style:`font-size:13px;font-weight:600;color:${h()?"#16a34a":"#ef4444"}`},c.value?`手动调整的桶合计已超过总量 ${l}，请调低后再打印（禁止打印）`:`各桶合计：${Math.round(g()*100)/100} / 总量 ${l} ${h()?"✓ 守恒":"✗ 不守恒，禁止打印"}`):null]);return w({title:`分桶打印 · ${i.batchNo||""}`,message:()=>x(),showCancelButton:!0,distinguishCancelAndClose:!0,confirmButtonText:"打印",cancelButtonText:"不拆分（整单一张）",closeOnClickModal:!1,beforeClose:(a,m,v)=>{if(a==="confirm"){if(r.value>1&&!h()){$.warning("各桶合计与总量不一致，禁止打印");return}v()}else v()}}).then(()=>{p(r.value>1?{split:r.value,adjust:e.value.slice()}:null)}).catch(a=>{p(a==="cancel"?null:void 0)})})}async function d(i){const p=(i||[]).filter(Boolean);if(!p.length)return!1;const l=[];for(const r of p){const n=await s(r);if(n===void 0)return!1;l.push(n?{row:r,split:n.split,adjust:n.adjust}:{row:r})}return q(l)}return{openBucketDialog:s,printWithBuckets:d}}export{z as u};
