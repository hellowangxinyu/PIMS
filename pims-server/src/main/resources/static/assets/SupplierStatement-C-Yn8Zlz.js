import{f}from"./fmt-DziXqmun.js";import{e as $,an as G,o as p,ao as N,aq as d,aF as m,aG as g,c as S,au as _,ar as F,at as r,aH as x,aw as J,r as h,aI as D,as as Q,aA as z,av as W,aB as Y}from"./vendor-CPRsvqa0.js";import{a as B}from"./index-BXKmxhh5.js";import{t as K}from"./date-eaW13Rd7.js";import{_ as R}from"./index-U0EtEsns.js";function y(l){return String(l??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function u(l){return Number(l||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}function X(l){const n=l.supplier||{},b=l.lines||[];let s=Number(l.opening||0);const o=b.map(t=>(s+=Number(t.debit||0)-Number(t.credit||0),`<tr>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${y(t.date)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${y(t.docNo)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${y(t.type)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${y(t.note)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.debit?u(t.debit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.credit?u(t.credit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${s<0?"color:#dc2626":""}">${u(s)}</td>
    </tr>`)).join("");return`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>供应商对账单</title>
<style>
  @page { size: A4 portrait; margin: 14mm }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; color: #111827; font-size: 12px; margin: 0 }
  .head { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #111827; padding-bottom: 10px; margin-bottom: 14px }
  .head h1 { font-size: 20px; margin: 0; letter-spacing: 2px }
  .head .co { font-size: 15px; font-weight: 700 }
  .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 24px; margin-bottom: 14px; font-size: 12px }
  .meta b { color: #374151 }
  table { width: 100%; border-collapse: collapse; font-size: 11px }
  thead th { background: #f3f4f6; padding: 7px 8px; border: 1px solid #d1d5db; font-weight: 600 }
  .summary { margin-top: 14px; display: flex; gap: 28px; font-size: 12px }
  .summary .item b { font-size: 14px }
  .closing { color: #dc2626 }
  .sign { margin-top: 46px; display: flex; justify-content: space-between; font-size: 12px }
  .sign div { width: 220px }
  .footnote { margin-top: 24px; font-size: 10px; color: #6b7280; line-height: 1.7 }
  @media print { .no-print { display: none } }
</style>
</head>
<body>
  <div class="head">
    <div>
      <h1>供应商对账单</h1>
      <div style="font-size:11px;color:#6b7280;margin-top:4px">对账期间：${y(l.from)} 至 ${y(l.to)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>供应商名称：<b>${y(n.name)}</b></div>
    <div>供应商编码：${y(n.code)}</div>
    <div>制单日期：${K()}</div>
  </div>
  <table>
    <thead>
      <tr>
        <th style="width:12%">日期</th><th style="width:18%">单据号</th><th style="width:12%">业务类型</th>
        <th style="width:26%">摘要</th><th style="width:10%">应付增加</th><th style="width:10%">付/退</th><th style="width:12%">结转余额</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td colspan="4" style="padding:6px 8px;border:1px solid #d1d5db"><b>期初余额</b></td>
        <td colspan="2" style="border:1px solid #d1d5db"></td>
        <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${Number(l.opening)<0?"color:#dc2626":""}"><b>${u(l.opening)}</b></td>
      </tr>
      ${o||'<tr><td colspan="7" style="padding:14px;text-align:center;border:1px solid #d1d5db;color:#9ca3af">本期无往来记录</td></tr>'}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"><b>本期合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${u(l.debit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${u(l.credit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
      <tr>
        <td colspan="6" style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2"><b>期末余额（我司应付供应商）</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2;text-align:right" class="closing"><b>¥ ${u(l.closing)}</b></td>
      </tr>
    </tbody>
  </table>
  <div class="summary">
    <div class="item">本期应付增加：<b>¥ ${u(l.debit)}</b></div>
    <div class="item">本期付款：<b>¥ ${u((b||[]).filter(t=>t.type==="付款").reduce((t,v)=>t+Number(v.credit||0),0))}</b></div>
    <div class="item">本期退货冲减：<b>¥ ${u((b||[]).filter(t=>t.type==="退货冲减").reduce((t,v)=>t+Number(v.credit||0),0))}</b></div>
  </div>
  <div class="sign">
    <div>制表人：____________</div>
    <div>供应商确认（盖章）：____________</div>
    <div>日期：____________</div>
  </div>
  <div class="footnote">
    对账口径：应付余额 = 应付立账累计 − 付款累计 − 采购退货冲减累计，与系统应付台账一致；
    如对以上数据有异议，请于收到对账单 7 日内与我司财务核对。
  </div>
</body>
</html>`}function Z(l){const n=window.open("","_blank");return n?(n.document.write(X(l)),n.document.close(),n.focus(),setTimeout(()=>{n.print(),n.close()},200),!0):!1}const ee={class:"report-page"},te={class:"toolbar"},de={key:0,class:"kpi-row"},ae={class:"kpi-card"},le={class:"kpi-value"},ie={class:"kpi-card"},oe={class:"kpi-value"},ne={class:"kpi-card"},re={class:"kpi-value green"},se={class:"kpi-card"},pe={class:"kpi-value"},ce={class:"kpi-card"},ue={class:"table-card"},be={class:"card-title"},me={key:2,class:"empty-tip"},ge={__name:"SupplierStatement",setup(l){const n=$([]),b=$(null),s=$(T()),o=$(null),t=$(!1),v=$([]);function T(){const a=new Date,e=new Date(a.getFullYear(),Math.floor(a.getMonth()/3)*3,1),c=`${e.getFullYear()}-${String(e.getMonth()+1).padStart(2,"0")}-${String(e.getDate()).padStart(2,"0")}`,k=`${a.getFullYear()}-${String(a.getMonth()+1).padStart(2,"0")}-${String(a.getDate()).padStart(2,"0")}`;return[c,k]}function I(a){return v.value.includes(a)}function M(a){return v.value.includes(a+":amount")||v.value.includes("finance:amount")}const P=D(()=>{var a;return(((a=o.value)==null?void 0:a.lines)||[]).filter(e=>e.type==="付款").reduce((e,c)=>e+Number(c.credit||0),0)}),H=D(()=>{var a;return(((a=o.value)==null?void 0:a.lines)||[]).filter(e=>e.type==="退货冲减").reduce((e,c)=>e+Number(c.credit||0),0)}),L=D(()=>{var e,c;let a=Math.round(Number(((e=o.value)==null?void 0:e.opening)||0)*100);return(((c=o.value)==null?void 0:c.lines)||[]).map(k=>(a+=Math.round(Number(k.debit||0)*100)-Math.round(Number(k.credit||0)*100),{...k,balance:a/100}))});async function j(){if(!b.value||!s.value||s.value.length!==2){Y.warning("请选择供应商和对账期间");return}t.value=!0;try{o.value=await B.get("/finance-report/supplier-statement",{params:{supplierId:b.value,from:s.value[0],to:s.value[1]}})}catch{}finally{t.value=!1}}function A(){Z(o.value)||Y.warning("浏览器拦截了弹窗，请允许本站弹出窗口")}return G(async()=>{try{v.value=JSON.parse(localStorage.getItem("user")||"{}").permissions||[]}catch{}try{n.value=await B.get("/supplier",{params:{enabled:!0}})}catch{}}),(a,e)=>{var V;const c=h("el-option"),k=h("el-select"),E=h("el-date-picker"),C=h("el-button"),O=h("el-skeleton"),w=h("el-table-column"),U=h("el-tag"),q=h("p-table");return p(),N("div",ee,[e[9]||(e[9]=d("div",{class:"report-header"},[d("h2",{class:"report-title"},"供应商对账单"),d("span",{class:"report-sub"},"期间往来明细 + 期末余额 · 可打印盖章确认")],-1)),d("div",te,[m(k,{modelValue:b.value,"onUpdate:modelValue":e[0]||(e[0]=i=>b.value=i),filterable:"",placeholder:"选择供应商",style:{width:"220px"}},{default:g(()=>[(p(!0),N(F,null,Q(n.value,i=>(p(),S(c,{key:i.id,label:i.name,value:i.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"]),m(E,{modelValue:s.value,"onUpdate:modelValue":e[1]||(e[1]=i=>s.value=i),type:"daterange","value-format":"YYYY-MM-DD","range-separator":"至","start-placeholder":"开始日期","end-placeholder":"结束日期",style:{width:"260px"}},null,8,["modelValue"]),m(C,{type:"primary",onClick:j,loading:t.value,disabled:!b.value||!s.value},{default:g(()=>[...e[2]||(e[2]=[z("查询",-1)])]),_:1},8,["loading","disabled"]),I("finance:read")?(p(),S(C,{key:0,onClick:A,disabled:!o.value},{default:g(()=>[...e[3]||(e[3]=[z("打印对账单",-1)])]),_:1},8,["disabled"])):_("",!0)]),t.value?(p(),S(O,{key:0,rows:8,animated:""})):_("",!0),o.value&&!t.value?(p(),N(F,{key:1},[M("finance-report")?(p(),N("div",de,[d("div",ae,[e[4]||(e[4]=d("div",{class:"kpi-label"},"期初余额",-1)),d("div",le,"¥"+r(x(f)(o.value.opening)),1)]),d("div",ie,[e[5]||(e[5]=d("div",{class:"kpi-label"},"本期应付增加",-1)),d("div",oe,"¥"+r(x(f)(o.value.debit)),1)]),d("div",ne,[e[6]||(e[6]=d("div",{class:"kpi-label"},"本期付款",-1)),d("div",re,"¥"+r(x(f)(P.value)),1)]),d("div",se,[e[7]||(e[7]=d("div",{class:"kpi-label"},"本期退货冲减",-1)),d("div",pe,"¥"+r(x(f)(H.value)),1)]),d("div",ce,[e[8]||(e[8]=d("div",{class:"kpi-label"},"期末余额（我司应付）",-1)),d("div",{class:J(["kpi-value",o.value.closing>0?"red":"green"])},[d("b",null,"¥"+r(x(f)(o.value.closing)),1)],2)])])):_("",!0),d("div",ue,[d("div",be,r((V=o.value.supplier)==null?void 0:V.name)+"（"+r(o.value.from)+" 至 "+r(o.value.to)+"）",1),m(q,{data:L.value,stripe:"",border:"",style:{width:"100%"}},{default:g(()=>[m(w,{prop:"date",label:"日期",width:"110"}),m(w,{prop:"docNo",label:"单据号","min-width":"140","show-overflow-tooltip":""}),m(w,{prop:"type",label:"业务类型",width:"100",align:"center"},{default:g(({row:i})=>[m(U,{type:{应付立账:"primary",付款:"success",退货冲减:"warning"}[i.type]||"info",size:"small"},{default:g(()=>[z(r(i.type),1)]),_:2},1032,["type"])]),_:1}),m(w,{prop:"note",label:"摘要","min-width":"170","show-overflow-tooltip":""}),M("finance-report")?(p(),S(w,{key:0,prop:"debit",label:"应付增加",width:"120",align:"right"},{default:g(({row:i})=>[z(r(i.debit?"¥"+x(f)(i.debit):""),1)]),_:1})):_("",!0),M("finance-report")?(p(),S(w,{key:1,prop:"credit",label:"付款/退货",width:"120",align:"right"},{default:g(({row:i})=>[z(r(i.credit?"¥"+x(f)(i.credit):""),1)]),_:1})):_("",!0),M("finance-report")?(p(),S(w,{key:2,prop:"balance",label:"结转余额",width:"130",align:"right"},{default:g(({row:i})=>[d("span",{style:W(i.balance<0?"color:#ef4444":"")},"¥"+r(x(f)(i.balance)),5)]),_:1})):_("",!0)]),_:1},8,["data"])])],64)):_("",!0),!o.value&&!t.value?(p(),N("div",me,"选择供应商和对账期间后查询")):_("",!0)])}}},ye=R(ge,[["__scopeId","data-v-9329b6c4"]]);export{ye as default};
