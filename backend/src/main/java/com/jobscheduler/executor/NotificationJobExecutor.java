package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * NotificationJobExecutor
 *
 * Creates / sends a notification message to a user.
 * In production, plug in your push-notification provider
 * (FCM, APNs, WebSockets, etc.) in place of the log statements.
 *
 * Expected payload:
 * {
 *   "userId"  : 101                       — target user ID (required)
 *   "message" : "Your job is completed"   — notification text (required)
 *   "channel" : "push"                    — delivery channel: push | sms | in-app (optional, default: in-app)
 * }
 */
@Slf4j
@Component
public class NotificationJobExecutor implements JobExecutor {

    private static final String DEFAULT_CHANNEL = "in-app";

    @Override
    public String queueName() {
        return "Notification";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        Object userIdRaw = payload.get("userId");
        if (userIdRaw == null) {
            throw new IllegalArgumentException("NotificationJobExecutor: 'userId' field is required");
        }
        long userId = ((Number) userIdRaw).longValue();

        String message = getString(payload, "message");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("NotificationJobExecutor: 'message' field is required");
        }

        String channel = getString(payload, "channel");
        if (channel == null || channel.isBlank()) channel = DEFAULT_CHANNEL;

        log.info("[NotificationJobExecutor] Job {} — Sending {} notification to userId={}",
                job.getId(), channel, userId);

        // ── Simulate notification delivery ───────────────────────────────────
        // Replace with real FCM / APNs / WebSocket call here.
        switch (channel.toLowerCase()) {
            case "push"   -> simulatePush(userId, message);
            case "sms"    -> simulateSms(userId, message);
            default       -> simulateInApp(userId, message);
        }

        log.info("[NotificationJobExecutor] Job {} — Notification delivered (channel={}, userId={})",
                job.getId(), channel, userId);
    }

    // ── simulated delivery methods (replace with real implementations) ────────

    private void simulatePush(long userId, String message) throws InterruptedException {
        log.info("[NotificationJobExecutor]   [PUSH]   → userId={} msg='{}'", userId, message);
        Thread.sleep(200);
    }

    private void simulateSms(long userId, String message) throws InterruptedException {
        log.info("[NotificationJobExecutor]   [SMS]    → userId={} msg='{}'", userId, message);
        Thread.sleep(250);
    }

    private void simulateInApp(long userId, String message) throws InterruptedException {
        log.info("[NotificationJobExecutor]   [IN-APP] → userId={} msg='{}'", userId, message);
        Thread.sleep(100);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
