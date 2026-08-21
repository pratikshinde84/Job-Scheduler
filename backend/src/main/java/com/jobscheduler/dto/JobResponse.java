package com.jobscheduler.dto;

import com.jobscheduler.entity.Job;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID queueId,
        String status,
        int priority,
        OffsetDateTime scheduledAt,
        OffsetDateTime nextRetryAt,
        OffsetDateTime lockedAt,
        String lockedBy,
        int attemptCount,
        int maxAttempts,
        Map<String, Object> payload,
        Map<String, Object> retryConfig,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static JobResponse from(Job j) {
        return new JobResponse(
                j.getId(),
                j.getQueue().getId(),
                j.getStatus().name(),
                j.getPriority(),
                j.getScheduledAt(),
                j.getNextRetryAt(),
                j.getLockedAt(),
                j.getLockedBy(),
                j.getAttemptCount(),
                j.getMaxAttempts(),
                j.getPayload(),
                j.getRetryConfig(),
                j.getLastError(),
                j.getCreatedAt(),
                j.getUpdatedAt()
        );
    }
}
