import api from '../lib/axios'
import type { Queue, RetryConfig } from '../types'

export const queuesApi = {
  list: (projectId: string) =>
    api.get<Queue[]>(`/projects/${projectId}/queues`).then((r) => r.data),

  get: (projectId: string, queueId: string) =>
    api.get<Queue>(`/projects/${projectId}/queues/${queueId}`).then((r) => r.data),

  create: (projectId: string, payload: {
    name: string
    concurrencyLimit: number
    defaultRetryConfig?: RetryConfig
  }) =>
    api.post<Queue>(`/projects/${projectId}/queues`, payload).then((r) => r.data),

  update: (projectId: string, queueId: string, patch: Partial<{
    concurrencyLimit: number
    isPaused: boolean
    defaultRetryConfig: RetryConfig
  }>) =>
    api.patch<Queue>(`/projects/${projectId}/queues/${queueId}`, patch).then((r) => r.data),

  pause: (projectId: string, queueId: string) =>
    api.post(`/projects/${projectId}/queues/${queueId}/pause`),

  resume: (projectId: string, queueId: string) =>
    api.post(`/projects/${projectId}/queues/${queueId}/resume`),

  delete: (projectId: string, queueId: string) =>
    api.delete(`/projects/${projectId}/queues/${queueId}`),
}
