package com.jobscheduler.controller;

import com.jobscheduler.dto.JobRequest;
import com.jobscheduler.dto.JobResponse;
import com.jobscheduler.dto.PageResponse;
import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.JobAttempt;
import com.jobscheduler.entity.User;
import com.jobscheduler.security.UserContext;
import com.jobscheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // ── Enqueue ───────────────────────────────────────────────────────────────

    /**
     * POST /api/queues/{queueId}/jobs
     * Enqueue a new job onto a specific queue.
     */
    @PostMapping("/api/queues/{queueId}/jobs")
    public ResponseEntity<JobResponse> enqueue(
            @PathVariable UUID queueId,
            @Valid @RequestBody JobRequest req) {

        Job job = jobService.enqueue(
                queueId,
                req.payload(),
                req.priority(),
                req.scheduledAt(),
                req.maxAttempts() > 0 ? req.maxAttempts() : 3,
                req.retryConfig());

        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
    }

    // ── List by queue ─────────────────────────────────────────────────────────

    /**
     * GET /api/queues/{queueId}/jobs?status=pending&page=0&size=20
     */
    @GetMapping("/api/queues/{queueId}/jobs")
    public ResponseEntity<PageResponse<JobResponse>> listByQueue(
            @PathVariable UUID queueId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Job.JobStatus jobStatus = status != null ? Job.JobStatus.valueOf(status) : null;
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(PageResponse.from(
                jobService.listByQueue(queueId, jobStatus, pageable),
                JobResponse::from));
    }

    // ── List all jobs for the current user ────────────────────────────────────

    /**
     * GET /api/jobs?page=0&size=20
     */
    @GetMapping("/api/jobs")
    public ResponseEntity<PageResponse<JobResponse>> listMyJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = UserContext.get();
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(PageResponse.from(
                jobService.listByUser(user.getId(), pageable),
                JobResponse::from));
    }

    // ── Single job ────────────────────────────────────────────────────────────

    /** GET /api/jobs/{jobId} */
    @GetMapping("/api/jobs/{jobId}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID jobId) {
        return ResponseEntity.ok(JobResponse.from(jobService.get(jobId)));
    }

    /** GET /api/jobs/{jobId}/attempts */
    @GetMapping("/api/jobs/{jobId}/attempts")
    public ResponseEntity<List<AttemptResponse>> getAttempts(@PathVariable UUID jobId) {
        List<AttemptResponse> attempts = jobService.getAttempts(jobId)
                .stream()
                .map(AttemptResponse::from)
                .toList();
        return ResponseEntity.ok(attempts);
    }

    // ── Job actions ───────────────────────────────────────────────────────────

    /** POST /api/jobs/{jobId}/cancel */
    @PostMapping("/api/jobs/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancel(@PathVariable UUID jobId) {
        return ResponseEntity.ok(JobResponse.from(jobService.cancelJob(jobId)));
    }

    /** POST /api/jobs/{jobId}/requeue */
    @PostMapping("/api/jobs/{jobId}/requeue")
    public ResponseEntity<JobResponse> requeue(@PathVariable UUID jobId) {
        return ResponseEntity.ok(JobResponse.from(jobService.requeueDeadJob(jobId)));
    }

    // ── Nested response DTO ───────────────────────────────────────────────────

    public record AttemptResponse(
            Long id,
            int attemptNumber,
            String workerName,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String errorStack
    ) {
        public static AttemptResponse from(JobAttempt a) {
            return new AttemptResponse(
                    a.getId(),
                    a.getAttemptNumber(),
                    a.getWorkerName(),
                    a.getStartedAt(),
                    a.getFinishedAt(),
                    a.getErrorStack());
        }
    }
}
