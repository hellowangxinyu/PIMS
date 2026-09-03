import{_ as J,l as k,o as Q,b as p,c as $,e as d,an as b,ao as m,au as w,g as _,F as I,t as s,h as W,aq as h,ar as C,f as G,k as S,n as K,E as M}from"./index-BnW5TqdP.js";import{a as Y}from"./index-B67FkASZ.js";function g(l){return String(l??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function c(l){return Number(l||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}function R(l){const n=l.customer||{},u=l.lines||[];let r=Number(l.opening||0);const i=u.map(t=>(r+=Number(t.debit||0)-Number(t.credit||0),`<tr>
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
      <div style="font-size:11px;color:#6b7280;margin-top:4px">对账期间：${g(l.from)} 至 ${g(l.to)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>客户名称：<b>${g(n.name)}</b></div>
    <div>客户编码：${g(n.code)}</div>
    <div>纳税人识别号：${g(n.taxNo||"—")}</div>
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
        <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${Number(l.opening)<0?"color:#dc2626":""}"><b>${c(l.opening)}</b></td>
      </tr>
      ${i||'<tr><td colspan="7" style="padding:14px;text-align:center;border:1px solid #d1d5db;color:#9ca3af">本期无往来记录</td></tr>'}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"><b>本期合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${c(l.debit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${c(l.credit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
      <tr>
        <td colspan="6" style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2"><b>期末余额（客户应付我司）</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2;text-align:right" class="closing"><b>¥ ${c(l.closing)}</b></td>
      </tr>
    </tbody>
  </table>
  <div class="summary">
    <div class="item">本期应收增加：<b>¥ ${c(l.debit)}</b></div>
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
</html>`}function X(l){const n=window.open("","_blank");return n?(n.document.write(R(l)),n.document.close(),n.focus(),setTimeout(()=>{n.print(),n.close()},200),!0):!1}const Z={class:"report-page"},ee={class:"toolbar"},te={key:0,class:"kpi-row"},de={class:"kpi-card"},ie={class:"kpi-value"},ae={class:"kpi-card"},le={class:"kpi-value"},oe={class:"kpi-card"},ne={class:"kpi-value green"},se={class:"kpi-card"},re={class:"kpi-value"},pe={class:"kpi-card"},ce={class:"table-card"},ue={class:"card-title"},be={key:2,class:"empty-tip"},me={__name:"CustomerStatement",setup(l){const n=k([]),u=k(null),r=k(O()),i=k(null),t=k(!1),v=k([]);function O(){const a=new Date;return[new Date(a.getFullYear(),Math.floor(a.getMonth()/3)*3,1).toISOString().slice(0,10),a.toISOString().slice(0,10)]}function T(a){return v.value.includes(a)}function z(a){return v.value.includes(a+":amount")||v.value.includes("finance:amount")}function f(a){return Number(a||0).toLocaleString("zh-CN",{minimumFractionDigits:2,maximumFractionDigits:2})}const B=C(()=>{var a;return(((a=i.value)==null?void 0:a.lines)||[]).filter(e=>e.type==="收款").reduce((e,x)=>e+Number(x.credit||0),0)}),P=C(()=>{var a;return(((a=i.value)==null?void 0:a.lines)||[]).filter(e=>e.type==="销售退货").reduce((e,x)=>e+Number(x.credit||0),0)}),E=C(()=>{var e,x;let a=Number(((e=i.value)==null?void 0:e.opening)||0);return(((x=i.value)==null?void 0:x.lines)||[]).map(N=>(a+=Number(N.debit||0)-Number(N.credit||0),{...N,balance:Number(a.toFixed(2))}))});async function L(){if(!u.value||!r.value||r.value.length!==2){M.warning("请选择客户和对账期间");return}t.value=!0;try{i.value=await Y.get("/finance-report/statement",{params:{customerId:u.value,from:r.value[0],to:r.value[1]}})}catch{}finally{t.value=!1}}function j(){X(i.value)||M.warning("浏览器拦截了弹窗，请允许本站弹出窗口")}return Q(async()=>{try{v.value=JSON.parse(localStorage.getItem("user")||"{}").permissions||[]}catch{}try{n.value=await Y.get("/customer",{params:{enabled:!0}})}catch{}}),(a,e)=>{var F,V;const x=h("el-option"),N=h("el-select"),H=h("el-date-picker"),D=h("el-button"),U=h("el-skeleton"),y=h("el-table-column"),q=h("el-tag"),A=h("p-table");return p(),$("div",Z,[e[9]||(e[9]=d("div",{class:"report-header"},[d("h2",{class:"report-title"},"客户对账单"),d("span",{class:"report-sub"},"期间往来明细 + 期末余额 · 可打印盖章确认")],-1)),d("div",ee,[b(N,{modelValue:u.value,"onUpdate:modelValue":e[0]||(e[0]=o=>u.value=o),filterable:"",placeholder:"选择客户",style:{width:"220px"}},{default:m(()=>[(p(!0),$(I,null,G(n.value,o=>(p(),w(x,{key:o.id,label:o.name,value:o.id},null,8,["label","value"]))),128))]),_:1},8,["modelValue"]),b(H,{modelValue:r.value,"onUpdate:modelValue":e[1]||(e[1]=o=>r.value=o),type:"daterange","value-format":"YYYY-MM-DD","range-separator":"至","start-placeholder":"开始日期","end-placeholder":"结束日期",style:{width:"260px"}},null,8,["modelValue"]),b(D,{type:"primary",onClick:L,loading:t.value,disabled:!u.value||!r.value},{default:m(()=>[...e[2]||(e[2]=[S("查询",-1)])]),_:1},8,["loading","disabled"]),T("finance:read")?(p(),w(D,{key:0,onClick:j,disabled:!i.value},{default:m(()=>[...e[3]||(e[3]=[S("打印对账单",-1)])]),_:1},8,["disabled"])):_("",!0)]),t.value?(p(),w(U,{key:0,rows:8,animated:""})):_("",!0),i.value&&!t.value?(p(),$(I,{key:1},[z("finance-report")?(p(),$("div",te,[d("div",de,[e[4]||(e[4]=d("div",{class:"kpi-label"},"期初余额",-1)),d("div",ie,"¥"+s(f(i.value.opening)),1)]),d("div",ae,[e[5]||(e[5]=d("div",{class:"kpi-label"},"本期应收增加",-1)),d("div",le,"¥"+s(f(i.value.debit)),1)]),d("div",oe,[e[6]||(e[6]=d("div",{class:"kpi-label"},"本期收款",-1)),d("div",ne,"¥"+s(f(B.value)),1)]),d("div",se,[e[7]||(e[7]=d("div",{class:"kpi-label"},"本期退货",-1)),d("div",re,"¥"+s(f(P.value)),1)]),d("div",pe,[e[8]||(e[8]=d("div",{class:"kpi-label"},"期末余额（客户应付）",-1)),d("div",{class:W(["kpi-value",i.value.closing>0?"red":"green"])},[d("b",null,"¥"+s(f(i.value.closing)),1)],2)])])):_("",!0),d("div",ce,[d("div",ue,s((F=i.value.customer)==null?void 0:F.name)+"（"+s(i.value.from)+" 至 "+s(i.value.to)+"）"+s((V=i.value.customer)!=null&&V.taxNo?" · 税号 "+i.value.customer.taxNo:""),1),b(A,{data:E.value,stripe:"",border:"",style:{width:"100%"}},{default:m(()=>[b(y,{prop:"date",label:"日期",width:"110"}),b(y,{prop:"docNo",label:"单据号","min-width":"140","show-overflow-tooltip":""}),b(y,{prop:"type",label:"业务类型",width:"100",align:"center"},{default:m(({row:o})=>[b(q,{type:{销售立账:"primary",收款:"success",销售退货:"warning"}[o.type]||"info",size:"small"},{default:m(()=>[S(s(o.type),1)]),_:2},1032,["type"])]),_:1}),b(y,{prop:"note",label:"摘要","min-width":"170","show-overflow-tooltip":""}),z("finance-report")?(p(),w(y,{key:0,prop:"debit",label:"应收增加",width:"120",align:"right"},{default:m(({row:o})=>[S(s(o.debit?"¥"+f(o.debit):""),1)]),_:1})):_("",!0),z("finance-report")?(p(),w(y,{key:1,prop:"credit",label:"收款/退货",width:"120",align:"right"},{default:m(({row:o})=>[S(s(o.credit?"¥"+f(o.credit):""),1)]),_:1})):_("",!0),z("finance-report")?(p(),w(y,{key:2,prop:"balance",label:"结转余额",width:"130",align:"right"},{default:m(({row:o})=>[d("span",{style:K(o.balance<0?"color:#ef4444":"")},"¥"+s(f(o.balance)),5)]),_:1})):_("",!0)]),_:1},8,["data"])])],64)):_("",!0),!i.value&&!t.value?(p(),$("div",be,"选择客户和对账期间后查询")):_("",!0)])}}},fe=J(me,[["__scopeId","data-v-b75ee077"]]);export{fe as default};
