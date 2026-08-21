package com.jobscheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

public record JobRequest(
        @NotNull Map<String, Object> payload,
        @Min(-32768) @Max(32767) int priority,
        OffsetDateTime scheduledAt,       // null = run immediately
        @Min(1) @Max(20) int maxAttempts,
        Map<String, Object> retryConfig   // null = use queue default
) {}
