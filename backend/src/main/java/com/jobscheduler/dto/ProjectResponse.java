package com.jobscheduler.dto;

import com.jobscheduler.entity.Project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String apiKeyPrefix,
        OffsetDateTime createdAt,
        int queueCount
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getApiKeyPrefix(),
                p.getCreatedAt(),
                p.getQueues() != null ? p.getQueues().size() : 0
        );
    }
}
