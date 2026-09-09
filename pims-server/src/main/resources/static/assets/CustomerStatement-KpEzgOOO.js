import{f as x}from"./fmt-DziXqmun.js";import{e as $,an as J,o as p,ao as S,aq as d,aF as m,aG as g,c as N,au as _,ar as Y,at as n,aH as h,aw as Q,r as y,aI as D,as as W,aA as z,av as K,aB as B}from"./vendor-DecCRiBa.js";import{a as T}from"./index-Dsdtu72Q.js";import{t as R}from"./date-eaW13Rd7.js";import{_ as X}from"./index-B_EXzNXl.js";function v(l){return String(l??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function u(l){return Number(l||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}function Z(l){const r=l.customer||{},b=l.lines||[];let s=Number(l.opening||0);const a=b.map(t=>(s+=Number(t.debit||0)-Number(t.credit||0),`<tr>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${v(t.date)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${v(t.docNo)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${v(t.type)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${v(t.note)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.debit?u(t.debit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.credit?u(t.credit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${s<0?"color:#dc2626":""}">${u(s)}</td>
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
      <div style="font-size:11px;color:#6b7280;margin-top:4px">对账期间：${v(l.from)} 至 ${v(l.to)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>客户名称：<b>${v(r.name)}</b></div>
    <div>客户编码：${v(r.code)}</div>
    <div>纳税人识别号：${v(r.taxNo||"—")}</div>
    <div>制单日期：${R()}</div>
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
        <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${Number(l.opening)<0?"color:#dc2626":""}"><b>${u(l.opening)}</b></td>
      </tr>
      ${a||'<tr><td colspan="7" style="padding:14px;text-align:center;border:1px solid #d1d5db;color:#9ca3af">本期无往来记录</td></tr>'}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"><b>本期合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${u(l.debit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${u(l.credit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
      <tr>
        <td colspan="6" style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2"><b>期末余额（客户应付我司）</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2;text-align:right" class="closing"><b>¥ ${u(l.closing)}</b></td>
      </tr>
    </tbody>
  </table>
  <div class="summary">
    <div class="item">本期应收增加：<b>¥ ${u(l.debit)}</b></div>
    <div class="item">本期收款：<b>¥ ${u((b||[]).filter(t=>t.type==="收款").reduce((t,f)=>t+Number(f.credit||0),0))}</b></div>
    <div class="item">本期退货：<b>¥ ${u((b||[]).filter(t=>t.type==="销售退货").reduce((t,f)=>t+Number(f.credit||0),0))}</b></div>
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
</html>`}function ee(l){const r=window.open("","_blank");return r?(r.document.write(Z(l)),r.document.close(),r.focus(),setTimeout(()=>{r.print(),r.close()},200),!0):!1}const te={class:"report-page"},de={class:"toolbar"},ae={key:0,class:"kpi-row"},ie={class:"kpi-card"},le={class:"kpi-value"},oe={class:"kpi-card"},re={class:"kpi-value"},ne={class:"kpi-card"},se={class:"kpi-value green"},pe={class:"kpi-card"},ce={class:"kpi-value"},ue={class:"kpi-card"},be={class:"table-card"},me={class:"card-title"},ge={key:2,class:"empty-tip"},ve={__name:"CustomerStatement",setup(l){const r=$([]),b=$(null),s=$(I()),a=$(null),t=$(!1),f=$([]);function I(){const i=new Date,e=new Date(i.getFullYear(),Math.floor(i.getMonth()/3)*3,1),c=`${e.getFullYear()}-${String(e.getMonth()+1).padStart(2,"0")}-${String(e.getDate()).padStart(2,"0")}`,k=`${i.getFullYear()}-${String(i.getMonth()+1).padStart(2,"0")}-${String(i.getDate()).padStart(2,"0")}`;return[c,k]}function P(i){return f.value.includes(i)}function C(i){return f.value.includes(i+":amount")||f.value.includes("finance:amount")}const H=D(()=>{var i;return(((i=a.value)==null?void 0:i.lines)||[]).filter(e=>e.type==="收款").reduce((e,c)=>e+Number(c.credit||0),0)}),L=D(()=>{var i;return(((i=a.value)==null?void 0:i.lines)||[]).filter(e=>e.type==="销售退货").reduce((e,c)=>e+Number(c.credit||0),0)}),j=D(()=>{var e,c;let i=Number(((e=a.value)==null?void 0:e.opening)||0);return(((c=a.value)==null?void 0:c.lines)||[]).map(k=>(i+=Number(k.debit||0)-Number(k.credit||0),{...k,balance:Number(i.toFixed(2))}))});async function A(){if(!b.value||!s.value||s.value.length!==2){B.warning("请选择客户和对账期间");return}t.value=!0;try{a.value=await T.get("/finance-report/statement",{params:{customerId:b.value,from:s.value[0],to:s.value[1]}})}catch{}finally{t.value=!1}}function E(){ee(a.value)||B.warning("浏览器拦截了弹窗，请允许本站弹出窗口")}return J(async()=>{try{f.value=JSON.parse(localStorage.getItem("user")||"{}").permissions||[]}catch{}try{r.value=await T.get("/customer",{params:{enabled:!0}})}catch{}}),(i,e)=>{var V,M;const c=y("el-option"),k=y("el-select"),O=y("el-date-picker"),F=y("el-button"),U=y("el-skeleton"),w=y("el-table-column"),q=y("el-tag"),G=y("p-table");return p(),S("div",te,[e[9]||(e[9]=d("div",{class:"report-header"},[d("h2",{class:"report-title"},"客户对账单"),d("span",{class:"report-sub"},"期间往来明细 + 期末余额 · 可打印盖章确认")],-1)),d("div",de,[m(k,{modelValue:b.value,"onUpdate:modelValue":e[0]||(e[0]=o=>b.value=o),filterable:"",placeholder:"选择客户",style:{width:"220px"}},{default:g(()=>[(p(!0),S(Y,null,W(r.value,o=>(p(),N(c,{key:o.id,label:o.name,value:o.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"]),m(O,{modelValue:s.value,"onUpdate:modelValue":e[1]||(e[1]=o=>s.value=o),type:"daterange","value-format":"YYYY-MM-DD","range-separator":"至","start-placeholder":"开始日期","end-placeholder":"结束日期",style:{width:"260px"}},null,8,["modelValue"]),m(F,{type:"primary",onClick:A,loading:t.value,disabled:!b.value||!s.value},{default:g(()=>[...e[2]||(e[2]=[z("查询",-1)])]),_:1},8,["loading","disabled"]),P("finance:read")?(p(),N(F,{key:0,onClick:E,disabled:!a.value},{default:g(()=>[...e[3]||(e[3]=[z("打印对账单",-1)])]),_:1},8,["disabled"])):_("",!0)]),t.value?(p(),N(U,{key:0,rows:8,animated:""})):_("",!0),a.value&&!t.value?(p(),S(Y,{key:1},[C("finance-report")?(p(),S("div",ae,[d("div",ie,[e[4]||(e[4]=d("div",{class:"kpi-label"},"期初余额",-1)),d("div",le,"¥"+n(h(x)(a.value.opening)),1)]),d("div",oe,[e[5]||(e[5]=d("div",{class:"kpi-label"},"本期应收增加",-1)),d("div",re,"¥"+n(h(x)(a.value.debit)),1)]),d("div",ne,[e[6]||(e[6]=d("div",{class:"kpi-label"},"本期收款",-1)),d("div",se,"¥"+n(h(x)(H.value)),1)]),d("div",pe,[e[7]||(e[7]=d("div",{class:"kpi-label"},"本期退货",-1)),d("div",ce,"¥"+n(h(x)(L.value)),1)]),d("div",ue,[e[8]||(e[8]=d("div",{class:"kpi-label"},"期末余额（客户应付）",-1)),d("div",{class:Q(["kpi-value",a.value.closing>0?"red":"green"])},[d("b",null,"¥"+n(h(x)(a.value.closing)),1)],2)])])):_("",!0),d("div",be,[d("div",me,n((V=a.value.customer)==null?void 0:V.name)+"（"+n(a.value.from)+" 至 "+n(a.value.to)+"）"+n((M=a.value.customer)!=null&&M.taxNo?" · 税号 "+a.value.customer.taxNo:""),1),m(G,{data:j.value,stripe:"",border:"",style:{width:"100%"}},{default:g(()=>[m(w,{prop:"date",label:"日期",width:"110"}),m(w,{prop:"docNo",label:"单据号","min-width":"140","show-overflow-tooltip":""}),m(w,{prop:"type",label:"业务类型",width:"100",align:"center"},{default:g(({row:o})=>[m(q,{type:{销售立账:"primary",收款:"success",销售退货:"warning"}[o.type]||"info",size:"small"},{default:g(()=>[z(n(o.type),1)]),_:2},1032,["type"])]),_:1}),m(w,{prop:"note",label:"摘要","min-width":"170","show-overflow-tooltip":""}),C("finance-report")?(p(),N(w,{key:0,prop:"debit",label:"应收增加",width:"120",align:"right"},{default:g(({row:o})=>[z(n(o.debit?"¥"+h(x)(o.debit):""),1)]),_:1})):_("",!0),C("finance-report")?(p(),N(w,{key:1,prop:"credit",label:"收款/退货",width:"120",align:"right"},{default:g(({row:o})=>[z(n(o.credit?"¥"+h(x)(o.credit):""),1)]),_:1})):_("",!0),C("finance-report")?(p(),N(w,{key:2,prop:"balance",label:"结转余额",width:"130",align:"right"},{default:g(({row:o})=>[d("span",{style:K(o.balance<0?"color:#ef4444":"")},"¥"+n(h(x)(o.balance)),5)]),_:1})):_("",!0)]),_:1},8,["data"])])],64)):_("",!0),!a.value&&!t.value?(p(),S("div",ge,"选择客户和对账期间后查询")):_("",!0)])}}},ke=X(ve,[["__scopeId","data-v-ebf45130"]]);export{ke as default};
