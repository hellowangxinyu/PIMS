/* global __APP_VERSION__ */
/**
 * 应用版本：vite build 时由 vite.config.js 的 appVersion() 注入（git 版本前缀 + 短 hash + 脏标记）。
 * vitest/未注入环境回退 'dev'。
 */
export const APP_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev'
