/**
 * v8.0（P0-10）本地时区日期工具——替代 new Date().toISOString().slice(0,10/7)。
 * toISOString() 是 UTC：北京时间 00:00–08:00 之间会得到"昨天"，夜班录单/月末结账默认日期必错。
 * 全部默认日期请用 todayLocal()/monthLocal()（按浏览器本地时区取年月日）。
 */

/** 今天 yyyy-MM-dd（本地时区） */
export function todayLocal() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 当月 yyyy-MM（本地时区） */
export function monthLocal() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** N 天前的本地日期 yyyy-MM-dd（近30天快捷键等场景） */
export function daysAgoLocal(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}


/** 毫秒时间戳 → 本地日期 yyyy-MM-dd（替代 new Date(ms).toISOString()——UTC 坑） */
export function msToDateLocal(ms) {
  const d = new Date(Number(ms))
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Date 对象 → 本地日期 yyyy-MM-dd（替代 d.toISOString().slice(0,10)） */
export function dateToLocal(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** N 个月前的本月 yyyy-MM（折旧/工资默认期间用） */
export function monthsAgoLocal(n) {
  const d = new Date()
  d.setMonth(d.getMonth() - n)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
