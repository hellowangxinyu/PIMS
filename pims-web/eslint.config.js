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
      'vue/multi-word-component-names': 'off',
      'no-unused-vars': 'warn',
      'vue/no-v-html': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/attributes-order': 'off'
    }
  },
  { ignores: ['node_modules/**', 'dist/**', 'src/main/resources/**'] }
]
