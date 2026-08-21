package com.jobscheduler.worker;

import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.Queue;
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

/**
 * Polls all active (non-paused) queues every 2 seconds and dispatches
 * runnable jobs to async execution threads.
 *
 * Each queue gets its own Semaphore whose permit count equals the queue's
 * concurrencyLimit, preventing over-saturation.
 */
@Slf4j
@Component
public class JobPoller {

    private final QueueRepository queueRepository;
    private final JobService jobService;
    private final String workerName;

    /** Per-queue semaphores — created lazily, keyed by queue ID. */
    private final Map<UUID, Semaphore> semaphores = new ConcurrentHashMap<>();

    public JobPoller(QueueRepository queueRepository,
                     JobService jobService,
                     @Value("${worker.name:spring-boot-instance-1}") String workerName) {
        this.queueRepository = queueRepository;
        this.jobService = jobService;
        this.workerName = workerName;
    }

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        List<Queue> activeQueues = queueRepository.findAllActive();
        for (Queue queue : activeQueues) {
            Semaphore sem = semaphores.computeIfAbsent(
                    queue.getId(),
                    id -> new Semaphore(queue.getConcurrencyLimit(), true));

            int available = sem.availablePermits();
            if (available <= 0) continue; // queue is at capacity

            List<Job> claimed = jobService.claimJobs(queue.getId(), workerName, available);
            for (Job job : claimed) {
                if (sem.tryAcquire()) {
                    executeAsync(job, sem);
                }
            }
        }
    }

    @Async
    public void executeAsync(Job job, Semaphore sem) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            jobService.markRunning(job.getId(), workerName);
            log.info("[{}] Starting job {} (queue={})", workerName, job.getId(), job.getQueue().getId());

            // ── Simulate / dispatch real work here ────────────────────────────
            // In a real system you'd route by job.getPayload().get("type") to
            // a registered JobHandler. This stub simulates 100–500 ms work.
            processJob(job);

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

    /**
     * Stub job processor. Replace this with your actual job dispatch logic,
     * e.g. routing by payload "type" to registered JobHandler beans.
     */
    private void processJob(Job job) throws InterruptedException {
        Map<String, Object> payload = job.getPayload();
        log.debug("[{}] Processing job {} payload={}", workerName, job.getId(), payload);
        // Simulate variable execution time
        long duration = 100 + (long) (Math.random() * 400);
        Thread.sleep(duration);
    }

    private String stackTraceToString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
