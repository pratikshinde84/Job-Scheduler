import axios from 'axios'
import { supabase } from './supabaseClient'

const getApiBaseUrl = () => {
  const envUrl = import.meta.env.VITE_API_BASE_URL
  if (!envUrl) return '/api'
  const trimmed = envUrl.trim().replace(/\/+$/, '')
  return trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`
}

const api = axios.create({
  baseURL: getApiBaseUrl(),
  headers: { 'Content-Type': 'application/json' },
})

// ── Request interceptor ───────────────────────────────────────────────────────
// 1. Ensures leading slashes in relative request paths (e.g. '/dashboard') don't
//    strip the '/api' prefix from the baseURL.
// 2. Attaches Supabase JWT bearer token for authentication.
api.interceptors.request.use(async (config) => {
  if (config.baseURL && config.url && !config.url.startsWith('http')) {
    const cleanUrl = config.url.startsWith('/') ? config.url.slice(1) : config.url
    const cleanBase = config.baseURL.endsWith('/') ? config.baseURL : `${config.baseURL}/`
    config.url = cleanBase + cleanUrl
    config.baseURL = undefined
  }

  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  } else {
    // No session at all — abort the request rather than sending it without auth
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
    // Swallow aborted requests silently
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
