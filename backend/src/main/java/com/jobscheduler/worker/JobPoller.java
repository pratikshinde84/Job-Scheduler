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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Polls all active queues every 2 seconds across multiple worker identities
 * (e.g. worker-instance-1, worker-instance-2, worker-instance-3).
 *
 * Each queue gets its own Semaphore whose permit count equals the queue's
 * concurrencyLimit.
 * Row-level FOR UPDATE SKIP LOCKED ensures complete atomic isolation between
 * worker instances.
 */
@Slf4j
@Component
public class JobPoller {

    private final QueueRepository queueRepository;
    private final JobService jobService;
    private final List<String> workerNames = new CopyOnWriteArrayList<>();

    /** Executor registry: queueName (lower-case) → executor bean */
    private final Map<String, JobExecutor> executorRegistry;

    /** Per-queue semaphores — created lazily, keyed by queue ID. */
    private final Map<UUID, Semaphore> semaphores = new ConcurrentHashMap<>();

    public JobPoller(QueueRepository queueRepository,
            JobService jobService,
            @Value("${worker.name-prefix:worker}") String namePrefix,
            @Value("${worker.count:4}") int workerCount,
            List<JobExecutor> executors) {

        this.queueRepository = queueRepository;
        this.jobService = jobService;

        for (int i = 1; i <= workerCount; i++) {
            this.workerNames.add(namePrefix + "-" + i);
        }

        // Build a lookup map: lower-case queue name → executor
        this.executorRegistry = executors.stream()
                .collect(Collectors.toMap(
                        e -> e.queueName().toLowerCase(),
                        Function.identity()));

        log.info("JobPoller initialized with {} worker identities {} and {} registered executor(s): {}",
                workerNames.size(), workerNames, executorRegistry.size(), executorRegistry.keySet());
    }

    public synchronized void addWorker(String workerName) {
        if (!workerNames.contains(workerName)) {
            workerNames.add(workerName);
            log.info("JobPoller registered dynamic worker instance: {}", workerName);
        }
    }

    public List<String> getWorkerNames() {
        return new ArrayList<>(workerNames);
    }

    // ── Poll loop ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        List<Queue> activeQueues = queueRepository.findAllActive();
        for (Queue queue : activeQueues) {
            Semaphore sem = semaphores.computeIfAbsent(
                    queue.getId(),
                    id -> new Semaphore(queue.getConcurrencyLimit(), true));

            for (String currentWorker : workerNames) {
                if (sem.availablePermits() <= 0)
                    break;

                // Claim 1 job per worker per pass for fair round-robin distribution across all
                // workers
                List<Job> claimed = jobService.claimJobs(queue.getId(), currentWorker, 1);
                for (Job job : claimed) {
                    if (sem.tryAcquire()) {
                        String resolvedQueueName = queue.getName();
                        executeAsync(job, resolvedQueueName, currentWorker, sem);
                    }
                }
            }
        }
    }

    // ── Async execution ───────────────────────────────────────────────────────

    @Async
    public void executeAsync(Job job, String queueName, String workerIdentity, Semaphore sem) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            jobService.markRunning(job.getId(), workerIdentity);
            log.info("[{}] Starting job {} on queue '{}'", workerIdentity, job.getId(), queueName);

            dispatch(job, queueName, workerIdentity);

            jobService.completeJob(job.getId(), workerIdentity, startedAt);
            log.info("[{}] Completed job {}", workerIdentity, job.getId());

        } catch (Exception ex) {
            log.error("[{}] Job {} failed: {}", workerIdentity, job.getId(), ex.getMessage(), ex);
            jobService.failJob(
                    job.getId(), workerIdentity, startedAt,
                    ex.getMessage(), stackTraceToString(ex));
        } finally {
            sem.release();
        }
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    private void dispatch(Job job, String queueName, String workerIdentity) throws Exception {
        JobExecutor executor = executorRegistry.get(queueName.toLowerCase());

        if (executor != null) {
            log.debug("[{}] Dispatching job {} to {}", workerIdentity, job.getId(),
                    executor.getClass().getSimpleName());
            executor.execute(job);
        } else {
            log.warn("[{}] No executor registered for queue '{}' — running fallback stub",
                    workerIdentity, queueName);
            fallbackStub(job, workerIdentity);
        }
    }

    private void fallbackStub(Job job, String workerIdentity) throws InterruptedException {
        long duration = 200 + (long) (Math.random() * 400);
        log.debug("[{}] Fallback stub: sleeping {} ms for job {}", workerIdentity, duration, job.getId());
        Thread.sleep(duration);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String stackTraceToString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
