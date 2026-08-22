package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DemoTaskExecutor
 *
 * Simulates a long-running task to test worker execution, concurrency, and retries.
 *
 * Expected payload:
 * {
 *   "duration"   : <int>     — how many seconds the task should run (default: 5)
 *   "shouldFail" : <boolean> — if true, the job throws an exception to trigger retry logic
 * }
 */
@Slf4j
@Component
public class DemoTaskExecutor implements JobExecutor {

    @Override
    public String queueName() {
        return "Demo-Task";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        int durationSeconds = getInt(payload, "duration", 5);
        boolean shouldFail  = getBool(payload, "shouldFail", false);

        log.info("[DemoTaskExecutor] Job {} — running for {} s, shouldFail={}",
                job.getId(), durationSeconds, shouldFail);

        // Simulate work in 500 ms chunks so logs are visible
        int chunks = Math.max(1, durationSeconds * 2);
        for (int i = 0; i < chunks; i++) {
            Thread.sleep(500);
            log.debug("[DemoTaskExecutor] Job {} — step {}/{}", job.getId(), i + 1, chunks);
        }

        if (shouldFail) {
            throw new RuntimeException(
                    "DemoTaskExecutor: intentional failure requested via shouldFail=true");
        }

        log.info("[DemoTaskExecutor] Job {} completed successfully", job.getId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int getInt(Map<String, Object> payload, String key, int defaultVal) {
        Object v = payload.get(key);
        if (v instanceof Number n) return n.intValue();
        return defaultVal;
    }

    private boolean getBool(Map<String, Object> payload, String key, boolean defaultVal) {
        Object v = payload.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }
}
