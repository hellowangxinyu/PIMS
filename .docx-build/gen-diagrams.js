// 渲染 SOP.md 中的 mermaid 流程图为高清 PNG（Edge headless + mermaid@11 CDN + 元素截图）
// 产出: diagrams/figNN.png + figmap.json
const puppeteer = require("puppeteer-core");
const fs = require("fs");
const path = require("path");
const { pathToFileURL } = require("url");

const BUILD = __dirname;
const OUT = path.join(BUILD, "diagrams");
fs.mkdirSync(OUT, { recursive: true });

const SOP = fs.readFileSync(path.join(BUILD, "..", "docs", "SOP.md"), "utf-8");

// 逐行扫描：记录最近的标题，遇到 ```mermaid 块收集代码
const diagrams = [];
let lastHeading = "";
const lines = SOP.split("\n");
for (let i = 0; i < lines.length; i++) {
  const h = lines[i].match(/^(#{1,4})\s+(.*)$/);
  if (h) { lastHeading = h[2].trim(); }
  if (lines[i].trim() === "```mermaid") {
    const code = [];
    let j = i + 1;
    while (j < lines.length && lines[j].trim() !== "```") { code.push(lines[j]); j++; }
    diagrams.push({ i: diagrams.length + 1, title: lastHeading, code: code.join("\n") });
    i = j;
  }
}
console.log(`提取到 ${diagrams.length} 张流程图`);

const html = `<!DOCTYPE html><html><head><meta charset="utf-8">
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<style>body{margin:0;background:#fff;padding:8px}
.fig{display:inline-block;background:#fff;padding:6px;margin:4px}</style></head><body>
<div id="c"></div>
<script>
window.__done = false; window.__err = null;
const DIAGRAMS = ${JSON.stringify(diagrams)};
mermaid.initialize({
  startOnLoad: false, securityLevel: "loose", theme: "neutral",
  flowchart: { htmlLabels: false, useMaxWidth: false },
  themeVariables: { fontFamily: '"Microsoft YaHei", sans-serif', fontSize: "15px" }
});
async function renderAll() {
  for (const d of DIAGRAMS) {
    const { svg } = await mermaid.render("g" + d.i, d.code);
    const div = document.createElement("div");
    div.id = "fig-" + d.i;
    div.className = "fig";
    div.innerHTML = svg;
    document.getElementById("c").appendChild(div);
  }
  window.__done = true;
}
renderAll().catch(e => { window.__err = String(e && e.stack || e); window.__done = true; });
</script></body></html>`;

const htmlPath = path.join(BUILD, "render.html");
fs.writeFileSync(htmlPath, html, "utf-8");

(async () => {
  const browser = await puppeteer.launch({
    executablePath: "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    headless: "new",
    args: ["--no-sandbox", "--disable-gpu", "--font-render-hinting=none"],
  });
  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1600, height: 1000, deviceScaleFactor: 2 });
    await page.goto(pathToFileURL(htmlPath).href, { waitUntil: "networkidle0", timeout: 60000 });
    await page.waitForFunction("window.__done === true", { timeout: 120000, polling: 500 });
    const err = await page.evaluate("window.__err");
    if (err) { console.error("页面渲染错误:", err); process.exit(1); }

    const map = [];
    for (const d of diagrams) {
      const el = await page.$("#fig-" + d.i);
      if (!el) { console.error(`找不到 #fig-${d.i}`); process.exit(1); }
      const file = `fig${String(d.i).padStart(2, "0")}.png`;
      await el.screenshot({ path: path.join(OUT, file) });
      const box = await el.boundingBox();
      map.push({ i: d.i, title: d.title, file, w: Math.round(box.width), h: Math.round(box.height) });
      console.log(`${file}  ${Math.round(box.width)}x${Math.round(box.height)} css-px (2x 截图)  <- ${d.title}`);
    }
    fs.writeFileSync(path.join(BUILD, "figmap.json"), JSON.stringify(map, null, 2));
    console.log(`完成：${map.length} 张 PNG -> ${OUT}`);
  } finally {
    await browser.close();
  }
})().catch(e => { console.error(e); process.exit(1); });
