package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import com.jobscheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DemoTaskExecutor — General-purpose background task executor.
 *
 * Proves the queue system works end-to-end:
 *   1. Print "Demo Task Started"
 *   2. Wait 3 seconds
 *   3. Print the custom message from the payload
 *   4. Mark COMPLETED and store result
 *
 * Expected payload:
 * {
 *   "message" : "Hello from Demo Task"   — custom message (optional)
 * }
 *
 * Stored result:
 * {
 *   "status"    : "completed",
 *   "message"   : "Hello from Demo Task",
 *   "startedAt" : "2026-08-22T12:00:00Z",
 *   "finishedAt": "2026-08-22T12:00:03Z",
 *   "durationMs": 3000
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoTaskExecutor implements JobExecutor {

    private final JobService jobService;

    @Override
    public String queueName() {
        return "Demo-Task";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();
        String message = getString(payload, "message");
        if (message == null || message.isBlank()) {
            message = "Demo task executed successfully";
        }

        Instant startedAt = Instant.now();
        log.info("[DemoTaskExecutor] Job {} -- Demo Task Started", job.getId());

        // Simulate 3 seconds of work
        Thread.sleep(3_000);

        Instant finishedAt = Instant.now();
        long durationMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli();

        log.info("[DemoTaskExecutor] Job {} -- {}", job.getId(), message);
        log.info("[DemoTaskExecutor] Job {} -- Demo Task Completed in {}ms", job.getId(), durationMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",     "completed");
        result.put("message",    message);
        result.put("startedAt",  startedAt.toString());
        result.put("finishedAt", finishedAt.toString());
        result.put("durationMs", durationMs);
        jobService.storeResult(job.getId(), result);
    }

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
