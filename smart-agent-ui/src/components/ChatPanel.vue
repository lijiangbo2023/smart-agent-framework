<template>
  <div class="chat-panel">
    <!-- Top Header -->
    <div class="chat-header">
      <div class="header-left">
        <div class="header-title" v-if="currentSessionTitle">{{ currentSessionTitle }}</div>
        <div class="header-title placeholder" v-else>新对话</div>
      </div>
      <div class="header-right">
        <button class="header-btn" @click="$emit('clear')" title="清空对话">
          <el-icon><Delete /></el-icon>
          <span>清空</span>
        </button>
      </div>
    </div>

    <!-- Messages area -->
    <div class="messages-container" ref="messagesContainer">

      <!-- Empty / Welcome state -->
      <div v-if="messages.length === 0 && !loading" class="welcome-state">
        <div class="welcome-brand">
          <div class="welcome-icon-wrapper">
            <div class="welcome-icon-ring"></div>
            <div class="welcome-icon-inner">
              <svg width="44" height="44" viewBox="0 0 24 24" fill="none">
                <path d="M12 2L2 7l10 5 10-5-10-5z" fill="currentColor" opacity="0.95"/>
                <path d="M2 17l10 5 10-5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/>
                <path d="M2 12l10 5 10-5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/>
              </svg>
            </div>
          </div>
        </div>
        <h2 class="welcome-title">你好，我是 <span class="gradient-text">Smart Agent</span></h2>
        <p class="welcome-desc">你的智能 AI 助手，可以回答问题、分析数据、调用工具</p>
        <div class="welcome-suggestions">
          <div
            class="suggestion-card"
            v-for="(item, i) in suggestionItems"
            :key="i"
            @click="fillSuggestion(item.text)"
          >
            <div class="suggestion-icon-wrap" :style="{ background: item.bg }">
              <el-icon class="suggestion-icon"><component :is="item.icon" /></el-icon>
            </div>
            <div class="suggestion-text">
              <div class="suggestion-label">{{ item.label }}</div>
              <div class="suggestion-desc">{{ item.text }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Message pairs -->
      <div v-for="msg in messages" :key="msg.id" class="message-pair">
        <!-- User message -->
        <div class="message user-message">
          <div class="message-body body-right">
            <div class="bubble user-bubble">{{ msg.userInput }}</div>
            <div class="message-time time-right">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <span>U</span>
          </div>
        </div>

        <!-- Agent reply -->
        <div class="message agent-message" v-if="msg.agentOutput">
          <div class="avatar agent-avatar">
            <span>AI</span>
          </div>
          <div class="message-body body-left">
            <div
              class="bubble agent-bubble"
              :class="{ collapsed: isLongMessage(msg.agentOutput) && !msg._expanded }"
              v-html="renderMarkdown(msg.agentOutput)"
            ></div>
            <button
              v-if="isLongMessage(msg.agentOutput)"
              class="expand-toggle"
              @click="msg._expanded = !msg._expanded"
            >
              {{ msg._expanded ? '收起' : '展开全部' }}
              <el-icon><ArrowUp v-if="msg._expanded" /><ArrowDown v-else /></el-icon>
            </button>
            <div class="message-footer">
              <span class="message-time">{{ formatTime(msg.time) }}</span>
              <div class="feedback-actions" v-if="msg.id && msg.status === 2">
                <button
                  class="fb-btn"
                  :class="{ active: msg.feedbackType === 1, like: msg.feedbackType === 1 }"
                  @click="$emit('feedback', { messageId: msg.id, action: 'like', currentStatus: feedbackStatus(msg) })"
                  title="有帮助"
                >
                  <el-icon><TopRight /></el-icon>
                </button>
                <button
                  class="fb-btn"
                  :class="{ active: msg.feedbackType === 2, dislike: msg.feedbackType === 2 }"
                  @click="$emit('feedback', { messageId: msg.id, action: 'dislike', currentStatus: feedbackStatus(msg) })"
                  title="需改进"
                >
                  <el-icon><BottomRight /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Loading / Streaming -->
        <div class="message agent-message" v-else-if="msg.status === 1">
          <div class="avatar agent-avatar">
            <span>AI</span>
          </div>
          <div class="message-body body-left">
            <!-- Streaming text -->
            <div class="bubble agent-bubble streaming-bubble" v-if="streamingText">
              <div v-html="renderMarkdown(streamingText)"></div>
              <span class="typing-cursor"></span>
            </div>
            <!-- Thinking spinner -->
            <div class="bubble agent-bubble thinking-bubble" v-else>
              <div class="ai-thinking">
                <div class="thinking-ring">
                  <div class="ring-spinner"></div>
                  <div class="ring-pulse"></div>
                </div>
                <span class="thinking-label">思考中</span>
              </div>
            </div>
            <!-- Reasoning / tool events -->
            <div class="events-panel" v-if="streamingEvents.length > 0">
              <el-collapse>
                <el-collapse-item>
                  <template #title>
                    <div class="events-title">
                      <el-icon><Cpu /></el-icon>
                      <span>推理过程 / 工具调用</span>
                    </div>
                  </template>
                  <div v-for="(evt, idx) in streamingEvents" :key="idx" class="event-item">
                    <span class="event-badge" :class="eventBadgeClass(evt.type)">{{ eventLabel(evt.type) }}</span>
                    <span class="event-agent" v-if="evt.agentName">{{ evt.agentName }}</span>
                    <div class="event-content" v-if="evt.content">{{ evt.content }}</div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="input-area">
      <div class="input-card" :class="{ focused: inputFocused }">
        <textarea
          v-model="inputText"
          class="chat-textarea"
          :placeholder="loading ? '正在回复中...' : '输入你的问题...'"
          :disabled="loading"
          @keydown.enter.exact.prevent="send"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
          @input="autoResize"
          ref="textareaRef"
          rows="1"
        ></textarea>
        <button class="send-btn" :class="{ disabled: loading || !inputText.trim(), active: inputText.trim() && !loading }" @click="send" :disabled="loading">
          <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
          <el-icon v-else><Top /></el-icon>
        </button>
      </div>
      <div class="input-hint">
        <span class="hint-key">Enter</span> 发送
        <span class="hint-dot">·</span>
        <span class="hint-key">Shift+Enter</span> 换行
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  streamingText: { type: String, default: '' },
  streamingEvents: { type: Array, default: () => [] },
  currentSessionTitle: { type: String, default: '' }
})

const emit = defineEmits(['send', 'feedback', 'clear'])

const inputText = ref('')
const messagesContainer = ref(null)
const textareaRef = ref(null)
const inputFocused = ref(false)

const suggestionItems = [
  { icon: 'DataAnalysis', label: '数据分析', text: '帮我分析一下今天的销售数据', bg: 'linear-gradient(135deg, #667eea20, #764ba220)' },
  { icon: 'Reading', label: '知识问答', text: '解释一下什么是 RAG 检索增强生成', bg: 'linear-gradient(135deg, #f093fb20, #f5576c20)' },
  { icon: 'Document', label: '代码生成', text: '帮我写一个 Python 快速排序算法', bg: 'linear-gradient(135deg, #43e97b20, #38f9d720)' },
  { icon: 'Search', label: '智能搜索', text: '搜索最新的 AI 技术趋势', bg: 'linear-gradient(135deg, #fa709a20, #fee14020)' }
]

function fillSuggestion(text) {
  inputText.value = text
  if (textareaRef.value) {
    textareaRef.value.focus()
    autoResize()
  }
}

function send() {
  if (!inputText.value.trim() || props.loading) return
  emit('send', inputText.value)
  inputText.value = ''
  nextTick(() => autoResize())
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const lineHeight = 24
  const maxLines = 5
  const maxHeight = lineHeight * maxLines
  el.style.height = Math.min(el.scrollHeight, maxHeight) + 'px'
}

function isLongMessage(text) {
  return text && text.length > 800
}

function renderMarkdown(text) {
  if (!text) return ''
  try {
    return DOMPurify.sanitize(marked(text, { breaks: true }))
  } catch {
    return DOMPurify.sanitize(text)
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

function eventBadgeClass(type) {
  switch (type) {
    case 'TOOL_CALL': return 'badge-warn'
    case 'TOOL_RESULT': return 'badge-success'
    case 'AGENT_RESULT': return 'badge-primary'
    default: return 'badge-info'
  }
}

function eventLabel(type) {
  switch (type) {
    case 'TOOL_CALL': return '工具调用'
    case 'TOOL_RESULT': return '工具结果'
    case 'AGENT_RESULT': return '回复'
    default: return type
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
  background: var(--chat-bg);
}

/* ========== Header ========== */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--chat-border);
  flex-shrink: 0;
}

.header-left {
  min-width: 0;
  flex: 1;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-title.placeholder {
  color: #9ca3af;
  font-weight: 500;
}

.header-right {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.header-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--chat-border);
  border-radius: var(--radius-sm);
  background: #fff;
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.header-btn:hover {
  background: #f9fafb;
  border-color: #d1d5db;
  color: #374151;
}

/* ========== Messages container ========== */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 28px 0;
}

/* ========== Welcome / Empty State ========== */
.welcome-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px 24px;
  animation: fadeIn 0.6s ease;
}

.welcome-brand {
  margin-bottom: 24px;
}

.welcome-icon-wrapper {
  position: relative;
  width: 88px;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome-icon-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--brand-gradient);
  opacity: 0.15;
  animation: pulseRing 3s ease-in-out infinite;
}

.welcome-icon-inner {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.35);
  position: relative;
  z-index: 1;
}

.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 10px;
  letter-spacing: -0.3px;
}

.gradient-text {
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-desc {
  font-size: 15px;
  color: #6b7280;
  margin-bottom: 36px;
}

.welcome-suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
  max-width: 560px;
}

.suggestion-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: var(--chat-surface);
  border: 1px solid var(--chat-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color var(--transition-normal), box-shadow var(--transition-normal),
    transform var(--transition-normal);
}

.suggestion-card:hover {
  border-color: var(--brand-primary-light);
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.12);
  transform: translateY(-3px);
}

.suggestion-icon-wrap {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.suggestion-icon {
  color: var(--brand-primary);
  font-size: 18px;
}

.suggestion-text {
  flex: 1;
  min-width: 0;
}

.suggestion-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-primary);
  margin-bottom: 3px;
}

.suggestion-desc {
  font-size: 13px;
  color: #4b5563;
  line-height: 1.4;
}

/* ========== Message layout ========== */
.message-pair {
  margin-bottom: 32px;
  animation: fadeInUp 0.35s ease-out;
}

.message {
  display: flex;
  align-items: flex-start;
  padding: 0 24px;
  margin-bottom: 8px;
  max-width: 820px;
  margin-left: auto;
  margin-right: auto;
  width: 100%;
}

.user-message {
  justify-content: flex-end;
}

.agent-message {
  justify-content: flex-start;
}

/* ========== Avatar ========== */
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-avatar {
  background: var(--brand-gradient);
  color: #fff;
  margin-left: 14px;
  order: 2;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.user-avatar span {
  font-size: 13px;
}

.agent-avatar {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: #fff;
  margin-right: 14px;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
}

.agent-avatar span {
  font-size: 11px;
  letter-spacing: 0.5px;
}

/* ========== Message body ========== */
.message-body {
  flex: 1;
  min-width: 0;
  max-width: 680px;
}

.body-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.body-left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

/* ========== Bubbles ========== */
.bubble {
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.user-bubble {
  background: var(--user-bubble-bg);
  color: var(--user-bubble-text);
  border-radius: var(--radius-xl) var(--radius-xl) 4px var(--radius-xl);
  max-width: 75%;
  display: inline-block;
  box-shadow: 0 2px 12px rgba(102, 126, 234, 0.25);
  line-height: 1.6;
}

.agent-bubble {
  background: var(--agent-bubble-bg);
  color: var(--agent-bubble-text);
  border-radius: var(--radius-xl) var(--radius-xl) var(--radius-xl) 4px;
  width: 100%;
  box-shadow: var(--agent-bubble-shadow);
  border: 1px solid var(--chat-border);
  border-left: 3px solid var(--brand-primary-light);
}

.agent-bubble.collapsed {
  max-height: 300px;
  overflow: hidden;
  position: relative;
}

.agent-bubble.collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: linear-gradient(to top, #fff 0%, transparent 100%);
  pointer-events: none;
}

.expand-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  padding: 4px 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--brand-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.expand-toggle:hover {
  background: rgba(99, 102, 241, 0.06);
}

/* Agent bubble markdown styles */
.agent-bubble :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 13px;
}
.agent-bubble :deep(th),
.agent-bubble :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 9px 14px;
  text-align: left;
}
.agent-bubble :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}
.agent-bubble :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin: 10px 0;
  font-size: 13px;
  position: relative;
}
.agent-bubble :deep(pre code) {
  display: block;
  padding: 14px 16px;
  overflow-x: auto;
}
.agent-bubble :deep(code) {
  font-family: var(--font-mono);
  font-size: 13px;
}
.agent-bubble :deep(p code) {
  background: rgba(99, 102, 241, 0.08);
  color: var(--brand-primary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12.5px;
}
.agent-bubble :deep(p) {
  margin: 5px 0;
}
.agent-bubble :deep(a) {
  color: var(--brand-primary);
  text-decoration: none;
  font-weight: 500;
}
.agent-bubble :deep(a:hover) {
  text-decoration: underline;
}
.agent-bubble :deep(ul),
.agent-bubble :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.agent-bubble :deep(blockquote) {
  border-left: 3px solid var(--brand-primary-light);
  padding-left: 14px;
  color: #6b7280;
  margin: 10px 0;
  background: rgba(99, 102, 241, 0.04);
  padding: 10px 14px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

/* ========== Thinking / Streaming ========== */
.thinking-bubble {
  padding: 18px 24px;
  display: flex;
  align-items: center;
}

.ai-thinking {
  display: flex;
  align-items: center;
  gap: 14px;
}

.thinking-ring {
  position: relative;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring-spinner {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2.5px solid rgba(99, 102, 241, 0.15);
  border-top-color: var(--brand-primary);
  animation: spinRing 1s linear infinite;
}

.ring-pulse {
  position: absolute;
  inset: 6px;
  border-radius: 50%;
  background: rgba(99, 102, 241, 0.15);
  animation: pulseRing 2s ease-in-out infinite;
}

.thinking-label {
  font-size: 14px;
  color: #6b7280;
  font-weight: 500;
}

.stream-bubble {
  position: relative;
}

.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 18px;
  background: var(--brand-primary);
  margin-left: 3px;
  vertical-align: text-bottom;
  animation: blink 0.8s step-end infinite;
  border-radius: 1px;
}

/* ========== Time & Footer ========== */
.message-time {
  font-size: 11px;
  color: #b0b5be;
  margin-top: 6px;
  padding: 0 4px;
}

.time-right {
  text-align: right;
}

.message-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding: 0 2px;
  width: 100%;
}

/* ========== Feedback buttons ========== */
.feedback-actions {
  display: flex;
  gap: 4px;
}

.fb-btn {
  width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 50%;
  background: transparent;
  color: #b0b5be;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
  transition: all var(--transition-fast);
}

.fb-btn:hover {
  background: #f3f4f6;
  color: #6b7280;
  transform: scale(1.12);
}

.fb-btn.like.active {
  color: var(--brand-primary);
  background: rgba(99, 102, 241, 0.08);
  border-color: rgba(99, 102, 241, 0.2);
}

.fb-btn.dislike.active {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.2);
}

/* ========== Events panel ========== */
.events-panel {
  margin-top: 12px;
  width: 100%;
  font-size: 13px;
}

.events-panel :deep(.el-collapse) {
  border: 1px solid var(--chat-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: #fafbfc;
}

.events-panel :deep(.el-collapse-item__header) {
  background: #f3f4f6;
  padding: 0 14px;
  height: 40px;
  font-size: 13px;
  border-bottom: 1px solid var(--chat-border);
}

.events-panel :deep(.el-collapse-item__wrap) {
  background: #fff;
}

.events-panel :deep(.el-collapse-item__content) {
  padding: 8px 14px;
}

.events-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #6b7280;
  font-size: 13px;
  font-weight: 500;
}

.event-item {
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
}

.event-item:last-child {
  border-bottom: none;
}

.event-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  flex-shrink: 0;
}

.badge-warn {
  background: #fef3c7;
  color: #92400e;
}

.badge-success {
  background: #d1fae5;
  color: #065f46;
}

.badge-primary {
  background: #e0e7ff;
  color: #3730a3;
}

.badge-info {
  background: #f3f4f6;
  color: #4b5563;
}

.event-agent {
  color: var(--brand-primary);
  font-weight: 500;
  font-size: 12px;
}

.event-content {
  color: #6b7280;
  font-size: 12px;
  word-break: break-all;
  flex: 1;
  min-width: 0;
  line-height: 1.5;
}

/* ========== Input area ========== */
.input-area {
  padding: 16px 24px 14px;
  background: var(--chat-bg);
  max-width: 820px;
  width: 100%;
  margin: 0 auto;
}

.input-card {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--input-bg);
  border: 1.5px solid var(--input-border);
  border-radius: var(--radius-2xl);
  padding: 10px 10px 10px 20px;
  box-shadow: var(--input-shadow);
  transition: border-color var(--transition-normal), box-shadow var(--transition-normal);
}

.input-card.focused {
  border-color: var(--input-focus-border);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1), 0 4px 20px rgba(0, 0, 0, 0.06);
}

.chat-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  font-family: var(--font-family);
  color: #1f2937;
  background: transparent;
  min-height: 24px;
  max-height: 120px;
  padding: 4px 0;
}

.chat-textarea::placeholder {
  color: #b0b5be;
}

.chat-textarea:disabled {
  opacity: 0.6;
}

.send-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 50%;
  background: #e5e7eb;
  color: #9ca3af;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  font-size: 18px;
  transition: all var(--transition-fast);
}

.send-btn.active {
  background: var(--brand-gradient);
  color: #fff;
  box-shadow: 0 2px 10px rgba(102, 126, 234, 0.35);
}

.send-btn.active:hover {
  transform: scale(1.08);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.45);
}

.send-btn.active:active {
  transform: scale(0.95);
}

.send-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.input-hint {
  text-align: center;
  font-size: 11px;
  color: #b0b5be;
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.hint-key {
  background: rgba(0, 0, 0, 0.04);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  color: #9ca3af;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.hint-dot {
  opacity: 0.4;
}
</style>
