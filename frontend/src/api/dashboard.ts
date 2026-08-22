import api from '../lib/axios'
import type { DashboardData } from '../types'

export const dashboardApi = {
  get: () => api.get<DashboardData>('/dashboard').then((r) => r.data),
  addWorker: () => api.post('/dashboard/workers/add').then((r) => r.data),
}

