<template>
  <div class="svg-donut-chart">
    <div class="donut-body">
      <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
        <g :transform="`translate(${size/2},${size/2})`">
          <path v-for="(seg, i) in segments" :key="i" :d="seg.path" :fill="seg.color" class="donut-seg" @mouseenter="activeIdx = i" @mouseleave="activeIdx = -1" />
          <!-- 中心文字 -->
          <text v-if="activeIdx >= 0" y="-6" text-anchor="middle" class="center-pct">{{ segments[activeIdx].pct }}%</text>
          <text v-if="activeIdx >= 0" y="14" text-anchor="middle" class="center-name">{{ truncate(segments[activeIdx].name) }}</text>
          <text v-if="activeIdx < 0" y="2" text-anchor="middle" class="center-total">{{ centerText || '总计' }}</text>
          <text v-if="activeIdx < 0" y="20" text-anchor="middle" class="center-val">{{ fmtVal(total) }}</text>
        </g>
      </svg>
      <!-- 图例 -->
      <div class="donut-legend">
        <div v-for="(seg, i) in segments" :key="i" class="legend-item" :class="{ active: activeIdx === i }" @mouseenter="activeIdx = i" @mouseleave="activeIdx = -1">
          <span class="legend-dot" :style="{ background: seg.color }"></span>
          <span class="legend-name">{{ seg.name }}</span>
          <span class="legend-val">{{ seg.pct }}%</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  size: { type: Number, default: 180 },
  colors: { type: Array, default: () => ['#7288a5', '#6f9a86', '#c2a069', '#b56a5c', '#9a8bb8', '#c08a9a', '#7aa39a', '#bd8355'] },
  centerText: { type: String, default: '' }
})

const activeIdx = ref(-1)
const innerR = 0.6

const total = computed(() => props.data.reduce((s, d) => s + (d.value || 0), 0))

const segments = computed(() => {
  const r = props.size / 2 - 4
  const ir = r * innerR
  let angle = -Math.PI / 2
  return props.data.map((d, i) => {
    const pct = total.value > 0 ? Math.round((d.value / total.value) * 1000) / 10 : 0
    const sweep = total.value > 0 ? (d.value / total.value) * Math.PI * 2 : 0
    const startAngle = angle
    const endAngle = angle + sweep
    angle = endAngle
    return {
      name: d.name || `项${i + 1}`,
      value: d.value,
      pct,
      color: props.colors[i % props.colors.length],
      path: arcPath(0, 0, r, ir, startAngle, endAngle)
    }
  })
})

function arcPath(cx, cy, outerR, innerRadius, start, end) {
  if (end - start >= Math.PI * 2 - 0.001) {
    end = start + Math.PI * 2 - 0.001
  }
  const largeArc = end - start > Math.PI ? 1 : 0
  const x1 = cx + outerR * Math.cos(start)
  const y1 = cy + outerR * Math.sin(start)
  const x2 = cx + outerR * Math.cos(end)
  const y2 = cy + outerR * Math.sin(end)
  const x3 = cx + innerRadius * Math.cos(end)
  const y3 = cy + innerRadius * Math.sin(end)
  const x4 = cx + innerRadius * Math.cos(start)
  const y4 = cy + innerRadius * Math.sin(start)
  return `M${x1},${y1} A${outerR},${outerR} 0 ${largeArc} 1 ${x2},${y2} L${x3},${y3} A${innerRadius},${innerRadius} 0 ${largeArc} 0 ${x4},${y4} Z`
}

function truncate(s) {
  if (!s) return ''
  return s.length > 6 ? s.slice(0, 6) + '…' : s
}

function fmtVal(v) {
  if (v >= 10000) return (v / 10000).toFixed(1) + 'w'
  return Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}
</script>

<style scoped>
.svg-donut-chart { width: 100%; }
.donut-body { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; justify-content: center; }
.donut-seg { cursor: pointer; transition: opacity 0.2s, transform 0.2s; transform-origin: center; }
.donut-seg:hover { opacity: 0.85; }
.center-pct { font-size: 18px; font-weight: 800; fill: var(--pims-text, #1f2937); }
.center-name { font-size: 11px; fill: var(--pims-text-secondary, #6b7280); }
.center-total { font-size: 11px; fill: var(--pims-text-secondary, #6b7280); }
.center-val { font-size: 14px; font-weight: 700; fill: var(--pims-text, #1f2937); }
.donut-legend { display: flex; flex-direction: column; gap: 6px; min-width: 120px; }
.legend-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--pims-text-secondary, #6b7280); padding: 3px 6px; border-radius: 6px; cursor: pointer; transition: background 0.15s; }
.legend-item.active { background: var(--pims-bg, #f8fafc); }
.legend-dot { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.legend-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.legend-val { font-weight: 600; color: var(--pims-text, #1f2937); }
@media (max-width: 480px) {
  .donut-body { flex-direction: column; }
  .donut-legend { flex-direction: row; flex-wrap: wrap; min-width: auto; }
}
</style>
