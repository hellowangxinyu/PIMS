import { ref } from 'vue'

// 八套主题：4 套纯配色 + 4 套质感风格（液态玻璃/3D 拟物/软糯黏土/复古像素，规则见 style.css 对应段）
export const THEMES = [
  { id: 'indigo', name: '黛青 · 素雅', primary: '#4a7c74', light: '#7da59d', dark: '#3a655e' },
  { id: 'emerald', name: '翡翠 · 松涛', primary: '#10b981', light: '#34d399', dark: '#059669' },
  { id: 'amber', name: '琥珀 · 暖阳', primary: '#d97706', light: '#f59e0b', dark: '#b45309' },
  { id: 'violet', name: '绛紫 · 晨曦', primary: '#8b5cf6', light: '#a78bfa', dark: '#7c3aed' },
  { id: 'glass', name: '液态玻璃', primary: '#0ea5e9', light: '#38bdf8', dark: '#0284c7' },
  { id: 'skeuo', name: '3D 拟物', primary: '#ea580c', light: '#f97316', dark: '#c2410c' },
  { id: 'clay', name: '软糯黏土', primary: '#d65a81', light: '#ef8fab', dark: '#b83e62' },
  { id: 'pixel', name: '复古像素', primary: '#0f766e', light: '#14b8a6', dark: '#115e59' }
]

const current = ref(localStorage.getItem('pims-theme') || 'indigo')

export function useTheme() {
  function setTheme(id) {
    current.value = id
    localStorage.setItem('pims-theme', id)
    if (id === 'indigo') document.documentElement.removeAttribute('data-theme')
    else document.documentElement.setAttribute('data-theme', id)
  }

  // 悬停即时预览，离开未确认则还原
  function previewTheme(id) {
    if (id === 'indigo') document.documentElement.removeAttribute('data-theme')
    else document.documentElement.setAttribute('data-theme', id)
  }
  function cancelPreview() {
    previewTheme(current.value)
  }

  return { themes: THEMES, current, setTheme, previewTheme, cancelPreview }
}
