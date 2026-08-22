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

import com.jobscheduler.worker.CronScheduler;
import com.jobscheduler.worker.JobPoller;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectRepository projectRepository;
    private final QueueRepository queueRepository;
    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final CronScheduler cronScheduler;
    private final JobPoller jobPoller;

    /**
     * GET /api/dashboard
     *
     * Returns an overview for the authenticated user:
     * - job counts grouped by status
     * - total project / queue / job counts
     * - list of active workers
     */
    @GetMapping
    public ResponseEntity<DashboardResponse> dashboard() {
        User user = UserContext.get();
        UUID userId = user.getId();

        // Remove stale offline workers from database
        List<Worker> offlineWorkers = workerRepository.findAll().stream()
                .filter(w -> w.getStatus() == Worker.WorkerStatus.offline)
                .toList();
        if (!offlineWorkers.isEmpty()) {
            workerRepository.deleteAll(offlineWorkers);
        }

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
                statusCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        long totalJobs = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long totalQueues = queueIds.size();
        long totalProjects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId).size();

        // Only active workers
        List<DashboardResponse.WorkerSummary> workers = workerRepository.findAll()
                .stream()
                .filter(w -> w.getStatus() == Worker.WorkerStatus.active)
                .sorted((w1, w2) -> w1.getName().compareTo(w2.getName()))
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

    /**
     * POST /api/dashboard/workers/add
     *
     * Dynamically registers and spawns a new active worker thread in the system.
     */
    @PostMapping("/workers/add")
    public ResponseEntity<DashboardResponse.WorkerSummary> addWorker() {
        List<Worker> existing = workerRepository.findAll();
        int maxWorkerIndex = 0;
        for (Worker w : existing) {
            if (w.getName().startsWith("worker-")) {
                try {
                    int idx = Integer.parseInt(w.getName().replace("worker-", ""));
                    if (idx > maxWorkerIndex)
                        maxWorkerIndex = idx;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String newWorkerName = "worker-" + (maxWorkerIndex + 1);

        OffsetDateTime now = OffsetDateTime.now();
        Worker worker = Worker.builder()
                .name(newWorkerName)
                .status(Worker.WorkerStatus.active)
                .lastHeartbeatAt(now)
                .build();
        workerRepository.save(worker);

        cronScheduler.addWorker(newWorkerName);
        jobPoller.addWorker(newWorkerName);

        return ResponseEntity.ok(new DashboardResponse.WorkerSummary(
                newWorkerName,
                Worker.WorkerStatus.active.name(),
                now.toString()));
    }
}
