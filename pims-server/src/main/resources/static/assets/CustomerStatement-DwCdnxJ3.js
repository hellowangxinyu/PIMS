import{f as x}from"./fmt-DziXqmun.js";import{e as w,an as J,o as p,ao as S,aq as d,aF as b,aG as m,c as N,au as _,ar as M,at as n,aH as y,aw as Q,r as h,aI as D,as as W,aA as z,av as K,aB as Y}from"./vendor-Byv5cfwf.js";import{a as B}from"./index-CT0jxrdf.js";import{_ as R}from"./index-P8jMBXf9.js";function g(i){return String(i??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function c(i){return Number(i||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}function X(i){const s=i.customer||{},u=i.lines||[];let r=Number(i.opening||0);const a=u.map(t=>(r+=Number(t.debit||0)-Number(t.credit||0),`<tr>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${g(t.date)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${g(t.docNo)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${g(t.type)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${g(t.note)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.debit?c(t.debit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.credit?c(t.credit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${r<0?"color:#dc2626":""}">${c(r)}</td>
    </tr>`)).join("");return`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>客户对账单</title>
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
      <h1>客户对账单</h1>
      <div style="font-size:11px;color:#6b7280;margin-top:4px">对账期间：${g(i.from)} 至 ${g(i.to)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>客户名称：<b>${g(s.name)}</b></div>
    <div>客户编码：${g(s.code)}</div>
    <div>纳税人识别号：${g(s.taxNo||"—")}</div>
    <div>制单日期：${new Date().toISOString().slice(0,10)}</div>
  </div>
  <table>
    <thead>
      <tr>
        <th style="width:12%">日期</th><th style="width:18%">单据号</th><th style="width:12%">业务类型</th>
        <th style="width:26%">摘要</th><th style="width:10%">应收增加</th><th style="width:10%">收/退</th><th style="width:12%">结转余额</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td colspan="4" style="padding:6px 8px;border:1px solid #d1d5db"><b>期初余额</b></td>
        <td colspan="2" style="border:1px solid #d1d5db"></td>
        <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${Number(i.opening)<0?"color:#dc2626":""}"><b>${c(i.opening)}</b></td>
      </tr>
      ${a||'<tr><td colspan="7" style="padding:14px;text-align:center;border:1px solid #d1d5db;color:#9ca3af">本期无往来记录</td></tr>'}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"><b>本期合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${c(i.debit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${c(i.credit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
      <tr>
        <td colspan="6" style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2"><b>期末余额（客户应付我司）</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2;text-align:right" class="closing"><b>¥ ${c(i.closing)}</b></td>
      </tr>
    </tbody>
  </table>
  <div class="summary">
    <div class="item">本期应收增加：<b>¥ ${c(i.debit)}</b></div>
    <div class="item">本期收款：<b>¥ ${c((u||[]).filter(t=>t.type==="收款").reduce((t,v)=>t+Number(v.credit||0),0))}</b></div>
    <div class="item">本期退货：<b>¥ ${c((u||[]).filter(t=>t.type==="销售退货").reduce((t,v)=>t+Number(v.credit||0),0))}</b></div>
  </div>
  <div class="sign">
    <div>制表人：____________</div>
    <div>客户确认（盖章）：____________</div>
    <div>日期：____________</div>
  </div>
  <div class="footnote">
    对账口径：应收余额 = 应收立账累计 − 收款累计 − 销售退货冲减累计，与系统应收台账一致；
    如对以上数据有异议，请于收到对账单 7 日内与我司财务核对。
  </div>
</body>
</html>`}function Z(i){const s=window.open("","_blank");return s?(s.document.write(X(i)),s.document.close(),s.focus(),setTimeout(()=>{s.print(),s.close()},200),!0):!1}const ee={class:"report-page"},te={class:"toolbar"},de={key:0,class:"kpi-row"},ae={class:"kpi-card"},ie={class:"kpi-value"},le={class:"kpi-card"},oe={class:"kpi-value"},se={class:"kpi-card"},ne={class:"kpi-value green"},re={class:"kpi-card"},pe={class:"kpi-value"},ce={class:"kpi-card"},ue={class:"table-card"},be={class:"card-title"},me={key:2,class:"empty-tip"},ge={__name:"CustomerStatement",setup(i){const s=w([]),u=w(null),r=w(O()),a=w(null),t=w(!1),v=w([]);function O(){const o=new Date;return[new Date(o.getFullYear(),Math.floor(o.getMonth()/3)*3,1).toISOString().slice(0,10),o.toISOString().slice(0,10)]}function T(o){return v.value.includes(o)}function C(o){return v.value.includes(o+":amount")||v.value.includes("finance:amount")}const P=D(()=>{var o;return(((o=a.value)==null?void 0:o.lines)||[]).filter(e=>e.type==="收款").reduce((e,f)=>e+Number(f.credit||0),0)}),H=D(()=>{var o;return(((o=a.value)==null?void 0:o.lines)||[]).filter(e=>e.type==="销售退货").reduce((e,f)=>e+Number(f.credit||0),0)}),j=D(()=>{var e,f;let o=Number(((e=a.value)==null?void 0:e.opening)||0);return(((f=a.value)==null?void 0:f.lines)||[]).map($=>(o+=Number($.debit||0)-Number($.credit||0),{...$,balance:Number(o.toFixed(2))}))});async function A(){if(!u.value||!r.value||r.value.length!==2){Y.warning("请选择客户和对账期间");return}t.value=!0;try{a.value=await B.get("/finance-report/statement",{params:{customerId:u.value,from:r.value[0],to:r.value[1]}})}catch{}finally{t.value=!1}}function E(){Z(a.value)||Y.warning("浏览器拦截了弹窗，请允许本站弹出窗口")}return J(async()=>{try{v.value=JSON.parse(localStorage.getItem("user")||"{}").permissions||[]}catch{}try{s.value=await B.get("/customer",{params:{enabled:!0}})}catch{}}),(o,e)=>{var F,I;const f=h("el-option"),$=h("el-select"),L=h("el-date-picker"),V=h("el-button"),U=h("el-skeleton"),k=h("el-table-column"),q=h("el-tag"),G=h("p-table");return p(),S("div",ee,[e[9]||(e[9]=d("div",{class:"report-header"},[d("h2",{class:"report-title"},"客户对账单"),d("span",{class:"report-sub"},"期间往来明细 + 期末余额 · 可打印盖章确认")],-1)),d("div",te,[b($,{modelValue:u.value,"onUpdate:modelValue":e[0]||(e[0]=l=>u.value=l),filterable:"",placeholder:"选择客户",style:{width:"220px"}},{default:m(()=>[(p(!0),S(M,null,W(s.value,l=>(p(),N(f,{key:l.id,label:l.name,value:l.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"]),b(L,{modelValue:r.value,"onUpdate:modelValue":e[1]||(e[1]=l=>r.value=l),type:"daterange","value-format":"YYYY-MM-DD","range-separator":"至","start-placeholder":"开始日期","end-placeholder":"结束日期",style:{width:"260px"}},null,8,["modelValue"]),b(V,{type:"primary",onClick:A,loading:t.value,disabled:!u.value||!r.value},{default:m(()=>[...e[2]||(e[2]=[z("查询",-1)])]),_:1},8,["loading","disabled"]),T("finance:read")?(p(),N(V,{key:0,onClick:E,disabled:!a.value},{default:m(()=>[...e[3]||(e[3]=[z("打印对账单",-1)])]),_:1},8,["disabled"])):_("",!0)]),t.value?(p(),N(U,{key:0,rows:8,animated:""})):_("",!0),a.value&&!t.value?(p(),S(M,{key:1},[C("finance-report")?(p(),S("div",de,[d("div",ae,[e[4]||(e[4]=d("div",{class:"kpi-label"},"期初余额",-1)),d("div",ie,"¥"+n(y(x)(a.value.opening)),1)]),d("div",le,[e[5]||(e[5]=d("div",{class:"kpi-label"},"本期应收增加",-1)),d("div",oe,"¥"+n(y(x)(a.value.debit)),1)]),d("div",se,[e[6]||(e[6]=d("div",{class:"kpi-label"},"本期收款",-1)),d("div",ne,"¥"+n(y(x)(P.value)),1)]),d("div",re,[e[7]||(e[7]=d("div",{class:"kpi-label"},"本期退货",-1)),d("div",pe,"¥"+n(y(x)(H.value)),1)]),d("div",ce,[e[8]||(e[8]=d("div",{class:"kpi-label"},"期末余额（客户应付）",-1)),d("div",{class:Q(["kpi-value",a.value.closing>0?"red":"green"])},[d("b",null,"¥"+n(y(x)(a.value.closing)),1)],2)])])):_("",!0),d("div",ue,[d("div",be,n((F=a.value.customer)==null?void 0:F.name)+"（"+n(a.value.from)+" 至 "+n(a.value.to)+"）"+n((I=a.value.customer)!=null&&I.taxNo?" · 税号 "+a.value.customer.taxNo:""),1),b(G,{data:j.value,stripe:"",border:"",style:{width:"100%"}},{default:m(()=>[b(k,{prop:"date",label:"日期",width:"110"}),b(k,{prop:"docNo",label:"单据号","min-width":"140","show-overflow-tooltip":""}),b(k,{prop:"type",label:"业务类型",width:"100",align:"center"},{default:m(({row:l})=>[b(q,{type:{销售立账:"primary",收款:"success",销售退货:"warning"}[l.type]||"info",size:"small"},{default:m(()=>[z(n(l.type),1)]),_:2},1032,["type"])]),_:1}),b(k,{prop:"note",label:"摘要","min-width":"170","show-overflow-tooltip":""}),C("finance-report")?(p(),N(k,{key:0,prop:"debit",label:"应收增加",width:"120",align:"right"},{default:m(({row:l})=>[z(n(l.debit?"¥"+y(x)(l.debit):""),1)]),_:1})):_("",!0),C("finance-report")?(p(),N(k,{key:1,prop:"credit",label:"收款/退货",width:"120",align:"right"},{default:m(({row:l})=>[z(n(l.credit?"¥"+y(x)(l.credit):""),1)]),_:1})):_("",!0),C("finance-report")?(p(),N(k,{key:2,prop:"balance",label:"结转余额",width:"130",align:"right"},{default:m(({row:l})=>[d("span",{style:K(l.balance<0?"color:#ef4444":"")},"¥"+n(y(x)(l.balance)),5)]),_:1})):_("",!0)]),_:1},8,["data"])])],64)):_("",!0),!a.value&&!t.value?(p(),S("div",me,"选择客户和对账期间后查询")):_("",!0)])}}},ye=R(ge,[["__scopeId","data-v-b20eb975"]]);export{ye as default};
