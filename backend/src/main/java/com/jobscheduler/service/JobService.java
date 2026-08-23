package com.jobscheduler.service;

import com.jobscheduler.entity.*;
import com.jobscheduler.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final JobAttemptRepository attemptRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final ProjectRepository projectRepository;
    private final AiSummaryService aiSummaryService;

    // ── Enqueue ───────────────────────────────────────────────────────────────

    @Transactional
    public Job enqueue(UUID queueId, Map<String, Object> payload,
            int priority, String cronExpression, OffsetDateTime scheduledAt,
            int maxAttempts, Map<String, Object> retryConfig) {

        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new EntityNotFoundException("Queue not found: " + queueId));

        OffsetDateTime targetScheduledAt = scheduledAt;
        if (cronExpression != null && !cronExpression.isBlank() && targetScheduledAt == null) {
            try {
                CronExpression cron = CronExpression.parse(cronExpression);
                targetScheduledAt = cron.next(OffsetDateTime.now());
            } catch (Exception e) {
                log.error("Invalid cron expression: {}", cronExpression, e);
            }
        }

        Job.JobStatus initialStatus = targetScheduledAt != null && targetScheduledAt.isAfter(OffsetDateTime.now())
                ? Job.JobStatus.scheduled
                : Job.JobStatus.pending;

        Job job = Job.builder()
                .queue(queue)
                .payload(payload)
                .priority((short) priority)
                .cronExpression(cronExpression)
                .status(initialStatus)
                .scheduledAt(targetScheduledAt != null ? targetScheduledAt : OffsetDateTime.now())
                .maxAttempts(maxAttempts)
                .retryConfig(retryConfig)
                .build();

        return jobRepository.save(job);
    }

    @Transactional
    public Job enqueue(UUID queueId, Map<String, Object> payload,
            int priority, OffsetDateTime scheduledAt,
            int maxAttempts, Map<String, Object> retryConfig) {
        return enqueue(queueId, payload, priority, null, scheduledAt, maxAttempts, retryConfig);
    }

    /**
     * Bulk enqueue — insert all jobs in a single transaction.
     * 
     * @param queueId     target queue
     * @param payloads    list of payload maps — one job per element
     * @param priority    shared priority applied to every job
     * @param maxAttempts shared maxAttempts applied to every job
     * @return list of saved Job entities
     */
    @Transactional
    public List<Job> bulkEnqueue(UUID queueId,
            List<Map<String, Object>> payloads,
            int priority,
            int maxAttempts) {

        if (payloads == null || payloads.isEmpty()) {
            throw new IllegalArgumentException("Payload array must not be empty");
        }
        if (payloads.size() > 500) {
            throw new IllegalArgumentException(
                    "Bulk enqueue is limited to 500 jobs per request, got " + payloads.size());
        }

        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new EntityNotFoundException("Queue not found: " + queueId));

        List<Job> jobs = payloads.stream()
                .map(payload -> Job.builder()
                        .queue(queue)
                        .payload(payload)
                        .priority((short) priority)
                        .status(Job.JobStatus.pending)
                        .scheduledAt(OffsetDateTime.now())
                        .maxAttempts(maxAttempts)
                        .build())
                .toList();

        List<Job> saved = jobRepository.saveAll(jobs);
        log.info("Bulk enqueued {} job(s) onto queue {}", saved.size(), queueId);
        return saved;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Job> listByQueue(UUID queueId, Job.JobStatus status, Pageable pageable) {
        if (status != null) {
            return jobRepository.findByQueueIdAndStatus(queueId, status, pageable);
        }
        return jobRepository.findByQueueId(queueId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Job> listByUser(UUID userId, Pageable pageable) {
        return jobRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Job get(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public List<JobAttempt> getAttempts(UUID jobId) {
        return attemptRepository.findByJobIdOrderByStartedAtDesc(jobId);
    }

    // ── Claim (called by worker poller) ───────────────────────────────────────

    @Transactional
    public List<Job> claimJobs(UUID queueId, String workerName, int limit) {
        List<UUID> claimedIds = jobRepository.claimJobs(queueId, workerName, limit);
        if (claimedIds.isEmpty())
            return List.of();
        return jobRepository.findAllById(claimedIds);
    }

    // ── Lifecycle transitions (called by worker after execution) ──────────────

    @Transactional
    public void markRunning(UUID jobId, String workerName) {
        int updated = jobRepository.markRunning(jobId, workerName);
        if (updated == 0) {
            log.warn("markRunning: job {} not claimed by worker {}", jobId, workerName);
        }
    }

    @Transactional
    public void completeJob(UUID jobId, String workerName, OffsetDateTime startedAt) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

        recordAttempt(job, workerName, startedAt, null);

        job.setStatus(Job.JobStatus.completed);
        job.setLockedAt(null);
        job.setLockedBy(null);
        jobRepository.save(job);

        // If this is a recurring cron job, schedule its next occurrence automatically
        if (job.getCronExpression() != null && !job.getCronExpression().isBlank()) {
            try {
                CronExpression cron = CronExpression.parse(job.getCronExpression());
                OffsetDateTime nextRun = cron.next(OffsetDateTime.now());
                if (nextRun != null) {
                    enqueue(job.getQueue().getId(), job.getPayload(), job.getPriority(),
                            job.getCronExpression(), nextRun, job.getMaxAttempts(), job.getRetryConfig());
                    log.info("Auto-scheduled next occurrence of recurring cron job for {}", nextRun);
                }
            } catch (Exception e) {
                log.error("Failed to calculate next run for cron expression {}: {}",
                        job.getCronExpression(), e.getMessage());
            }
        }
    }

    // ── Result storage (called by executors before completeJob) ──────────────

    @Transactional
    public void storeResult(UUID jobId, Map<String, Object> result) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setResult(result);
            jobRepository.save(job);
            log.info("Stored result for job {}: {}", jobId, result);
        });
    }

    @Transactional
    public void failJob(UUID jobId, String workerName, OffsetDateTime startedAt,
            String errorMessage, String errorStack) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

        recordAttempt(job, workerName, startedAt, errorStack);

        job.setLastError(errorMessage);
        try {
            String summary = aiSummaryService.generateSummary(job, errorMessage, errorStack);
            job.setFailureSummary(summary);
        } catch (Exception e) {
            log.error("Failed to generate AI summary for job {}: {}", jobId, e.getMessage());
        }
        job.setLockedAt(null);
        job.setLockedBy(null);

        int effective = effectiveMaxAttempts(job);

        if (job.getAttemptCount() >= effective) {
            // Exhausted retries → dead
            job.setStatus(Job.JobStatus.dead);
            jobRepository.save(job);
            moveToDead(job, errorMessage);
        } else {
            // Schedule retry with back-off
            job.setStatus(Job.JobStatus.pending);
            job.setNextRetryAt(calculateNextRetry(job));
            job.setScheduledAt(job.getNextRetryAt());
            jobRepository.save(job);
        }
    }

    @Transactional
    public String generateOrGetAiSummary(UUID jobId) {
        Job job = get(jobId);
        if (job.getFailureSummary() != null && !job.getFailureSummary().isBlank()) {
            return job.getFailureSummary();
        }
        String lastErr = job.getLastError();
        String summary = aiSummaryService.generateSummary(job, lastErr, null);
        job.setFailureSummary(summary);
        jobRepository.save(job);
        return summary;
    }

    // ── Manual operations ─────────────────────────────────────────────────────

    @Transactional
    public Job cancelJob(UUID jobId) {
        Job job = get(jobId);
        if (job.getStatus() == Job.JobStatus.running || job.getStatus() == Job.JobStatus.claimed) {
            throw new IllegalStateException("Cannot cancel a running/claimed job");
        }
        job.setStatus(Job.JobStatus.dead);
        return jobRepository.save(job);
    }

    @Transactional
    public Job requeueDeadJob(UUID jobId) {
        Job job = get(jobId);
        if (job.getStatus() != Job.JobStatus.dead && job.getStatus() != Job.JobStatus.failed) {
            throw new IllegalStateException("Only dead or failed jobs can be re-queued");
        }
        job.setStatus(Job.JobStatus.pending);
        job.setAttemptCount(0);
        job.setLastError(null);
        job.setScheduledAt(OffsetDateTime.now());
        job.setNextRetryAt(null);
        return jobRepository.save(job);
    }

    // ── Reaper helper (called by Reaper scheduler) ────────────────────────────

    @Transactional
    public void resetStuckJob(Job job) {
        log.warn("Reaper resetting stuck job {} locked by {}", job.getId(), job.getLockedBy());
        job.setStatus(Job.JobStatus.pending);
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setLastError("Job timed out and was reset by reaper");
        jobRepository.save(job);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void recordAttempt(Job job, String workerName,
            OffsetDateTime startedAt, String errorStack) {
        JobAttempt attempt = JobAttempt.builder()
                .job(job)
                .attemptNumber(job.getAttemptCount())
                .workerName(workerName)
                .startedAt(startedAt)
                .finishedAt(OffsetDateTime.now())
                .errorStack(errorStack)
                .build();
        attemptRepository.save(attempt);
    }

    private void moveToDead(Job job, String error) {
        DeadLetterEntry entry = DeadLetterEntry.builder()
                .job(job)
                .originalPayload(job.getPayload())
                .error(error)
                .build();
        deadLetterRepository.save(entry);
        log.info("Job {} moved to dead-letter queue", job.getId());
    }

    private int effectiveMaxAttempts(Job job) {
        if (job.getRetryConfig() != null && job.getRetryConfig().containsKey("max_attempts")) {
            return ((Number) job.getRetryConfig().get("max_attempts")).intValue();
        }
        Queue queue = job.getQueue();
        if (queue.getDefaultRetryConfig() != null
                && queue.getDefaultRetryConfig().containsKey("max_attempts")) {
            return ((Number) queue.getDefaultRetryConfig().get("max_attempts")).intValue();
        }
        return job.getMaxAttempts();
    }

    private OffsetDateTime calculateNextRetry(Job job) {
        Map<String, Object> config = job.getRetryConfig() != null
                ? job.getRetryConfig()
                : (job.getQueue().getDefaultRetryConfig() != null
                        ? job.getQueue().getDefaultRetryConfig()
                        : Map.of());

        String strategy = (String) config.getOrDefault("strategy", "exponential");
        int baseDelay = ((Number) config.getOrDefault("base_delay_seconds", 1)).intValue();
        int attempt = job.getAttemptCount();

        long delaySecs = switch (strategy) {
            case "fixed" -> baseDelay;
            case "linear" -> (long) baseDelay * attempt;
            default -> // exponential with jitter
                (long) (baseDelay * Math.pow(2, attempt - 1))
                        + (long) (Math.random() * baseDelay);
        };

        // Cap at 1 hour
        delaySecs = Math.min(delaySecs, 3600);
        return OffsetDateTime.now().plusSeconds(delaySecs);
    }
}
