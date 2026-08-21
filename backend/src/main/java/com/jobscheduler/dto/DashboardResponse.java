package com.jobscheduler.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponse(
        Map<String, Long> jobCountsByStatus,   // e.g. {"pending":12,"running":3,...}
        long totalJobs,
        long totalQueues,
        long totalProjects,
        List<WorkerSummary> workers
) {
    public record WorkerSummary(
            String name,
            String status,
            String lastHeartbeatAt
    ) {}
}
