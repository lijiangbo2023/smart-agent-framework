import axios from 'axios'

const api = axios.create({ baseURL: '/api/agent' })

let authUserId = null
export function setAuthUserId(id) { authUserId = id }

// Add JWT token and userId to all requests
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  const uid = authUserId || config.headers['X-User-Id']
  if (uid) {
    config.headers['X-User-Id'] = uid
  }
  return config
})

export function getHistory(userId, page = 1, size = 20) {
  return api.get(`/history/${userId}`, { params: { page, size } })
}

export function getConversation(userId, sessionId) {
  return api.get(`/conversation/${userId}/${sessionId}`)
}

export function chat(userId, sessionId, message) {
  return api.post('/chat', { userId, sessionId, message })
}

export function chatStream(userId, sessionId, message, signal) {
  const token = localStorage.getItem('token')
  const headers = {
    'Content-Type': 'application/json',
    'X-User-Id': authUserId || userId
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify({ userId, sessionId, message }),
    signal
  })
}

export function sendFeedback(messageId, action, currentStatus) {
  return api.post('/feedback', { messageId, action, currentStatus })
}

export function deleteConversation(userId, sessionId) {
  return api.delete(`/conversation/${userId}/${sessionId}`)
}
