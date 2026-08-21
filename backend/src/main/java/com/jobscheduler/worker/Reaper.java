package com.jobscheduler.worker;

import com.jobscheduler.entity.Job;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.WorkerRepository;
import com.jobscheduler.entity.Worker;
import com.jobscheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scans for and recovers two categories of problem jobs:
 *
 *  1. Stuck claimed jobs — claimed but not transitioned to running/completed
 *     within the lock timeout window (default 5 minutes). Reset to pending.
 *
 *  2. Stale workers — workers that haven't sent a heartbeat in 3× the
 *     heartbeat interval. Marked offline.
 *
 * Runs every 60 seconds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Reaper {

    /** A job is considered stuck if locked_at is older than this many minutes. */
    private static final int LOCK_TIMEOUT_MINUTES = 5;

    /** A worker is considered stale if no heartbeat in this many seconds. */
    private static final int WORKER_STALE_SECONDS = 60;

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final WorkerRepository workerRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void reap() {
        reaperSweepJobs();
        reaperSweepWorkers();
    }

    private void reaperSweepJobs() {
        OffsetDateTime lockThreshold = OffsetDateTime.now().minusMinutes(LOCK_TIMEOUT_MINUTES);
        List<Job> stuckJobs = jobRepository.findStaleClaimed(lockThreshold);

        if (stuckJobs.isEmpty()) return;

        log.warn("Reaper found {} stuck job(s)", stuckJobs.size());
        for (Job job : stuckJobs) {
            try {
                jobService.resetStuckJob(job);
            } catch (Exception ex) {
                log.error("Reaper failed to reset job {}: {}", job.getId(), ex.getMessage());
            }
        }
    }

    private void reaperSweepWorkers() {
        OffsetDateTime heartbeatThreshold = OffsetDateTime.now().minusSeconds(WORKER_STALE_SECONDS);
        List<Worker> staleWorkers = workerRepository.findStaleWorkers(heartbeatThreshold);

        for (Worker worker : staleWorkers) {
            log.warn("Reaper marking stale worker offline: {}", worker.getName());
            worker.setStatus(Worker.WorkerStatus.offline);
            workerRepository.save(worker);
        }
    }
}
