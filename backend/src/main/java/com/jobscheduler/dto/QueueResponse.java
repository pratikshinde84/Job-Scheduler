package com.jobscheduler.dto;

import com.jobscheduler.entity.Queue;

import java.util.Map;
import java.util.UUID;

public record QueueResponse(
        UUID id,
        UUID projectId,
        String name,
        int concurrencyLimit,
        boolean isPaused,
        Map<String, Object> defaultRetryConfig
) {
    public static QueueResponse from(Queue q) {
        return new QueueResponse(
                q.getId(),
                q.getProject().getId(),
                q.getName(),
                q.getConcurrencyLimit(),
                Boolean.TRUE.equals(q.getIsPaused()),
                q.getDefaultRetryConfig()
        );
    }
}
