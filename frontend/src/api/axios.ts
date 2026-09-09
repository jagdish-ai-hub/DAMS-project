import axios from 'axios'
import { ACCESS_TOKEN_KEY, clearSession } from '../auth/session'

const BASE_URL = import.meta.env.VITE_API_URL || ''

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach the access token (sessionStorage — no persistence across a browser restart, by design)
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(ACCESS_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// On 401 (expired/invalid token) clear the session and return to the login screen.
// There is no refresh flow in v1 — see plan.md rev 3.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearSession()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login?expired=1'
      }
    }

    // Surface the X-Request-ID so a failure can be traced to the exact server log line (AGENT.md).
    // It is appended to the message here — the single choke point — so every ErrorBanner in the
    // app shows it without each screen wiring it up separately.
    const requestId = error.response?.headers?.['x-request-id']
    if (requestId && error.response?.data) {
      error.response.data._requestId = requestId
      if (typeof error.response.data.message === 'string'
        && !error.response.data.message.includes(requestId)) {
        error.response.data.message += ` (ref ${requestId})`
      }
    }

    return Promise.reject(error)
  },
)

export default api
