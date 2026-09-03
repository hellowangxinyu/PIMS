// PIMS SOP V2.0 Word 版生成脚本（docx-js）
// 结构：Section1 封面(R1+WR-2) / Section2 文档控制+目录(Roman) / Section3 正文(Arabic)
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  ImageRun, PageBreak, Header, Footer, PageNumber, NumberFormat,
  AlignmentType, HeadingLevel, WidthType, BorderStyle, ShadingType,
  LevelFormat, TableOfContents, SectionType, TableLayoutType,
} = require("docx");
const fs = require("fs");
const path = require("path");

const BUILD = __dirname;
const figmap = JSON.parse(fs.readFileSync(path.join(BUILD, "figmap.json"), "utf-8"));
const fig = (i) => {
  const f = figmap.find(x => x.i === i);
  return { buf: fs.readFileSync(path.join(BUILD, "diagrams", f.file)), w: f.w, h: f.h };
};

// ── WR-2 Retro Green palette（design-system.md）──
const P = {
  bg: "F4F1E9", primary: "2A4A3A", accent: "C89F62",
  cover: { titleColor: "2A4A3A", subtitleColor: "606060", metaColor: "707070", footerColor: "A0A0A0" },
  table: { headerBg: "2A4A3A", headerText: "FFFFFF", accentLine: "2A4A3A", innerLine: "D0D8D0", surface: "F0EDE5" },
};

const NB = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: NB, bottom: NB, left: NB, right: NB };
const allNoBorders = { top: NB, bottom: NB, left: NB, right: NB, insideHorizontal: NB, insideVertical: NB };

// ══════════ 封面配方 R1 依赖（design-system.md 原样实现）══════════
function splitTitleLines(title, charsPerLine) {
  if (title.length <= charsPerLine) return [title];
  const breakAfter = new Set([..."，。、；：！？", ..."的与和及之在于为", ..."-_—–·/", ..." \t"]);
  const lines = [];
  let remaining = title;
  while (remaining.length > charsPerLine) {
    let breakAt = -1;
    for (let i = charsPerLine; i >= Math.floor(charsPerLine * 0.6); i--) {
      if (i < remaining.length && breakAfter.has(remaining[i - 1])) { breakAt = i; break; }
    }
    if (breakAt === -1) {
      const limit = Math.min(remaining.length, Math.ceil(charsPerLine * 1.3));
      for (let i = charsPerLine + 1; i < limit; i++) {
        if (breakAfter.has(remaining[i - 1])) { breakAt = i; break; }
      }
    }
    if (breakAt === -1) {
      breakAt = charsPerLine;
      const prevChar = remaining[breakAt - 1], nextChar = remaining[breakAt];
      if (prevChar && nextChar && !breakAfter.has(prevChar) && !breakAfter.has(nextChar) &&
          /[\u4e00-\u9fff]/.test(prevChar) && /[\u4e00-\u9fff]/.test(nextChar)) breakAt = breakAt - 1;
    }
    lines.push(remaining.slice(0, breakAt).trim());
    remaining = remaining.slice(breakAt).trim();
  }
  if (remaining) lines.push(remaining);
  if (lines.length > 1 && lines[lines.length - 1].length <= 2) {
    const last = lines.pop();
    lines[lines.length - 1] += last;
  }
  return lines;
}

function calcTitleLayout(title, maxWidthTwips, preferredPt = 40, minPt = 24) {
  const charWidth = (pt) => pt * 20;
  const charsPerLine = (pt) => Math.floor(maxWidthTwips / charWidth(pt));
  let titlePt = preferredPt, lines;
  while (titlePt >= minPt) {
    const cpl = charsPerLine(titlePt);
    if (cpl < 2) { titlePt -= 2; continue; }
    lines = splitTitleLines(title, cpl);
    if (lines.length <= 3) break;
    titlePt -= 2;
  }
  if (!lines || lines.length > 3) {
    lines = splitTitleLines(title, charsPerLine(minPt));
    titlePt = minPt;
  }
  return { titlePt, titleLines: lines };
}

function calcCoverSpacing(params) {
  const { titleLineCount = 1, titlePt = 36, hasSubtitle = false, hasEnglishLabel = false,
    metaLineCount = 0, fixedHeight = 800, pageHeight = 16838, marginTop = 0, marginBottom = 0 } = params;
  const SAFETY = 1200;
  const usableHeight = pageHeight - marginTop - marginBottom - SAFETY;
  const titleHeight = titleLineCount * (titlePt * 23 + 200);
  const subtitleHeight = hasSubtitle ? (12 * 23 + 600) : 0;
  const englishLabelHeight = hasEnglishLabel ? (9 * 23 + 600) : 0;
  const metaHeight = metaLineCount * (10 * 23 + 100);
  const implicitParaHeight = 3 * 300;
  const contentHeight = titleHeight + subtitleHeight + englishLabelHeight + metaHeight + fixedHeight + implicitParaHeight;
  const safeRemaining = Math.max(usableHeight - contentHeight, 400);
  const FOOTER_MIN = 800;
  const rawTop = Math.floor(safeRemaining * 0.45);
  const rawBottom = Math.floor(safeRemaining * 0.45);
  const bottomSpacing = Math.max(rawBottom, FOOTER_MIN);
  const topSpacing = Math.max(rawTop - Math.max(0, FOOTER_MIN - rawBottom), 400);
  const midSpacing = Math.max(safeRemaining - topSpacing - bottomSpacing, 0);
  return { topSpacing, midSpacing, bottomSpacing };
}

function buildCoverR1(config) {
  const PA = config.palette;
  const padL = 1200, padR = 800;
  const availableWidth = 11906 - padL - padR - 300;
  const { titlePt, titleLines } = calcTitleLayout(config.title, availableWidth, 40, 24);
  const titleSize = titlePt * 2;
  const spacing = calcCoverSpacing({
    titleLineCount: titleLines.length, titlePt,
    hasSubtitle: !!config.subtitle, hasEnglishLabel: !!config.englishLabel,
    metaLineCount: (config.metaLines || []).length, fixedHeight: 400,
  });
  const accentLeft = { style: BorderStyle.SINGLE, size: 8, color: PA.accent, space: 12 };
  const children = [];
  children.push(new Paragraph({ spacing: { before: spacing.topSpacing } }));
  if (config.englishLabel) {
    children.push(new Paragraph({
      indent: { left: padL, right: padR }, spacing: { after: 500 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: PA.accent, space: 8 } },
      children: [new TextRun({ text: config.englishLabel.split("").join("  "),
        size: 18, color: PA.accent, font: { ascii: "Calibri", eastAsia: "SimHei" }, characterSpacing: 40 })],
    }));
  }
  for (let i = 0; i < titleLines.length; i++) {
    children.push(new Paragraph({
      indent: { left: padL },
      spacing: { after: i < titleLines.length - 1 ? 100 : 300, line: Math.ceil(titlePt * 23), lineRule: "atLeast" },
      children: [new TextRun({ text: titleLines[i], size: titleSize, bold: true,
        color: PA.cover.titleColor, font: { eastAsia: "SimHei", ascii: "Arial" } })],
    }));
  }
  if (config.subtitle) {
    children.push(new Paragraph({
      indent: { left: padL }, spacing: { after: 800 },
      children: [new TextRun({ text: config.subtitle, size: 24, color: PA.cover.subtitleColor,
        font: { eastAsia: "Microsoft YaHei", ascii: "Arial" } })],
    }));
  }
  for (const line of (config.metaLines || [])) {
    children.push(new Paragraph({
      indent: { left: padL + 200 }, spacing: { after: 80 },
      border: { left: accentLeft },
      children: [new TextRun({ text: line, size: 24, color: PA.cover.metaColor,
        font: { eastAsia: "Microsoft YaHei", ascii: "Arial" } })],
    }));
  }
  children.push(new Paragraph({ spacing: { before: spacing.bottomSpacing } }));
  children.push(new Paragraph({
    indent: { left: padL, right: padR },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: PA.accent, space: 8 } },
    spacing: { before: 200 },
    children: [
      new TextRun({ text: config.footerLeft || "", size: 16, color: PA.cover.footerColor, font: { ascii: "Arial", eastAsia: "Microsoft YaHei" } }),
      new TextRun({ text: "                                        " }),
      new TextRun({ text: config.footerRight || "", size: 16, color: PA.cover.footerColor, font: { ascii: "Arial", eastAsia: "Microsoft YaHei" } }),
    ],
  }));
  return [new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: allNoBorders,
    rows: [new TableRow({
      height: { value: 16838, rule: "exact" },
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: PA.bg }, borders: noBorders, verticalAlign: "top",
        children,
      })],
    })],
  })];
}

// ══════════ 正文组件 ══════════
const F_BODY = { ascii: "Times New Roman", eastAsia: "SimSun" };
const F_HEAD = { ascii: "Times New Roman", eastAsia: "SimHei" };

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1, alignment: AlignmentType.CENTER,
    spacing: { before: 400, after: 200, line: 312 },
    children: [new TextRun({ text, bold: true, size: 32, color: P.primary, font: F_HEAD })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 280, after: 140, line: 312 },
    children: [new TextRun({ text, bold: true, size: 28, color: P.primary, font: F_HEAD })],
  });
}
function body(text, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED, indent: { firstLine: 480 },
    spacing: { line: 312, after: opts.after ?? 80 },
    children: runsOf(text, { size: 24, color: "000000", font: F_BODY }),
  });
}
// 支持 **加粗** 片段
function runsOf(text, base) {
  const parts = String(text).split(/\*\*/);
  return parts.map((t, i) => t === "" ? null : new TextRun({ ...base, text: t, bold: i % 2 === 1 })).filter(Boolean);
}
function li(ref, text) {
  return new Paragraph({
    numbering: { reference: ref, level: 0 },
    alignment: AlignmentType.JUSTIFIED, spacing: { line: 312, after: 60 },
    children: runsOf(text, { size: 24, color: "000000", font: F_BODY }),
  });
}
function noteP(text) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED, indent: { firstLine: 480 },
    spacing: { line: 312, before: 60, after: 120 },
    children: [
      new TextRun({ text: "注意：", bold: true, size: 24, color: P.primary, font: F_BODY }),
      ...runsOf(text, { size: 24, color: "000000", font: F_BODY }),
    ],
  });
}
// 流程图 + 图注（保持宽高比：宽<=585px 高<=820px）
function figure(i, caption) {
  const f = fig(i);
  let dw = Math.min(585, f.w), dh = Math.round(dw * f.h / f.w);
  if (dh > 820) { dh = 820; dw = Math.round(820 * f.w / f.h); }
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { before: 120, after: 40 }, keepNext: true,
      children: [new ImageRun({ data: f.buf, transformation: { width: dw, height: dh }, type: "png" })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { after: 160 },
      children: [new TextRun({ text: caption, size: 21, color: "606060", font: F_BODY })],
    }),
  ];
}
// Horizontal-Only 表格（WR-2 tokens）
function mkTable(headers, rows, widths, titleText) {
  const cellPara = (text, bold, color) => new Paragraph({
    alignment: AlignmentType.LEFT, spacing: { line: 312 },
    children: runsOf(text, { size: 21, bold, color, font: F_BODY }),
  });
  const out = [];
  if (titleText) {
    out.push(new Paragraph({
      keepNext: true, spacing: { before: 120, after: 60 },
      children: [new TextRun({ text: titleText, bold: true, size: 21, color: "404040", font: F_BODY })],
    }));
  }
  out.push(new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: P.table.accentLine },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: P.table.accentLine },
      left: NB, right: NB,
      insideHorizontal: { style: BorderStyle.SINGLE, size: 1, color: P.table.innerLine },
      insideVertical: NB,
    },
    rows: [
      new TableRow({
        tableHeader: true, cantSplit: true,
        children: headers.map((t, k) => new TableCell({
          children: [cellPara(t, true, P.table.headerText)],
          shading: { type: ShadingType.CLEAR, fill: P.table.headerBg },
          margins: { top: 60, bottom: 60, left: 120, right: 120 },
          width: { size: widths[k], type: WidthType.PERCENTAGE },
        })),
      }),
      ...rows.map((r, ri) => new TableRow({
        cantSplit: true,
        children: r.map((t, k) => new TableCell({
          children: [cellPara(t, false, "000000")],
          shading: ri % 2 === 0 ? { type: ShadingType.CLEAR, fill: "FFFFFF" } : { type: ShadingType.CLEAR, fill: P.table.surface },
          margins: { top: 60, bottom: 60, left: 120, right: 120 },
          width: { size: widths[k], type: WidthType.PERCENTAGE },
        })),
      })),
    ],
  }));
  return out;
}

const pageNumFooter = () => new Footer({
  children: [new Paragraph({
    alignment: AlignmentType.CENTER,
    children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "888888", font: F_BODY })],
  })],
});
const docHeader = () => new Header({
  children: [new Paragraph({
    alignment: AlignmentType.CENTER,
    border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: P.table.innerLine, space: 4 } },
    children: [new TextRun({ text: "芃远综合管理系统（PIMS）标准操作流程 SOP · V2.0", size: 18, color: "888888", font: F_BODY })],
  })],
});

// ══════════ 正文内容 ══════════
const bodyChildren = [];

// —— 第一章 全局操作铁律 ——
bodyChildren.push(h1("一、全局操作铁律"));
bodyChildren.push(body("以下十条规则适用于所有岗位、所有单据，是全系统必须遵守的基础约束，后续章节不再重复表述。"));
bodyChildren.push(...mkTable(
  ["序号", "铁律", "说明"],
  [
    ["1", "**质检前置**", "所有入库（采购、生产、委外、其他）必须质检合格方可入库，库存即合格品；出库不再校验质检。"],
    ["2", "**批次必选**", "所有出库（生产、委外、销售、其他）必须选批号，成本按批号单价直取；批号全局唯一，从到货、质检到库存全链贯通。"],
    ["3", "**三级仓位**", "出入库均按仓库、分库、库位三级选择，下级联动。"],
    ["4", "**单位统一 kg**", "所有物料单位统一为公斤，生产、委外、配方单据单位不可修改。"],
    ["5", "**财务联动**", "收货才立应付、发货审核才立应收，下单不立账；内部领料与入库不产生应收应付。"],
    ["6", "**物料编码自动生成**", "新建物料选完小类即自动生成编码，无手动按钮、不可编辑、全局唯一、无编码不能保存；编码此后不可修改。"],
    ["7", "**过期批次拦截**", "过期批次（到期日早于当天）禁止一切正常出库与调拨；只有「其他出库」的报废、样品、退货用途放行。"],
    ["8", "**特殊仓库隔离**", "不合格品库（REJECT）、油尾库（TAILING）的库存被所有正常出库、调拨与自动 FIFO 排除，库存报表默认不显示。"],
    ["9", "**单点登录**", "一个账号同一时间只能登录一处，新登录会自动顶掉旧会话；发现突然掉线或 401，先确认是否有人在别处使用同一账号。"],
    ["10", "**状态中文化**", "所有状态显示中文；若出现英文状态码，属于显示异常，请联系管理员。"],
  ],
  [8, 22, 70], "表 1-1 全局操作铁律"
));

// —— 第二章 角色与权限 ——
bodyChildren.push(h1("二、角色与权限总览"));
bodyChildren.push(...mkTable(
  ["角色", "定位", "核心权限", "主要工作菜单"],
  [
    ["SS 系统管理员", "系统维护", "全部权限（admin 账号）", "系统设置全部"],
    ["GM 总经理", "经营决策", "全模块查看、采购审核与反审核、财务金额可见", "报表中心、经营看板"],
    ["PM 生产管理员", "生产组织", "生产、配方、采购做单与审核，库存写，质检", "生产管理、排产中心"],
    ["BUYER 采购员", "采购执行", "供应商与物料维护、采购做单、价格可见", "采购管理"],
    ["TECH_LEAD 技术主管", "配方工艺", "配方读写、工艺路线读写", "配方管理、工艺路线"],
    ["TECHNICIAN 技术员", "配方查阅", "配方只读为主", "配方管理"],
    ["OUTSOURCE 委外管理员", "委外加工", "委外单、委外出入库、库存写", "委外管理"],
    ["STOREKEEPER 仓管", "仓库收发", "仓库与库存写、生产与委外出入库、各类查看", "库存中心、各类出入库"],
    ["SALES 销售员", "销售执行", "客户、销售单、发货、退货，财务金额可见", "销售管理"],
    ["FINANCE 财务", "资金账务", "收付款、财务审核与反审核、价格与付款条件", "财务管理、账龄报表"],
    ["QC 质检员", "质量判定", "质检判定（qc:write）与全模块只读", "质量管理"],
  ],
  [20, 14, 38, 28], "表 2-1 系统预置角色与权限"
));
bodyChildren.push(noteP("权限变更保存后，该角色所有在线用户会被立即踢下线，重新登录后新权限生效，属于正常保护机制。"));

// —— 第三章 业务全景 ——
bodyChildren.push(h1("三、业务全景图"));
bodyChildren.push(body("一张图看懂 PIMS 主业务流：左边进料、中间造货、右边卖货、底下钱流。采购进料经来料质检入原料仓；配方与工艺路线作为技术支撑驱动自制生产与委外加工两条造货线；产出的半成品与成品经质检入库；成品经销售发货送达客户；收货立应付、发货审核立应收，收付款单按 FIFO 自动冲减。"));
bodyChildren.push(...figure(1, "图 3-1 PIMS 业务全景图"));
bodyChildren.push(body("**核心串联模型**：物料表是系统的中心，**物料编码（productCode）是唯一纽带**——配方、销售订单明细、生产订单都指向同一物料编码。配方与销售订单录入都必须从物料库中选择 B（半成品）或 C（成品）产品，不允许出现游离编码。"));
bodyChildren.push(noteP("判断半成品或成品必须查看物料的「大类」字段，不能按编码首字母猜测，例如半成品编码是 PJ 开头而不是 B 开头。"));

// —— 第四章 上线初始化 ——
bodyChildren.push(h1("四、系统上线初始化"));
bodyChildren.push(body("本章适用于系统首次启用或新仓库启用场景，由系统管理员（SS）或总经理（GM）操作。初始化顺序如图 4-1。"));
bodyChildren.push(...figure(2, "图 4-1 系统上线初始化流程"));
bodyChildren.push(body("期初库存导入的操作要点："));
bodyChildren.push(li("list-ch4", "**期初模板 8 列**：物料编码、物料名称、仓库名称、数量为必填；单价、批号、生产日期、过期日期选填。模板从库存查询页工具栏「下载期初模板」获取。"));
bodyChildren.push(li("list-ch4", "**防重闸门**：已有库存的物料禁止导入，同一文件二次导入会全部拒绝——期初导入是一次性动作。"));
bodyChildren.push(li("list-ch4", "每行落一个批次；批号可填（须全局唯一）也可留空自动生成；过期日期留空按物料质保期推算；导入后质检状态直接记为合格，立即可用。"));
bodyChildren.push(li("list-ch4", "特殊仓库（自有仓、委外仓、不合格品库、油尾库）由系统启动时自动创建，无需手工建立；不合格品库与油尾库禁止修改类型、删除或停用。"));

// —— 第五章 配方与工艺路线 ——
bodyChildren.push(h1("五、配方与工艺路线（技术准备）"));
bodyChildren.push(body("本章由技术主管（TECH_LEAD）或生产管理员（PM）操作，菜单位置：生产管理、配方管理、工艺路线。"));
bodyChildren.push(h2("5.1 配方版本生命周期"));
bodyChildren.push(...figure(3, "图 5-1 配方版本生命周期"));
bodyChildren.push(li("list-ch5a", "**只有已发布（RELEASED）版本可被生产订单与委外订单引用**；生产订单始终取该产品最新发布版。"));
bodyChildren.push(li("list-ch5a", "配方树可引用子配方（半成品）：展开时自动递归取子配方的已发布版本，实现「制浆到制漆」两阶段生产。"));
bodyChildren.push(li("list-ch5a", "制浆产半成品（B 类，研磨浆），制漆产成品（C 类，成品漆）——浆和漆是两类物料。"));
bodyChildren.push(li("list-ch5a", "原材料节点支持配置平替物料（双向关系），配方树中可一键切换，用量保持不变。"));
bodyChildren.push(li("list-ch5a", "打印贯通：配方工艺指导单、生产投料单、委外单均按配方绑定的路线打印工序与步骤。"));
bodyChildren.push(li("list-ch5a", "配方名称（产品名）不允许重复，保存时系统自动查重。"));
bodyChildren.push(h2("5.2 工艺路线"));
bodyChildren.push(body("路线由工序、步骤（支持投料顺序占位符）、工序质检项三级构成。同类型可建多条命名路线，每类型一条默认路线（新建配方时自动带出）。被配方引用的路线以及默认路线均不可删除。系统预置默认路线：制浆 6 工序（预混、砂磨、调色、过滤、打包留样送检、刷缸）与制漆 5 工序（预混/调漆、调色、过滤、打包留样送检、刷缸），可直接在管理页修改。"));
bodyChildren.push(h2("5.3 制漆配方的油尾节点"));
bodyChildren.push(...figure(4, "图 5-2 制漆配方油尾节点校验链"));
bodyChildren.push(body("油尾节点允许制漆配方直接投用油尾库中的同体系成品漆。系统执行硬校验：仅制漆配方、必须为 C 类成品、主材体系一致、且编码相同或色系相同、油尾库有库存，任一不满足即拒绝保存。校验通过后配方含油尾投料，消化方式为生产人工领料（自动 FIFO 出库排除油尾库）。"));

// —— 第六章 采购与来料质检 ——
bodyChildren.push(h1("六、采购与来料质检"));
bodyChildren.push(body("本章由采购员（BUYER）做单、总经理或生产管理员审核、质检员（QC）判定、仓管（STOREKEEPER）收货，菜单位置：采购管理。全流程如图 6-1。"));
bodyChildren.push(...figure(5, "图 6-1 采购到货质检全流程"));
bodyChildren.push(li("list-ch6", "审核后的采购单才允许到货录入；已审核单据可以反审核退回开立状态。"));
bodyChildren.push(li("list-ch6", "到货录入时批号由系统自动生成（B+日期+3 位序号），一个批号从到货、质检到库存全链贯通；判定弹窗中检验员可按实物包装批号补填或修正。"));
bodyChildren.push(li("list-ch6", "质检不合格的货从未入库：退货单审核通过即视为退货完成（直接退供应商），系统不产生退货出库、也不冲应付（收货才立应付）。"));
bodyChildren.push(li("list-ch6", "质检判定按物料大类自动套用检测项模板并逐项录入实测值（详见第十一章）。"));
bodyChildren.push(li("list-ch6", "低库存预警按「物料+仓库」口径触发，报表中可一键生成采购单（建议量为平均月用量，自动带目标仓库）。"));

// —— 第七章 生产管理 ——
bodyChildren.push(h1("七、生产管理"));
bodyChildren.push(body("本章由生产管理员（PM）组织订单与排产、仓管（STOREKEEPER）执行领料与入库、质检员（QC）判定，菜单位置：生产管理。"));
bodyChildren.push(h2("7.1 生产全流程"));
bodyChildren.push(...figure(6, "图 7-1 生产全流程（订单、排产、领料、入库、完工）"));
bodyChildren.push(li("list-ch7a", "**排产中心**只处理已确认订单，排产时做全量库存校验：缺任何原料都会被拦截，且一次性列出所有缺口，无需反复试。"));
bodyChildren.push(li("list-ch7a", "生产出库单（领料）创建即确认并直接扣库存，没有确认按钮。"));
bodyChildren.push(li("list-ch7a", "**生产入库前置校验**：订单必须存在已确认的领料出库单（未领料不能入库）；同一订单禁止重复参照入库；已完工订单不出现在参照下拉。"));
bodyChildren.push(li("list-ch7a", "**自动完工**：入库质检合格（入库单完成）后订单状态自动置为已完工，无需手动操作；手动「完工」按钮仅作兜底。"));
bodyChildren.push(li("list-ch7a", "**投出比口径**：投入为已确认领料量合计，产出为已入库合格量合计，比率等于产出除以投入乘百分之百；阈值 95%；无投入显示未知。"));
bodyChildren.push(li("list-ch7a", "订单展示状态优先级：已发货、已完工、已入库、已投料。"));
bodyChildren.push(h2("7.2 异常订单处理闭环"));
bodyChildren.push(...figure(7, "图 7-2 异常订单处理闭环"));
bodyChildren.push(body("已完工且投出比低于 95% 的订单自动进入异常列表（生产管理、异常订单处理），处置流程为待处理、处理中、已闭环三步闭环，需记录原因、措施与处理人，支持重开与 Excel 导出存档。生产订单页「已完工」页签中异常单标红，可就地弹窗处理。"));

// —— 第八章 委外管理 ——
bodyChildren.push(h1("八、委外管理"));
bodyChildren.push(body("本章由委外管理员（OUTSOURCE）操作，菜单位置：委外管理。全流程如图 8-1。"));
bodyChildren.push(...figure(8, "图 8-1 委外加工全流程"));
bodyChildren.push(li("list-ch8", "委外发料不做调拨：人工发料直接从自有仓扣减；自动 FIFO 从代工厂名下的委外仓扣减，按实际业务二选一。"));
bodyChildren.push(li("list-ch8", "委外入库与生产入库同样走质检前置，前置校验为必须已委外出库。"));
bodyChildren.push(li("list-ch8", "质检合格自动入库并按入库量乘加工费单价生成加工费应付；不合格货物自动转入不合格品库。"));
bodyChildren.push(li("list-ch8", "委外入库支持溯源查询：配方谱系、发料批次与代工厂信息全程可追溯。"));

// —— 第九章 销售管理 ——
bodyChildren.push(h1("九、销售管理"));
bodyChildren.push(body("本章由销售员（SALES）做单、仓管（STOREKEEPER）发货，菜单位置：销售管理。"));
bodyChildren.push(h2("9.1 销售订单与发货"));
bodyChildren.push(...figure(9, "图 9-1 销售订单与发货流程"));
bodyChildren.push(li("list-ch9a", "**两步式录入**：必须先选「销售大类」（材料、半成品、成品），明细下拉才会显示该大类物料；切换大类会清空已录明细。搜物料用编码或名称，不是配方号。"));
bodyChildren.push(li("list-ch9a", "应收到期日规则：款到发货为发货当天；账期 N 天为发货日加 N 天；月结、两月结、三月结为次月、次两月、次三月 1 号；货到付款为立账当天。"));
bodyChildren.push(li("list-ch9a", "发货单生成时不占库存、不立应收；仓管审核那一刻才按批号扣库存并生成应收，审核前系统会校验明细必须已定价。"));
bodyChildren.push(h2("9.2 销售退货"));
bodyChildren.push(...figure(10, "图 9-2 销售退货流程"));
bodyChildren.push(body("退货单创建时客户可不从档案选择（参照原出库单带出或直接输入客户名）；销售员审核后由仓管执行退货入库，系统自动生成新批号并按 FIFO 自动红冲应收。"));
bodyChildren.push(h2("9.3 油尾退回与消化"));
bodyChildren.push(...figure(11, "图 9-3 油尾退回与消化流程"));
bodyChildren.push(body("油尾退回是客户退回未用完油漆（已加稀料）的专门通道：参照原销售出库单创建退回单（YW 单号），确认后自动入油尾库（原批号带回、质检状态记 TAILING、单位成本 0、不走质检、精确到库位），此后不可再销售。结算两种方式：折价退（单价大于 0）冲减客户应收；付费回收（单价小于 0）生成客户应收。唯一消化途径是制漆配方添加油尾节点（见图 5-2）后生产人工领料。销售、委外、调拨与自动 FIFO 一律禁止动用油尾库。"));

// —— 第十章 库存中心 ——
bodyChildren.push(h1("十、库存中心"));
bodyChildren.push(body("本章由仓管（STOREKEEPER）操作，菜单位置：库存中心。"));
bodyChildren.push(h2("10.1 出库通用校验链"));
bodyChildren.push(...figure(12, "图 10-1 出库通用校验链（每次出库系统自动执行）"));
bodyChildren.push(h2("10.2 库存查询"));
bodyChildren.push(li("list-ch10a", "**双视图**：按编码视图显示该编码跨批次合计总量与均价；按批次视图显示每个批次的余量与质检信息。"));
bodyChildren.push(li("list-ch10a", "批次视图质检贯通：批次行直接显示质检单号、检测结果、检验员与检验日期；行首展开可查看逐项检测明细；点击检测结果可弹出完整质检报告单并支持打印。"));
bodyChildren.push(li("list-ch10a", "历史批次显示「未质检」属正常现象：该批次无质检记录时系统只做精确匹配，不跨批次兜底。"));
bodyChildren.push(h2("10.3 日常库存操作"));
bodyChildren.push(...mkTable(
  ["操作", "入口", "要点"],
  [
    ["跨仓调拨", "库存查询/库存操作", "禁止过期批次；不合格品库物料不可调出。"],
    ["盘点", "盘库管理", "盘盈、盘亏、库位调整三类，确认立即生效。"],
    ["其他入库", "其他入库", "创建即送检（无确认按钮），批号与库位必选。"],
    ["其他出库", "其他出库", "用途必须选择（报废、样品、退货等）；是过期料与隔离仓库存的唯一合法出口（限报废、退货、样品）；确认即扣库存。"],
    ["期初导入", "库存查询工具栏", "详见第四章。"],
  ],
  [16, 22, 62], "表 10-1 日常库存操作一览"
));
bodyChildren.push(h2("10.4 库存预警"));
bodyChildren.push(body("**低库存预警**（报表中心）按「物料+仓库」口径计算月用量、日用量、安全库存与可用天数；委外仓无出库记录不预警；支持一键生成采购单。**过期预警**（报表中心）提供临期与已过期批次清单并支持导出；过期料的处理通道为其他出库报废。"));

// —— 第十一章 质量管理 ——
bodyChildren.push(h1("十一、质量管理"));
bodyChildren.push(body("本章由质检员（QC）操作，菜单位置：质量管理、质检管理、质检模板。"));
bodyChildren.push(h2("11.1 质检模板体系"));
bodyChildren.push(body("质检单创建时按物料大类自动套用检测项模板，共 7 套默认模板："));
bodyChildren.push(...mkTable(
  ["适用大类", "模板", "检测项数"],
  [
    ["S 溶剂", "溶剂模板", "8"],
    ["R 树脂", "树脂模板", "10"],
    ["A 助剂", "助剂模板", "8"],
    ["P 颜料", "颜料模板", "10"],
    ["F 填料", "填料模板", "8"],
    ["B 半成品", "研磨浆模板", "8"],
    ["C 成品", "成品漆出厂模板", "12"],
  ],
  [34, 40, 26], "表 11-1 质检检测项模板（按物料大类）"
));
bodyChildren.push(...figure(13, "图 11-1 质检模板快照机制"));
bodyChildren.push(li("list-ch11a", "检测项内容在质检模板管理页维护（改页面即可，不要改代码）；同类别可建多套模板，只有「默认」那套生效。"));
bodyChildren.push(li("list-ch11a", "质检管理按被检物料类型分材料、半成品、成品三个页签。"));
bodyChildren.push(li("list-ch11a", "打印与预览共用同一张报告单；模板页可「预览效果」。"));
bodyChildren.push(li("list-ch11a", "判定弹窗可补填批号，作为检验员按实物包装批号录入的兜底路径。"));
bodyChildren.push(h2("11.2 质检判定与库存落点"));
bodyChildren.push(...figure(14, "图 11-2 质检判定与库存落点（全场景）"));
bodyChildren.push(body("来料质检不合格自动生成采购退货单、货物退供应商；生产、委外与其他入库质检不合格时货物自动转入不合格品库（判定时选择存放库位，不选自动落默认库位），后续仅允许通过其他出库的报废或退货清账。所有库存与报表口径默认排除 REJECT 与 TAILING 两种状态；显式选择不合格品库可查看明细。"));

// —— 第十二章 财务管理 ——
bodyChildren.push(h1("十二、财务管理"));
bodyChildren.push(body("本章由财务（FINANCE）操作，菜单位置：财务管理与报表中心财务类报表。应收应付的三个立账点：来料质检合格入库按到货单金额立应付；委外入库合格按入库量乘加工费单价立应付；销售出库审核按实际出库量乘单价立应收（到期日按客户收款条件自动计算）。收付款单选择客户或供应商后按 FIFO 自动冲减最早未清账单，账单状态自动流转为未付、部分收付、已结清。"));
bodyChildren.push(...figure(15, "图 12-1 应收应付与收付款冲减"));
bodyChildren.push(body("报表支撑：应收明细、应付明细、应收总表、应付总表、账龄分析、财务趋势分析（报表中心）。金额字段按权限显隐（需采购价格或财务金额权限）。"));

// —— 第十三章 报表与看板 ——
bodyChildren.push(h1("十三、报表与看板"));
bodyChildren.push(body("报表中心供总经理（GM）与各岗位使用，共 18 张报表加工作台驾驶舱。"));
bodyChildren.push(...mkTable(
  ["看板/报表", "用途"],
  [
    ["经营看板（驾驶舱）", "区间累计销售额与毛利 KPI、销售与毛利排行 TOP8、库存预警、订单执行全景，月份联动。"],
    ["工作台", "统计卡、快捷操作、最新采购与销售订单、委外工单、月度订单与财务汇总。"],
    ["销售报表 / 采购报表 / 采购分析", "量价走势、供应商结构。"],
    ["生产报表 / 生产进度表", "产量、订单执行进度。"],
    ["库存报表 / 库存分析", "库存结构、周转。"],
    ["委外报表", "加工量与费用。"],
    ["低库存预警 / 过期预警", "详见 10.4 节。"],
    ["质检报表", "合格率统计，含检测明细。"],
    ["账龄 / 应收应付 / 趋势", "财务分析（详见第十二章）。"],
  ],
  [36, 64], "表 13-1 报表与看板一览"
));

// —— 第十四章 系统管理 ——
bodyChildren.push(h1("十四、系统管理"));
bodyChildren.push(body("本章由系统管理员（SS）操作，菜单位置：系统设置。"));
bodyChildren.push(...figure(16, "图 14-1 系统管理流程"));
bodyChildren.push(h2("14.1 用户与角色权限"));
bodyChildren.push(body("用户管理负责建账号、分配角色与重置密码；角色权限为树形勾选（模块可见、做单、审核、敏感字段四级）。保存后该角色在线用户立即被踢下线，重新登录后新权限生效。"));
bodyChildren.push(h2("14.2 操作日志"));
bodyChildren.push(body("记录所有写操作（增、删、改）与登录成功失败，查询类操作不记录；内容包含时间、账号、IP、模块、参数（密码与密钥自动脱敏）与耗时。保留策略：在线可查近 30 天（查询跨度上限 93 天）；超 30 天自动归档，归档数据按月检索；归档保留 12 个月后自动清理，全程无需人工维护。"));
bodyChildren.push(h2("14.3 运维提醒"));
bodyChildren.push(body("系统升级部署后，各用户浏览器可能仍在使用旧页面缓存，需刷新页面（必要时强制刷新）才能看到新功能。"));

// —— 附录 A ——
bodyChildren.push(h1("附录 A 单据状态机速查表"));
bodyChildren.push(...mkTable(
  ["单据", "状态流转", "关键自动点"],
  [
    ["采购单（原料/成品）", "开立、已审核、全部到货、已结束（可反审核）", "到货量达标自动「全部到货」。"],
    ["来料/出厂质检单", "待检、合格、让步接收、退货", "合格自动入库并立应付；不合格自动开退货单。"],
    ["生产订单", "开立、已确认、已排产、已完工", "入库质检合格自动完工；投出比低于 95% 进异常列表。"],
    ["生产出库单（领料）", "创建即已确认（扣库存）", "自动 FIFO 跳过过期与隔离仓。"],
    ["生产/委外入库单", "确认（即待质检）、完成、质检退货", "合格自动入正式仓；不合格自动入不合格品库。"],
    ["委外订单", "开立、已确认、已委外、已完工（可取消排产）", "排产校验库存；入库合格生成加工费应付。"],
    ["销售订单", "开立、已确认、已发货、已结束", "全部发完自动「已发货」；结束后不可再发货或转单。"],
    ["销售出库单", "草稿（不扣库存）、已确认（扣库存并立应收）", "未定价拦截审核。"],
    ["采购退货单", "草稿、完成（质检退货）；草稿、已审核、完成（手工退货）", "质检退货不出库、不冲应付。"],
    ["销售退货单", "草稿、已审核、完成", "退货入库自动红冲应收。"],
    ["油尾退回单", "草稿、完成（确认即入油尾库）", "不走质检；折价退冲应收。"],
    ["其他入库单", "创建即待质检、判定后入库", "—"],
    ["其他出库单", "草稿、已确认（扣库存）", "过期料与隔离仓唯一合法出口（限报废、退货、样品）。"],
    ["盘库单", "确认即生效", "—"],
    ["应收/应付", "未付、部分收付、已结清", "收付款 FIFO 自动冲减。"],
    ["配方版本", "草稿、已发布、已归档", "新版发布时旧版自动归档。"],
    ["异常订单处置", "待处理、处理中、已闭环（可重开）", "—"],
  ],
  [20, 44, 36], "表 A-1 单据状态机速查"
));

// —— 附录 B ——
bodyChildren.push(h1("附录 B 常见问题速查"));
bodyChildren.push(...mkTable(
  ["现象", "原因与处理"],
  [
    ["操作中突然掉线或 401", "单点登录：同账号在别处登录把您顶掉了，属正常保护机制。"],
    ["销售订单明细搜不到物料", "没有先选「销售大类」，选大类后下拉才会出现对应物料。"],
    ["某批次显示「未质检」", "该批次入库早于质检信息持久化功能且无精确匹配质检单，属正常显示。"],
    ["出库报「批次已过期」", "过期批次禁止正常出库；确需处理走其他出库的报废、样品或退货。"],
    ["出库选不到某批货", "该批在不合格品库或油尾库（隔离），或已被上次出库扣完。"],
    ["部署更新后看不到新功能", "浏览器页面缓存，刷新页面（强制刷新）即可。"],
    ["生产订单入库后状态没变已完工", "不会发生：入库质检合格即自动完工；历史遗留单系统启动时已自动补全。"],
    ["半成品编码是 PJ 开头不是 B", "编码按小类规则生成；判断类别看物料「大类」字段，不看编码首字母。"],
    ["质检判定结果跟以前不一样", "改质检模板只影响新建单；历史单持有当时快照，不受影响。"],
    ["采购退货后应付没减少", "正常：质检不合格的货从未入库立账，退货不产生任何出库与冲账。"],
  ],
  [34, 66], "表 B-1 常见问题速查"
));

// ══════════ 文档组装 ══════════
const numberingConfig = ["list-ch4", "list-ch5a", "list-ch6", "list-ch7a", "list-ch8", "list-ch9a", "list-ch10a", "list-ch11a"].map(ref => ({
  reference: ref,
  levels: [{
    level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
    style: { paragraph: { indent: { left: 720, hanging: 360 } } },
  }],
}));

const pgSize = { width: 11906, height: 16838 };
const pgMargin = { top: 1440, bottom: 1440, left: 1701, right: 1417 };

const doc = new Document({
  creator: "PIMS 系统管理组",
  title: "芃远综合管理系统（PIMS）标准操作流程 SOP V2.0",
  styles: {
    default: {
      document: {
        run: { font: F_BODY, size: 24, color: "000000" },
        paragraph: { spacing: { line: 312 } },
      },
      heading1: {
        run: { font: F_HEAD, size: 32, bold: true, color: P.primary },
        paragraph: { spacing: { before: 400, after: 200, line: 312 }, outlineLevel: 0 },
      },
      heading2: {
        run: { font: F_HEAD, size: 28, bold: true, color: P.primary },
        paragraph: { spacing: { before: 280, after: 140, line: 312 }, outlineLevel: 1 },
      },
    },
  },
  numbering: { config: numberingConfig },
  sections: [
    // ── Section 1: 封面（margin 0，无页码）──
    {
      properties: { page: { size: pgSize, margin: { top: 0, bottom: 0, left: 0, right: 0 } } },
      children: buildCoverR1({
        title: "标准操作流程 SOP",
        subtitle: "芃远综合管理系统（PIMS）· 流程图版",
        englishLabel: "STANDARD OPERATING PROCEDURES",
        metaLines: [
          "文档版本：V2.0（2026 年 8 月 17 日发布）",
          "编制单位：PIMS 系统管理组",
          "适用对象：全体系统操作人员",
          "文档密级：内部资料",
        ],
        footerLeft: "广东芃远新材料",
        footerRight: "PIMS · V2.0 · 2026",
        palette: P,
      }),
    },
    // ── Section 2: 文档控制 + 目录（Roman 页码）──
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: pgSize, margin: pgMargin, pageNumbers: { start: 1, formatType: NumberFormat.UPPER_ROMAN } },
      },
      headers: { default: docHeader() },
      footers: { default: pageNumFooter() },
      children: [
        new Paragraph({
          alignment: AlignmentType.CENTER, spacing: { before: 240, after: 200 },
          children: [new TextRun({ text: "文 档 信 息", bold: true, size: 32, color: P.primary, font: F_HEAD })],
        }),
        ...mkTable(
          ["项目", "内容"],
          [
            ["文档名称", "芃远综合管理系统（PIMS）标准操作流程（SOP）"],
            ["版本 / 日期", "V2.0 / 2026-08-17"],
            ["版本性质", "流程图版，全面改写；旧版 V1.14 纯文字手册归档于 docs/SOP-v1.14-archived.md"],
            ["适用范围", "采购、仓储、生产、排产、质检、委外、销售、财务、技术及系统管理全体岗位"],
            ["配套文档", "PRD.md（需求与修订记录）、API.md（接口）、DEPLOY.md（部署）"],
            ["主要变更", "补齐排产中心、投出比与异常订单闭环、不合格品库、油尾体系、期初库存导入、过期批次拦截、自动 FIFO 领料、销售转生产/转委外、结束订单、经营看板、质检模板体系、低库存预警按仓库口径、操作日志、AI 智能助手等模块"],
          ],
          [22, 78], "表 0-1 文档控制信息"
        ),
        new Paragraph({
          alignment: AlignmentType.CENTER, spacing: { before: 400, after: 300 },
          children: [new TextRun({ text: "目  录", bold: true, size: 32, color: P.primary, font: F_HEAD })],
        }),
        new TableOfContents("Table of Contents", { hyperlink: true, headingStyleRange: "1-2" }),
        new Paragraph({
          spacing: { before: 200 },
          children: [new TextRun({
            text: "说明：本目录由域代码生成。文档编辑后如需刷新页码，请在目录上点击右键并选择「更新域」。",
            italics: true, size: 18, color: "888888", font: F_BODY,
          })],
        }),
      ],
    },
    // ── Section 3: 正文（Arabic 页码从 1 起）──
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: pgSize, margin: pgMargin, pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL } },
      },
      headers: { default: docHeader() },
      footers: { default: pageNumFooter() },
      children: bodyChildren,
    },
  ],
});

Packer.toBuffer(doc).then(buf => {
  const out = path.join(BUILD, "SOP.docx");
  fs.writeFileSync(out, buf);
  console.log("生成完成:", out, `${(buf.length / 1024 / 1024).toFixed(2)} MB`);
});
