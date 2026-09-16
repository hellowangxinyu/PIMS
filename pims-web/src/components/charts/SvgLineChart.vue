<template>
  <div class="svg-line-chart" ref="containerRef">
    <svg :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`">
      <defs>
        <linearGradient v-for="(s, i) in seriesList" :key="'grad'+i" :id="'lineGrad'+uid+'_'+i" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="s.color" stop-opacity="0.25" />
          <stop offset="100%" :stop-color="s.color" stop-opacity="0.02" />
        </linearGradient>
      </defs>
      <!-- Y轴网格线 -->
      <g v-for="(tick, i) in yTicks" :key="'y'+i">
        <line :x1="pad.left" :y1="tick.y" :x2="width - pad.right" :y2="tick.y" stroke="var(--pims-border-light, #e5e7eb)" stroke-width="1" stroke-dasharray="3,3" />
        <text :x="pad.left - 8" :y="tick.y + 4" text-anchor="end" class="axis-label">{{ tick.label }}</text>
      </g>
      <!-- X轴标签 -->
      <text v-for="(lb, i) in labels" :key="'x'+i" :x="xPos(i)" :y="height - 6" text-anchor="middle" class="axis-label">{{ formatLabel(lb) }}</text>
      <!-- 面积填充 + 折线 -->
      <template v-for="(s, si) in seriesList" :key="'s'+si">
        <path :d="areaPath(s.values)" :fill="`url(#lineGrad${uid}_${si})`" />
        <path :d="linePath(s.values)" fill="none" :stroke="s.color" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
        <circle v-for="(v, i) in s.values" :key="'c'+si+'_'+i" :cx="xPos(i)" :cy="yPos(v)" r="3.5" :fill="s.color" stroke="#fff" stroke-width="1.5" class="data-dot" @mouseenter="showTip($event, i, si)" @mouseleave="tipVisible=false" />
      </template>
    </svg>
    <!-- 悬浮提示 -->
    <div v-if="tipVisible" class="chart-tip" :style="{ left: tipX + 'px', top: tipY + 'px' }">
      <div class="tip-title">{{ labels[tipIdx] }}</div>
      <div v-for="(s, i) in seriesList" :key="i" class="tip-row">
        <span class="tip-dot" :style="{ background: s.color }"></span>
        <span>{{ s.name }}: {{ fmtVal(s.values[tipIdx]) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  labels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 240 },
  colors: { type: Array, default: () => ['#7288a5', '#6f9a86', '#c2a069', '#b56a5c'] }
})

const uid = Math.random().toString(36).slice(2, 8)
const containerRef = ref(null)
const width = ref(500)
const pad = { top: 20, right: 20, bottom: 30, left: 55 }

const tipVisible = ref(false)
const tipX = ref(0)
const tipY = ref(0)
const tipIdx = ref(0)

const seriesList = computed(() => {
  return props.series.map((s, i) => ({
    name: s.name || `系列${i + 1}`,
    color: s.color || props.colors[i % props.colors.length],
    values: s.values || []
  }))
})

const maxVal = computed(() => {
  let max = 0
  seriesList.value.forEach(s => s.values.forEach(v => { if (v > max) max = v }))
  return max || 1
})

const yTicks = computed(() => {
  const ticks = []
  const step = niceStep(maxVal.value)
  for (let v = 0; v <= maxVal.value * 1.1; v += step) {
    ticks.push({ label: fmtAxis(v), y: yPos(v) })
  }
  return ticks.slice(0, 6)
})

function niceStep(max) {
  const rough = max / 4
  const mag = Math.pow(10, Math.floor(Math.log10(rough || 1)))
  const norm = rough / mag
  if (norm <= 1) return mag
  if (norm <= 2) return 2 * mag
  if (norm <= 5) return 5 * mag
  return 10 * mag
}

function xPos(i) {
  const n = props.labels.length
  if (n <= 1) return pad.left + (width.value - pad.left - pad.right) / 2
  return pad.left + (i / (n - 1)) * (width.value - pad.left - pad.right)
}

function yPos(v) {
  const h = props.height - pad.top - pad.bottom
  return pad.top + h - (v / (maxVal.value * 1.1)) * h
}

function linePath(values) {
  if (!values.length) return ''
  return values.map((v, i) => `${i === 0 ? 'M' : 'L'}${xPos(i)},${yPos(v)}`).join(' ')
}

function areaPath(values) {
  if (!values.length) return ''
  const base = props.height - pad.bottom
  let d = `M${xPos(0)},${base}`
  values.forEach((v, i) => { d += ` L${xPos(i)},${yPos(v)}` })
  d += ` L${xPos(values.length - 1)},${base} Z`
  return d
}

function formatLabel(lb) {
  if (!lb) return ''
  // 完整日期（如 2026-08-05）原样显示；带时间的长字符串才截成 月-日
  return lb.length > 10 ? lb.slice(5) : lb
}

function fmtAxis(v) {
  if (v >= 10000) return (v / 10000).toFixed(1) + 'w'
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return Math.round(v).toString()
}

function fmtVal(v) {
  if (v == null) return '-'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function showTip(e, i, si) {
  tipIdx.value = i
  tipVisible.value = true
  const rect = containerRef.value.getBoundingClientRect()
  tipX.value = Math.min(e.clientX - rect.left + 12, rect.width - 170)
  tipY.value = e.clientY - rect.top - 10
}

function resize() {
  if (containerRef.value) {
    const w = containerRef.value.clientWidth
    if (w > 0) width.value = w
  }
}

let resizeObserver = null
onMounted(() => {
  resize()
  window.addEventListener('resize', resize)
  // Dialog 内渲染时容器可能尚无宽度，用 ResizeObserver 监听
  if (containerRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => resize())
    resizeObserver.observe(containerRef.value)
  }
  // 延迟再测一次（兼容 Dialog 动画）
  setTimeout(resize, 350)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.svg-line-chart { position: relative; width: 100%; }
.svg-line-chart svg { display: block; width: 100%; height: auto; }
.axis-label { font-size: 11px; fill: var(--pims-text-secondary, #6b7280); }
.data-dot { cursor: pointer; transition: r 0.15s; }
.data-dot:hover { r: 5.5; }
.chart-tip { position: absolute; pointer-events: none; background: rgba(30,30,46,0.92); color: #fff; padding: 8px 12px; border-radius: 8px; font-size: 12px; white-space: nowrap; z-index: 10; transform: translateY(-100%); }
.tip-title { font-weight: 600; margin-bottom: 4px; }
.tip-row { display: flex; align-items: center; gap: 6px; line-height: 1.6; }
.tip-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
</style>
