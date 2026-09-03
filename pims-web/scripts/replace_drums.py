# -*- coding: utf-8 -*-
p = 'D:/开发/PIMS/pims-web/src/views/Login.vue'
s = open(p, encoding='utf-8').read()

# ============ 1) 200kg 开口桶（每只外溢形状各异） ============
# 桶身公共部分
DRUM_BASE = """<svg viewBox="0 0 120 150" aria-hidden="true">
              <path d="M24 22 L27 134 Q27.5 140 34 140 L86 140 Q92.5 140 93 134 L96 22 Z" fill="#cdd5df" stroke="#98a3b1" stroke-width="1.5"/>
              <path d="M25 58 Q60 66 95 58" fill="none" stroke="#aeb9c5" stroke-width="5"/>
              <path d="M25 63 Q60 71 95 63" fill="none" stroke="#eef2f6" stroke-width="2.5"/>
              <path d="M25 92 Q60 100 95 92" fill="none" stroke="#aeb9c5" stroke-width="5"/>
              <path d="M25 97 Q60 105 95 97" fill="none" stroke="#eef2f6" stroke-width="2.5"/>
              <path d="M26 122 Q60 129 94 122" fill="none" stroke="#aeb9c5" stroke-width="4"/>
              <ellipse cx="60" cy="140" rx="37" ry="11" fill="#dfe6ec" stroke="#98a3b1" stroke-width="2"/>
              <ellipse cx="60" cy="30" rx="38" ry="12" fill="none" stroke="#98a3b1" stroke-width="4"/>
              <rect x="80" y="22" width="9" height="15" rx="2.5" fill="#7c8694"/>
              <ellipse cx="60" cy="20" rx="36" ry="11" fill="#8b95a3"/>
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="{c}"/>
              {overflow}
            </svg>"""

# 7 种不同的外溢形状（blob + 长短不一的漆淌 + 积漆）
OVERFLOWS = {
 '赤': """<path d="M28 18 Q36 6 50 12 Q60 6 68 16 Q58 24 50 21 Q40 25 30 23 Q24 20 28 18 Z" fill="{c}"/>
              <path d="M32 20 q-2 26 2 38 q1 5 -2 6 q-3 -1 -2 -6 q-4 -12 -1 -38 z" fill="{c}"/>
              <ellipse cx="31" cy="66" rx="4" ry="2.5" fill="{c}"/>""",
 '橙': """<path d="M40 20 Q48 8 60 12 Q70 7 78 16 Q70 24 62 21 Q52 25 42 22 Q36 20 40 20 Z" fill="{c}"/>
              <path d="M50 19 q0 14 1 19 q0.5 3 -1 4 q-2 -1 -1 -4 q-1 -5 0 -19 z" fill="{c}"/>
              <ellipse cx="50" cy="40" rx="3" ry="2" fill="{c}"/>
              <path d="M66 18 q0 12 1 16 q0.5 3 -1 4 q-2 -1 -1 -4 q-1 -4 0 -16 z" fill="{c}"/>
              <ellipse cx="66" cy="36" rx="3" ry="2" fill="{c}"/>""",
 '黄': """<path d="M26 20 Q40 10 58 12 Q72 8 84 18 Q74 26 62 23 Q48 27 34 24 Q26 22 26 20 Z" fill="{c}"/>
              <path d="M70 17 q3 28 -2 52 q-1 5 2 6 q3 -1 2 -6 q5 -24 -2 -52 z" fill="{c}"/>
              <ellipse cx="70" cy="72" rx="4.5" ry="2.5" fill="{c}"/>""",
 '绿': """<path d="M52 18 Q58 10 66 14 Q72 10 76 16 Q70 22 64 20 Q58 23 52 18 Z" fill="{c}"/>
              <path d="M56 16 q0 8 1 11 q0.5 2.5 -1 3.5 q-2 -1 -1 -3.5 q-1 -3 0 -11 z" fill="{c}"/>
              <path d="M64 15 q0 7 1 10 q0.5 2 -1 3 q-2 -1 -1 -3 q-1 -3 0 -10 z" fill="{c}"/>
              <path d="M70 15 q0 9 1 12 q0.5 2.5 -1 3.5 q-2 -1 -1 -3.5 q-1 -3 0 -12 z" fill="{c}"/>""",
 '青': """<path d="M34 18 Q44 8 58 12 Q66 7 72 15 Q64 22 56 20 Q46 24 36 21 Q30 20 34 18 Z" fill="{c}"/>
              <path d="M56 18 q0 18 1 29 q0.5 4 -1.5 5 q-3 -1 -1.5 -5 q-1 -11 0 -29 z" fill="{c}"/>
              <ellipse cx="56" cy="50" rx="4" ry="2.5" fill="{c}"/>""",
 '蓝': """<path d="M24 22 Q36 12 52 14 Q66 9 80 18 Q70 28 58 25 Q44 30 30 27 Q22 25 24 22 Z" fill="{c}"/>
              <path d="M58 16 q6 20 0 46 q-1 5 2 6 q3 -1 2 -6 q6 -26 0 -46 z" fill="{c}"/>
              <ellipse cx="60" cy="66" rx="4.5" ry="2.5" fill="{c}"/>""",
 '紫': """<path d="M24 20 Q34 10 48 12 Q58 8 64 15 Q54 22 46 20 Q36 24 26 22 Q22 20 24 20 Z" fill="{c}"/>
              <path d="M24 20 q-2 32 3 54 q1 5 -2.5 6 q-3.5 -1 -2.5 -6 q-5 -22 -1 -54 z" fill="{c}"/>
              <path d="M40 21 q0 14 1 19 q0.5 3 -1 4 q-2 -1 -1 -4 q-1 -5 0 -19 z" fill="{c}"/>
              <ellipse cx="25" cy="78" rx="4" ry="2.5" fill="{c}"/>""",
}

RAINBOW = [
    ('#e23b3b', '赤 · 焰红'), ('#f28c1e', '橙 · 蜜橙'), ('#f2c11e', '黄 · 明黄'),
    ('#3fa63f', '绿 · 草绿'), ('#1fb6a8', '青 · 青碧'), ('#2b6fd6', '蓝 · 晴蓝'), ('#8a4fd6', '紫 · 黛紫'),
]
DRUM_BTN = ('          <button class="bucket" style="--bc:{c}" data-name="{name}" @click="pickSplat(\'{c}\')">\n'
            + DRUM_BASE +
            '\n          </button>')
drums = '\n'.join(DRUM_BTN.format(c=c, name=n, overflow=OVERFLOWS[n.split('·')[0].strip()].format(c=c)) for c, n in RAINBOW)

new_band = (
    '        <!-- 涂料桶排：200kg 开口桶，点击换全屏泼溅色 + 右侧泼漆区 -->\n'
    '        <div class="bucket-wrap">\n'
    '          <div class="bucket-row" ref="swatchRef">\n' + drums + '\n          </div>\n'
    '          <!-- 桶右侧泼漆区 -->\n'
    '          <div class="splash-area" aria-hidden="true">\n'
    '            <span class="area-hint">点桶泼漆</span>\n'
    '            <span v-for="sp in areaSplats" :key="sp.id" class="area-splat"\n'
    '                  :style="{ left: sp.left + \'%\', top: sp.top + \'%\', width: sp.size + \'px\', background: sp.c, transform: `rotate(${sp.rot}deg)`, borderRadius: sp.shape }"></span>\n'
    '          </div>\n'
    '        </div>\n'
    '        <div class="swatch-caption">芃远漆色 · 赤橙黄绿青蓝紫</div>')

old_start = s.index('        <!-- 涂料桶排：七色外溢漆桶，点击换全屏泼溅色 -->')
old_end = s.index('        <div class="swatch-caption">芃远漆色 · 赤橙黄绿青蓝紫</div>')
s = s[:old_start] + new_band + s[old_end:]
print('1) drums ok')

# ============ 2) JS：泼漆区状态 ============
old_js = """// 全屏泼溅涂料色（点击漆桶切换，默认赤）
const splat = ref('#e23b3b')
function pickSplat(c) {
  splat.value = c
}"""
new_js = """// 全屏泼溅涂料色（点击漆桶切换，默认赤）
const splat = ref('#e23b3b')
// 桶右侧泼漆区：点击一次泼一滩（形状/大小/位置随机，最多留 10 滩）
const AREA_SHAPES = [
  '58% 42% 55% 45% / 48% 55% 45% 52%',
  '45% 55% 48% 52% / 55% 45% 58% 42%',
  '60% 40% 52% 48% / 45% 60% 40% 55%',
  '50% 50% 62% 38% / 55% 45% 50% 50%'
]
const areaSplats = ref([])
let areaId = 0
function pickSplat(c) {
  splat.value = c
  areaSplats.value.push({
    id: ++areaId,
    left: 6 + Math.random() * 66,
    top: 14 + Math.random() * 52,
    size: 16 + Math.random() * 22,
    rot: -35 + Math.random() * 70,
    c,
    shape: AREA_SHAPES[Math.floor(Math.random() * AREA_SHAPES.length)]
  })
  if (areaSplats.value.length > 10) areaSplats.value.shift()
}"""
assert old_js in s
s = s.replace(old_js, new_js)
print('2) js ok')

# ============ 3) CSS：桶尺寸 + 泼漆区 ============
old_css = """.bucket-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-top: 30px;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.bucket {
  position: relative;
  width: 54px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  filter: drop-shadow(0 10px 14px rgba(0,0,0,0.35));
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.2s;
}"""
new_css = """.bucket-wrap {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  margin-top: 30px;
}
.bucket-row {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.bucket {
  position: relative;
  width: clamp(32px, 7.5cqw, 56px);
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  filter: drop-shadow(0 10px 14px rgba(0,0,0,0.35));
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.2s;
}

/* 桶右侧泼漆区 */
.splash-area {
  position: relative;
  width: 126px;
  height: 96px;
  flex-shrink: 0;
  border: 1.5px dashed rgba(255,255,255,0.22);
  border-radius: 12px;
  background: rgba(255,255,255,0.045);
  overflow: hidden;
}
.area-hint {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  font-size: 10px;
  letter-spacing: 3px;
  color: rgba(255,255,255,0.35);
  pointer-events: none;
  white-space: nowrap;
}
.area-splat {
  position: absolute;
  aspect-ratio: 1;
  box-shadow:
    inset -3px -4px 8px rgba(0,0,0,0.3),
    inset 3px 3px 5px rgba(255,255,255,0.25);
  animation: splatPop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes splatPop {
  from { opacity: 0; transform: scale(0.2); }
  to { opacity: 1; transform: scale(1); }
}"""
assert old_css in s
s = s.replace(old_css, new_css)
print('3) css ok')

open(p, 'w', encoding='utf-8').write(s)
print('done, len:', len(s))
