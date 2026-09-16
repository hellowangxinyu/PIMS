// v10.1 移动分支标识（高频页卡片视图用）：与 body.pims-mobile 同口径（≤768px）
// 桌面恒为 false——现有模板分支渲染路径与改动前完全一致
import { ref } from 'vue'

export const isMobile = ref(typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches)
