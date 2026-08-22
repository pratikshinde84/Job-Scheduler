package com.jobscheduler.controller;

import com.jobscheduler.dto.DashboardLogResponse;
import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.JobAttempt;
import com.jobscheduler.entity.User;
import com.jobscheduler.entity.Worker;
import com.jobscheduler.repository.JobAttemptRepository;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.WorkerRepository;
import com.jobscheduler.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardLogController {

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final WorkerRepository workerRepository;

    /**
     * GET /api/dashboard/logs?limit=50
     *
     * Returns a chronological stream of job logs, attempt executions, worker
     * assignments,
     * timestamps, and execution metrics.
     */
    @GetMapping("/logs")
    public ResponseEntity<DashboardLogResponse> getLogs(
            @RequestParam(defaultValue = "50") int limit) {

        User user = UserContext.get();
        UUID userId = user.getId();

        // 1. Fetch recent attempts
        List<JobAttempt> attempts = jobAttemptRepository.findRecentAttemptsByUserId(
                userId, PageRequest.of(0, Math.min(limit, 100)));

        // 2. Fetch recent jobs
        List<Job> jobs = jobRepository.findByUserId(
                userId, PageRequest.of(0, Math.min(limit, 100), Sort.by("updatedAt").descending())).getContent();

        List<DashboardLogResponse.LogEntry> logEntries = new ArrayList<>();

        // Convert Attempts into Log Entries
        for (JobAttempt a : attempts) {
            Job j = a.getJob();
            Long durationMs = null;
            if (a.getStartedAt() != null && a.getFinishedAt() != null) {
                durationMs = Duration.between(a.getStartedAt(), a.getFinishedAt()).toMillis();
            }

            boolean isError = a.getErrorStack() != null && !a.getErrorStack().isBlank();
            String level = isError ? "ERROR" : "SUCCESS";
            String event = isError
                    ? (a.getAttemptNumber() >= j.getMaxAttempts() ? "JOB_DEAD" : "JOB_FAILED_RETRY")
                    : "JOB_COMPLETED";

            String queueName = j.getQueue() != null ? j.getQueue().getName() : "unknown";
            String projectName = (j.getQueue() != null && j.getQueue().getProject() != null)
                    ? j.getQueue().getProject().getName()
                    : "unknown";

            String message;
            if (isError) {
                message = String.format("Attempt #%d on queue '%s' failed on worker '%s': %s",
                        a.getAttemptNumber(), queueName, a.getWorkerName(),
                        truncateError(a.getErrorStack()));
            } else {
                message = String.format("Attempt #%d on queue '%s' completed successfully on worker '%s' in %d ms.",
                        a.getAttemptNumber(), queueName, a.getWorkerName(),
                        durationMs != null ? durationMs : 0);
            }

            logEntries.add(new DashboardLogResponse.LogEntry(
                    "attempt-" + a.getId(),
                    j.getId().toString(),
                    queueName,
                    projectName,
                    a.getWorkerName() != null ? a.getWorkerName() : "unassigned",
                    a.getAttemptNumber(),
                    j.getMaxAttempts(),
                    level,
                    event,
                    message,
                    a.getErrorStack(),
                    j.getPayload(),
                    j.getResult(),
                    durationMs,
                    a.getStartedAt()));
        }

        // Convert Jobs status updates into Log Entries
        for (Job j : jobs) {
            String queueName = j.getQueue() != null ? j.getQueue().getName() : "unknown";
            String projectName = (j.getQueue() != null && j.getQueue().getProject() != null)
                    ? j.getQueue().getProject().getName()
                    : "unknown";

            String level = switch (j.getStatus()) {
                case completed -> "SUCCESS";
                case failed -> "WARN";
                case dead -> "ERROR";
                case running, claimed -> "INFO";
                default -> "INFO";
            };

            String event = "JOB_" + j.getStatus().name().toUpperCase();
            String message = String.format("Job [%s] status: %s (Priority: %d, Attempts: %d/%d)",
                    shortId(j.getId()), j.getStatus().name(), j.getPriority(), j.getAttemptCount(), j.getMaxAttempts());

            logEntries.add(new DashboardLogResponse.LogEntry(
                    "job-status-" + j.getId() + "-" + j.getStatus(),
                    j.getId().toString(),
                    queueName,
                    projectName,
                    j.getLockedBy() != null ? j.getLockedBy() : "unassigned",
                    j.getAttemptCount(),
                    j.getMaxAttempts(),
                    level,
                    event,
                    message,
                    j.getLastError(),
                    j.getPayload(),
                    j.getResult(),
                    null,
                    j.getUpdatedAt() != null ? j.getUpdatedAt() : OffsetDateTime.now(ZoneOffset.UTC)));
        }

        // Sort log entries newest first
        logEntries.sort((a, b) -> {
            if (a.timestamp() == null || b.timestamp() == null)
                return 0;
            return b.timestamp().compareTo(a.timestamp());
        });

        // Limit response size
        if (logEntries.size() > limit) {
            logEntries = logEntries.subList(0, limit);
        }

        // 3. Compute Metrics
        long totalJobs = jobs.size();
        long totalAttempts = attempts.size();
        long successCount = jobs.stream().filter(j -> j.getStatus() == Job.JobStatus.completed).count();
        long deadCount = jobs.stream().filter(j -> j.getStatus() == Job.JobStatus.dead).count();
        long failureCount = attempts.stream().filter(a -> a.getErrorStack() != null && !a.getErrorStack().isBlank())
                .count();

        double successRatePct = totalJobs > 0 ? ((double) successCount / totalJobs) * 100.0 : 100.0;
        double retryRatePct = totalAttempts > 0 ? ((double) failureCount / totalAttempts) * 100.0 : 0.0;

        OptionalDouble avgDuration = attempts.stream()
                .filter(a -> a.getStartedAt() != null && a.getFinishedAt() != null)
                .mapToLong(a -> Duration.between(a.getStartedAt(), a.getFinishedAt()).toMillis())
                .average();

        long activeWorkers = workerRepository.findAll().stream()
                .filter(w -> w.getStatus() == Worker.WorkerStatus.active)
                .count();

        DashboardLogResponse.ExecutionMetrics metrics = new DashboardLogResponse.ExecutionMetrics(
                totalJobs,
                totalAttempts,
                successCount,
                failureCount,
                deadCount,
                Math.round(successRatePct * 10.0) / 10.0,
                Math.round(retryRatePct * 10.0) / 10.0,
                avgDuration.isPresent() ? Math.round(avgDuration.getAsDouble()) : 0,
                activeWorkers);

        return ResponseEntity.ok(new DashboardLogResponse(logEntries, metrics));
    }

    private static String shortId(UUID id) {
        if (id == null)
            return "";
        return id.toString().substring(0, 8);
    }

    private static String truncateError(String error) {
        if (error == null)
            return "";
        String firstLine = error.split("\n")[0];
        return firstLine.length() > 100 ? firstLine.substring(0, 100) + "..." : firstLine;
    }
}
