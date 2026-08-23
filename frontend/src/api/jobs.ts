import api from '../lib/axios'
import type { Job, JobAttempt, JobStatus, Page, RetryConfig } from '../types'

export const jobsApi = {
  listByQueue: (queueId: string, params?: { status?: JobStatus; page?: number; size?: number }) =>
    api.get<Page<Job>>(`/queues/${queueId}/jobs`, { params }).then((r) => r.data),

  listMine: (params?: { page?: number; size?: number }) =>
    api.get<Page<Job>>('/jobs', { params }).then((r) => r.data),

  get: (jobId: string) =>
    api.get<Job>(`/jobs/${jobId}`).then((r) => r.data),

  attempts: (jobId: string) =>
    api.get<JobAttempt[]>(`/jobs/${jobId}/attempts`).then((r) => r.data),

  enqueue: (queueId: string, payload: {
    payload: Record<string, unknown>
    priority?: number
    scheduledAt?: string
    cronExpression?: string
    maxAttempts?: number
    concurrency?: number
    retryConfig?: RetryConfig
  }) =>
    api.post<Job>(`/queues/${queueId}/jobs`, payload).then((r) => r.data),

  cancel: (jobId: string) =>
    api.post<Job>(`/jobs/${jobId}/cancel`).then((r) => r.data),

  requeue: (jobId: string) =>
    api.post<Job>(`/jobs/${jobId}/requeue`).then((r) => r.data),

  getAiSummary: (jobId: string) =>
    api.post<{ summary: string }>(`/jobs/${jobId}/ai-summary`).then((r) => r.data.summary),

  bulkEnqueue: (queueId: string, body: {
    payloads: Record<string, unknown>[]
    priority?: number
    maxAttempts?: number
    concurrency?: number
  }) =>
    api.post<{ enqueued: number; queueId: string; jobIds: string[] }>(
      `/queues/${queueId}/jobs/bulk`, body
    ).then((r) => r.data),

}
