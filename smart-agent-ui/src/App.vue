<template>
  <el-container class="app-container">
    <el-aside width="300px" class="sidebar">
      <div class="sidebar-header">
        <h3>Smart Agent</h3>
        <el-button type="primary" size="small" @click="newConversation">
          <el-icon><Plus /></el-icon> 新对话
        </el-button>
      </div>
      <div class="sidebar-user">
        <el-input v-model="userId" placeholder="输入用户ID" size="small" @change="loadHistory">
          <template #prepend>用户</template>
        </el-input>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: session.sessionId === currentSessionId }"
          @click="selectSession(session)"
        >
          <div class="session-title">{{ session.preview }}</div>
          <div class="session-time">{{ formatTime(session.time) }}</div>
        </div>
        <el-empty v-if="sessions.length === 0" description="暂无对话" :image-size="60" />
      </div>
    </el-aside>
    <el-main class="chat-main">
      <ChatPanel
        :messages="currentMessages"
        :loading="loading"
        :streaming-text="streamingText"
        :streaming-events="streamingEvents"
        @send="handleSend"
        @feedback="handleFeedback"
      />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getHistory, getConversation, chatStream, sendFeedback } from './api/agent.js'
import ChatPanel from './components/ChatPanel.vue'

const userId = ref('user001')
const sessions = ref([])
const currentSessionId = ref('')
const currentMessages = ref([])
const loading = ref(false)
const streamingText = ref('')
const streamingEvents = ref([])

function generateSessionId() {
  return crypto.randomUUID().replace(/-/g, '')
}

async function loadHistory() {
  try {
    const res = await getHistory(userId.value)
    const data = res.data.data || []
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

  try {
    const response = await chatStream(userId.value, currentSessionId.value, message)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          try {
            const data = JSON.parse(line.substring(5).trim())
            streamingEvents.value.push(data)
            if (data.content && data.content.trim()) {
              if (data.type === 'AGENT_RESULT' || data.isLast) {
                streamingText.value = data.content
              } else if (data.type === 'REASONING') {
                // reasoning events shown in event log
              } else {
                streamingText.value = data.content
              }
            }
          } catch (e) { /* skip malformed SSE */ }
        }
      }
    }

    const last = currentMessages.value[currentMessages.value.length - 1]
    if (last) {
      last.agentOutput = streamingText.value
      last.status = 2
    }
    loadHistory()
  } catch (e) {
    console.error('chatStream error', e)
    const last = currentMessages.value[currentMessages.value.length - 1]
    if (last) {
      last.agentOutput = '请求失败，请重试'
      last.status = -1
    }
  } finally {
    loading.value = false
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
    console.error('feedback error', e)
  }
}

onMounted(() => {
  loadHistory()
  newConversation()
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }
.app-container { height: 100vh; }
.sidebar {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e4e7ed;
}
.sidebar-header h3 { font-size: 16px; color: #303133; }
.sidebar-user { padding: 12px 16px; }
.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}
.session-item:hover { background: #e6e8eb; }
.session-item.active { background: #d9ecff; }
.session-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-time { font-size: 12px; color: #909399; margin-top: 4px; }
.chat-main { padding: 0; display: flex; flex-direction: column; }
</style>
