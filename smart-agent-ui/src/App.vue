<template>
  <div class="app-layout">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- Brand -->
      <div class="sidebar-brand">
        <div class="brand-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M12 2L2 7l10 5 10-5-10-5z" fill="currentColor" opacity="0.95"/>
            <path d="M2 17l10 5 10-5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/>
            <path d="M2 12l10 5 10-5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/>
          </svg>
          <div class="brand-glow"></div>
        </div>
        <div class="brand-text">
          <span class="brand-name">Smart Agent</span>
          <span class="brand-tag">AI Assistant</span>
        </div>
      </div>

      <!-- New chat button -->
      <div class="sidebar-new-chat">
        <button class="new-chat-btn" @click="newConversation">
          <el-icon><Plus /></el-icon>
          <span>新对话</span>
        </button>
      </div>

      <!-- User input -->
      <div class="sidebar-user">
        <div class="user-input-wrapper">
          <el-icon class="user-input-icon"><User /></el-icon>
          <input
            v-model="userId"
            class="dark-input"
            placeholder="用户 ID"
            @change="loadHistory"
          />
        </div>
      </div>

      <!-- Session list -->
      <div class="session-list-wrapper">
        <div class="session-list-label">历史对话</div>
        <div class="session-list">
          <template v-for="group in groupedSessions" :key="group.label">
            <div class="session-group-title">{{ group.label }}</div>
            <div
              v-for="session in group.items"
              :key="session.sessionId"
              class="session-item"
              :class="{ active: session.sessionId === currentSessionId }"
              @click="selectSession(session)"
            >
              <div class="session-item-inner">
                <el-icon class="session-icon"><ChatDotRound /></el-icon>
                <div class="session-info">
                  <div class="session-title">{{ session.preview }}</div>
                  <div class="session-time">{{ formatSessionTime(session.time) }}</div>
                </div>
              </div>
            </div>
          </template>
          <div v-if="sessions.length === 0" class="session-empty">
            <el-icon class="empty-icon"><ChatLineSquare /></el-icon>
            <span>暂无对话记录</span>
          </div>
        </div>
        <div class="session-list-fade"></div>
      </div>

      <!-- Sidebar footer -->
      <div class="sidebar-footer">
        <div class="footer-divider"></div>
        <div class="footer-row">
          <el-icon class="footer-icon"><Setting /></el-icon>
          <span class="footer-label">设置</span>
        </div>
        <div class="footer-version">Smart Agent v1.0.0</div>
      </div>
    </aside>

    <!-- Main chat area -->
    <main class="chat-main">
      <ChatPanel
        :messages="currentMessages"
        :loading="loading"
        :streaming-text="streamingText"
        :streaming-events="streamingEvents"
        :current-session-title="currentSessionTitle"
        @send="handleSend"
        @feedback="handleFeedback"
        @clear="newConversation"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistory, getConversation, chatStream, sendFeedback } from './api/agent.js'
import ChatPanel from './components/ChatPanel.vue'

const userId = ref('user001')
const sessions = ref([])
const currentSessionId = ref('')
const currentMessages = ref([])
const loading = ref(false)
const streamingText = ref('')
const streamingEvents = ref([])
const sidebarCollapsed = ref(false)

const currentSessionTitle = computed(() => {
  if (!currentSessionId.value) return ''
  const s = sessions.value.find(s => s.sessionId === currentSessionId.value)
  return s ? s.preview : ''
})

function generateSessionId() {
  return crypto.randomUUID().replace(/-/g, '')
}

function formatSessionTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diffDays = Math.floor((today - target) / 86400000)
  const timeStr = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  if (diffDays === 0) return timeStr
  if (diffDays === 1) return `昨天 ${timeStr}`
  if (diffDays < 7) return `${diffDays}天前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function getSessionGroupLabel(time) {
  if (!time) return '其他'
  const d = new Date(time)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diffDays = Math.floor((today - target) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return '近 7 天'
  if (diffDays < 30) return '近 30 天'
  return '更早'
}

const groupedSessions = computed(() => {
  const groups = new Map()
  sessions.value.forEach(s => {
    const label = getSessionGroupLabel(s.time)
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label).push(s)
  })
  const order = ['今天', '昨天', '近 7 天', '近 30 天', '更早', '其他']
  const result = []
  order.forEach(label => {
    if (groups.has(label)) {
      result.push({ label, items: groups.get(label) })
    }
  })
  return result
})

async function loadHistory() {
  try {
    const res = await getHistory(userId.value)
    const page = res.data.data || {}
    const data = page.records || []
    const sessionMap = new Map()
    data.forEach(m => {
      if (!sessionMap.has(m.sessionId)) {
        sessionMap.set(m.sessionId, {
          sessionId: m.sessionId,
          preview: m.userInput ? m.userInput.substring(0, 30) : '新对话',
          time: m.gmtCreate
        })
      }
    })
    sessions.value = Array.from(sessionMap.values())
  } catch (e) {
    console.error('loadHistory error', e)
  }
}

async function selectSession(session) {
  currentSessionId.value = session.sessionId
  streamingText.value = ''
  streamingEvents.value = []
  try {
    const res = await getConversation(userId.value, session.sessionId)
    currentMessages.value = (res.data.data || []).map(m => ({
      id: m.id,
      role: 'pair',
      userInput: m.userInput,
      agentOutput: m.agentOutput,
      status: m.status,
      feedbackType: m.feedbackType,
      time: m.gmtCreate
    }))
  } catch (e) {
    console.error('selectSession error', e)
  }
}

function newConversation() {
  currentSessionId.value = generateSessionId()
  currentMessages.value = []
  streamingText.value = ''
  streamingEvents.value = []
}

async function handleSend(message) {
  if (!message.trim()) return
  if (!currentSessionId.value) {
    currentSessionId.value = generateSessionId()
  }

  currentMessages.value.push({
    id: Date.now(),
    role: 'pair',
    userInput: message,
    agentOutput: null,
    status: 1,
    time: new Date()
  })

  loading.value = true
  streamingText.value = ''
  streamingEvents.value = []
  const seenToolCallIds = new Set()

  try {
    const response = await chatStream(userId.value, currentSessionId.value, message)
    if (!response.ok) {
      throw new Error(`请求失败 (HTTP ${response.status})`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    const processLines = (text) => {
      for (const line of text.split('\n')) {
        if (!line.startsWith('data:')) continue
        try {
          const data = JSON.parse(line.substring(5).trim())
          handleStreamEvent(data, seenToolCallIds)
        } catch (e) { /* skip malformed SSE */ }
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      processLines(lines.join('\n'))
    }

    if (buffer.trim()) {
      processLines(buffer)
    }

    const last = currentMessages.value[currentMessages.value.length - 1]
    if (last) {
      last.agentOutput = streamingText.value
      last.status = 2
    }
    loadHistory()
  } catch (e) {
    console.error('chatStream error', e)
    ElMessage.error(e.message || '请求失败，请重试')
    const last = currentMessages.value[currentMessages.value.length - 1]
    if (last) {
      last.agentOutput = '请求失败，请重试'
      last.status = -1
    }
  } finally {
    loading.value = false
  }
}

function handleStreamEvent(data, seenToolCallIds) {
  if (!data.content || !data.content.trim()) return

  if (data.type === 'AGENT_RESULT' && !data.content.startsWith('{')) {
    streamingText.value = data.content
    return
  }

  if (data.type === 'TOOL_RESULT' && data.content.startsWith('{')) {
    try {
      const nested = JSON.parse(data.content)
      handleNestedEvent(nested, data.agentName, seenToolCallIds)
      return
    } catch { /* not JSON, fall through */ }
  }

  if (data.type === 'TOOL_RESULT' && data.isLast) {
    streamingText.value = data.content
    return
  }
}

function handleNestedEvent(nested, agentName, seenToolCallIds) {
  const blocks = nested.message?.content || []

  for (const block of blocks) {
    if (block.type === 'tool_use' && block.name && block.name !== '__fragment__') {
      if (block.id && seenToolCallIds.has(block.id)) continue
      if (block.id) seenToolCallIds.add(block.id)
      streamingEvents.value.push({
        type: 'TOOL_CALL',
        agentName,
        content: `调用工具: ${block.name}`
      })
    }
    if (block.type === 'tool_result' && block.output) {
      const text = block.output.filter(o => o.type === 'text').map(o => o.text).join('')
      if (text) {
        streamingEvents.value.push({
          type: 'TOOL_RESULT',
          agentName,
          content: text.substring(0, 300)
        })
      }
    }
  }

  if (nested.type === 'REASONING') {
    const textBlock = blocks.find(b => b.type === 'text')
    if (textBlock?.text) {
      if (nested.isLast) {
        streamingText.value = textBlock.text
      } else {
        streamingText.value += textBlock.text
      }
    }
  }

  if (nested.type === 'AGENT_RESULT') {
    const textBlock = blocks.find(b => b.type === 'text')
    if (textBlock?.text) {
      streamingText.value = textBlock.text
      streamingEvents.value.push({
        type: 'AGENT_RESULT',
        agentName,
        content: textBlock.text.substring(0, 200)
      })
    }
  }
}

async function handleFeedback({ messageId, action, currentStatus }) {
  try {
    const res = await sendFeedback(messageId, action, currentStatus)
    const newStatus = res.data.data
    const msg = currentMessages.value.find(m => m.id === messageId)
    if (msg) {
      msg.feedbackType = newStatus === 'like' ? 1 : newStatus === 'dislike' ? 2 : 0
    }
  } catch (e) {
    ElMessage.error('反馈提交失败')
  }
}

onMounted(() => {
  loadHistory()
  newConversation()
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ========== Sidebar ========== */
.sidebar {
  width: var(--sidebar-width, 290px);
  background: var(--sidebar-bg, #0f0f23);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

/* Brand */
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 20px 18px;
}

.brand-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--brand-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  position: relative;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.brand-glow {
  position: absolute;
  inset: -2px;
  border-radius: 16px;
  background: var(--brand-gradient);
  opacity: 0.3;
  filter: blur(8px);
  z-index: -1;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.brand-name {
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.3px;
  line-height: 1.2;
}

.brand-tag {
  font-size: 11px;
  color: var(--sidebar-text-muted);
  font-weight: 500;
  letter-spacing: 0.3px;
  text-transform: uppercase;
}

/* New chat button */
.sidebar-new-chat {
  padding: 0 14px 12px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 0;
  border: none;
  border-radius: var(--radius-md);
  background: var(--brand-gradient);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast),
    filter var(--transition-fast);
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
  filter: brightness(1.1);
}

.new-chat-btn:active {
  transform: scale(0.97);
}

/* User input */
.sidebar-user {
  padding: 0 14px 14px;
}

.user-input-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-sm);
  padding: 0 12px;
  transition: border-color var(--transition-normal), background var(--transition-normal);
}

.user-input-wrapper:focus-within {
  border-color: var(--brand-primary-light);
  background: rgba(255, 255, 255, 0.08);
}

.user-input-icon {
  color: var(--sidebar-text-muted);
  font-size: 14px;
  flex-shrink: 0;
}

.dark-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--sidebar-text);
  font-size: 13px;
  padding: 10px 0;
  font-family: inherit;
}

.dark-input::placeholder {
  color: var(--sidebar-text-muted);
}

/* Session list */
.session-list-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

.session-list-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--sidebar-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  padding: 6px 20px 10px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.session-group-title {
  font-size: 10px;
  font-weight: 600;
  color: var(--sidebar-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 12px 12px 6px;
  opacity: 0.7;
}

.session-group-title:first-child {
  padding-top: 4px;
}

.session-item {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: 2px;
  position: relative;
  transition: background var(--transition-normal), border-color var(--transition-normal);
  border-left: 3px solid transparent;
}

.session-item:hover {
  background: var(--sidebar-hover);
}

.session-item.active {
  background: var(--sidebar-active);
  border-left-color: var(--sidebar-active-border);
}

.session-item-inner {
  display: flex;
  align-items: center;
  gap: 10px;
}

.session-icon {
  font-size: 15px;
  color: var(--sidebar-text-muted);
  flex-shrink: 0;
  opacity: 0.6;
  transition: color var(--transition-normal), opacity var(--transition-normal);
}

.session-item:hover .session-icon {
  opacity: 0.9;
}

.session-item.active .session-icon {
  color: var(--brand-primary-light);
  opacity: 1;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  color: var(--sidebar-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
  font-weight: 500;
}

.session-time {
  font-size: 11px;
  color: var(--sidebar-text-muted);
  margin-top: 3px;
  opacity: 0.8;
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 0;
  color: var(--sidebar-text-muted);
  font-size: 13px;
}

.empty-icon {
  font-size: 32px;
  opacity: 0.3;
}

/* Fade gradient at bottom of session list */
.session-list-fade {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 48px;
  background: linear-gradient(to top, var(--sidebar-bg) 0%, transparent 100%);
  pointer-events: none;
}

/* Sidebar footer */
.sidebar-footer {
  padding: 12px 16px 16px;
}

.footer-divider {
  height: 1px;
  background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.1), transparent);
  margin-bottom: 12px;
}

.footer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-normal);
  color: var(--sidebar-text-muted);
  font-size: 13px;
}

.footer-row:hover {
  background: var(--sidebar-hover);
  color: var(--sidebar-text);
}

.footer-icon {
  font-size: 15px;
}

.footer-label {
  font-weight: 500;
}

.footer-version {
  font-size: 10px;
  color: var(--sidebar-text-muted);
  text-align: center;
  margin-top: 8px;
  opacity: 0.5;
  letter-spacing: 0.3px;
}

/* ========== Main chat area ========== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--chat-bg);
}
</style>
