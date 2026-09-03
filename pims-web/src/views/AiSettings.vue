<template>
  <div class="page-container">
    <div class="page-header">
      <h2>AI 设置</h2>
      <div class="header-actions">
        <el-button :loading="testing" @click="testConn">测试连接</el-button>
        <el-button type="primary" @click="save">保存配置</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-form :model="cfg" label-width="100px" class="ai-form">
        <el-form-item label="接口协议">
          <el-radio-group v-model="cfg.protocol">
            <el-radio value="openai">OpenAI 兼容</el-radio>
            <el-radio value="anthropic">Anthropic 兼容</el-radio>
          </el-radio-group>
          <div class="form-tip">Anthropic 兼容适用于 MiniMax 的 /anthropic 端点等</div>
        </el-form-item>

        <el-form-item label="接口地址">
          <el-input v-model="cfg.baseUrl" clearable
            :placeholder="cfg.protocol === 'anthropic'
              ? '如 https://api.minimaxi.com/anthropic'
              : '如 https://api.minimaxi.com/v1'" />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input v-model="cfg.apiKey" type="password" show-password placeholder="sk-..." clearable />
        </el-form-item>

        <el-form-item label="模型名称">
          <el-input v-model="cfg.model" placeholder="如 MiniMax-M3 / deepseek-chat / qwen-plus" clearable />
        </el-form-item>

        <el-form-item label="思考强度">
          <el-select v-model="cfg.thinking" style="width: 220px">
            <el-option label="关闭（回复快）" value="off" />
            <el-option label="自动（推荐）" value="adaptive" />
            <el-option label="深度思考（更准确）" value="deep" />
          </el-select>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="cfg.enabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const cfg = ref({ baseUrl: '', apiKey: '', model: '', enabled: false, protocol: 'openai', thinking: 'adaptive' })
const testing = ref(false)

async function loadConfig() {
  cfg.value = await api.get('/ai/config')
  // 后端 enabled 为字符串 "true"/"false"，el-switch 需要布尔值
  cfg.value.enabled = cfg.value.enabled === 'true'
}

async function save() {
  await api.put('/ai/config', cfg.value)
  ElMessage.success('配置已保存')
}

async function testConn() {
  testing.value = true
  try {
    const r = await api.post('/ai/test', {}, { timeout: 60000 })
    ElMessage.success(`连接成功（模型：${r.model || '未知'}）`)
  } catch (e) {
    // 错误提示由拦截器弹出
  } finally {
    testing.value = false
  }
}

onMounted(async () => {
  try { await loadConfig() } catch {}
})
</script>

<style scoped>
.ai-form {
  max-width: min(720px, 100%);
  margin: 0 auto;
}
.form-tip {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}
</style>
