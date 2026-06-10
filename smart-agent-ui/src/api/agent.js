import axios from 'axios'

const api = axios.create({ baseURL: '/api/agent' })

export function getHistory(userId) {
  return api.get(`/history/${userId}`)
}

export function getConversation(userId, sessionId) {
  return api.get(`/conversation/${userId}/${sessionId}`)
}

export function chat(userId, sessionId, message) {
  return api.post('/chat', { userId, sessionId, message })
}

export function chatStream(userId, sessionId, message) {
  return fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, sessionId, message })
  })
}

export function sendFeedback(messageId, action, currentStatus) {
  return api.post('/feedback', { messageId, action, currentStatus })
}
