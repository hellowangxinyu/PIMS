import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { Aim, AlarmClock, ArrowDown, Avatar, Bell, Box, Brush, Calendar, Check, Checked, CircleCheck, Close, Coin, Collection, Connection, CreditCard, DataAnalysis, DataLine, Document, Download, Expand, Files, Folder, FolderOpened, Grid, Histogram, HomeFilled, House, Key, Link, List, Loading, MagicStick, Memo, Money, Notebook, Odometer, OfficeBuilding, Operation, PieChart, Position, PriceTag, RefreshLeft, Search, SetUp, Setting, ShoppingCart, ShoppingTrolley, Stamp, Stopwatch, Suitcase, Ticket, Tickets, TrendCharts, Upload, User, Van, Wallet, WalletFilled, Warning } from '@element-plus/icons-vue'
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

// v7.1 按需注册（60 个实际使用图标；原全量 ~290 个，配合 vendor 分包）
for (const icon of [Aim, AlarmClock, ArrowDown, Avatar, Bell, Box, Brush, Calendar, Check, Checked, CircleCheck, Close, Coin, Collection, Connection, CreditCard, DataAnalysis, DataLine, Document, Download, Expand, Files, Folder, FolderOpened, Grid, Histogram, HomeFilled, House, Key, Link, List, Loading, MagicStick, Memo, Money, Notebook, Odometer, OfficeBuilding, Operation, PieChart, Position, PriceTag, RefreshLeft, Search, SetUp, Setting, ShoppingCart, ShoppingTrolley, Stamp, Stopwatch, Suitcase, Ticket, Tickets, TrendCharts, Upload, User, Van, Wallet, WalletFilled, Warning]) {
  const comp = icon
  app.component(comp.name, comp)
}

app.mount('#app')
