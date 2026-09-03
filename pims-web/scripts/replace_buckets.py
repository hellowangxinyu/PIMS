# -*- coding: utf-8 -*-
import re

p = 'D:/开发/PIMS/pims-web/src/views/Login.vue'
s = open(p, encoding='utf-8').read()

# ============ 1) 模板：漆滴舞台 → 移除 ============
old_drip = """        <!-- 漆滴坠落舞台：周期滴落 + 溅落 -->
        <div class="drip-stage" :style="dripColor.light ? { '--drip-light': dripColor.light, '--drip-dark': dripColor.dark } : {}" aria-hidden="true">
          <span class="drip d1"></span>
          <span class="drip d2"></span>
          <span class="splash"></span>
          <span class="pool"></span>
          <span class="sdrop s1"></span>
          <span class="sdrop s2"></span>
          <span class="sdrop s3"></span>
          <span class="sdrop s4"></span>
          <span class="sdrop s5"></span>
        </div>

"""
assert old_drip in s
s = s.replace(old_drip, "")

# ============ 2) 模板：色卡条 → 七色漆桶 ============
BUCKET = """          <button class="bucket" style="--bc:{c}" data-name="{name}" @click="pickSplat('{c}')">
            <svg viewBox="0 0 100 118" aria-hidden="true">
              <path d="M22 34 Q50 8 78 34" fill="none" stroke="#9aa4b2" stroke-width="4" stroke-linecap="round"/>
              <path d="M17 38 L23 104 Q24 110 30 110 L70 110 Q76 110 77 104 L83 38 Z" fill="#cdd5df" stroke="#98a3b1" stroke-width="1.5"/>
              <path d="M20 44 L80 44" stroke="#eef2f6" stroke-width="3" stroke-linecap="round"/>
              <path d="M23 100 L77 100" stroke="#7c8694" stroke-width="3" stroke-linecap="round"/>
              <path d="M26 36 Q36 22 50 26 Q66 20 74 34 Q82 40 76 50 Q64 44 56 54 Q46 44 32 50 Q22 44 26 36 Z" fill="var(--bc)"/>
              <path d="M58 48 q5 16 0 26 q-2 4 2 6 q4 -2 2 -6 q-5 -10 0 -26 z" fill="var(--bc)"/>
              <ellipse cx="60" cy="84" rx="5" ry="3" fill="var(--bc)" opacity="0.9"/>
              <ellipse cx="50" cy="38" rx="33" ry="8" fill="none" stroke="#aeb9c5" stroke-width="3"/>
            </svg>
          </button>"""
RAINBOW = [
    ('#e23b3b', '赤 · 焰红'), ('#f28c1e', '橙 · 蜜橙'), ('#f2c11e', '黄 · 明黄'),
    ('#3fa63f', '绿 · 草绿'), ('#1fb6a8', '青 · 青碧'), ('#2b6fd6', '蓝 · 晴蓝'), ('#8a4fd6', '紫 · 黛紫'),
]
buckets_html = '\n'.join(BUCKET.format(c=c, name=n) for c, n in RAINBOW)

old_sw_start = s.index('        <!-- 漆色卡条：浮雕 + 悬停显色名 -->')
old_sw_end = s.index('      </div>', old_sw_start)  # 色卡条外层 div 结束
old_sw_end = s.index('\n', s.index('PENGYUAN COLOUR INDEX', old_sw_start)) + 1  # 到 caption 行尾
new_sw = (
    '        <!-- 涂料桶排：七色外溢漆桶，点击换全屏泼溅色 -->\n'
    '        <div class="bucket-row" ref="swatchRef">\n'
    + buckets_html +
    '\n        </div>\n'
    '        <div class="swatch-caption">芃远漆色 · 赤橙黄绿青蓝紫</div>\n'
)
s = s[:old_sw_start] + new_sw + s[old_sw_end:]

# ============ 3) 模板：根节点 --splat + 泼溅层 ============
old_root = '<div class="login-page" :class="{ \'has-mouse\': mouseIn }" @mousemove="onMove" @mouseenter="onMouseIn" @mouseleave="onLeave">'
splats = [
    ('4%', '6%', '46px', '20deg', ''), ('15%', '17%', '26px', '-30deg', ''), ('27%', '7%', '18px', '60deg', ''),
    ('37%', '24%', '32px', '-10deg', ' drip'), ('7%', '36%', '22px', '90deg', ''), ('21%', '51%', '40px', '12deg', ' drip'),
    ('33%', '62%', '20px', '-45deg', ''), ('11%', '70%', '30px', '35deg', ''), ('29%', '83%', '24px', '-15deg', ''),
    ('40%', '90%', '16px', '75deg', ''), ('52%', '5%', '26px', '25deg', ' right'), ('78%', '10%', '16px', '-40deg', ' right'),
    ('60%', '88%', '30px', '10deg', ' right drip'), ('86%', '78%', '18px', '60deg', ' right'),
    ('92%', '38%', '22px', '-20deg', ' right'), ('68%', '48%', '14px', '90deg', ' right'),
]
splat_html = ''.join(
    '      <span class="splat%s" style="left:%s; top:%s; width:%s; transform:rotate(%s)"></span>\n' % (cls, l, t, w, r)
    for l, t, w, r, cls in splats)
new_root = (
    '<div class="login-page" :class="{ \'has-mouse\': mouseIn }" :style="{ \'--splat\': splat }" '
    '@mousemove="onMove" @mouseenter="onMouseIn" @mouseleave="onLeave">\n'
    '    <!-- 全屏泼溅涂料层（颜色随点击的漆桶） -->\n'
    '    <div class="splatter-layer" aria-hidden="true">\n' + splat_html + '    </div>'
)
assert old_root in s
s = s.replace(old_root, new_root)
print('1-3) template ok')

# ============ 4) JS：泼溅色状态 ============
start = s.index('const DRIP_COLORS = {')
end = s.index('function clampNum(v, max) {')
new_js = """// 全屏泼溅涂料色（点击漆桶切换，默认赤）
const splat = ref('#e23b3b')
function pickSplat(c) {
  splat.value = c
}

"""
s = s[:start] + new_js + s[end:]
print('4) js ok')

# ============ 5) CSS ============
old_fade = """.drip-stage { animation: fadeIn 0.6s 0.24s ease both; }
.swatch-strip { animation: fadeIn 0.6s 0.34s ease both; }
.swatch-caption { animation: fadeIn 0.6s 0.44s ease both; }"""
new_fade = """.bucket-row { animation: fadeIn 0.6s 0.24s ease both; }
.swatch-caption { animation: fadeIn 0.6s 0.34s ease both; }"""
assert old_fade in s
s = s.replace(old_fade, new_fade)

start = s.index('/* ========== 漆滴坠落舞台（增强：双滴错落 + 漆洼扩散 + 五珠飞溅） ========== */')
end = s.index('/* ========== 漆色卡条（浮雕） ========== */')
s = s[:start] + s[end:]

start = s.index('/* ========== 漆色卡条（浮雕） ========== */')
end = s.index('/* 漆滴边缘 */')
bucket_css = """/* ========== 涂料桶排（七色外溢漆桶） ========== */
.bucket-row {
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
}
.bucket svg { width: 100%; display: block; }
.bucket:hover {
  transform: translateY(-7px);
  filter: drop-shadow(0 16px 22px rgba(0,0,0,0.45));
}
.bucket::after {
  content: attr(data-name);
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  background: rgba(15,23,42,0.88);
  color: #fff;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: all 0.18s ease;
  z-index: 5;
}
.bucket:hover::after {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

/* ========== 全屏泼溅涂料层 ========== */
.splatter-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.splat {
  position: absolute;
  aspect-ratio: 1;
  background: var(--splat);
  border-radius: 58% 42% 55% 45% / 48% 55% 45% 52%;
  opacity: 0.8;
  box-shadow:
    inset -3px -4px 8px rgba(0,0,0,0.28),
    inset 3px 3px 5px rgba(255,255,255,0.22);
  transition: background-color 0.35s ease;
}
.splat::before {
  content: '';
  position: absolute;
  width: 28%; height: 28%;
  border-radius: 50%;
  background: var(--splat);
  right: -16%; top: 14%;
  transition: background-color 0.35s ease;
}
.splat::after {
  content: '';
  position: absolute;
  width: 17%; height: 17%;
  border-radius: 50%;
  background: var(--splat);
  left: -11%; bottom: 4%;
  transition: background-color 0.35s ease;
}
/* 拉长流淌状泼溅 */
.splat.drip {
  border-radius: 48% 52% 45% 55% / 62% 62% 38% 38%;
}
/* 右半屏（浅色背景）减淡 */
.splat.right { opacity: 0.35; }

/* 漆滴边缘 */
"""
s = s[:start] + bucket_css + s[end:]

open(p, 'w', encoding='utf-8').write(s)
print('5) css ok, len:', len(s))
