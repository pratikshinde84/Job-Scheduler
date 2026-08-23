// ── Auth ─────────────────────────────────────────────────────────────────────
export interface AuthUser {
  id: string
  email: string
  name: string
  avatarUrl: string | null
}

// ── Projects ─────────────────────────────────────────────────────────────────
export interface Project {
  id: string
  name: string
  apiKeyPrefix: string
  createdAt: string
  queueCount: number
}

// ── Queues ───────────────────────────────────────────────────────────────────
export type RetryConfig = {
  strategy: 'exponential' | 'linear' | 'fixed'
  base_delay_seconds: number
  max_attempts: number
}

export interface Queue {
  id: string
  projectId: string
  name: string
  concurrencyLimit: number
  isPaused: boolean
  defaultRetryConfig: RetryConfig | null
}

// ── Jobs ─────────────────────────────────────────────────────────────────────
export type JobStatus =
  | 'pending'
  | 'scheduled'
  | 'claimed'
  | 'running'
  | 'completed'
  | 'failed'
  | 'dead'

export interface Job {
  id: string
  queueId: string
  status: JobStatus
  priority: number
  scheduledAt: string
  cronExpression?: string | null
  nextRetryAt: string | null
  lockedAt: string | null
  lockedBy: string | null
  attemptCount: number
  maxAttempts: number
  payload: Record<string, unknown>
  retryConfig: RetryConfig | null
  lastError: string | null
  result: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export interface JobAttempt {
  id: number
  attemptNumber: number
  workerName: string
  startedAt: string
  finishedAt: string | null
  errorStack: string | null
}

// ── Workers ───────────────────────────────────────────────────────────────────
export type WorkerStatus = 'active' | 'draining' | 'offline'

export interface WorkerSummary {
  name: string
  status: WorkerStatus
  lastHeartbeatAt: string | null
}

// ── Dashboard ─────────────────────────────────────────────────────────────────
export interface DashboardData {
  jobCountsByStatus: Partial<Record<JobStatus, number>>
  totalJobs: number
  totalQueues: number
  totalProjects: number
  workers: WorkerSummary[]
}

// ── Pagination ────────────────────────────────────────────────────────────────
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
