package com.jobscheduler.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DashboardLogResponse(
        List<LogEntry> logs,
        ExecutionMetrics metrics) {
    public record LogEntry(
            String id,
            String jobId,
            String queueName,
            String projectName,
            String workerName,
            int attemptNumber,
            int maxAttempts,
            String level, // INFO, SUCCESS, WARN, ERROR
            String event, // JOB_ENQUEUED, JOB_CLAIMED, JOB_RUNNING, JOB_COMPLETED, JOB_FAILED_RETRY,
                          // JOB_DEAD, JOB_REQUEUED, JOB_CANCELLED
            String message,
            String details,
            Map<String, Object> payload,
            Map<String, Object> result,
            Long durationMs,
            OffsetDateTime timestamp) {
    }

    public record ExecutionMetrics(
            long totalJobs,
            long totalAttempts,
            long successCount,
            long failureCount,
            long deadCount,
            double successRatePct,
            double retryRatePct,
            double avgDurationMs,
            long activeWorkerCount) {
    }
}
