<template>
  <div class="chat-panel">
    <div class="messages-container" ref="messagesContainer">
      <div v-for="msg in messages" :key="msg.id" class="message-pair">
        <!-- 用户消息 -->
        <div class="message user-message">
          <div class="message-avatar user-avatar">U</div>
          <div class="message-body">
            <div class="message-content user-content">{{ msg.userInput }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>
        <!-- Agent 回复 -->
        <div class="message agent-message" v-if="msg.agentOutput">
          <div class="message-avatar agent-avatar">A</div>
          <div class="message-body">
            <div class="message-content agent-content" v-html="renderMarkdown(msg.agentOutput)"></div>
            <div class="message-footer">
              <span class="message-time">{{ formatTime(msg.time) }}</span>
              <div class="feedback-actions" v-if="msg.id && msg.status === 2">
                <el-button
                  :type="msg.feedbackType === 1 ? 'primary' : 'default'"
                  size="small"
                  circle
                  @click="$emit('feedback', { messageId: msg.id, action: 'like', currentStatus: feedbackStatus(msg) })"
                >
                  <el-icon><TopRight /></el-icon>
                </el-button>
                <el-button
                  :type="msg.feedbackType === 2 ? 'danger' : 'default'"
                  size="small"
                  circle
                  @click="$emit('feedback', { messageId: msg.id, action: 'dislike', currentStatus: feedbackStatus(msg) })"
                >
                  <el-icon><BottomRight /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>
        <!-- Loading / Streaming -->
        <div class="message agent-message" v-else-if="msg.status === 1">
          <div class="message-avatar agent-avatar">A</div>
          <div class="message-body">
            <div class="message-content agent-content" v-if="streamingText">
              <div v-html="renderMarkdown(streamingText)"></div>
            </div>
            <div class="message-content agent-content thinking" v-else>
              <el-icon class="is-loading"><Loading /></el-icon> 正在思考...
            </div>
            <!-- 推理过程/工具调用展示 -->
            <div class="events-panel" v-if="streamingEvents.length > 0">
              <el-collapse>
                <el-collapse-item title="推理过程 / 工具调用">
                  <div v-for="(evt, idx) in streamingEvents" :key="idx" class="event-item">
                    <el-tag size="small" :type="eventTagType(evt.type)" class="event-type">{{ evt.type }}</el-tag>
                    <span class="event-agent" v-if="evt.agentName">{{ evt.agentName }}</span>
                    <div class="event-content" v-if="evt.content">{{ evt.content.substring(0, 200) }}</div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="input-area">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="输入消息，按 Enter 发送..."
        :disabled="loading"
        @keydown.enter.exact.prevent="send"
        resize="none"
      />
      <el-button type="primary" :loading="loading" @click="send" class="send-btn">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  streamingText: { type: String, default: '' },
  streamingEvents: { type: Array, default: () => [] }
})

const emit = defineEmits(['send', 'feedback'])

const inputText = ref('')
const messagesContainer = ref(null)

function send() {
  if (!inputText.value.trim() || props.loading) return
  emit('send', inputText.value)
  inputText.value = ''
}

function renderMarkdown(text) {
  if (!text) return ''
  try {
    return marked(text, { breaks: true })
  } catch {
    return text
  }
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleString('zh-CN', { hour12: false })
}

function feedbackStatus(msg) {
  if (msg.feedbackType === 1) return 'like'
  if (msg.feedbackType === 2) return 'dislike'
  return 'none'
}

function eventTagType(type) {
  switch (type) {
    case 'REASONING': return 'warning'
    case 'TOOL_RESULT': return 'success'
    case 'AGENT_RESULT': return 'primary'
    default: return 'info'
  }
}

watch(() => props.messages.length, () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
})

watch(() => props.streamingText, () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
})
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.message-pair {
  margin-bottom: 24px;
}
.message {
  display: flex;
  margin-bottom: 12px;
}
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  flex-shrink: 0;
  margin-right: 12px;
}
.user-avatar { background: #409eff; color: white; }
.agent-avatar { background: #67c23a; color: white; }
.message-body { flex: 1; min-width: 0; }
.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.user-content {
  background: #ecf5ff;
  color: #303133;
  display: inline-block;
}
.agent-content {
  background: #f5f7fa;
  color: #303133;
}
.agent-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.agent-content :deep(th),
.agent-content :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 8px 12px;
  text-align: left;
}
.agent-content :deep(th) { background: #f2f6fc; }
.agent-content :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}
.agent-content :deep(code) {
  font-family: 'Menlo', 'Monaco', monospace;
  font-size: 13px;
}
.thinking {
  color: #909399;
  font-style: italic;
}
.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.message-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.feedback-actions { display: flex; gap: 4px; }
.events-panel {
  margin-top: 8px;
  font-size: 13px;
}
.event-item {
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
}
.event-type { flex-shrink: 0; }
.event-agent { color: #409eff; font-weight: 500; }
.event-content {
  color: #606266;
  font-size: 12px;
  word-break: break-all;
  flex: 1;
  min-width: 0;
}
.input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: white;
}
.input-area :deep(.el-textarea__inner) {
  font-size: 14px;
}
.send-btn { height: 54px; }
</style>
