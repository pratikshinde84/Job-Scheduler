package com.jobscheduler.controller;

import com.jobscheduler.dto.DashboardResponse;
import com.jobscheduler.entity.User;
import com.jobscheduler.entity.Worker;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.ProjectRepository;
import com.jobscheduler.repository.QueueRepository;
import com.jobscheduler.repository.WorkerRepository;
import com.jobscheduler.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectRepository projectRepository;
    private final QueueRepository queueRepository;
    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;

    /**
     * GET /api/dashboard
     *
     * Returns an overview for the authenticated user:
     *  - job counts grouped by status
     *  - total project / queue / job counts
     *  - list of workers with their current status
     */
    @GetMapping
    public ResponseEntity<DashboardResponse> dashboard() {
        User user = UserContext.get();
        UUID userId = user.getId();

        // Collect all queue IDs owned by this user
        List<UUID> queueIds = queueRepository.findByUserId(userId)
                .stream()
                .map(q -> q.getId())
                .toList();

        // Job status counts
        Map<String, Long> statusCounts = new HashMap<>();
        if (!queueIds.isEmpty()) {
            List<Object[]> rows = jobRepository.countByStatusForQueues(queueIds);
            for (Object[] row : rows) {
                // row[0] = JobStatus enum, row[1] = count
                statusCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        long totalJobs = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long totalQueues = queueIds.size();
        long totalProjects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId).size();

        // All workers (system-wide)
        List<DashboardResponse.WorkerSummary> workers = workerRepository.findAll()
                .stream()
                .map(w -> new DashboardResponse.WorkerSummary(
                        w.getName(),
                        w.getStatus().name(),
                        w.getLastHeartbeatAt() != null ? w.getLastHeartbeatAt().toString() : null))
                .toList();

        return ResponseEntity.ok(new DashboardResponse(
                statusCounts,
                totalJobs,
                totalQueues,
                totalProjects,
                workers));
    }
}
