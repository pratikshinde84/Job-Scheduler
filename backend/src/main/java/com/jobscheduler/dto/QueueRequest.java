package com.jobscheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record QueueRequest(
        @NotBlank @Size(min = 1, max = 120) String name,
        @Min(1) @Max(100) int concurrencyLimit,
        Map<String, Object> defaultRetryConfig
) {}
