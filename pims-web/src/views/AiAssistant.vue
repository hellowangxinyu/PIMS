<template>
  <div class="page-container ai-page">
    <div class="page-header">
      <h2>AI 智能助手</h2>
      <div class="header-actions">
        <el-button link type="primary" @click="goSettings">AI 设置</el-button>
      </div>
    </div>

    <!-- 对话卡片 -->
    <div class="table-card chat-card">
      <div class="chat-head">
        <span>对话窗口（AI 可读取全库数据做统计与分析）</span>
        <el-button link type="primary" @click="newChat">新对话</el-button>
      </div>
      <div class="chat-body" ref="chatBodyRef">
        <div v-if="!messages.length && !sending" class="chat-empty">
          <template v-if="!ready">
            <p>🤖 我是 PIMS 智能助手。</p>
            <p>AI 尚未配置或未启用，请先到「<el-link type="primary" @click="goSettings">系统设置 → AI 设置</el-link>」填写接口配置。</p>
          </template>
          <template v-else>
            <p>🤖 我是 PIMS 智能助手，可以帮你查询统计、分析业务数据。</p>
            <p class="chat-sugs">
              <el-tag v-for="s in suggestions" :key="s" class="sug" @click="quickAsk(s)">{{ s }}</el-tag>
            </p>
          </template>
        </div>
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="msg-bubble">{{ m.content }}</div>
        </div>
        <div v-if="sending" class="msg-row assistant">
          <div class="msg-bubble thinking">AI 思考中（可能需要一点时间）…</div>
        </div>
      </div>
      <div class="chat-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入问题，如：各仓库库存排行 / 上月销售总额"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="sending" @click="send" class="send-btn">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const messages = ref(loadMessages())
const input = ref('')
const sending = ref(false)
const ready = ref(true)
const chatBodyRef = ref(null)

const suggestions = ['上个月销售总额是多少？', '当前各仓库库存排行', '本月质检合格率如何？', '应收账款超过 60 天的客户有哪些？']

const STORAGE_KEY = 'pims-ai-messages'

// 对话记录持久化到 localStorage：切换页面/刷新后回来不丢失
function loadMessages() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]') } catch { return [] }
}
function saveMessages() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value)) } catch {}
}
function pushMsg(role, content) {
  messages.value.push({ role, content })
  saveMessages()
}

function goSettings() {
  router.push('/ai-settings')
}

async function quickAsk(s) {
  input.value = s
  send()
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  pushMsg('user', text)
  input.value = ''
  sending.value = true
  scrollToBottom()
  try {
    const r = await api.post('/ai/chat', { messages: messages.value }, { timeout: 120000 })
    pushMsg('assistant', r.reply)
  } catch (e) {
    pushMsg('assistant', '⚠️ ' + (e.message || '请求失败，请稍后重试'))
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function newChat() {
  messages.value = []
  try { localStorage.removeItem(STORAGE_KEY) } catch {}
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatBodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(async () => {
  // 检查 AI 是否已配置（未配置时给出引导提示）
  try {
    const c = await api.get('/ai/config')
    ready.value = c.enabled === 'true' && !!(c.baseUrl && c.apiKey && c.model)
  } catch { ready.value = false }
  scrollToBottom()
})
</script>

<style scoped>
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 180px);
  min-height: 340px;
}
.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0 12px;
  font-weight: 600;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  background: var(--pims-bg);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-size: 14px;
}
.msg-row.user .msg-bubble {
  background: var(--pims-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-row.assistant .msg-bubble {
  background: var(--pims-card-bg);
  color: var(--el-text-color-primary);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-bottom-left-radius: 4px;
}
.msg-bubble.thinking {
  color: #94a3b8;
  font-style: italic;
}
.chat-empty {
  margin: auto;
  text-align: center;
  color: #94a3b8;
  line-height: 2;
}
.chat-sugs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 8px;
}
.chat-sugs .sug {
  cursor: pointer;
}
.chat-input {
  display: flex;
  gap: 12px;
  padding-top: 12px;
  align-items: flex-end;
}
.chat-input .el-textarea {
  flex: 1;
}
.send-btn {
  height: 52px;
}

@media (max-width: 700px) {
  .chat-card {
    height: calc(100vh - 260px);
    min-height: 280px;
  }
  .msg-bubble {
    max-width: 92%;
  }
}
</style>
