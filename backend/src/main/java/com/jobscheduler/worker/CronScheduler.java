package com.jobscheduler.worker;

import com.jobscheduler.entity.Worker;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Two responsibilities:
 *
 *  1. Promote: every 10 s, flip scheduled → pending for jobs whose
 *     scheduled_at has passed (the poller only picks up 'pending').
 *
 *  2. Heartbeat: every 15 s, upsert this worker's heartbeat into the
 *     workers table so the dashboard can show live worker status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronScheduler {

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;

    @Value("${worker.name:spring-boot-instance-1}")
    private String workerName;

    /**
     * Promotes scheduled jobs to pending so the poller picks them up.
     * Runs every 10 seconds.
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
     * Sends a heartbeat to keep the worker record alive.
     * Runs every 15 seconds.
     */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void heartbeat() {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = workerRepository.upsertHeartbeat(workerName, now, Worker.WorkerStatus.active.name());

        if (updated == 0) {
            // Worker record doesn't exist yet — create it
            Worker worker = Worker.builder()
                    .name(workerName)
                    .lastHeartbeatAt(now)
                    .status(Worker.WorkerStatus.active)
                    .build();
            workerRepository.save(worker);
            log.info("Registered new worker: {}", workerName);
        } else {
            log.debug("Heartbeat sent for worker: {}", workerName);
        }
    }

    /**
     * On graceful shutdown, mark the worker as offline.
     * Called via @PreDestroy or shutdown hook.
     */
    @jakarta.annotation.PreDestroy
    @Transactional
    public void markOffline() {
        log.info("Worker {} going offline", workerName);
        workerRepository.upsertHeartbeat(
                workerName, OffsetDateTime.now(), Worker.WorkerStatus.offline.name());
    }
}
