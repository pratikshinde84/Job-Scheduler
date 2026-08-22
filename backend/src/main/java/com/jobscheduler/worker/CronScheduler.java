package com.jobscheduler.worker;

import com.jobscheduler.entity.Worker;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.WorkerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Responsibilities:
 * 1. Promote: every 10 s, flip scheduled → pending for jobs whose scheduled_at
 * has passed.
 * 2. Heartbeat: every 15 s, upserts heartbeats for all concurrent worker
 * instances
 * so the dashboard shows live worker statuses.
 */
@Slf4j
@Component
public class CronScheduler {

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final List<String> workerNames = new CopyOnWriteArrayList<>();

    public CronScheduler(JobRepository jobRepository,
            WorkerRepository workerRepository,
            @Value("${worker.name-prefix:worker}") String namePrefix,
            @Value("${worker.count:4}") int workerCount) {
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        for (int i = 1; i <= workerCount; i++) {
            this.workerNames.add(namePrefix + "-" + i);
        }
        log.info("CronScheduler initialized for {} worker instances: {}", workerCount, workerNames);
    }

    public synchronized void addWorker(String workerName) {
        if (!workerNames.contains(workerName)) {
            workerNames.add(workerName);
            log.info("CronScheduler registered dynamic worker instance: {}", workerName);
        }
    }

    /**
     * Promotes scheduled jobs to pending so pollers pick them up.
     */
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void promoteScheduledJobs() {
        int promoted = jobRepository.promoteScheduledJobs();
        if (promoted > 0) {
            log.info("CronScheduler promoted {} scheduled job(s) to pending", promoted);
        }
    }

    /**
     * Sends heartbeats for all active worker instances.
     */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void heartbeat() {
        OffsetDateTime now = OffsetDateTime.now();
        for (String wName : workerNames) {
            int updated = workerRepository.upsertHeartbeat(wName, now, Worker.WorkerStatus.active.name());
            if (updated == 0) {
                Worker worker = Worker.builder()
                        .name(wName)
                        .lastHeartbeatAt(now)
                        .status(Worker.WorkerStatus.active)
                        .build();
                workerRepository.save(worker);
                log.info("Registered new worker instance: {}", wName);
            }
        }
    }

    /**
     * On graceful shutdown, mark all worker instances offline.
     */
    @jakarta.annotation.PreDestroy
    @Transactional
    public void markOffline() {
        OffsetDateTime now = OffsetDateTime.now();
        for (String wName : workerNames) {
            log.info("Worker instance {} going offline", wName);
            workerRepository.upsertHeartbeat(wName, now, Worker.WorkerStatus.offline.name());
        }
    }
}
