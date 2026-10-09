import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

/**
 * v6.4 最简 ESLint（仅止血增量污染，不追溯历史——按 UI 审查建议"新写/改动页面顺手收敛"）
 * vue/no- 走 recommended；规则全 warn 级（不阻断开发），仅两条硬错：未定义变量与 vue 模板语法错
 */
export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    rules: {
      'no-undef': 'error',
      'vue/no-parsing-error': 'error',
      // v12.0.3：空 catch 是项目一贯的"取消/坏数据兜底"有意设计（catch { return } 体系），降 warn 保留可见不阻断；
      // 不可见空白豁免字符串/模板内部——打印单据模板的全角空格排版（单号：xxx　　日期、批　号）是版式一部分，
      // 代码语法位置（真隐患）仍报错。自此 lint 全绿恢复"新错误一眼可见"。
      'no-empty': 'warn',
      'no-irregular-whitespace': ['warn', { skipStrings: true, skipTemplates: true }],
      'vue/multi-word-component-names': 'off',
      'no-unused-vars': 'warn',
      'vue/no-unused-vars': 'warn',
      'vue/no-v-html': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/attributes-order': 'off'
    }
  },
  { ignores: ['node_modules/**', 'dist/**', 'src/main/resources/**'] }
]
