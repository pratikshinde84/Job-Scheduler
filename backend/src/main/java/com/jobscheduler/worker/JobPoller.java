package com.jobscheduler.worker;

import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.Queue;
import com.jobscheduler.executor.JobExecutor;
import com.jobscheduler.repository.QueueRepository;
import com.jobscheduler.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Polls all active (non-paused) queues every 2 seconds and dispatches
 * runnable jobs to async execution threads.
 *
 * Each queue gets its own Semaphore whose permit count equals the queue's
 * concurrencyLimit, preventing over-saturation.
 *
 * Job routing: the queue name is matched case-insensitively to a registered
 * {@link JobExecutor} bean. If no match is found, a fallback stub executes.
 */
@Slf4j
@Component
public class JobPoller {

    private final QueueRepository queueRepository;
    private final JobService jobService;
    private final String workerName;

    /** Executor registry: queueName (lower-case) → executor bean */
    private final Map<String, JobExecutor> executorRegistry;

    /** Per-queue semaphores — created lazily, keyed by queue ID. */
    private final Map<UUID, Semaphore> semaphores = new ConcurrentHashMap<>();

    public JobPoller(QueueRepository queueRepository,
                     JobService jobService,
                     @Value("${worker.name:spring-boot-instance-1}") String workerName,
                     List<JobExecutor> executors) {
        this.queueRepository = queueRepository;
        this.jobService = jobService;
        this.workerName = workerName;

        // Build a lookup map: lower-case queue name → executor
        this.executorRegistry = executors.stream()
                .collect(Collectors.toMap(
                        e -> e.queueName().toLowerCase(),
                        Function.identity()));

        log.info("JobPoller registered {} executor(s): {}",
                executorRegistry.size(), executorRegistry.keySet());
    }

    // ── Poll loop ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        List<Queue> activeQueues = queueRepository.findAllActive();
        for (Queue queue : activeQueues) {
            Semaphore sem = semaphores.computeIfAbsent(
                    queue.getId(),
                    id -> new Semaphore(queue.getConcurrencyLimit(), true));

            int available = sem.availablePermits();
            if (available <= 0) continue;

            List<Job> claimed = jobService.claimJobs(queue.getId(), workerName, available);
            for (Job job : claimed) {
                if (sem.tryAcquire()) {
                    // Resolve queue name HERE while the JPA session is still open.
                    // executeAsync runs in a separate thread with no session —
                    // calling job.getQueue().getName() there causes LazyInitializationException.
                    String resolvedQueueName = queue.getName();
                    executeAsync(job, resolvedQueueName, sem);
                }
            }
        }
    }

    // ── Async execution ───────────────────────────────────────────────────────

    @Async
    public void executeAsync(Job job, String queueName, Semaphore sem) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            jobService.markRunning(job.getId(), workerName);
            log.info("[{}] Starting job {} on queue '{}'", workerName, job.getId(), queueName);

            dispatch(job, queueName);

            jobService.completeJob(job.getId(), workerName, startedAt);
            log.info("[{}] Completed job {}", workerName, job.getId());

        } catch (Exception ex) {
            log.error("[{}] Job {} failed: {}", workerName, job.getId(), ex.getMessage(), ex);
            jobService.failJob(
                    job.getId(), workerName, startedAt,
                    ex.getMessage(), stackTraceToString(ex));
        } finally {
            sem.release();
        }
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /**
     * Looks up the correct {@link JobExecutor} by queue name and delegates.
     * Falls back to a stub if no executor is registered for that queue name.
     */
    private void dispatch(Job job, String queueName) throws Exception {
        JobExecutor executor = executorRegistry.get(queueName.toLowerCase());

        if (executor != null) {
            log.debug("[{}] Dispatching job {} to {}", workerName, job.getId(),
                    executor.getClass().getSimpleName());
            executor.execute(job);
        } else {
            log.warn("[{}] No executor registered for queue '{}' — running fallback stub",
                    workerName, queueName);
            fallbackStub(job);
        }
    }

    /**
     * Fallback for queues that have no registered executor.
     * Simulates 200–600 ms of work so jobs don't stack up indefinitely.
     */
    private void fallbackStub(Job job) throws InterruptedException {
        long duration = 200 + (long) (Math.random() * 400);
        log.debug("[{}] Fallback stub: sleeping {} ms for job {}", workerName, duration, job.getId());
        Thread.sleep(duration);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String stackTraceToString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
