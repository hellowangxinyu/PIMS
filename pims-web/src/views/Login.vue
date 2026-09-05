<template>
  <div class="login-page" :class="{ 'has-mouse': mouseIn }" :style="{ '--splat': splat }" @mousemove="onMove" @mouseleave="onLeave">
    <!-- 全屏泼溅涂料层（颜色随点击的漆桶） -->
    <div class="splatter-layer" aria-hidden="true">
      <span class="splat" style="left:4%; top:6%; width:46px; transform:rotate(20deg)"></span>
      <span class="splat" style="left:15%; top:17%; width:26px; transform:rotate(-30deg)"></span>
      <span class="splat" style="left:27%; top:7%; width:18px; transform:rotate(60deg)"></span>
      <span class="splat drip" style="left:37%; top:24%; width:32px; transform:rotate(-10deg)"></span>
      <span class="splat" style="left:7%; top:36%; width:22px; transform:rotate(90deg)"></span>
      <span class="splat drip" style="left:21%; top:51%; width:40px; transform:rotate(12deg)"></span>
      <span class="splat" style="left:33%; top:62%; width:20px; transform:rotate(-45deg)"></span>
      <span class="splat" style="left:11%; top:70%; width:30px; transform:rotate(35deg)"></span>
      <span class="splat" style="left:29%; top:83%; width:24px; transform:rotate(-15deg)"></span>
      <span class="splat" style="left:40%; top:90%; width:16px; transform:rotate(75deg)"></span>
      <span class="splat right" style="left:52%; top:5%; width:26px; transform:rotate(25deg)"></span>
      <span class="splat right" style="left:78%; top:10%; width:16px; transform:rotate(-40deg)"></span>
      <span class="splat right drip" style="left:60%; top:88%; width:30px; transform:rotate(10deg)"></span>
      <span class="splat right" style="left:86%; top:78%; width:18px; transform:rotate(60deg)"></span>
      <span class="splat right" style="left:92%; top:38%; width:22px; transform:rotate(-20deg)"></span>
      <span class="splat right" style="left:68%; top:48%; width:14px; transform:rotate(90deg)"></span>
    </div>
    <!-- 左侧品牌面板（涂料主题） -->
    <aside class="brand-panel">
      <div class="paint-blobs" ref="blobsRef">
        <div class="blob b1"></div>
        <div class="blob b2"></div>
        <div class="blob b3"></div>
      </div>



      <div class="brand-content">
        <header class="brand-head">
          <div class="brand-icon">
            <svg viewBox="0 0 64 64" width="30" height="30" aria-hidden="true">
              <path d="M32 11c6.5 9 14.5 16.4 14.5 25a14.5 14.5 0 1 1-29 0c0-8.6 8-16 14.5-25z" fill="#fff"/>
              <circle cx="26.5" cy="38" r="4.5" fill="#7da59d" opacity="0.55"/>
            </svg>
          </div>
          <div>
            <div class="brand-name">PIMS</div>
            <div class="brand-en">PENGYUAN IMS</div>
          </div>
        </header>

        <div class="brand-hero">
          <h1>王新宇的芃远综合管理系统</h1>
          <p>涂料 · 新材料 —— 采购、生产、库存、销售、财务，一体化管理</p>
        </div>

        <!-- 涂料桶排：200kg 开口桶，点击换全屏泼溅色 + 右侧泼漆区 -->
        <div class="bucket-wrap">
          <div class="bucket-row" ref="swatchRef">
          <button class="bucket" style="--bc:#e23b3b" data-name="赤 · 焰红" @click="pickSplat('#e23b3b')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#e23b3b"/>
              <path d="M28 18 Q36 6 50 12 Q60 6 68 16 Q58 24 50 21 Q40 25 30 23 Q24 20 28 18 Z" fill="#e23b3b"/>
              <path d="M32 20 q-2 26 2 38 q1 5 -2 6 q-3 -1 -2 -6 q-4 -12 -1 -38 z" fill="#e23b3b"/>
              <ellipse cx="31" cy="66" rx="4" ry="2.5" fill="#e23b3b"/>
            </svg>
          </button>
          <button class="bucket" style="--bc:#f28c1e" data-name="橙 · 蜜橙" @click="pickSplat('#f28c1e')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#f28c1e"/>
              <path d="M40 20 Q48 8 60 12 Q70 7 78 16 Q70 24 62 21 Q52 25 42 22 Q36 20 40 20 Z" fill="#f28c1e"/>
              <path d="M50 19 q0 14 1 19 q0.5 3 -1 4 q-2 -1 -1 -4 q-1 -5 0 -19 z" fill="#f28c1e"/>
              <ellipse cx="50" cy="40" rx="3" ry="2" fill="#f28c1e"/>
              <path d="M66 18 q0 12 1 16 q0.5 3 -1 4 q-2 -1 -1 -4 q-1 -4 0 -16 z" fill="#f28c1e"/>
              <ellipse cx="66" cy="36" rx="3" ry="2" fill="#f28c1e"/>
            </svg>
          </button>
          <button class="bucket" style="--bc:#f2c11e" data-name="黄 · 明黄" @click="pickSplat('#f2c11e')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#f2c11e"/>
              <path d="M26 20 Q40 10 58 12 Q72 8 84 18 Q74 26 62 23 Q48 27 34 24 Q26 22 26 20 Z" fill="#f2c11e"/>
              <path d="M70 17 q3 28 -2 52 q-1 5 2 6 q3 -1 2 -6 q5 -24 -2 -52 z" fill="#f2c11e"/>
              <ellipse cx="70" cy="72" rx="4.5" ry="2.5" fill="#f2c11e"/>
            </svg>
          </button>
          <button class="bucket" style="--bc:#3fa63f" data-name="绿 · 草绿" @click="pickSplat('#3fa63f')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#3fa63f"/>
              <path d="M52 18 Q58 10 66 14 Q72 10 76 16 Q70 22 64 20 Q58 23 52 18 Z" fill="#3fa63f"/>
              <path d="M56 16 q0 8 1 11 q0.5 2.5 -1 3.5 q-2 -1 -1 -3.5 q-1 -3 0 -11 z" fill="#3fa63f"/>
              <path d="M64 15 q0 7 1 10 q0.5 2 -1 3 q-2 -1 -1 -3 q-1 -3 0 -10 z" fill="#3fa63f"/>
              <path d="M70 15 q0 9 1 12 q0.5 2.5 -1 3.5 q-2 -1 -1 -3.5 q-1 -3 0 -12 z" fill="#3fa63f"/>
            </svg>
          </button>
          <button class="bucket" style="--bc:#1fb6a8" data-name="青 · 青碧" @click="pickSplat('#1fb6a8')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#1fb6a8"/>
              <path d="M34 18 Q44 8 58 12 Q66 7 72 15 Q64 22 56 20 Q46 24 36 21 Q30 20 34 18 Z" fill="#1fb6a8"/>
              <path d="M56 18 q0 18 1 29 q0.5 4 -1.5 5 q-3 -1 -1.5 -5 q-1 -11 0 -29 z" fill="#1fb6a8"/>
              <ellipse cx="56" cy="50" rx="4" ry="2.5" fill="#1fb6a8"/>
            </svg>
          </button>
          <button class="bucket" style="--bc:#2b6fd6" data-name="蓝 · 晴蓝" @click="pickSplat('#2b6fd6')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#2b6fd6"/>
              <path d="M24 22 Q36 12 52 14 Q66 9 80 18 Q70 28 58 25 Q44 30 30 27 Q22 25 24 22 Z" fill="#2b6fd6"/>
              <path d="M58 16 q6 20 0 46 q-1 5 2 6 q3 -1 2 -6 q6 -26 0 -46 z" fill="#2b6fd6"/>
              <ellipse cx="60" cy="66" rx="4.5" ry="2.5" fill="#2b6fd6"/>
            </svg>
          </button>
          <button class="bucket" style="--bc:#8a4fd6" data-name="紫 · 黛紫" @click="pickSplat('#8a4fd6')">
<svg viewBox="0 0 120 150" aria-hidden="true">
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
              <ellipse cx="60" cy="20" rx="34" ry="9" fill="#8a4fd6"/>
              <path d="M24 20 Q34 10 48 12 Q58 8 64 15 Q54 22 46 20 Q36 24 26 22 Q22 20 24 20 Z" fill="#8a4fd6"/>
              <path d="M24 20 q-2 32 3 54 q1 5 -2.5 6 q-3.5 -1 -2.5 -6 q-5 -22 -1 -54 z" fill="#8a4fd6"/>
              <path d="M40 21 q0 14 1 19 q0.5 3 -1 4 q-2 -1 -1 -4 q-1 -5 0 -19 z" fill="#8a4fd6"/>
              <ellipse cx="25" cy="78" rx="4" ry="2.5" fill="#8a4fd6"/>
            </svg>
          </button>
          </div>
          <div class="splash-row">
            <!-- 泼漆区：一团主泼溅 + 飞溅小滴，颜色随桶 -->
            <div class="splash-area" ref="splashRef" aria-hidden="true">
              <span v-for="sp in areaSplats" :key="sp.id" :class="sp.kind"
                    :style="{ left: sp.left + '%', top: sp.top + '%', width: sp.size + 'px', background: sp.c, '--rot': sp.rot + 'deg', borderRadius: sp.shape, animationDelay: sp.delay + 's' }"></span>
            </div>
            <!-- 泼溅右侧：四色调配配方（随点击桶切换） -->
            <div class="recipe-panel" v-if="recipe">
              <div class="recipe-title">{{ recipe.name }} · 四色配方</div>
              <div class="recipe-bar">
                <div v-for="it in recipe.items" :key="it.n" class="recipe-seg"
                     :style="{ background: it.c, flex: it.p }"></div>
              </div>
              <div class="recipe-labels">
                <span v-for="it in recipe.items" :key="it.n" :style="{ flex: it.p }" :title="it.n">{{ it.n }} {{ it.p }}%</span>
              </div>
            </div>
          </div>
        </div>
        <div class="swatch-caption">芃远漆色 · 赤橙黄绿青蓝紫</div>
      </div>

      <!-- 漆滴边缘 -->
      <div class="paint-drip" aria-hidden="true">
        <span></span><span></span><span></span><span></span>
        <span></span><span></span><span></span><span></span>
      </div>

      <!-- 七只毛绒公仔（主色系/大小/动作各不相同） -->
      <div class="plush-row" aria-hidden="true">
<span class="toywrap bear" :class="{ flash: toys.bear.flash, celebrate: show.celebrating.bear }"
      :style="{ transform: `translateY(${toys.bear.ty}px) rotate(${toys.bear.wob + toys.bear.lean}deg) scale(${2 - toys.bear.s}, ${toys.bear.s})` }"
      @click="clickToy('bear')" @mousedown="pressToy('bear')" @mouseup="releaseToy('bear')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <ellipse class="bf" cx="75" cy="131" rx="37" ry="23" fill="#c9935e" stroke="#a97c4a" stroke-width="2.5"/>
            <circle cx="51" cy="145" r="9" fill="#c9935e"/><circle cx="99" cy="145" r="9" fill="#c9935e"/>
            <circle cx="51" cy="145" r="4.5" fill="#e8c49a"/><circle cx="99" cy="145" r="4.5" fill="#e8c49a"/>
            <g class="anim-wave">
              <ellipse cx="118" cy="98" rx="9" ry="20" transform="rotate(35 118 98)" fill="#c9935e"/>
              <circle cx="127" cy="78" r="8" fill="#c9935e"/><circle cx="127" cy="78" r="4" fill="#e8c49a"/>
            </g>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.bear.rot + toys.bear.baseRot}deg)` }">
            <circle cx="75.0" cy="37.0" r="7" fill="#a97c4a" opacity="0.9"/><circle cx="47.4" cy="43.2" r="7" fill="#a97c4a" opacity="0.9"/><circle cx="102.6" cy="43.2" r="7" fill="#a97c4a" opacity="0.9"/><circle cx="31.3" cy="66.2" r="6" fill="#a97c4a" opacity="0.9"/><circle cx="118.7" cy="66.2" r="6" fill="#a97c4a" opacity="0.9"/><circle cx="29.0" cy="86.9" r="5" fill="#a97c4a" opacity="0.9"/><circle cx="121.0" cy="86.9" r="5" fill="#a97c4a" opacity="0.9"/>
            <circle class="ef" cx="38" cy="44" r="15" fill="#c9935e"/>
            <circle cx="38" cy="44" r="7" fill="#e8c49a"/>
            <circle class="ef" cx="112" cy="44" r="15" fill="#c9935e"/>
            <circle cx="112" cy="44" r="7" fill="#e8c49a"/>
            <circle class="hf" cx="75" cy="80" r="46" fill="#c9935e" stroke="#a97c4a" stroke-width="2.5"/>
            <ellipse cx="75" cy="95" rx="20" ry="15" fill="#f3dcc0"/>
            <ellipse cx="75" cy="88" rx="7" ry="5" fill="#6b4a2f"/>
            <path d="M75 93 q0 8 -7 8 M75 93 q0 8 7 8" fill="none" stroke="#6b4a2f" stroke-width="2" stroke-linecap="round"/>
            <ellipse cx="46" cy="88" rx="7" ry="4.5" fill="#f5a8a8" opacity="0.7"/>
            <ellipse cx="104" cy="88" rx="7" ry="4.5" fill="#f5a8a8" opacity="0.7"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="58" cy="72" rx="8" ry="8" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.bear.pxL}px, ${toys.bear.py}px)` }" cx="58" cy="72" r="4" fill="#2f1f12"/>
              <circle cx="57.0" cy="71.0" r="1.5" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="92" cy="72" rx="8" ry="8" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.bear.pxR}px, ${toys.bear.py}px)` }" cx="92" cy="72" r="4" fill="#2f1f12"/>
              <circle cx="91.0" cy="71.0" r="1.5" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.bear" class="bubble" :class="show.bubbles.bear.kind">{{ show.bubbles.bear.text }}</div>
</span>
<span class="toywrap bunny" :class="{ flash: toys.bunny.flash, celebrate: show.celebrating.bunny }"
      :style="{ transform: `translateY(${toys.bunny.ty}px) rotate(${toys.bunny.wob + toys.bunny.lean}deg) scale(${2 - toys.bunny.s}, ${toys.bunny.s})` }"
      @click="clickToy('bunny')" @mousedown="pressToy('bunny')" @mouseup="releaseToy('bunny')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <ellipse class="bf" cx="75" cy="131" rx="35" ry="23" fill="#f6ede2" stroke="#e2d3bd" stroke-width="2.5"/>
            <ellipse cx="75" cy="135" rx="21" ry="13" fill="#fff"/>
            <circle cx="55" cy="145" r="8" fill="#f6ede2"/><circle cx="95" cy="145" r="8" fill="#f6ede2"/>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.bunny.rot + toys.bunny.baseRot}deg)` }">
            <circle cx="75.0" cy="51.0" r="7" fill="#e2d3bd" opacity="0.9"/><circle cx="51.0" cy="56.0" r="7" fill="#e2d3bd" opacity="0.9"/><circle cx="99.0" cy="56.0" r="7" fill="#e2d3bd" opacity="0.9"/><circle cx="37.0" cy="76.0" r="6" fill="#e2d3bd" opacity="0.9"/><circle cx="113.0" cy="76.0" r="6" fill="#e2d3bd" opacity="0.9"/><circle cx="35.0" cy="94.0" r="5" fill="#e2d3bd" opacity="0.9"/><circle cx="115.0" cy="94.0" r="5" fill="#e2d3bd" opacity="0.9"/>
            <ellipse class="ef" cx="48" cy="26" rx="11" ry="30" transform="rotate(-18 48 26)" fill="#f6ede2"/>
            <ellipse cx="48" cy="28" rx="6" ry="20" transform="rotate(-18 48 28)" fill="#f3c3cf"/>
            <g class="anim-twitch">
              <ellipse class="ef" cx="102" cy="26" rx="11" ry="30" transform="rotate(42 102 30)" fill="#f6ede2"/>
              <ellipse cx="102" cy="28" rx="6" ry="20" transform="rotate(42 102 32)" fill="#f3c3cf"/>
            </g>
            <circle class="hf" cx="75" cy="88" r="40" fill="#f6ede2" stroke="#e2d3bd" stroke-width="2.5"/>
            <ellipse cx="75" cy="102" rx="13" ry="10" fill="#fff"/>
            <ellipse cx="75" cy="98" rx="5" ry="4" fill="#e58ba2"/>
            <path d="M75 101 q-3 5 -7 2 M75 101 q3 5 7 2" fill="none" stroke="#3a2c22" stroke-width="2" stroke-linecap="round"/>
            <ellipse cx="52" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.7"/>
            <ellipse cx="98" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.7"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="60" cy="84" rx="8" ry="8" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.bunny.pxL}px, ${toys.bunny.py}px)` }" cx="60" cy="84" r="4" fill="#3a2c22"/>
              <circle cx="59.0" cy="83.0" r="1.5" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="90" cy="84" rx="8" ry="8" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.bunny.pxR}px, ${toys.bunny.py}px)` }" cx="90" cy="84" r="4" fill="#3a2c22"/>
              <circle cx="89.0" cy="83.0" r="1.5" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.bunny" class="bubble" :class="show.bubbles.bunny.kind">{{ show.bubbles.bunny.text }}</div>
</span>
<span class="toywrap panda" :class="{ flash: toys.panda.flash, celebrate: show.celebrating.panda }"
      :style="{ transform: `translateY(${toys.panda.ty}px) rotate(${toys.panda.wob + toys.panda.lean}deg) scale(${2 - toys.panda.s}, ${toys.panda.s})` }"
      @click="clickToy('panda')" @mousedown="pressToy('panda')" @mouseup="releaseToy('panda')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <ellipse class="bf" cx="75" cy="131" rx="36" ry="24" fill="#fff" stroke="#e6e6e6" stroke-width="2.5"/>
            <circle cx="36" cy="116" r="12" fill="#2e2a33"/><circle cx="114" cy="116" r="12" fill="#2e2a33"/>
            <circle cx="44" cy="131" r="11" fill="#2e2a33"/><circle cx="106" cy="131" r="11" fill="#2e2a33"/>
            <ellipse cx="58" cy="140" rx="13" ry="8" fill="#2e2a33"/><ellipse cx="92" cy="140" rx="13" ry="8" fill="#2e2a33"/>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.panda.rot + toys.panda.baseRot}deg)` }">
            <circle cx="75.0" cy="49.0" r="7" fill="#e6e6e6" opacity="0.9"/><circle cx="49.8" cy="54.4" r="7" fill="#e6e6e6" opacity="0.9"/><circle cx="100.2" cy="54.4" r="7" fill="#e6e6e6" opacity="0.9"/><circle cx="35.1" cy="75.4" r="6" fill="#e6e6e6" opacity="0.9"/><circle cx="114.9" cy="75.4" r="6" fill="#e6e6e6" opacity="0.9"/><circle cx="33.0" cy="94.3" r="5" fill="#e6e6e6" opacity="0.9"/><circle cx="117.0" cy="94.3" r="5" fill="#e6e6e6" opacity="0.9"/>
            <circle class="ef" cx="40" cy="52" r="13" fill="#2e2a33"/>
            <circle class="ef" cx="110" cy="52" r="13" fill="#2e2a33"/>
            <circle class="hf" cx="75" cy="88" r="42" fill="#fff" stroke="#e6e6e6" stroke-width="2.5"/>
            <ellipse cx="60" cy="84" rx="11" ry="13" transform="rotate(-15 60 84)" fill="#2e2a33"/>
            <ellipse cx="90" cy="84" rx="11" ry="13" transform="rotate(15 90 84)" fill="#2e2a33"/>
            <ellipse cx="75" cy="104" rx="14" ry="11" fill="#f0e9de"/>
            <ellipse cx="75" cy="100" rx="5.5" ry="4" fill="#2e2a33"/>
            <path d="M75 104 q0 7 -7 7 M75 104 q0 7 7 7" fill="none" stroke="#2e2a33" stroke-width="2" stroke-linecap="round"/>
            <ellipse cx="50" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.65"/>
            <ellipse cx="100" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.65"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="60" cy="84" rx="5.5" ry="5.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.panda.pxL}px, ${toys.panda.py}px)` }" cx="60" cy="84" r="2.8" fill="#1f1b23"/>
              <circle cx="59.3" cy="83.3" r="1.1" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="90" cy="84" rx="5.5" ry="5.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.panda.pxR}px, ${toys.panda.py}px)` }" cx="90" cy="84" r="2.8" fill="#1f1b23"/>
              <circle cx="89.3" cy="83.3" r="1.1" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.panda" class="bubble" :class="show.bubbles.panda.kind">{{ show.bubbles.panda.text }}</div>
</span>
<span class="toywrap fox" :class="{ flash: toys.fox.flash, celebrate: show.celebrating.fox }"
      :style="{ transform: `translateY(${toys.fox.ty}px) rotate(${toys.fox.wob + toys.fox.lean}deg) scale(${2 - toys.fox.s}, ${toys.fox.s})` }"
      @click="clickToy('fox')" @mousedown="pressToy('fox')" @mouseup="releaseToy('fox')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <g class="anim-wag">
            <circle cx="34" cy="118" r="13" fill="#e8834f"/><circle cx="23" cy="130" r="11" fill="#e8834f"/>
            <circle cx="27" cy="142" r="9" fill="#f8c896"/>
          </g>
            <ellipse class="bf" cx="75" cy="131" rx="35" ry="23" fill="#e8834f" stroke="#d56f3c" stroke-width="2.5"/>
            <ellipse cx="75" cy="137" rx="20" ry="13" fill="#f8c896"/>
            <circle cx="54" cy="145" r="8.5" fill="#e8834f"/><circle cx="96" cy="145" r="8.5" fill="#e8834f"/>
            <circle cx="54" cy="145" r="4" fill="#f8c896"/><circle cx="96" cy="145" r="4" fill="#f8c896"/>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.fox.rot + toys.fox.baseRot}deg)` }">
            <circle cx="75.0" cy="47.0" r="7" fill="#d56f3c" opacity="0.9"/><circle cx="51.0" cy="52.0" r="7" fill="#d56f3c" opacity="0.9"/><circle cx="99.0" cy="52.0" r="7" fill="#d56f3c" opacity="0.9"/><circle cx="37.0" cy="72.0" r="6" fill="#d56f3c" opacity="0.9"/><circle cx="113.0" cy="72.0" r="6" fill="#d56f3c" opacity="0.9"/><circle cx="35.0" cy="90.0" r="5" fill="#d56f3c" opacity="0.9"/><circle cx="115.0" cy="90.0" r="5" fill="#d56f3c" opacity="0.9"/>
            <path class="ef" d="M40 52 L52 20 L66 44 Z" fill="#e8834f"/>
            <path d="M47 47 L53 28 L61 43 Z" fill="#f8c896"/>
            <path class="ef" d="M110 52 L98 20 L84 44 Z" fill="#e8834f"/>
            <path d="M103 47 L97 28 L89 43 Z" fill="#f8c896"/>
            <circle class="hf" cx="75" cy="84" r="40" fill="#e8834f" stroke="#d56f3c" stroke-width="2.5"/>
            <ellipse cx="75" cy="102" rx="20" ry="14" fill="#f8c896"/>
            <ellipse cx="75" cy="94" rx="6.5" ry="4.5" fill="#5c3a2a"/>
            <path d="M75 98 q0 7 -6 7 M75 98 q0 7 6 7" fill="none" stroke="#5c3a2a" stroke-width="2" stroke-linecap="round"/>
            <ellipse cx="48" cy="96" rx="6" ry="4" fill="#f9a8d4" opacity="0.6"/>
            <ellipse cx="102" cy="96" rx="6" ry="4" fill="#f9a8d4" opacity="0.6"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="60" cy="80" rx="7.5" ry="7.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.fox.pxL}px, ${toys.fox.py}px)` }" cx="60" cy="80" r="3.8" fill="#4a2f1d"/>
              <circle cx="59.0" cy="79.0" r="1.4" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="90" cy="80" rx="7.5" ry="7.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.fox.pxR}px, ${toys.fox.py}px)` }" cx="90" cy="80" r="3.8" fill="#4a2f1d"/>
              <circle cx="89.0" cy="79.0" r="1.4" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.fox" class="bubble" :class="show.bubbles.fox.kind">{{ show.bubbles.fox.text }}</div>
</span>
<span class="toywrap frog" :class="{ flash: toys.frog.flash, celebrate: show.celebrating.frog }"
      :style="{ transform: `translateY(${toys.frog.ty}px) rotate(${toys.frog.wob + toys.frog.lean}deg) scale(${2 - toys.frog.s}, ${toys.frog.s})` }"
      @click="clickToy('frog')" @mousedown="pressToy('frog')" @mouseup="releaseToy('frog')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <ellipse class="bf" cx="75" cy="131" rx="36" ry="22" fill="#7cc576" stroke="#68b061" stroke-width="2.5"/>
            <ellipse cx="75" cy="136" rx="20" ry="12" fill="#a9dd9b"/>
            <ellipse cx="38" cy="143" rx="15" ry="8" transform="rotate(-24 38 143)" fill="#7cc576"/>
            <ellipse cx="112" cy="143" rx="15" ry="8" transform="rotate(24 112 143)" fill="#7cc576"/>
            <circle cx="52" cy="144" r="9" fill="#7cc576"/><circle cx="98" cy="144" r="9" fill="#7cc576"/>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.frog.rot + toys.frog.baseRot}deg)` }">
            <circle cx="75.0" cy="55.0" r="7" fill="#68b061" opacity="0.9"/><circle cx="51.0" cy="60.0" r="7" fill="#68b061" opacity="0.9"/><circle cx="99.0" cy="60.0" r="7" fill="#68b061" opacity="0.9"/><circle cx="37.0" cy="80.0" r="6" fill="#68b061" opacity="0.9"/><circle cx="113.0" cy="80.0" r="6" fill="#68b061" opacity="0.9"/><circle cx="35.0" cy="98.0" r="5" fill="#68b061" opacity="0.9"/><circle cx="115.0" cy="98.0" r="5" fill="#68b061" opacity="0.9"/>
            <circle class="ef" cx="58" cy="56" r="11" fill="#7cc576"/>
            <circle class="ef" cx="92" cy="56" r="11" fill="#7cc576"/>
            <circle class="hf" cx="75" cy="92" r="40" fill="#7cc576" stroke="#68b061" stroke-width="2.5"/>
            <path d="M52 100 Q75 118 98 100" fill="none" stroke="#2f5d2b" stroke-width="2.5" stroke-linecap="round"/>
            <ellipse cx="48" cy="104" rx="6.5" ry="4.5" fill="#f9a8d4" opacity="0.55"/>
            <ellipse cx="102" cy="104" rx="6.5" ry="4.5" fill="#f9a8d4" opacity="0.55"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="58" cy="56" rx="7.5" ry="7.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.frog.pxL}px, ${toys.frog.py}px)` }" cx="58" cy="56" r="3.6" fill="#264b24"/>
              <circle cx="57.1" cy="55.1" r="1.4" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="92" cy="56" rx="7.5" ry="7.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.frog.pxR}px, ${toys.frog.py}px)` }" cx="92" cy="56" r="3.6" fill="#264b24"/>
              <circle cx="91.1" cy="55.1" r="1.4" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.frog" class="bubble" :class="show.bubbles.frog.kind">{{ show.bubbles.frog.text }}</div>
</span>
<span class="toywrap penguin" :class="{ flash: toys.penguin.flash, celebrate: show.celebrating.penguin }"
      :style="{ transform: `translateY(${toys.penguin.ty}px) rotate(${toys.penguin.wob + toys.penguin.lean}deg) scale(${2 - toys.penguin.s}, ${toys.penguin.s})` }"
      @click="clickToy('penguin')" @mousedown="pressToy('penguin')" @mouseup="releaseToy('penguin')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <ellipse class="bf" cx="75" cy="97" rx="34" ry="42" fill="#3b5b9f" stroke="#2c4575" stroke-width="2.5"/>
            <ellipse cx="75" cy="112" rx="22" ry="26" fill="#fff"/>
            <g class="anim-flap">
              <ellipse cx="38" cy="100" rx="9" ry="20" transform="rotate(25 38 100)" fill="#2c4575"/>
              <ellipse cx="112" cy="100" rx="9" ry="20" transform="rotate(-25 112 100)" fill="#2c4575"/>
            </g>
            <ellipse cx="58" cy="141" rx="10" ry="6" fill="#f59e0b"/><ellipse cx="92" cy="141" rx="10" ry="6" fill="#f59e0b"/>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.penguin.rot + toys.penguin.baseRot}deg)` }">
            <circle cx="75.0" cy="43.0" r="7" fill="#2c4575" opacity="0.9"/><circle cx="59.4" cy="45.2" r="7" fill="#2c4575" opacity="0.9"/><circle cx="90.6" cy="45.2" r="7" fill="#2c4575" opacity="0.9"/><circle cx="50.3" cy="58.2" r="6" fill="#2c4575" opacity="0.9"/><circle cx="99.7" cy="58.2" r="6" fill="#2c4575" opacity="0.9"/><circle cx="49.0" cy="69.9" r="5" fill="#2c4575" opacity="0.9"/><circle cx="101.0" cy="69.9" r="5" fill="#2c4575" opacity="0.9"/>
            <circle class="hf" cx="75" cy="66" r="26" fill="#3b5b9f" stroke="#2c4575" stroke-width="2.5"/>
            <ellipse cx="75" cy="72" rx="22" ry="16" fill="#fff"/>
            <path d="M69 76 L81 76 L75 87 Z" fill="#f59e0b"/>
            <circle cx="60" cy="52" r="6" fill="#4a6cb8"/><circle cx="75" cy="47" r="7" fill="#4a6cb8"/><circle cx="90" cy="52" r="6" fill="#4a6cb8"/>
            <ellipse cx="56" cy="80" rx="5" ry="3.5" fill="#f9a8d4" opacity="0.6"/>
            <ellipse cx="94" cy="80" rx="5" ry="3.5" fill="#f9a8d4" opacity="0.6"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="62" cy="68" rx="6.5" ry="6.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.penguin.pxL}px, ${toys.penguin.py}px)` }" cx="62" cy="68" r="3.5" fill="#1c2539"/>
              <circle cx="61.1" cy="67.1" r="1.3" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="88" cy="68" rx="6.5" ry="6.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.penguin.pxR}px, ${toys.penguin.py}px)` }" cx="88" cy="68" r="3.5" fill="#1c2539"/>
              <circle cx="87.1" cy="67.1" r="1.3" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.penguin" class="bubble" :class="show.bubbles.penguin.kind">{{ show.bubbles.penguin.text }}</div>
</span>
<span class="toywrap monster" :class="{ flash: toys.monster.flash, celebrate: show.celebrating.monster }"
      :style="{ transform: `translateY(${toys.monster.ty}px) rotate(${toys.monster.wob + toys.monster.lean}deg) scale(${2 - toys.monster.s}, ${toys.monster.s})` }"
      @click="clickToy('monster')" @mousedown="pressToy('monster')" @mouseup="releaseToy('monster')">
  <svg class="plush" viewBox="0 0 150 150">
          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>
          <g class="body anim-bob">
            <g class="anim-wob">
            <circle cx="52" cy="50" r="8" fill="#3a655e"/><circle cx="98" cy="50" r="8" fill="#3a655e"/>
            <circle class="bf" cx="75" cy="88" r="42" fill="#7da59d" stroke="#3a655e" stroke-width="2.5"/>
            <circle cx="55" cy="48" r="7" fill="#a3c2bc"/><circle cx="75" cy="44" r="8" fill="#a3c2bc"/><circle cx="95" cy="48" r="7" fill="#a3c2bc"/>
            <circle cx="40" cy="112" r="9" fill="#5f9187"/><circle cx="110" cy="112" r="9" fill="#5f9187"/>
            <circle cx="58" cy="140" r="8" fill="#5f9187"/><circle cx="92" cy="140" r="8" fill="#5f9187"/>
          </g>
          </g>
          <g class="head-idle anim-sway">
          <g class="head" :style="{ transform: `rotate(${toys.monster.rot + toys.monster.baseRot}deg)` }">
            <circle cx="75.0" cy="49.0" r="7" fill="#3a655e" opacity="0.9"/><circle cx="53.4" cy="53.2" r="7" fill="#3a655e" opacity="0.9"/><circle cx="96.6" cy="53.2" r="7" fill="#3a655e" opacity="0.9"/><circle cx="40.8" cy="71.2" r="6" fill="#3a655e" opacity="0.9"/><circle cx="109.2" cy="71.2" r="6" fill="#3a655e" opacity="0.9"/><circle cx="39.0" cy="87.4" r="5" fill="#3a655e" opacity="0.9"/><circle cx="111.0" cy="87.4" r="5" fill="#3a655e" opacity="0.9"/>
            <circle class="hf" cx="75" cy="82" r="36" fill="#7da59d" stroke="#3a655e" stroke-width="2.5"/>
            <path d="M62 103 Q75 116 88 103" fill="none" stroke="#5b21b6" stroke-width="3" stroke-linecap="round"/>
            <ellipse cx="48" cy="96" rx="6.5" ry="4.5" fill="#e9d5ff" opacity="0.75"/>
            <ellipse cx="102" cy="96" rx="6.5" ry="4.5" fill="#e9d5ff" opacity="0.75"/>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="60" cy="82" rx="8.5" ry="8.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.monster.pxL}px, ${toys.monster.py}px)` }" cx="60" cy="82" r="4.5" fill="#3b1d6e"/>
              <circle cx="58.9" cy="80.9" r="1.7" fill="#fff"/>
            </g>
            <g class="eye">
              <ellipse class="eye-white anim-blink" cx="90" cy="82" rx="8.5" ry="8.5" fill="#fff"/>
              <circle class="pupil" :style="{ transform: `translate(${toys.monster.pxR}px, ${toys.monster.py}px)` }" cx="90" cy="82" r="4.5" fill="#3b1d6e"/>
              <circle cx="88.9" cy="80.9" r="1.7" fill="#fff"/>
            </g>
          </g>
          </g>
        </svg>
  <div v-if="show.bubbles.monster" class="bubble" :class="show.bubbles.monster.kind">{{ show.bubbles.monster.text }}</div>
</span>
      </div>
    </aside>

    <!-- 右侧表单面板 -->
    <main class="form-panel">
      <!-- 幼儿园水彩画背景：元素分布在登录卡片四周（避开中央卡片区），像半透明白纸下的画 -->
      <svg class="watercolor-bg" viewBox="0 0 1000 640" preserveAspectRatio="none" aria-hidden="true">
        <!-- 太阳（左上） -->
        <circle cx="95" cy="100" r="34" fill="#fcd34d"/>
        <g stroke="#fcd34d" stroke-width="6" stroke-linecap="round">
          <line x1="95" y1="45" x2="95" y2="28"/>
          <line x1="95" y1="155" x2="95" y2="172"/>
          <line x1="40" y1="100" x2="23" y2="100"/>
          <line x1="150" y1="100" x2="167" y2="100"/>
          <line x1="56" y1="61" x2="44" y2="49"/>
          <line x1="134" y1="61" x2="146" y2="49"/>
          <line x1="56" y1="139" x2="44" y2="151"/>
          <line x1="134" y1="139" x2="146" y2="151"/>
        </g>
        <!-- 云（左上/右上各两朵） -->
        <path d="M180 118 q-26 -10 -28 -32 q-2 -24 24 -30 q12 -24 40 -14 q26 -8 34 10 q26 2 24 30 q-4 22 -30 26 z" fill="#e0f2fe" stroke="#93c5fd" stroke-width="4" stroke-linejoin="round"/>
        <path d="M900 76 q-20 -8 -22 -24 q-2 -19 19 -24 q10 -18 31 -11 q20 -6 26 8 q20 1 18 23 q-3 17 -23 21 z" fill="#e0f2fe" stroke="#93c5fd" stroke-width="4" stroke-linejoin="round"/>
        <path d="M840 150 q-16 -6 -18 -20 q-2 -15 15 -19 q8 -14 25 -8 q16 -5 21 6 q16 1 14 18 q-2 14 -18 17 z" fill="#e0f2fe" stroke="#93c5fd" stroke-width="4" stroke-linejoin="round"/>
        <!-- 彩虹（左中，四道弯） -->
        <g fill="none" stroke-linecap="round">
          <path d="M55 340 q75 -100 160 0" stroke="#fca5a5" stroke-width="11"/>
          <path d="M66 340 q69 -80 138 0" stroke="#fcd34d" stroke-width="11"/>
          <path d="M77 340 q63 -60 116 0" stroke="#a7f3d0" stroke-width="11"/>
          <path d="M88 340 q57 -42 94 0" stroke="#93c5fd" stroke-width="11"/>
        </g>
        <!-- 房子（左下角） -->
        <path d="M40 440 l55 -66 l55 66 z" fill="#f87171" stroke="#ef4444" stroke-width="4" stroke-linejoin="round"/>
        <rect x="50" y="446" width="90" height="70" fill="#fde68a" stroke="#f59e0b" stroke-width="4"/>
        <rect x="76" y="472" width="32" height="44" fill="#d97706"/>
        <rect x="62" y="456" width="22" height="22" fill="#93c5fd" stroke="#60a5fa" stroke-width="3"/>
        <rect x="108" y="456" width="22" height="22" fill="#93c5fd" stroke="#60a5fa" stroke-width="3"/>
        <rect x="122" y="398" width="13" height="32" fill="#d6a35c"/>
        <path d="M116 398 h24 l-7 14 h-10 z" fill="#f87171"/>
        <!-- 树（右下角） -->
        <path d="M848 482 q4 -24 0 -44" stroke="#d6a35c" stroke-width="9" stroke-linecap="round"/>
        <circle cx="843" cy="408" r="28" fill="#86efac" stroke="#4ade80" stroke-width="4"/>
        <circle cx="866" cy="430" r="21" fill="#a7f3d0" stroke="#4ade80" stroke-width="3.5"/>
        <circle cx="823" cy="432" r="17" fill="#6ee7b7" stroke="#4ade80" stroke-width="3.5"/>
        <!-- 花（右中/右下/左下各一朵） -->
        <g>
          <path d="M800 470 q2 24 0 46" stroke="#4ade80" stroke-width="4" stroke-linecap="round"/>
          <path d="M787 490 q11 4 25 0" stroke="#4ade80" stroke-width="3.5" stroke-linecap="round"/>
          <circle cx="786" cy="452" r="10" fill="#f9a8d4"/>
          <circle cx="800" cy="443" r="10" fill="#f9a8d4"/>
          <circle cx="814" cy="452" r="10" fill="#f9a8d4"/>
          <circle cx="792" cy="465" r="10" fill="#f9a8d4"/>
          <circle cx="808" cy="465" r="10" fill="#f9a8d4"/>
          <circle cx="800" cy="458" r="8" fill="#fde047"/>
        </g>
        <g>
          <path d="M910 500 q2 20 0 40" stroke="#4ade80" stroke-width="4" stroke-linecap="round"/>
          <path d="M898 520 q10 4 23 0" stroke="#4ade80" stroke-width="3.5" stroke-linecap="round"/>
          <circle cx="897" cy="484" r="9" fill="#c4b5fd"/>
          <circle cx="910" cy="476" r="9" fill="#c4b5fd"/>
          <circle cx="923" cy="484" r="9" fill="#c4b5fd"/>
          <circle cx="902" cy="496" r="9" fill="#c4b5fd"/>
          <circle cx="917" cy="496" r="9" fill="#c4b5fd"/>
          <circle cx="910" cy="490" r="7" fill="#fde047"/>
        </g>
        <g>
          <path d="M170 500 q1 16 0 34" stroke="#4ade80" stroke-width="4" stroke-linecap="round"/>
          <circle cx="160" cy="480" r="8" fill="#fcd34d"/>
          <circle cx="170" cy="473" r="8" fill="#fcd34d"/>
          <circle cx="180" cy="480" r="8" fill="#fcd34d"/>
          <circle cx="163" cy="489" r="8" fill="#fcd34d"/>
          <circle cx="176" cy="489" r="8" fill="#fcd34d"/>
          <circle cx="170" cy="484" r="6" fill="#f87171"/>
        </g>
        <!-- 小鸟（右上/左中） -->
        <g stroke="#7dd3fc" stroke-width="5" stroke-linecap="round" fill="none">
          <path d="M860 200 q15 -19 30 0 q15 -19 30 0"/>
          <path d="M60 220 q13 -16 26 0 q13 -16 26 0"/>
        </g>
        <!-- 气球（右中上） -->
        <path d="M900 300 q-3 22 -8 34" stroke="#94a3b8" stroke-width="3" stroke-linecap="round" fill="none"/>
        <ellipse cx="900" cy="276" rx="17" ry="22" fill="#f9a8d4" stroke="#f472b6" stroke-width="4"/>
        <path d="M893 295 q7 7 14 0" fill="none" stroke="#f472b6" stroke-width="3" stroke-linecap="round"/>
        <!-- 草地（底部波浪） -->
        <path d="M0 600 q30 -14 60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0" fill="none" stroke="#86efac" stroke-width="5" stroke-linecap="round"/>
        <path d="M0 616 q30 12 60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0 t60 0" fill="none" stroke="#a7f3d0" stroke-width="4" stroke-linecap="round"/>
      </svg>
      <!-- 金属卷材装饰：卷芯 + 展开的彩涂卷带（漆面颜色随色号选择） -->
      <svg class="coil-art" viewBox="0 0 240 130" aria-hidden="true">
        <defs>
          <linearGradient id="coilMetal" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="#c9d2dc"/>
            <stop offset="0.25" stop-color="#eef2f6"/>
            <stop offset="0.5" stop-color="#c3ccd6"/>
            <stop offset="0.75" stop-color="#e4e9ee"/>
            <stop offset="1" stop-color="#aeb9c5"/>
          </linearGradient>
          <linearGradient id="coilMetalD" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="#a7b2be"/>
            <stop offset="0.5" stop-color="#c9d2dc"/>
            <stop offset="1" stop-color="#98a3b1"/>
          </linearGradient>
        </defs>
        <!-- 后层小卷芯（纵深） -->
        <path d="M10 38 v32 a20 8 0 0 0 40 0 v-32 z" fill="url(#coilMetalD)"/>
        <ellipse cx="30" cy="38" rx="20" ry="8" fill="#dfe6ec"/>
        <ellipse cx="30" cy="38" rx="8" ry="3.2" fill="#8b95a3"/>
        <!-- 主卷芯 -->
        <path d="M44 52 v44 a30 12 0 0 0 60 0 v-44 z" fill="url(#coilMetal)"/>
        <ellipse cx="74" cy="52" rx="30" ry="12" fill="#f2f5f8"/>
        <ellipse cx="74" cy="52" rx="11" ry="4.5" fill="#98a3b1"/>
        <!-- 展开的彩涂卷带（漆面颜色随色号） -->
        <path d="M106 58 q52 -9 126 5 q9 17 0 23 q-76 11 -126 -3 z" :fill="raal.c"/>
        <!-- 卷带烤漆高光 -->
        <path d="M114 60 q52 -8 116 5" fill="none" stroke="rgba(255,255,255,0.55)" stroke-width="3.5" stroke-linecap="round"/>
        <path d="M112 72 q52 6 118 -2" fill="none" stroke="rgba(255,255,255,0.22)" stroke-width="2.5" stroke-linecap="round"/>
        <!-- 卷带末端漆滴 -->
        <circle cx="236" cy="86" r="5.5" :fill="raal.c"/>
        <circle cx="235" cy="84.5" r="1.8" fill="rgba(255,255,255,0.65)"/>
      </svg>
      <!-- 大漆滴（背景水彩） -->
      <svg class="big-drip" viewBox="0 0 64 64" aria-hidden="true">
        <path d="M32 8c6.5 9 14 16.5 14 25.5a14.5 14.5 0 1 1-29 0C17 24.5 24.5 17 32 8z" fill="#4a6fa5"/>
      </svg>
      <div class="form-shell">
        <!-- 移动端紧凑品牌头 -->
        <div class="mobile-brand">
          <div class="brand-icon small">
            <svg viewBox="0 0 64 64" width="24" height="24" aria-hidden="true">
              <path d="M32 11c6.5 9 14.5 16.4 14.5 25a14.5 14.5 0 1 1-29 0c0-8.6 8-16 14.5-25z" fill="#fff"/>
              <circle cx="26.5" cy="38" r="4.5" fill="#7da59d" opacity="0.55"/>
            </svg>
          </div>
          <h1>王新宇的芃远综合管理系统</h1>
        </div>

        <!-- 四色漆条：呼应主题色板 -->
        <div class="chip-strip" aria-hidden="true"></div>

        <div class="form-eyebrow"><span class="eyebrow-dot"></span>PENGYUAN COATINGS</div>
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-sub">使用您的账号进入工作台</p>


        <div class="form-area">
          <div class="input-group" :class="{ 'is-focus': focusField === 'user' }" @mouseenter="onInputOver" @mouseleave="onInputOut">
            <span class="input-icon">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 22c0-4.4 3.6-8 8-8s8 3.6 8 8"/></svg>
            </span>
            <label class="float-label" :class="{ up: focusField === 'user' || form.username }">用户名</label>
            <input
              v-model="form.username"
              type="text"
              aria-label="用户名"
              autocomplete="username"
              @focus="focusField='user'"
              @blur="focusField=''"
              @keyup.enter="focusPwd"
              ref="userInput"
            />
          </div>

          <div class="input-group" :class="{ 'is-focus': focusField === 'pwd' }" @mouseenter="onInputOver" @mouseleave="onInputOut">
            <span class="input-icon">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            </span>
            <label class="float-label" :class="{ up: focusField === 'pwd' || form.password }">密码</label>
            <input
              v-model="form.password"
              :type="showPwd ? 'text' : 'password'"
              aria-label="密码"
              autocomplete="current-password"
              @focus="focusField='pwd'"
              @blur="focusField=''"
              @keyup.enter="login"
              ref="pwdInput"
            />
            <span class="pwd-toggle" @click="showPwd=!showPwd" role="button" tabindex="0" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @keydown.enter="showPwd=!showPwd">
              <svg v-if="!showPwd" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8-2.16 3.19-2.16 3.19-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </span>
          </div>

          <button class="login-btn" :class="{ loading: loading }" @click="login" @mousedown="ripple" :disabled="loading"
                  :style="!loading && raal.code ? { background: `linear-gradient(135deg, ${raal.light}, ${raal.dark})` } : {}">
            <span v-if="!loading">登 录</span>
            <span v-else class="btn-spinner"></span>
          </button>

          <!-- 工业色号带：悬停显色号，点击换卷材漆面与按钮色 -->
          <div class="ral-band">
            <button class="ral-chip" :class="{ active: raal.code === 'RAL 5010' }" style="--c:#0e294b" data-name="RAL 5010 · 龙胆蓝" @click="applyRal('RAL 5010', '#0e294b', '#4a6fa5', '#0e294b')"></button>
            <button class="ral-chip" :class="{ active: raal.code === 'RAL 3009' }" style="--c:#643438" data-name="RAL 3009 · 氧化红" @click="applyRal('RAL 3009', '#643438', '#94585c', '#4a2427')"></button>
            <button class="ral-chip" :class="{ active: raal.code === 'RAL 6005' }" style="--c:#114232" data-name="RAL 6005 · 苔藓绿" @click="applyRal('RAL 6005', '#114232', '#3d7a62', '#0b2f24')"></button>
            <button class="ral-chip" :class="{ active: raal.code === 'RAL 7016' }" style="--c:#383e4a" data-name="RAL 7016 · 煤灰" @click="applyRal('RAL 7016', '#383e4a', '#636b78', '#252a33')"></button>
            <button class="ral-chip" :class="{ active: raal.code === 'RAL 1018' }" style="--c:#e8b93e" data-name="RAL 1018 · 锌黄" @click="applyRal('RAL 1018', '#e8b93e', '#f5d478', '#b98f1f')"></button>
          </div>

          <div class="hint-row"><kbd>Enter</kbd> 快速登录</div>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        </div>

        <div class="footer-text">© 2026 广东芃远新材料公司</div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { RIDDLES } from '../data/riddles'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)
const showPwd = ref(false)
const errorMsg = ref('')
const focusField = ref('')
const userInput = ref(null)
const pwdInput = ref(null)

const blobsRef = ref(null)
const swatchRef = ref(null)
// 鼠标是否在屏幕内：在 → 公仔停下各自动作追随鼠标；不在 → 各自表演
const mouseIn = ref(false)

const TOY_ORDER = ['bear', 'bunny', 'panda', 'fox', 'frog', 'penguin', 'monster']

// 七只公仔：视线/转头/受光/背光/物理状态
const BASE_TILT = { bear: 0, bunny: 4, panda: -2, fox: -7, frog: 5, penguin: 0, monster: -3 }
const toys = reactive(Object.fromEntries(TOY_ORDER.map(k => [k, {
  rot: 0, baseRot: BASE_TILT[k], pxL: 0, pxR: 0, py: 0, lean: 0,
  lx: 0, ly: 0, dx: 0, dy: 0,
  s: 1, v: 0, wob: 0, wv: 0, ty: 0,
  active: false, flash: false, pressed: false, sTarget: 1
}])))

// 每只灵敏度：转头 / 瞳孔 / 受光强度位移
const TOY_LOOK = {
  bear:    { rotK: 18, pxK: 3.6, pyK: 3.0, pmax: 3.8, lxK: 34, lyK: 24, leanK: 4 },
  bunny:   { rotK: 22, pxK: 4.0, pyK: 3.4, pmax: 3.8, lxK: 38, lyK: 26, leanK: 5 },
  panda:   { rotK: 15, pxK: 2.8, pyK: 2.2, pmax: 2.6, lxK: 28, lyK: 20, leanK: 3.5 },
  fox:     { rotK: 19, pxK: 3.8, pyK: 3.2, pmax: 3.6, lxK: 36, lyK: 25, leanK: 4.5 },
  frog:    { rotK: 24, pxK: 3.9, pyK: 3.3, pmax: 3.4, lxK: 40, lyK: 27, leanK: 5.5 },
  penguin: { rotK: 17, pxK: 3.4, pyK: 2.8, pmax: 3.2, lxK: 32, lyK: 22, leanK: 4.2 },
  monster: { rotK: 20, pxK: 4.2, pyK: 3.6, pmax: 3.9, lxK: 36, lyK: 24, leanK: 5 }
}

// 点击色卡 → 雨滴/溅花/飞溅小滴换成对应漆色
// 工业色号：选定后卷材漆面与登录按钮跟随
const raal = ref({ code: '', c: '#3b5b9f', light: '', dark: '' })
function applyRal(code, c, light, dark) {
  raal.value = { code, c, light, dark }
}

// 全屏泼溅涂料色（点击漆桶切换，默认赤）
const splat = ref('#e23b3b')
// 桶右侧泼漆区：一次泼溅 = 一团主溅 + 数个飞溅小滴，只保留最新一种颜色
const AREA_SHAPES = [
  '58% 42% 55% 45% / 48% 55% 45% 52%',
  '45% 55% 48% 52% / 55% 45% 58% 42%',
  '60% 40% 52% 48% / 45% 60% 40% 55%',
  '50% 50% 62% 38% / 55% 45% 50% 50%',
  '65% 35% 50% 50% / 42% 62% 38% 58%'
]
const areaSplats = ref([])
const splashRef = ref(null)
let areaId = 0
// 四色调配配方：每色 = 4 种基础色浆，配比合计 100%
const RECIPES = {
  '#e23b3b': { name: '赤 · 焰红', items: [
    { n: '大红', c: '#d63a2f', p: 64 },
    { n: '永固紫', c: '#6b3fa0', p: 16 },
    { n: '中黄', c: '#f0b429', p: 12 },
    { n: '钛白', c: '#f4f4ef', p: 8 }
  ] },
  '#f28c1e': { name: '橙 · 蜜橙', items: [
    { n: '中黄', c: '#f0b429', p: 46 },
    { n: '大红', c: '#d63a2f', p: 24 },
    { n: '钛白', c: '#f4f4ef', p: 26 },
    { n: '炭黑', c: '#2e2e33', p: 4 }
  ] },
  '#f2c11e': { name: '黄 · 明黄', items: [
    { n: '中黄', c: '#f0b429', p: 66 },
    { n: '钛白', c: '#f4f4ef', p: 20 },
    { n: '大红', c: '#d63a2f', p: 10 },
    { n: '炭黑', c: '#2e2e33', p: 4 }
  ] },
  '#3fa63f': { name: '绿 · 草绿', items: [
    { n: '酞菁绿', c: '#1a9e6e', p: 48 },
    { n: '中黄', c: '#f0b429', p: 30 },
    { n: '钛白', c: '#f4f4ef', p: 17 },
    { n: '炭黑', c: '#2e2e33', p: 5 }
  ] },
  '#1fb6a8': { name: '青 · 青碧', items: [
    { n: '酞菁蓝', c: '#2456c9', p: 34 },
    { n: '酞菁绿', c: '#1a9e6e', p: 32 },
    { n: '钛白', c: '#f4f4ef', p: 29 },
    { n: '炭黑', c: '#2e2e33', p: 5 }
  ] },
  '#2b6fd6': { name: '蓝 · 晴蓝', items: [
    { n: '酞菁蓝', c: '#2456c9', p: 52 },
    { n: '群青', c: '#3b4bd8', p: 24 },
    { n: '钛白', c: '#f4f4ef', p: 19 },
    { n: '炭黑', c: '#2e2e33', p: 5 }
  ] },
  '#8a4fd6': { name: '紫 · 黛紫', items: [
    { n: '永固紫', c: '#6b3fa0', p: 58 },
    { n: '酞菁蓝', c: '#2456c9', p: 18 },
    { n: '大红', c: '#d63a2f', p: 9 },
    { n: '钛白', c: '#f4f4ef', p: 15 }
  ] }
}
const recipe = ref(RECIPES['#e23b3b'])
function pickSplat(c) {
  splat.value = c
  recipe.value = RECIPES[c]
  // 泼溅团尺寸按泼溅区实际宽度比例计算（720P~4K 等比缩放）
  const aw = splashRef.value ? splashRef.value.getBoundingClientRect().width : 126
  const set = []
  const bl = 8 + Math.random() * 52   // 主溅位置
  const bt = 10 + Math.random() * 44
  set.push({
    id: ++areaId, kind: 'area-blob',
    left: bl, top: bt,
    size: aw * (0.2 + Math.random() * 0.13),
    rot: -35 + Math.random() * 70,
    c, delay: 0,
    shape: AREA_SHAPES[Math.floor(Math.random() * AREA_SHAPES.length)]
  })
  // 飞溅小滴：围绕主溅四散，各自小延迟依次弹出
  const drops = 4 + Math.floor(Math.random() * 3)
  for (let i = 0; i < drops; i++) {
    set.push({
      id: ++areaId, kind: 'area-drop',
      left: Math.max(2, Math.min(92, bl - 18 + Math.random() * 40)),
      top: Math.max(4, Math.min(82, bt - 14 + Math.random() * 34)),
      size: aw * (0.045 + Math.random() * 0.07),
      rot: -60 + Math.random() * 120,
      c, delay: 0.05 + i * 0.035 + Math.random() * 0.08,
      shape: '50%'
    })
  }
  areaSplats.value = set
}

function clampNum(v, max) {
  return Math.max(-max, Math.min(max, v))
}
function applyLook(mx, my) {
  for (const key of TOY_ORDER) {
    const t = toys[key]
    const k = TOY_LOOK[key]
    t.rot = mx * k.rotK
    t.pxL = t.pxR = clampNum(mx * k.pxK, k.pmax)
    t.py = clampNum(my * k.pyK, k.pmax * 0.85)
    t.lean = mx * k.leanK
    t.lx = mx * k.lxK
    t.ly = my * k.lyK
    t.dx = -mx * k.lxK * 0.7
    t.dy = -my * k.lyK * 0.7
  }
}

// ===== 点击物理反馈：质量-弹簧模拟（压缩→过冲回弹→抖动衰减） =====
let simRunning = false
function clickToy(key) {
  const t = toys[key]
  t.s = 0.72           // 瞬间压扁（更深的 duang 起手）
  t.v = 0.55           // 回弹初速度
  t.wv = (Math.random() * 2 - 1) * 2.6  // 随机扭转
  t.active = true
  t.flash = true       // 受光闪一下（光源脉冲）
  setTimeout(() => { t.flash = false }, 340)
  if (!simRunning) { simRunning = true; requestAnimationFrame(simStep) }
}
// 按住拉伸（幅度有限），松开回弹
function pressToy(key) {
  const t = toys[key]
  t.pressed = true
  t.sTarget = 1.16
  t.active = true
  if (!simRunning) { simRunning = true; requestAnimationFrame(simStep) }
}
function releaseToy(key) {
  const t = toys[key]
  if (!t.pressed) return
  t.pressed = false
  t.sTarget = 1
  t.active = true
}
// ===== 脑筋急转弯对话表演：随机一只出题，其余六只挨个乱猜，最后揭晓 =====
const CHEERS = ['耶！我答对啦！', '我就说嘛！', '这题太简单了！', '看到没？我猜的！', '嘿嘿，我聪明吧？', '今晚加鸡腿！']
const show = reactive({ active: false, asker: '', phase: 'q', riddle: '', answer: '', bubbles: {}, rightGuesses: [], celebrating: {}, distractors: [], ansIdx: 0 })
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
  // 本题自带 4 个"沾边"错误答案，翻倍洗牌 → 一轮 6 个回答不重复
  show.distractors = [...r.d, ...r.d].sort(() => Math.random() - 0.5)
  show.ansIdx = 0
  show.rightGuesses = []
  show.celebrating = {}
  show.bubbles = { [asker]: { text: r.q, kind: 'asker' } }
  // 其余六只挨个冒泡乱猜
  others.forEach((t, i) => {
    showTimers.push(setTimeout(() => {
      if (!show.active) return
      // 5% 概率答对，其余从本题的"沾边"错误答案池里按序抽取（已洗牌，一轮不重复）
      const isRight = Math.random() < 0.05
      const text = isRight ? show.answer : show.distractors[show.ansIdx++ % show.distractors.length]
      show.bubbles[t] = { text, kind: 'answer' }
      if (isRight) show.rightGuesses.push(t)
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
    // 答对的公仔：跳跃庆祝 + 庆祝对话
    show.rightGuesses.forEach(g => {
      show.celebrating[g] = true
      show.bubbles[g] = { text: CHEERS[Math.floor(Math.random() * CHEERS.length)], kind: 'cheer' }
      showTimers.push(setTimeout(() => {
        show.celebrating[g] = false
        if (show.active && show.bubbles[g]) delete show.bubbles[g]
      }, 3200))
    })
  }, revealAt))
  // 一轮结束，安排下一轮
  showTimers.push(setTimeout(() => {
    show.active = false
    show.bubbles = {}
    scheduleShow()
  }, revealAt + 5600))
}

// 任意位置松开鼠标都结束拉伸
// 任意位置松开鼠标都结束拉伸
function onGlobalMouseUp() {
  for (const key of TOY_ORDER) {
    const t = toys[key]
    if (t.pressed) { t.pressed = false; t.sTarget = 1; t.active = true }
  }
}
onMounted(() => {
  window.addEventListener('mouseup', onGlobalMouseUp)
  pickSplat('#e23b3b')   // 默认先泼一团赤色
  scheduleShow()
})
onUnmounted(() => {
  window.removeEventListener('mouseup', onGlobalMouseUp)
  stopShow()
})

function simStep() {
  let any = false
  for (const key of TOY_ORDER) {
    const t = toys[key]
    // 按住拉伸：缓动拉向目标，抖动衰减
    if (t.pressed) {
      t.s = Math.min(1.3, t.s + (t.sTarget - t.s) * 0.3)
      t.wv *= 0.85
      t.wob += t.wv
      t.ty = 0
      any = true
      continue
    }
    if (!t.active) continue
    // 离散弹簧（dt=1 帧）：低阻尼 → 多次自然回弹（duang duang）
    t.v = (t.v + (1 - t.s) * 0.14) * 0.75
    t.s = Math.max(0.4, Math.min(1.8, t.s + t.v))
    t.wv *= 0.84
    t.wob += t.wv
    t.ty = (1 - t.s) * -8 - t.v * 6      // 压扁向下坐 + 回弹上跳
    if (Math.abs(t.v) < 0.002 && Math.abs(t.s - 1) < 0.012 && Math.abs(t.wv) < 0.05) {
      t.active = false; t.s = 1; t.v = 0; t.wob = 0; t.wv = 0; t.ty = 0
    }
    any = true
  }
  if (any) requestAnimationFrame(simStep)
  else simRunning = false
}

function focusPwd() {
  pwdInput.value?.focus()
}

// 按钮点击涟漪定位
function ripple(e) {
  const r = e.currentTarget.getBoundingClientRect()
  e.currentTarget.style.setProperty('--rx', ((e.clientX - r.left) / r.width * 100).toFixed(1) + '%')
  e.currentTarget.style.setProperty('--ry', ((e.clientY - r.top) / r.height * 100).toFixed(1) + '%')
}

// 鼠标视差 + 光源 + 公仔追踪
function onMove(e) {
  const x = e.clientX / window.innerWidth - 0.5
  const y = e.clientY / window.innerHeight - 0.5
  if (blobsRef.value) blobsRef.value.style.transform = `translate3d(${(x * -22).toFixed(1)}px, ${(y * -14).toFixed(1)}px, 0)`
  if (swatchRef.value) swatchRef.value.style.transform = `translate3d(${(x * 10).toFixed(1)}px, ${(y * 6).toFixed(1)}px, 0)`
  // 只有鼠标悬停在用户名/密码输入框上时，公仔才盯着鼠标
  if (mouseIn.value) applyLook(x, y)
}
function resetToyLook() {
  for (const key of TOY_ORDER) {
    const t = toys[key]
    t.rot = 0; t.pxL = 0; t.pxR = 0; t.py = 0; t.lean = 0
    t.lx = 0; t.ly = 0; t.dx = 0; t.dy = 0
  }
}
// 鼠标进入输入框：公仔盯鼠标 + 暂停出题
function onInputOver() {
  mouseIn.value = true
  stopShow()
}
// 鼠标离开输入框：公仔自由活动 + 恢复出题
function onInputOut() {
  mouseIn.value = false
  scheduleShow()
  resetToyLook()
}
function onLeave() {
  mouseIn.value = false
  scheduleShow()
  resetToyLook()
  if (blobsRef.value) blobsRef.value.style.transform = ''
  if (swatchRef.value) swatchRef.value.style.transform = ''
}

async function login() {
  errorMsg.value = ''
  if (!form.value.username) { errorMsg.value = '请输入用户名'; userInput.value?.focus(); return }
  if (!form.value.password) { errorMsg.value = '请输入密码'; pwdInput.value?.focus(); return }

  loading.value = true
  try {
    const data = await api.post('/auth/login', form.value)
    // v6.2 安全：token 走 HttpOnly Cookie（服务端自动 Set-Cookie，JS 不可读、XSS 无法窃取），不再落 localStorage
    localStorage.setItem('user', JSON.stringify(data))
    ElMessage.success('登录成功')
    router.replace('/')
  } catch (e) {
    errorMsg.value = '用户名或密码错误'
    form.value.password = ''
    pwdInput.value?.focus()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ========== 整体双栏 ========== */
.login-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ========== 左侧品牌面板 ========== */
.brand-panel {
  position: relative;
  width: 46%;
  min-width: 420px;
  background: linear-gradient(160deg, var(--pims-sidebar-start) 0%, var(--pims-sidebar-mid) 55%, rgba(var(--pims-primary-dark-rgb),0.85) 130%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 64px 96px;
  overflow: hidden;
}


/* 氛围光斑（包裹层做视差位移、内部保持漂浮动画） */
.paint-blobs {
  position: absolute; inset: 0; pointer-events: none;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  animation: float 22s infinite ease-in-out;
}
.b1 { width: 380px; height: 380px; background: rgba(var(--pims-primary-light-rgb),0.28); top: -120px; left: -100px; }
.b2 { width: 300px; height: 300px; background: rgba(var(--pims-primary-rgb),0.22); bottom: 10%; right: -90px; animation-delay: -7s; }
.b3 { width: 220px; height: 220px; background: rgba(245,158,11,0.14); top: 38%; left: 55%; animation-delay: -13s; animation-duration: 26s; }

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(28px, -26px) scale(1.08); }
  50% { transform: translate(-22px, 18px) scale(0.95); }
  75% { transform: translate(-28px, -10px) scale(1.04); }
}

.brand-content { position: relative; z-index: 1; }

/* 入场错帧浮现（只动 opacity，避免与视差位移冲突） */
.brand-head { animation: fadeIn 0.6s ease both; }
.brand-hero { animation: fadeIn 0.6s 0.12s ease both; }
.bucket-row { animation: fadeIn 0.6s 0.24s ease both; }
.swatch-caption { animation: fadeIn 0.6s 0.34s ease both; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

.brand-head { display: flex; align-items: center; gap: 14px; margin-bottom: 40px; }
.brand-icon {
  width: 52px; height: 52px;
  background: linear-gradient(135deg, var(--pims-primary-light), var(--pims-primary));
  border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8px 28px rgba(var(--pims-primary-rgb),0.45);
  flex-shrink: 0;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.brand-head:hover .brand-icon { transform: rotate(-8deg) scale(1.06); }
.brand-name { font-size: 20px; font-weight: 800; color: #fff; letter-spacing: 2px; line-height: 1.2; }
.brand-en { font-size: 10px; color: rgba(255,255,255,0.45); letter-spacing: 3px; margin-top: 2px; }

.brand-hero h1 {
  margin: 0 0 14px;
  font-size: clamp(24px, 2.4vw, 32px);
  font-weight: 800;
  color: #fff;
  letter-spacing: 1px;
  line-height: 1.35;
}
.brand-hero p {
  margin: 0;
  font-size: 14px;
  color: rgba(255,255,255,0.55);
  letter-spacing: 0.5px;
  line-height: 1.7;
}

/* ========== 涂料桶排（七色外溢漆桶） ========== */
.bucket-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 30px;
}
.bucket-row {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.splash-row {
  display: flex;
  align-items: stretch;
  flex-wrap: wrap;
  gap: 16px;
}
.bucket {
  position: relative;
  width: clamp(36px, 9.5cqw, 96px);
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  filter: drop-shadow(0 10px 14px rgba(0,0,0,0.35));
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.2s;
}

/* 桶右侧泼漆区（无框无文字，纯漆面；随面板 cqw 缩放） */
.splash-area {
  position: relative;
  width: clamp(88px, 17cqw, 232px);
  height: clamp(68px, 13cqw, 178px);
  flex-shrink: 0;
  overflow: hidden;
}
.area-blob {
  position: absolute;
  aspect-ratio: 1;
  box-shadow:
    inset -3px -4px 8px rgba(0,0,0,0.3),
    inset 3px 3px 5px rgba(255,255,255,0.25);
  animation: splatPop 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.area-drop {
  position: absolute;
  aspect-ratio: 1;
  box-shadow:
    inset -1.5px -2px 3px rgba(0,0,0,0.28),
    inset 1.5px 1.5px 2.5px rgba(255,255,255,0.3);
  animation: splatPop 0.26s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
@keyframes splatPop {
  from { opacity: 0; transform: scale(0.15) rotate(calc(var(--rot, 0deg) - 25deg)); }
  60% { opacity: 1; transform: scale(1.12) rotate(calc(var(--rot, 0deg) + 4deg)); }
  to { opacity: 1; transform: scale(1) rotate(var(--rot, 0deg)); }
}

/* ========== 四色调配配方卡（泼溅右侧；限宽防大屏过度拉伸） ========== */
.recipe-panel {
  flex: 1 1 240px;
  min-width: 0;
  max-width: clamp(230px, 38cqw, 430px);
  padding: 9px 12px 8px;
  background: rgba(255,255,255,0.055);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 12px;
  box-shadow: 0 10px 24px rgba(0,0,0,0.22);
  animation: recipeIn 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes recipeIn {
  from { opacity: 0; transform: translateX(10px) scale(0.96); }
  to { opacity: 1; transform: translateX(0) scale(1); }
}
.recipe-title {
  font-size: 10px;
  letter-spacing: 2.5px;
  color: rgba(255,255,255,0.62);
  margin-bottom: 7px;
  white-space: nowrap;
}
.recipe-bar {
  display: flex;
  height: 18px;
  border-radius: 5px;
  overflow: hidden;
  box-shadow: inset 0 -3px 5px rgba(0,0,0,0.28), 0 2px 6px rgba(0,0,0,0.25);
}
.recipe-labels {
  display: flex;
  margin-top: 6px;
}
.recipe-labels span {
  font-size: 9px;
  line-height: 1.3;
  color: rgba(255,255,255,0.68);
  text-align: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  padding: 0 3px;
}
.recipe-labels span:first-child { text-align: left; }
.recipe-labels span:last-child { text-align: right; }
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
/* 漆滴边缘 */
.paint-drip {
  position: absolute;
  top: 0; right: 0;
  width: 14px; height: 100%;
  background: linear-gradient(180deg, var(--pims-sidebar-mid), var(--pims-sidebar-start));
}
.paint-drip span {
  position: absolute;
  top: 0; left: 0;
  width: 14px;
  background: linear-gradient(180deg, var(--pims-sidebar-mid), var(--pims-sidebar-start));
  border-radius: 0 0 10px 10px;
  box-shadow: -1px 2px 6px rgba(0,0,0,0.25);
}
.paint-drip span:nth-child(1) { top: 6%;  height: 30px; }
.paint-drip span:nth-child(2) { top: 18%; height: 58px; }
.paint-drip span:nth-child(3) { top: 33%; height: 40px; }
.paint-drip span:nth-child(4) { top: 47%; height: 76px; }
.paint-drip span:nth-child(5) { top: 62%; height: 46px; }
.paint-drip span:nth-child(6) { top: 76%; height: 64px; }
.paint-drip span:nth-child(7) { top: 88%; height: 36px; }
.paint-drip span:nth-child(8) { top: 94%; height: 50px; }

/* ========== 七只毛绒公仔 ========== */
.plush-row {
  /* 公仔尺寸随面板宽度自适应（容器查询） */
  --toy-w: 104px;
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  display: flex;
  align-items: flex-end;
  pointer-events: none;
}
.brand-panel { container-type: inline-size; }
@supports (container-type: inline-size) {
  .plush-row { --toy-w: clamp(64px, 15cqw, 186px); }
}
.toywrap {
  position: relative;
  width: var(--toy-w);
  height: var(--toy-w);
  margin-left: calc(var(--toy-w) * -0.24);
  flex-shrink: 0;
  display: inline-block;
  pointer-events: auto;
  cursor: pointer;
  transform-origin: 50% 100%;
  transition: filter 0.2s;
  user-select: none;
}
.toywrap:first-child { margin-left: 0; }
.toywrap:hover { filter: drop-shadow(0 12px 20px rgba(0,0,0,0.4)); }

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
.bubble.cheer {
  background: #fff1f2;
  border: 1.5px solid #fb7185;
  font-weight: 700;
  color: #be123c;
}
.bubble.cheer::after { border-top-color: #fff1f2; }

/* 答对庆祝：连跳两下 + 扭动 */
.toywrap.celebrate {
  animation: celebrateJump 0.7s cubic-bezier(0.34, 1.56, 0.64, 1) 2;
}
@keyframes celebrateJump {
  0%   { transform: translateY(0) rotate(0deg) scale(1, 1); }
  28%  { transform: translateY(-30px) rotate(-7deg) scale(1.02, 0.98); }
  50%  { transform: translateY(0) rotate(3deg) scale(1.08, 0.92); }
  72%  { transform: translateY(-14px) rotate(-4deg) scale(1.02, 0.98); }
  100% { transform: translateY(0) rotate(0deg) scale(1, 1); }
}
@keyframes bubbleIn {
  from { opacity: 0; transform: translateX(-50%) translateY(8px) scale(0.88); }
  to { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}

/* 大小不一：各角色尺寸比例 */
/* 间距不规则但不过深：既错落又保证全身可见 */
.toywrap.bear   { width: calc(var(--toy-w) * 1.12); height: calc(var(--toy-w) * 1.12); margin-left: calc(var(--toy-w) * -0.18); }
.toywrap.panda  { width: calc(var(--toy-w) * 1.08); height: calc(var(--toy-w) * 1.08); margin-left: calc(var(--toy-w) * 0.10); }
.toywrap.monster{ width: calc(var(--toy-w) * 1.02); height: calc(var(--toy-w) * 1.02); margin-left: calc(var(--toy-w) * 0.14); }
.toywrap.penguin{ width: calc(var(--toy-w) * 0.95); height: calc(var(--toy-w) * 0.95); margin-left: calc(var(--toy-w) * -0.12); }
.toywrap.frog   { width: calc(var(--toy-w) * 0.90); height: calc(var(--toy-w) * 0.90); margin-left: calc(var(--toy-w) * 0.03); }
.toywrap.bunny  { width: calc(var(--toy-w) * 0.85); height: calc(var(--toy-w) * 0.85); margin-left: calc(var(--toy-w) * -0.10); }
.toywrap.fox    { margin-left: calc(var(--toy-w) * -0.20); }

.plush {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 10px 16px rgba(0,0,0,0.3));
}

/* 绒毛扰动 */

/* 头随鼠标转动（绕颈部）+ 物理抖动 */
.plush .head {
  transform-origin: 75px 112px;
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.plush .pupil {
  transition: transform 0.2s ease-out;
}

/* 浮雕：左上受光 + 右下背光（HTML 层） */
/* 点击光脉冲 */
.toywrap.flash .toy-light {
  animation: lightPulse 0.34s ease-out;
}
@keyframes lightPulse {
  0% { opacity: 0.5; }
  45% { opacity: 1.4; }
  100% { opacity: 1; }
}

/* 呼吸起伏（节奏各异、相位错开） */
.plush .anim-bob { transform-origin: 75px 136px; }
.toywrap:nth-child(1) .anim-bob { animation: bob 3.4s ease-in-out infinite alternate; }
.toywrap:nth-child(2) .anim-bob { animation: bob 2.6s ease-in-out infinite alternate; }
.toywrap:nth-child(3) .anim-bob { animation: bob 4.2s ease-in-out infinite alternate; animation-delay: 0.6s; }
.toywrap:nth-child(4) .anim-bob { animation: bob 3.0s ease-in-out infinite alternate; animation-delay: 1.2s; }
.toywrap:nth-child(5) .anim-bob { animation: bob 2.2s ease-in-out infinite alternate; animation-delay: 0.3s; }
.toywrap:nth-child(6) .anim-bob { animation: bob 3.8s ease-in-out infinite alternate; animation-delay: 1.6s; }
.toywrap:nth-child(7) .anim-bob { animation: bob 4.6s ease-in-out infinite alternate; animation-delay: 0.9s; }

/* 眨眼（周期与相位各不相同，绝不齐眨） */
.plush .anim-blink { transform-origin: center; }
.toywrap:nth-child(1) .anim-blink { animation: blink 5.2s infinite; }
.toywrap:nth-child(2) .anim-blink { animation: blink 4.2s infinite; animation-delay: 0.7s; }
.toywrap:nth-child(3) .anim-blink { animation: blink 6.0s infinite; animation-delay: 1.4s; }
.toywrap:nth-child(4) .anim-blink { animation: blink 4.8s infinite; animation-delay: 2.1s; }
.toywrap:nth-child(5) .anim-blink { animation: blink 3.6s infinite; animation-delay: 2.8s; }
.toywrap:nth-child(6) .anim-blink { animation: blink 5.6s infinite; animation-delay: 3.5s; }
.toywrap:nth-child(7) .anim-blink { animation: blink 4.4s infinite; animation-delay: 4.2s; }

/* 各自签名动作（异步表演） */
@keyframes wave { 0%, 100% { transform: rotate(16deg); } 50% { transform: rotate(-24deg); } }
.anim-wave { transform-origin: 127px 78px; animation: wave 2.8s ease-in-out infinite; animation-delay: 0.4s; }
@keyframes twitch { 0%, 100% { transform: rotate(0deg); } 38% { transform: rotate(9deg); } 58% { transform: rotate(-3deg); } }
.anim-twitch { transform-origin: 102px 30px; animation: twitch 3.6s ease-in-out infinite; animation-delay: 1.1s; }
@keyframes wag { 0%, 100% { transform: rotate(12deg); } 50% { transform: rotate(-15deg); } }
.anim-wag { transform-origin: 24px 136px; animation: wag 1.9s ease-in-out infinite; animation-delay: 0.7s; }
@keyframes flap { 0%, 100% { transform: rotate(-13deg); } 50% { transform: rotate(13deg); } }
.anim-flap { transform-origin: 75px 100px; animation: flap 2.4s ease-in-out infinite; animation-delay: 1.5s; }
@keyframes monwob { 0%, 100% { transform: rotate(-6deg); } 50% { transform: rotate(6deg); } }
.anim-wob { transform-origin: 75px 128px; animation: monwob 4.6s ease-in-out infinite; animation-delay: 0.2s; }


/* 头部闲置摇摆：鼠标不在时各自摇头晃脑 */
@keyframes sway { 0%, 100% { transform: rotate(-6deg); } 50% { transform: rotate(6deg); } }
.plush .anim-sway { transform-origin: 75px 112px; }
.toywrap:nth-child(1) .anim-sway { animation: sway 5.0s ease-in-out infinite; }
.toywrap:nth-child(2) .anim-sway { animation: sway 6.5s ease-in-out infinite; animation-delay: 1.2s; }
.toywrap:nth-child(3) .anim-sway { animation: sway 4.5s ease-in-out infinite; animation-delay: 2.4s; }
.toywrap:nth-child(4) .anim-sway { animation: sway 7.0s ease-in-out infinite; animation-delay: 0.8s; }
.toywrap:nth-child(5) .anim-sway { animation: sway 5.5s ease-in-out infinite; animation-delay: 3.1s; }
.toywrap:nth-child(6) .anim-sway { animation: sway 6.0s ease-in-out infinite; animation-delay: 1.9s; }
.toywrap:nth-child(7) .anim-sway { animation: sway 4.0s ease-in-out infinite; animation-delay: 2.8s; }

/* 鼠标进入屏幕 → 全部停下各自动作，专心追随鼠标 */
.login-page.has-mouse .plush-row [class*='anim-'] { animation-play-state: paused; }

@keyframes bob {
  from { transform: scaleY(1) translateY(0); }
  to { transform: scaleY(1.05) translateY(-1.5px); }
}
@keyframes blink {
  0%, 93%, 100% { transform: scaleY(1); }
  95%, 98% { transform: scaleY(0.1); }
}

/* 矮屏收缩 */
@media (max-height: 820px) {
  .plush-row { --toy-w: clamp(62px, 15cqw, 88px); }
  .brand-panel { padding-bottom: 80px; }
}

/* ========== 右侧表单面板 ========== */
.form-panel {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    radial-gradient(700px 320px at 90% -5%, rgba(59,91,159,0.10), transparent 60%),
    radial-gradient(560px 300px at 5% 105%, rgba(216,122,60,0.09), transparent 55%),
    #f5f6f8;
  overflow-y: auto;
}

/* 幼儿园水彩画背景：整体极淡，像半透明白纸下透出的画 */
.watercolor-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.13;
  pointer-events: none;
  z-index: 0;
}

.form-shell {
  position: relative;
  z-index: 0;
  width: min(420px, 100%);
  /* 烤漆玻璃卡：半透白底 + 漆面高光与厚度（不模糊背景，水彩画透出） */
  padding: 42px 38px 30px;
  background: rgba(255,255,255,0.62);
  border-radius: 24px;
  border: 1px solid rgba(255,255,255,0.85);
  box-shadow:
    0 30px 70px rgba(30,41,59,0.16),
    0 6px 20px rgba(30,41,59,0.08),
    inset 0 1px 0 rgba(255,255,255,0.95),
    inset 0 -1px 0 rgba(148,163,184,0.18);
  animation: cardIn 0.7s cubic-bezier(0.16, 1, 0.3, 1);
}
/* 顶部主题色漆线（烤漆封边） */
.form-shell::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 5px;
  border-radius: 24px 24px 0 0;
  background: linear-gradient(90deg, var(--pims-primary-light), var(--pims-primary) 45%, var(--pims-primary-dark));
  box-shadow: inset 0 -1px 2px rgba(0,0,0,0.25), 0 1px 3px rgba(var(--pims-primary-rgb),0.35);
  pointer-events: none;
}
/* 金属卷材装饰 */
.coil-art {
  position: absolute;
  right: -1%;
  top: 3%;
  width: clamp(250px, 27vw, 480px);
  opacity: 1;
  filter: drop-shadow(0 22px 40px rgba(30,41,59,0.30));
  transform: rotate(-3deg);
  pointer-events: none;
  z-index: 1;
  animation: coilFloat 7s ease-in-out infinite;
}
@keyframes coilFloat {
  0%, 100% { transform: rotate(-3deg) translateY(0); }
  50% { transform: rotate(-1.5deg) translateY(-9px); }
}
/* 大漆滴水彩 */
.big-drip {
  position: absolute;
  left: 3%;
  bottom: 4%;
  width: 130px;
  opacity: 0.07;
  transform: rotate(18deg);
  pointer-events: none;
  z-index: 1;
}

/* 工业色号带：真实色卡样板（漆面内凹高光） */
.ral-band {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}
.ral-chip {
  position: relative;
  width: 30px; height: 30px;
  border-radius: 9px;
  border: 2px solid rgba(255,255,255,0.95);
  background: var(--c);
  cursor: pointer;
  box-shadow:
    0 2px 6px rgba(15,23,42,0.28),
    inset 0 1px 1.5px rgba(255,255,255,0.45),
    inset 0 -3px 4px rgba(0,0,0,0.28);
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.22s;
  padding: 0;
}
.ral-chip:hover {
  transform: translateY(-4px) scale(1.14);
  box-shadow:
    0 6px 14px rgba(15,23,42,0.32),
    inset 0 1px 1.5px rgba(255,255,255,0.5),
    inset 0 -3px 4px rgba(0,0,0,0.26);
}
.ral-chip.active {
  outline: 2.5px solid var(--pims-primary);
  outline-offset: 3px;
  transform: translateY(-2px) scale(1.08);
  box-shadow:
    0 0 0 5px rgba(var(--pims-primary-rgb),0.12),
    0 6px 14px rgba(15,23,42,0.32),
    inset 0 1px 1.5px rgba(255,255,255,0.5),
    inset 0 -3px 4px rgba(0,0,0,0.26);
}
.ral-chip::after {
  content: attr(data-name);
  position: absolute;
  bottom: calc(100% + 9px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  background: rgba(15,23,42,0.9);
  color: #fff;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.3px;
  padding: 4px 9px;
  border-radius: 7px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: all 0.18s ease;
  box-shadow: 0 4px 12px rgba(15,23,42,0.3);
}
.ral-chip:hover::after {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

/* 表单后主题光晕 */
.form-shell::after {
  content: '';
  position: absolute;
  inset: -70px -90px;
  z-index: -1;
  background: radial-gradient(340px 220px at 50% 18%, rgba(var(--pims-primary-rgb),0.12), transparent 70%);
  pointer-events: none;
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(28px); }
  to { opacity: 1; transform: translateY(0); }
}

.mobile-brand { display: none; }

/* 四色漆条（浮雕） */
.chip-strip {
  height: 6px;
  border-radius: 3px;
  margin-bottom: 26px;
  background: linear-gradient(90deg, #4a7c74 0 25%, #b08968 25% 50%, #b58a3c 50% 75%, #b05a4e 75% 100%);   /* v6.9.1 黛青系 */
  box-shadow:
    inset 0 1px 1px rgba(255,255,255,0.75),
    inset 0 -2px 3px rgba(0,0,0,0.3),
    0 2px 6px rgba(15,23,42,0.16);
}

.form-eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--pims-primary);
  animation: fadeUp 0.5s 0.08s ease both;
}
.eyebrow-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--pims-primary-light), var(--pims-primary-dark));
  box-shadow: 0 0 0 3px rgba(var(--pims-primary-rgb),0.15), 0 1px 3px rgba(var(--pims-primary-rgb),0.4);
}
.form-title {
  margin: 0 0 6px;
  font-size: 28px;
  font-weight: 800;
  color: #1e293b;
  letter-spacing: 0.5px;
  animation: fadeUp 0.5s 0.14s ease both;
}
.form-sub {
  margin: 0 0 32px;
  font-size: 13px;
  color: #64748b;
  animation: fadeUp 0.5s 0.2s ease both;
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 输入框（浮动标签 + 烤漆内高光） */
.input-group {
  position: relative;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  background: #fff;
  border: 1.5px solid #e2e8f0;
  border-radius: 14px;
  box-shadow:
    inset 0 2px 4px rgba(15,23,42,0.04),
    0 1px 3px rgba(15,23,42,0.05);
  transition: border-color 0.25s, box-shadow 0.25s, transform 0.2s;
}
.input-group:hover { border-color: #cbd5e1; }
.input-group:focus-within {
  border-color: var(--pims-primary-light);
  box-shadow:
    0 0 0 4px rgba(var(--pims-primary-rgb),0.13),
    inset 0 2px 4px rgba(15,23,42,0.03);
}
.input-icon {
  display: flex; align-items: center; justify-content: center;
  width: 46px; color: #94a3b8;
  flex-shrink: 0;
  transition: color 0.25s, transform 0.25s;
}
.input-group:focus-within .input-icon {
  color: var(--pims-primary);
  transform: scale(1.08);
}
.input-group input {
  flex: 1;
  min-width: 0;
  background: none; border: none; outline: none;
  padding: 20px 12px 7px 0;
  font-size: 15px; color: #1e293b;
  font-family: inherit;
}
/* 浮动标签 */
.float-label {
  position: absolute;
  left: 46px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: #94a3b8;
  pointer-events: none;
  transition: all 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}
.float-label.up {
  top: 9px;
  transform: none;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--pims-primary);
}

.pwd-toggle {
  display: flex; align-items: center; justify-content: center;
  width: 46px; cursor: pointer; color: #94a3b8;
  transition: color 0.2s;
  flex-shrink: 0;
}
.pwd-toggle:hover { color: var(--pims-primary); }

/* 登录按钮：烤漆质感（顶部高光 + 底部厚度）+ 流光 + 点击涟漪 */
.login-btn {
  width: 100%; margin-top: 6px;
  padding: 15px;
  border: none; border-radius: 14px;
  background: linear-gradient(135deg, var(--pims-primary-light), var(--pims-primary) 55%, var(--pims-primary-dark));
  color: #fff; font-size: 16px; font-weight: 600;
  letter-spacing: 4px; cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s, filter 0.25s;
  box-shadow:
    0 8px 22px rgba(var(--pims-primary-rgb),0.38),
    inset 0 1px 0 rgba(255,255,255,0.38),
    inset 0 -3px 6px rgba(0,0,0,0.18);
  position: relative; overflow: hidden;
}
.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow:
    0 12px 30px rgba(var(--pims-primary-rgb),0.48),
    inset 0 1px 0 rgba(255,255,255,0.42),
    inset 0 -3px 6px rgba(0,0,0,0.16);
  filter: brightness(1.06);
}
.login-btn:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow:
    0 3px 10px rgba(var(--pims-primary-rgb),0.35),
    inset 0 2px 4px rgba(0,0,0,0.2);
  filter: brightness(0.98);
}
.login-btn.loading {
  pointer-events: none;
  background: rgba(var(--pims-primary-light-rgb),0.5);
  box-shadow: none;
}

/* 流光扫过 */
.login-btn::after {
  content: '';
  position: absolute;
  top: 0; left: -60%;
  width: 40%; height: 100%;
  background: linear-gradient(100deg, transparent, rgba(255,255,255,0.35), transparent);
  transform: skewX(-20deg);
  pointer-events: none;
  animation: sheen 3.4s ease-in-out infinite;
}
@keyframes sheen {
  0%, 55% { left: -60%; }
  85%, 100% { left: 135%; }
}
.login-btn.loading::after { display: none; }

/* 点击涟漪 */
.login-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(circle at var(--rx, 50%) var(--ry, 50%), rgba(255,255,255,0.45), transparent 55%);
  opacity: 0;
  pointer-events: none;
}
.login-btn:active:not(:disabled)::before {
  animation: ripple 0.45s ease-out;
}
@keyframes ripple {
  from { opacity: 0.9; transform: scale(0.15); }
  to { opacity: 0; transform: scale(1.3); }
}

.btn-spinner {
  display: inline-block;
  width: 20px; height: 20px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 快捷键提示 */
.hint-row {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
}
.hint-row kbd {
  font-family: inherit;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 6px;
  border: 1px solid #d7dce5;
  background: #f1f5f9;
  color: #64748b;
  box-shadow: 0 1px 0 #cbd5e1;
}

/* 错误提示 */
.error-msg {
  margin-top: 14px;
  text-align: center;
  color: #dc2626;
  font-size: 13px;
  animation: shake 0.4s ease;
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  50% { transform: translateX(6px); }
  75% { transform: translateX(-4px); }
}

.footer-text {
  margin-top: 40px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}

/* ========== 响应式 ========== */
@media (max-width: 900px) {
  .brand-panel { display: none; }
  .watercolor-bg { display: none; }

  .form-panel {
    background: linear-gradient(160deg, var(--pims-sidebar-start) 0%, var(--pims-sidebar-mid) 55%, rgba(var(--pims-primary-dark-rgb),0.85) 130%);
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 24px;
  }
  .mobile-brand h1 {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    color: #1e293b;
    letter-spacing: 1px;
  }
  .brand-icon.small { width: 40px; height: 40px; border-radius: 11px; }

  .form-shell {
    background: #fff;
    border-radius: 20px;
    padding: 32px 26px 26px;
    box-shadow: 0 24px 60px rgba(0,0,0,0.35);
  }
  .form-shell::before {
    border-radius: 20px 20px 0 0;
  }
  .coil-art {
    right: 0;
    top: -2%;
    width: 150px;
    opacity: 0.65;
  }
  .big-drip { opacity: 0.14; }
  .form-title { font-size: 22px; }
  .footer-text { margin-top: 24px; }
}

/* 触屏设备无 Enter 键提示 */
@media (hover: none) {
  .hint-row { display: none; }
}
</style>
