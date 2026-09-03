import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import PTable from './components/PTable.js'

// 主题早期注入：在 Vue 挂载前应用，避免首屏闪默认色
try {
  const t = localStorage.getItem('pims-theme')
  if (t && t !== 'indigo') document.documentElement.setAttribute('data-theme', t)
} catch { /* 隐私模式等场景忽略 */ }

const app = createApp(App)
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.component('PTable', PTable) // 全局表格组件：el-table + 列宽拖拽持久化

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
