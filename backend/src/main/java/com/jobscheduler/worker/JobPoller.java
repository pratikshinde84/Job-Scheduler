package com.jobscheduler.worker;

import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.Queue;
import com.jobscheduler.executor.JobExecutor;
import com.jobscheduler.repository.QueueRepository;
import com.jobscheduler.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final JobRunner jobRunner;
    private final List<String> workerNames = new CopyOnWriteArrayList<>();

    /** Executor registry: queueName (lower-case) → executor bean */
    private final Map<String, JobExecutor> executorRegistry;

    /** Per-queue semaphores — created lazily, keyed by queue ID. */
    private final Map<UUID, Semaphore> semaphores = new ConcurrentHashMap<>();

    public JobPoller(QueueRepository queueRepository,
            JobService jobService,
            JobRunner jobRunner,
            @Value("${worker.name-prefix:worker}") String namePrefix,
            @Value("${worker.count:4}") int workerCount,
            List<JobExecutor> executors) {

        this.queueRepository = queueRepository;
        this.jobService = jobService;
        this.jobRunner = jobRunner;

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
                if (sem.availablePermits() <= 0) {
                    break;
                }

                // Acquire semaphore permit BEFORE claiming job from database
                if (sem.tryAcquire()) {
                    List<Job> claimed = jobService.claimJobs(queue.getId(), currentWorker, 1);
                    if (claimed.isEmpty()) {
                        // No job was available to claim; release permit immediately
                        sem.release();
                    } else {
                        Job job = claimed.get(0);
                        String resolvedQueueName = queue.getName();
                        JobExecutor executor = executorRegistry.get(resolvedQueueName.toLowerCase());

                        // Dispatch to JobRunner bean for true @Async parallel execution
                        jobRunner.executeAsync(job, resolvedQueueName, currentWorker, sem, executor);
                    }
                }
            }
        }
    }
}
