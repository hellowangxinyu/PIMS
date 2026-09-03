import{_ as A,l as k,o as J,b as p,c as S,e as d,an as b,ao as m,au as w,g as x,F as V,t as r,h as Q,aq as _,ar as D,f as W,k as $,n as G,E as I}from"./index-BnW5TqdP.js";import{a as M}from"./index-B67FkASZ.js";function h(l){return String(l??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function c(l){return Number(l||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}function K(l){const n=l.supplier||{},u=l.lines||[];let s=Number(l.opening||0);const o=u.map(t=>(s+=Number(t.debit||0)-Number(t.credit||0),`<tr>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${h(t.date)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${h(t.docNo)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${h(t.type)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${h(t.note)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.debit?c(t.debit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${t.credit?c(t.credit):""}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${s<0?"color:#dc2626":""}">${c(s)}</td>
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
      <div style="font-size:11px;color:#6b7280;margin-top:4px">对账期间：${h(l.from)} 至 ${h(l.to)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>供应商名称：<b>${h(n.name)}</b></div>
    <div>供应商编码：${h(n.code)}</div>
    <div>制单日期：${new Date().toISOString().slice(0,10)}</div>
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
        <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${Number(l.opening)<0?"color:#dc2626":""}"><b>${c(l.opening)}</b></td>
      </tr>
      ${o||'<tr><td colspan="7" style="padding:14px;text-align:center;border:1px solid #d1d5db;color:#9ca3af">本期无往来记录</td></tr>'}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"><b>本期合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${c(l.debit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${c(l.credit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
      <tr>
        <td colspan="6" style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2"><b>期末余额（我司应付供应商）</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2;text-align:right" class="closing"><b>¥ ${c(l.closing)}</b></td>
      </tr>
    </tbody>
  </table>
  <div class="summary">
    <div class="item">本期应付增加：<b>¥ ${c(l.debit)}</b></div>
    <div class="item">本期付款：<b>¥ ${c((u||[]).filter(t=>t.type==="付款").reduce((t,g)=>t+Number(g.credit||0),0))}</b></div>
    <div class="item">本期退货冲减：<b>¥ ${c((u||[]).filter(t=>t.type==="退货冲减").reduce((t,g)=>t+Number(g.credit||0),0))}</b></div>
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
</html>`}function R(l){const n=window.open("","_blank");return n?(n.document.write(K(l)),n.document.close(),n.focus(),setTimeout(()=>{n.print(),n.close()},200),!0):!1}const X={class:"report-page"},Z={class:"toolbar"},ee={key:0,class:"kpi-row"},te={class:"kpi-card"},de={class:"kpi-value"},ie={class:"kpi-card"},le={class:"kpi-value"},ae={class:"kpi-card"},oe={class:"kpi-value green"},ne={class:"kpi-card"},re={class:"kpi-value"},se={class:"kpi-card"},pe={class:"table-card"},ce={class:"card-title"},ue={key:2,class:"empty-tip"},be={__name:"SupplierStatement",setup(l){const n=k([]),u=k(null),s=k(Y()),o=k(null),t=k(!1),g=k([]);function Y(){const i=new Date;return[new Date(i.getFullYear(),Math.floor(i.getMonth()/3)*3,1).toISOString().slice(0,10),i.toISOString().slice(0,10)]}function O(i){return g.value.includes(i)}function z(i){return g.value.includes(i+":amount")||g.value.includes("finance:amount")}function v(i){return Number(i||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}const T=D(()=>{var i;return(((i=o.value)==null?void 0:i.lines)||[]).filter(e=>e.type==="付款").reduce((e,f)=>e+Number(f.credit||0),0)}),B=D(()=>{var i;return(((i=o.value)==null?void 0:i.lines)||[]).filter(e=>e.type==="退货冲减").reduce((e,f)=>e+Number(f.credit||0),0)}),P=D(()=>{var e,f;let i=Number(((e=o.value)==null?void 0:e.opening)||0);return(((f=o.value)==null?void 0:f.lines)||[]).map(N=>(i+=Number(N.debit||0)-Number(N.credit||0),{...N,balance:Number(i.toFixed(2))}))});async function E(){if(!u.value||!s.value||s.value.length!==2){I.warning("请选择供应商和对账期间");return}t.value=!0;try{o.value=await M.get("/finance-report/supplier-statement",{params:{supplierId:u.value,from:s.value[0],to:s.value[1]}})}catch{}finally{t.value=!1}}function L(){R(o.value)||I.warning("浏览器拦截了弹窗，请允许本站弹出窗口")}return J(async()=>{try{g.value=JSON.parse(localStorage.getItem("user")||"{}").permissions||[]}catch{}try{n.value=await M.get("/supplier",{params:{enabled:!0}})}catch{}}),(i,e)=>{var F;const f=_("el-option"),N=_("el-select"),j=_("el-date-picker"),C=_("el-button"),H=_("el-skeleton"),y=_("el-table-column"),U=_("el-tag"),q=_("p-table");return p(),S("div",X,[e[9]||(e[9]=d("div",{class:"report-header"},[d("h2",{class:"report-title"},"供应商对账单"),d("span",{class:"report-sub"},"期间往来明细 + 期末余额 · 可打印盖章确认")],-1)),d("div",Z,[b(N,{modelValue:u.value,"onUpdate:modelValue":e[0]||(e[0]=a=>u.value=a),filterable:"",placeholder:"选择供应商",style:{width:"220px"}},{default:m(()=>[(p(!0),S(V,null,W(n.value,a=>(p(),w(f,{key:a.id,label:a.name,value:a.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"]),b(j,{modelValue:s.value,"onUpdate:modelValue":e[1]||(e[1]=a=>s.value=a),type:"daterange","value-format":"YYYY-MM-DD","range-separator":"至","start-placeholder":"开始日期","end-placeholder":"结束日期",style:{width:"260px"}},null,8,["modelValue"]),b(C,{type:"primary",onClick:E,loading:t.value,disabled:!u.value||!s.value},{default:m(()=>[...e[2]||(e[2]=[$("查询",-1)])]),_:1},8,["loading","disabled"]),O("finance:read")?(p(),w(C,{key:0,onClick:L,disabled:!o.value},{default:m(()=>[...e[3]||(e[3]=[$("打印对账单",-1)])]),_:1},8,["disabled"])):x("",!0)]),t.value?(p(),w(H,{key:0,rows:8,animated:""})):x("",!0),o.value&&!t.value?(p(),S(V,{key:1},[z("finance-report")?(p(),S("div",ee,[d("div",te,[e[4]||(e[4]=d("div",{class:"kpi-label"},"期初余额",-1)),d("div",de,"¥"+r(v(o.value.opening)),1)]),d("div",ie,[e[5]||(e[5]=d("div",{class:"kpi-label"},"本期应付增加",-1)),d("div",le,"¥"+r(v(o.value.debit)),1)]),d("div",ae,[e[6]||(e[6]=d("div",{class:"kpi-label"},"本期付款",-1)),d("div",oe,"¥"+r(v(T.value)),1)]),d("div",ne,[e[7]||(e[7]=d("div",{class:"kpi-label"},"本期退货冲减",-1)),d("div",re,"¥"+r(v(B.value)),1)]),d("div",se,[e[8]||(e[8]=d("div",{class:"kpi-label"},"期末余额（我司应付）",-1)),d("div",{class:Q(["kpi-value",o.value.closing>0?"red":"green"])},[d("b",null,"¥"+r(v(o.value.closing)),1)],2)])])):x("",!0),d("div",pe,[d("div",ce,r((F=o.value.supplier)==null?void 0:F.name)+"（"+r(o.value.from)+" 至 "+r(o.value.to)+"）",1),b(q,{data:P.value,stripe:"",border:"",style:{width:"100%"}},{default:m(()=>[b(y,{prop:"date",label:"日期",width:"110"}),b(y,{prop:"docNo",label:"单据号","min-width":"140","show-overflow-tooltip":""}),b(y,{prop:"type",label:"业务类型",width:"100",align:"center"},{default:m(({row:a})=>[b(U,{type:{应付立账:"primary",付款:"success",退货冲减:"warning"}[a.type]||"info",size:"small"},{default:m(()=>[$(r(a.type),1)]),_:2},1032,["type"])]),_:1}),b(y,{prop:"note",label:"摘要","min-width":"170","show-overflow-tooltip":""}),z("finance-report")?(p(),w(y,{key:0,prop:"debit",label:"应付增加",width:"120",align:"right"},{default:m(({row:a})=>[$(r(a.debit?"¥"+v(a.debit):""),1)]),_:1})):x("",!0),z("finance-report")?(p(),w(y,{key:1,prop:"credit",label:"付款/退货",width:"120",align:"right"},{default:m(({row:a})=>[$(r(a.credit?"¥"+v(a.credit):""),1)]),_:1})):x("",!0),z("finance-report")?(p(),w(y,{key:2,prop:"balance",label:"结转余额",width:"130",align:"right"},{default:m(({row:a})=>[d("span",{style:G(a.balance<0?"color:#ef4444":"")},"¥"+r(v(a.balance)),5)]),_:1})):x("",!0)]),_:1},8,["data"])])],64)):x("",!0),!o.value&&!t.value?(p(),S("div",ue,"选择供应商和对账期间后查询")):x("",!0)])}}},ve=A(be,[["__scopeId","data-v-5a66e9aa"]]);export{ve as default};
