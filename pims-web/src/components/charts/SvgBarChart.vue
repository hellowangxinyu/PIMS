<template>
  <div class="svg-bar-chart" ref="containerRef">
    <svg :width="width" :height="chartHeight" :viewBox="`0 0 ${width} ${chartHeight}`">
      <!-- 横向柱状图 -->
      <template v-if="horizontal">
        <g v-for="(item, i) in data" :key="i">
          <text :x="labelW - 6" :y="barY(i) + barH / 2 + 4" text-anchor="end" class="axis-label bar-label">{{ truncate(item.name) }}</text>
          <rect :x="labelW" :y="barY(i)" :width="barWidth(item.value)" :height="barH" :rx="barH / 2" :fill="getColor(i)" class="bar-rect" />
          <text :x="labelW + barWidth(item.value) + 8" :y="barY(i) + barH / 2 + 4" class="axis-label val-label">{{ fmtVal(item.value) }}</text>
        </g>
      </template>
      <!-- 纵向柱状图 -->
      <template v-else>
        <g v-for="(item, i) in data" :key="i">
          <rect :x="vBarX(i)" :y="vBarY(item.value)" :width="vBarW" :height="vBarHeight(item.value)" :rx="3" :fill="getColor(i)" class="bar-rect" />
          <text :x="vBarX(i) + vBarW / 2" :y="chartHeight - 6" text-anchor="middle" class="axis-label">{{ truncate(item.name, 4) }}</text>
          <text :x="vBarX(i) + vBarW / 2" :y="vBarY(item.value) - 5" text-anchor="middle" class="axis-label val-label">{{ fmtVal(item.value) }}</text>
        </g>
      </template>
    </svg>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  horizontal: { type: Boolean, default: true },
  height: { type: Number, default: 260 },
  color: { type: String, default: '#7288a5' },
  colors: { type: Array, default: () => ['#7288a5', '#9a8bb8', '#b3a8cc', '#c9c2dd', '#8d9cc0', '#7288a5', '#9a8bb8', '#b3a8cc', '#c9c2dd', '#8d9cc0'] }
})

const containerRef = ref(null)
const width = ref(500)

const labelW = 90
const barH = 22
const barGap = 12

const chartHeight = computed(() => {
  if (props.horizontal) {
    return Math.max(props.data.length * (barH + barGap) + 10, 60)
  }
  return props.height
})

const maxVal = computed(() => {
  let max = 0
  props.data.forEach(d => { if (d.value > max) max = d.value })
  return max || 1
})

// 横向
function barY(i) { return 8 + i * (barH + barGap) }
function barWidth(v) {
  const maxW = width.value - labelW - 70
  return Math.max((v / maxVal.value) * maxW, 2)
}

// 纵向
const vPad = { top: 25, bottom: 28, left: 10, right: 10 }
const vBarW = computed(() => {
  const n = props.data.length || 1
  const avail = width.value - vPad.left - vPad.right
  return Math.min(avail / n * 0.6, 40)
})
function vBarX(i) {
  const n = props.data.length || 1
  const avail = width.value - vPad.left - vPad.right
  const slot = avail / n
  return vPad.left + slot * i + (slot - vBarW.value) / 2
}
function vBarY(v) {
  const h = chartHeight.value - vPad.top - vPad.bottom
  return vPad.top + h - (v / maxVal.value) * h
}
function vBarHeight(v) {
  const h = chartHeight.value - vPad.top - vPad.bottom
  return Math.max((v / maxVal.value) * h, 2)
}

function getColor(i) {
  if (props.data.length <= 1) return props.color
  return props.colors[i % props.colors.length]
}

function truncate(s, max = 8) {
  if (!s) return ''
  return s.length > max ? s.slice(0, max) + '…' : s
}

function fmtVal(v) {
  if (v == null) return '-'
  if (v >= 10000) return (v / 10000).toFixed(1) + 'w'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

function resize() {
  if (containerRef.value) width.value = containerRef.value.clientWidth
}

onMounted(() => { resize(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => window.removeEventListener('resize', resize))
</script>

<style scoped>
.svg-bar-chart { width: 100%; }
.svg-bar-chart svg { display: block; width: 100%; height: auto; }
.axis-label { font-size: 11px; fill: var(--pims-text-secondary, #6b7280); }
.bar-label { font-size: 12px; fill: var(--pims-text, #1f2937); font-weight: 500; }
.val-label { font-size: 11px; fill: var(--pims-text-secondary, #6b7280); font-weight: 600; }
.bar-rect { transition: opacity 0.2s; }
.bar-rect:hover { opacity: 0.8; }
</style>
