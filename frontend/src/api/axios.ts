import axios from 'axios'
import { ACCESS_TOKEN_KEY, clearSession } from '../auth/session'

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

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

    // Surface the X-Request-ID so a failure can be traced to the exact server log line (AGENT.md)
    const requestId = error.response?.headers?.['x-request-id']
    if (requestId && error.response?.data) {
      error.response.data._requestId = requestId
    }

    return Promise.reject(error)
  },
)

export default api
