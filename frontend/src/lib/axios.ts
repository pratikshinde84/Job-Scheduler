import axios from 'axios'
import { supabase } from './supabaseClient'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// ── Request interceptor ───────────────────────────────────────────────────────
// Always fetch the current session before each request so we get a fresh token
// even after Supabase silently refreshes it in the background.
api.interceptors.request.use(async (config) => {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  } else {
    // No session at all — abort the request rather than sending it without auth,
    // which would only produce a noisy 401 in the console.
    const controller = new AbortController()
    controller.abort('No active session')
    config.signal = controller.signal
  }
  return config
})

// ── Response interceptor ──────────────────────────────────────────────────────
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // Swallow aborted requests silently (caused by the no-session abort above)
    if (axios.isCancel(error)) return Promise.reject(error)

    if (error.response?.status === 401) {
      // Token genuinely expired and refresh failed — sign out and redirect
      await supabase.auth.signOut()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
