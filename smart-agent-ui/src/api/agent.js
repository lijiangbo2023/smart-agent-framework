import axios from 'axios'

const api = axios.create({ baseURL: '/api/agent' })

// Set userId header for ownership validation on all requests
api.interceptors.request.use(config => {
  const userId = config.headers['X-User-Id'] || window.__smartAgentUserId
  if (userId) {
    config.headers['X-User-Id'] = userId
  }
  return config
})

export function setUserId(userId) {
  window.__smartAgentUserId = userId
}

export function getHistory(userId, page = 1, size = 20) {
  return api.get(`/history/${userId}`, { params: { page, size } })
}

export function getConversation(userId, sessionId) {
  return api.get(`/conversation/${userId}/${sessionId}`)
}

export function chat(userId, sessionId, message) {
  return api.post('/chat', { userId, sessionId, message })
}

export function chatStream(userId, sessionId, message) {
  const userIdHeader = window.__smartAgentUserId || userId
  return fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userIdHeader
    },
    body: JSON.stringify({ userId, sessionId, message })
  })
}

export function sendFeedback(messageId, action, currentStatus) {
  return api.post('/feedback', { messageId, action, currentStatus })
}
