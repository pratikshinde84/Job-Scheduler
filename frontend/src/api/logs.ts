import api from '../lib/axios'

export interface LogEntry {
    id: string
    jobId: string
    queueName: string
    projectName: string
    workerName: string
    attemptNumber: number
    maxAttempts: number
    level: 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR'
    event: string
    message: string
    details: string | null
    payload: Record<string, unknown> | null
    result: Record<string, unknown> | null
    durationMs: number | null
    timestamp: string
}

export interface ExecutionMetrics {
    totalJobs: number
    totalAttempts: number
    successCount: number
    failureCount: number
    deadCount: number
    successRatePct: number
    retryRatePct: number
    avgDurationMs: number
    activeWorkerCount: number
}

export interface DashboardLogResponse {
    logs: LogEntry[]
    metrics: ExecutionMetrics
}

export const logsApi = {
    getLogs: async (limit = 50): Promise<DashboardLogResponse> => {
        const res = await api.get<DashboardLogResponse>(`/dashboard/logs?limit=${limit}`)
        return res.data
    },
}
