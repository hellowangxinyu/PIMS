# -*- coding: utf-8 -*-
p = 'D:/开发/PIMS/pims-web/src/views/Login.vue'
s = open(p, encoding='utf-8').read()

# ============ 1) 模板：径向光照中心变量（替换 --la 角度） ============
for k in ['bear', 'bunny', 'panda', 'fox', 'frog', 'penguin', 'monster']:
    old_l = ('<span class="toy-light" :style="{ \'--la\': `${toys.%s.ang + 180}deg`, '
             'transform: `translate(${toys.%s.lx}px, ${toys.%s.ly}px)` }"></span>' % (k, k, k))
    new_l = ('<span class="toy-light" :style="{ \'--lcx\': toys.%s.lcx + \'%%\', \'--lcy\': toys.%s.lcy + \'%%\', '
             'transform: `translate(${toys.%s.lx}px, ${toys.%s.ly}px)` }"></span>' % (k, k, k, k))
    assert old_l in s, k + ' light'
    s = s.replace(old_l, new_l)
    old_d = ('<span class="toy-dark" :style="{ \'--la\': `${toys.%s.ang}deg`, '
             'transform: `translate(${toys.%s.dx}px, ${toys.%s.dy}px)` }"></span>' % (k, k, k))
    new_d = ('<span class="toy-dark" :style="{ \'--dcx\': toys.%s.dcx + \'%%\', \'--dcy\': toys.%s.dcy + \'%%\', '
             'transform: `translate(${toys.%s.dx}px, ${toys.%s.dy}px)` }"></span>' % (k, k, k, k))
    assert old_d in s, k + ' dark'
    s = s.replace(old_d, new_d)
print('1) template ok')

# ============ 2) JS：光照中心百分比 ============
old_init = "  rot: 0, baseRot: BASE_TILT[k], pxL: 0, pxR: 0, py: 0, lean: 0, ang: 0,"
new_init = ("  rot: 0, baseRot: BASE_TILT[k], pxL: 0, pxR: 0, py: 0, lean: 0,\n"
            "  lcx: 38, lcy: 38, dcx: 62, dcy: 62,")
assert old_init in s
s = s.replace(old_init, new_init)

old_al = """    t.lean = mx * k.leanK
    // 光照方向角：受光层朝向鼠标、背光层反向（鼠标即光源参照）
    t.ang = Math.atan2(my, mx) * 180 / Math.PI
    // 受光层朝鼠标方向偏移，背光层反向"""
new_al = """    t.lean = mx * k.leanK
    // 径向光照中心：受光层中心朝鼠标方向移动，背光层反向（鼠标即光源参照）
    t.lcx = 50 - mx * 38
    t.lcy = 50 - my * 38
    t.dcx = 50 + mx * 38
    t.dcy = 50 + my * 38
    // 受光层朝鼠标方向偏移，背光层反向"""
assert old_al in s
s = s.replace(old_al, new_al)

old_ol = "    t.rot = 0; t.pxL = 0; t.pxR = 0; t.py = 0; t.lean = 0; t.ang = 0\n"
new_ol = "    t.rot = 0; t.pxL = 0; t.pxR = 0; t.py = 0; t.lean = 0\n    t.lcx = 38; t.lcy = 38; t.dcx = 62; t.dcy = 62\n"
assert old_ol in s
s = s.replace(old_ol, new_ol)
print('2) js ok')

# ============ 3) CSS：径向渐变（衰减在剪影边缘前归零） ============
old_css = """.toy-light {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  clip-path: ellipse(34% 40% at 50% 62%);
  /* 方向性光照：亮侧随鼠标方向实时旋转（无混合模式，纯 alpha，绝无光晕） */
  background: linear-gradient(var(--la, 315deg), rgba(255,255,255,0.42), rgba(255,255,255,0.08) 52%, transparent 74%);
  transition: transform 0.3s ease-out;
}
.toy-dark {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  clip-path: ellipse(34% 40% at 50% 62%);
  background: linear-gradient(var(--la, 135deg), rgba(10,8,22,0.34), transparent 58%);
  transition: transform 0.3s ease-out;
}"""
new_css = """.toy-light {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  clip-path: ellipse(34% 40% at 50% 62%);
  /* 径向方向光：中心朝鼠标，四周均匀衰减，在剪影边缘前已完全透明 → 无环无晕 */
  background: radial-gradient(circle at var(--lcx, 38%) var(--lcy, 38%),
              rgba(255,255,255,0.34) 0%, rgba(255,255,255,0.09) 36%, rgba(255,255,255,0) 54%);
  background-size: 135% 135%;
  transition: transform 0.3s ease-out;
}
.toy-dark {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  clip-path: ellipse(34% 40% at 50% 62%);
  background: radial-gradient(circle at var(--dcx, 62%) var(--dcy, 62%),
              rgba(10,8,22,0.3) 0%, rgba(10,8,22,0.08) 36%, rgba(10,8,22,0) 54%);
  background-size: 135% 135%;
  transition: transform 0.3s ease-out;
}"""
assert old_css in s
s = s.replace(old_css, new_css)
print('3) css ok')

# ============ 4) 浮雕再减淡 ============
old_r = """  background:
    radial-gradient(circle at 32% 26%, rgba(255,255,255,0.2), transparent 46%),
    radial-gradient(circle at 72% 82%, rgba(0,0,0,0.18), transparent 52%);"""
new_r = """  background:
    radial-gradient(circle at 32% 26%, rgba(255,255,255,0.14), transparent 40%),
    radial-gradient(circle at 72% 82%, rgba(0,0,0,0.12), transparent 44%);"""
assert old_r in s
s = s.replace(old_r, new_r)
print('4) relief ok')

# ============ 5) 尺寸公式：任何窗口宽度都不溢出 ============
old_c = ".plush-row { --toy-w: clamp(74px, 16cqw, 152px); }"
new_c = ".plush-row { --toy-w: clamp(64px, 15cqw, 152px); }"
assert old_c in s
s = s.replace(old_c, new_c)
old_m = "@media (max-height: 820px) {\n  .plush-row { --toy-w: 88px; }"
new_m = "@media (max-height: 820px) {\n  .plush-row { --toy-w: clamp(64px, 15cqw, 88px); }"
assert old_m in s
s = s.replace(old_m, new_m)
print('5) sizing ok')

open(p, 'w', encoding='utf-8').write(s)
print('done, len:', len(s))
