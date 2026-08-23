package com.jobscheduler.worker;

import com.jobscheduler.entity.Job;
import com.jobscheduler.executor.JobExecutor;
import com.jobscheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.Semaphore;

/**
 * JobRunner — Dedicated component for asynchronous job execution.
 *
 * Moving @Async execution to a separate Spring component ensures that
 * invocations go through Spring's AOP proxy layer, running job executions
 * in parallel across the task execution thread pool instead of blocking
 * the main @Scheduled poller thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {

    private final JobService jobService;

    @Async
    public void executeAsync(Job job, String queueName, String workerIdentity, Semaphore sem, JobExecutor executor) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            jobService.markRunning(job.getId(), workerIdentity);
            log.info("[{}] Starting job {} on queue '{}'", workerIdentity, job.getId(), queueName);

            if (executor != null) {
                log.debug("[{}] Dispatching job {} to {}", workerIdentity, job.getId(),
                        executor.getClass().getSimpleName());
                executor.execute(job);
            } else {
                log.warn("[{}] No executor registered for queue '{}' — running fallback stub",
                        workerIdentity, queueName);
                fallbackStub(job, workerIdentity);
            }

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

    private void fallbackStub(Job job, String workerIdentity) throws InterruptedException {
        long duration = 200 + (long) (Math.random() * 400);
        log.debug("[{}] Fallback stub: sleeping {} ms for job {}", workerIdentity, duration, job.getId());
        Thread.sleep(duration);
    }

    private String stackTraceToString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
