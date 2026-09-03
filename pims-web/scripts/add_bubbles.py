# -*- coding: utf-8 -*-
import re

p = 'D:/开发/PIMS/pims-web/src/views/Login.vue'
s = open(p, encoding='utf-8').read()
TOYS = ['bear', 'bunny', 'panda', 'fox', 'frog', 'penguin', 'monster']

# ============ 1) 移除 HTML 光影层 span（共 7×3 个） ============
pat = re.compile(r'\s*<span class="toy-(?:relief|light|dark)"[^>]*?></span>', re.S)
s, n = pat.subn('', s)
assert n == 21, n
print('1) overlay spans removed:', n)

# ============ 2) SVG 原生光照：defs + 光照椭圆（插入每个 </svg> 前） ============
for k in TOYS:
    defs = (
        '        <defs>\n'
        '          <radialGradient id="lg-%s" cx="50%%" cy="50%%" r="60%%" :fx="toys.%s.lfx + \'%%\'" :fy="toys.%s.lfy + \'%%\'">\n'
        '            <stop offset="0%%" stop-color="#ffffff" stop-opacity="0.30"/>\n'
        '            <stop offset="42%%" stop-color="#ffffff" stop-opacity="0.06"/>\n'
        '            <stop offset="68%%" stop-color="#ffffff" stop-opacity="0"/>\n'
        '          </radialGradient>\n'
        '          <radialGradient id="dg-%s" cx="50%%" cy="50%%" r="60%%" :fx="toys.%s.dfx + \'%%\'" :fy="toys.%s.dfy + \'%%\'">\n'
        '            <stop offset="0%%" stop-color="#0a0816" stop-opacity="0.28"/>\n'
        '            <stop offset="45%%" stop-color="#0a0816" stop-opacity="0.06"/>\n'
        '            <stop offset="70%%" stop-color="#0a0816" stop-opacity="0"/>\n'
        '          </radialGradient>\n'
        '        </defs>\n'
        '        <ellipse fill="url(#lg-%s)" cx="75" cy="100" rx="54" ry="62"/>\n'
        '        <ellipse fill="url(#dg-%s)" cx="75" cy="100" rx="54" ry="62"/>\n' % (
            k, k, k, k, k, k, k, k)
    )
    old = '        </svg>\n</span>'
    new = defs + '        </svg>\n</span>'
    assert old in s, k
    s = s.replace(old, new, 1)
print('2) svg lighting ok')

# ============ 3) JS：lfx/lfy/dfx/dfy（替换 lcx/lcy/dcx/dcy） ============
old_init = "  rot: 0, baseRot: BASE_TILT[k], pxL: 0, pxR: 0, py: 0, lean: 0,\n  lcx: 38, lcy: 38, dcx: 62, dcy: 62,"
new_init = "  rot: 0, baseRot: BASE_TILT[k], pxL: 0, pxR: 0, py: 0, lean: 0,\n  lfx: 38, lfy: 38, dfx: 62, dfy: 62,"
assert old_init in s
s = s.replace(old_init, new_init)

old_al = """    t.lean = mx * k.leanK
    // 径向光照中心：受光层中心朝鼠标方向移动，背光层反向（鼠标即光源参照）
    t.lcx = 50 - mx * 38
    t.lcy = 50 - my * 38
    t.dcx = 50 + mx * 38
    t.dcy = 50 + my * 38
    // 受光层朝鼠标方向偏移，背光层反向"""
new_al = """    t.lean = mx * k.leanK
    // SVG 原生径向光照焦点：受光焦点朝鼠标方向移动，背光焦点反向（鼠标即光源参照）
    t.lfx = 50 - mx * 42
    t.lfy = 50 - my * 42
    t.dfx = 50 + mx * 42
    t.dfy = 50 + my * 42"""
assert old_al in s
s = s.replace(old_al, new_al)

old_ol = "    t.rot = 0; t.pxL = 0; t.pxR = 0; t.py = 0; t.lean = 0\n    t.lcx = 38; t.lcy = 38; t.dcx = 62; t.dcy = 62\n"
new_ol = "    t.rot = 0; t.pxL = 0; t.pxR = 0; t.py = 0; t.lean = 0\n    t.lfx = 38; t.lfy = 38; t.dfx = 62; t.dfy = 62\n"
assert old_ol in s
s = s.replace(old_ol, new_ol)
print('3) js lighting ok')

# ============ 4) CSS：删除 overlay 规则与逐只裁剪规则 ============
start = s.index('.toy-relief {')
end = s.index('/* 点击光脉冲 */')
removed = s[start:end]
s = s[:start] + s[end:]
n_clip = removed.count('.toywrap.')
print('4) overlay css removed, clip rules:', n_clip)

# ============ 5) 间距调柔（不深交叠，保证全身可见） ============
old_sp = """/* 间距不规则：有的紧贴、有的留缝、有的交叠 */
.toywrap.bear   { width: calc(var(--toy-w) * 1.12); height: calc(var(--toy-w) * 1.12); margin-left: calc(var(--toy-w) * -0.30); }
.toywrap.panda  { width: calc(var(--toy-w) * 1.08); height: calc(var(--toy-w) * 1.08); margin-left: calc(var(--toy-w) * 0.08); }
.toywrap.monster{ width: calc(var(--toy-w) * 1.02); height: calc(var(--toy-w) * 1.02); margin-left: calc(var(--toy-w) * 0.14); }
.toywrap.penguin{ width: calc(var(--toy-w) * 0.95); height: calc(var(--toy-w) * 0.95); margin-left: calc(var(--toy-w) * -0.18); }
.toywrap.frog   { width: calc(var(--toy-w) * 0.90); height: calc(var(--toy-w) * 0.90); margin-left: calc(var(--toy-w) * 0.02); }
.toywrap.bunny  { width: calc(var(--toy-w) * 0.85); height: calc(var(--toy-w) * 0.85); margin-left: calc(var(--toy-w) * -0.14); }
.toywrap.fox    { margin-left: calc(var(--toy-w) * -0.28); }"""
new_sp = """/* 间距不规则但不过深：既错落又保证全身可见 */
.toywrap.bear   { width: calc(var(--toy-w) * 1.12); height: calc(var(--toy-w) * 1.12); margin-left: calc(var(--toy-w) * -0.18); }
.toywrap.panda  { width: calc(var(--toy-w) * 1.08); height: calc(var(--toy-w) * 1.08); margin-left: calc(var(--toy-w) * 0.10); }
.toywrap.monster{ width: calc(var(--toy-w) * 1.02); height: calc(var(--toy-w) * 1.02); margin-left: calc(var(--toy-w) * 0.14); }
.toywrap.penguin{ width: calc(var(--toy-w) * 0.95); height: calc(var(--toy-w) * 0.95); margin-left: calc(var(--toy-w) * -0.12); }
.toywrap.frog   { width: calc(var(--toy-w) * 0.90); height: calc(var(--toy-w) * 0.90); margin-left: calc(var(--toy-w) * 0.03); }
.toywrap.bunny  { width: calc(var(--toy-w) * 0.85); height: calc(var(--toy-w) * 0.85); margin-left: calc(var(--toy-w) * -0.10); }
.toywrap.fox    { margin-left: calc(var(--toy-w) * -0.20); }"""
assert old_sp in s
s = s.replace(old_sp, new_sp)

old_c = ".plush-row { --toy-w: clamp(64px, 15cqw, 152px); }"
new_c = ".plush-row { --toy-w: clamp(62px, 15cqw, 152px); }"
assert old_c in s
s = s.replace(old_c, new_c)
old_m = "@media (max-height: 820px) {\n  .plush-row { --toy-w: clamp(64px, 15cqw, 88px); }"
new_m = "@media (max-height: 820px) {\n  .plush-row { --toy-w: clamp(62px, 15cqw, 88px); }"
assert old_m in s
s = s.replace(old_m, new_m)
print('5) spacing ok')

# ============ 6) 对话气泡系统：import + 状态 + 逻辑 ============
anchor_imp = "import api from '../api'"
assert anchor_imp in s
s = s.replace(anchor_imp, "import api from '../api'\nimport { RIDDLES } from '../data/riddles'", 1)

# 表演状态与逻辑（插在 onGlobalMouseUp 定义之前）
anchor_logic = "// 任意位置松开鼠标都结束拉伸"
show_code = """// ===== 脑筋急转弯对话表演：随机一只出题，其余六只挨个乱猜，最后揭晓 =====
const GUESSES = [
  '我觉得是……这样？', '这题我会！是它！', '让我想想……猜不出来',
  '肯定不是这个……吧', '我知道了！但是不说', '八成是——算了',
  '这题我熟！明天告诉你', '我猜是……随便吧', '放弃，下一个',
  '嗯……我觉得不是', '好难！我先干饭', '我猜中了！才怪'
]
const show = reactive({ active: false, asker: '', phase: 'q', riddle: '', answer: '', bubbles: {} })
let showTimers = []
let showTimeout = null

function stopShow() {
  showTimers.forEach(clearTimeout)
  showTimers = []
  if (showTimeout) { clearTimeout(showTimeout); showTimeout = null }
  show.active = false
  show.bubbles = {}
}
function scheduleShow() {
  if (show.active || showTimeout) return
  showTimeout = setTimeout(startShow, 4000 + Math.random() * 6000)
}
function startShow() {
  showTimeout = null
  const r = RIDDLES[Math.floor(Math.random() * RIDDLES.length)]
  const asker = TOY_ORDER[Math.floor(Math.random() * TOY_ORDER.length)]
  const others = TOY_ORDER.filter(t => t !== asker).sort(() => Math.random() - 0.5)
  show.active = true
  show.asker = asker
  show.phase = 'q'
  show.riddle = r.q
  show.answer = r.a
  show.bubbles = { [asker]: { text: r.q, kind: 'asker' } }
  // 其余六只挨个冒泡乱猜
  others.forEach((t, i) => {
    showTimers.push(setTimeout(() => {
      if (!show.active) return
      show.bubbles[t] = { text: GUESSES[Math.floor(Math.random() * GUESSES.length)], kind: 'answer' }
      showTimers.push(setTimeout(() => {
        if (show.active && show.bubbles[t]) delete show.bubbles[t]
      }, 2300))
    }, 2800 + i * 2400))
  })
  // 六只都答完后出题者揭晓正确答案
  const revealAt = 2800 + others.length * 2400 + 500
  showTimers.push(setTimeout(() => {
    if (!show.active) return
    show.phase = 'a'
    show.bubbles = { [asker]: { text: r.a, kind: 'correct' } }
  }, revealAt))
  // 一轮结束，安排下一轮
  showTimers.push(setTimeout(() => {
    show.active = false
    show.bubbles = {}
    scheduleShow()
  }, revealAt + 5600))
}

// 任意位置松开鼠标都结束拉伸"""
assert anchor_logic in s
s = s.replace(anchor_logic, show_code + '\n' + anchor_logic, 1)

# 鼠标进入 → 停演；离开 → 安排表演
old_mi = '@mouseenter="mouseIn = true"'
new_mi = '@mouseenter="onMouseIn"'
assert old_mi in s
s = s.replace(old_mi, new_mi)

anchor_fn = """function onLeave() {
  mouseIn.value = false"""
new_fn = """function onMouseIn() {
  mouseIn.value = true
  stopShow()
}
function onLeave() {
  mouseIn.value = false
  scheduleShow()"""
assert anchor_fn in s
s = s.replace(anchor_fn, new_fn, 1)

# onMounted 初始安排 + onUnmounted 清理
old_mount = "onMounted(() => window.addEventListener('mouseup', onGlobalMouseUp))\nonUnmounted(() => window.removeEventListener('mouseup', onGlobalMouseUp))"
new_mount = "onMounted(() => {\n  window.addEventListener('mouseup', onGlobalMouseUp)\n  scheduleShow()\n})\nonUnmounted(() => {\n  window.removeEventListener('mouseup', onGlobalMouseUp)\n  stopShow()\n})"
assert old_mount in s
s = s.replace(old_mount, new_mount)
print('6) show logic ok')

# ============ 7) 模板：每只公仔加气泡容器 ============
for k in TOYS:
    old_span = '</span>\n\n'
    # 在 toywrap 结束标签前插入气泡（toywrap 结构：</svg>…overlays 已删…</span>）
    old_end = '        </svg>\n</span>'
    new_end = ('        </svg>\n'
               '  <div v-if="show.bubbles.%s" class="bubble" :class="show.bubbles.%s.kind">{{ show.bubbles.%s.text }}</div>\n'
               '</span>' % (k, k, k))
    assert old_end in s, k
    s = s.replace(old_end, new_end, 1)
print('7) bubble template ok')

# ============ 8) 气泡 CSS（追加到 .toywrap:hover 规则后） ============
anchor_css = """.toywrap:hover { filter: drop-shadow(0 12px 20px rgba(0,0,0,0.4)); }"""
bubble_css = anchor_css + """

/* ===== 对话气泡 ===== */
.bubble {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 8px);
  transform: translateX(-50%);
  z-index: 8;
  max-width: 190px;
  background: #fff;
  border-radius: 13px;
  padding: 7px 11px;
  font-size: 12px;
  line-height: 1.5;
  color: #1e293b;
  box-shadow: 0 8px 22px rgba(0,0,0,0.28);
  white-space: normal;
  pointer-events: none;
  animation: bubbleIn 0.28s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.bubble::after {
  content: '';
  position: absolute;
  left: 50%; bottom: -7px;
  transform: translateX(-50%);
  border: 7px solid transparent;
  border-top-color: #fff;
  border-bottom: 0;
}
.bubble.asker {
  background: #fffbea;
  border: 1.5px solid #fbbf24;
  font-weight: 600;
  max-width: 230px;
}
.bubble.asker::after { border-top-color: #fffbea; }
.bubble.correct {
  background: #ecfdf5;
  border: 1.5px solid #34d399;
  font-weight: 700;
}
.bubble.correct::after { border-top-color: #ecfdf5; }
@keyframes bubbleIn {
  from { opacity: 0; transform: translateX(-50%) translateY(8px) scale(0.88); }
  to { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}"""
assert anchor_css in s
s = s.replace(anchor_css, bubble_css, 1)
print('8) bubble css ok')

open(p, 'w', encoding='utf-8').write(s)
print('done, len:', len(s))
