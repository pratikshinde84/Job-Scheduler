package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.Notification;
import com.jobscheduler.entity.User;
import com.jobscheduler.repository.NotificationRepository;
import com.jobscheduler.repository.UserRepository;
import com.jobscheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * NotificationJobExecutor
 *
 * Looks up a user by email, sends them an in-app / push / sms notification
 * by storing it in the notifications table (inbox), and stores the delivery
 * result back on the job record.
 *
 * Expected payload:
 * {
 *   "userEmail" : "user@example.com"         -- recipient email (required)
 *   "message"   : "Your job is completed"    -- notification body  (required)
 *   "channel"   : "in-app"                   -- push | sms | in-app (default: in-app)
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJobExecutor implements JobExecutor {

    private final UserRepository         userRepository;
    private final NotificationRepository notificationRepository;
    private final JobService             jobService;

    private static final String DEFAULT_CHANNEL = "in-app";

    @Override
    public String queueName() {
        return "Notification";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        String userEmail = getString(payload, "userEmail");
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "NotificationJobExecutor: 'userEmail' is required");
        }

        String message = getString(payload, "message");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "NotificationJobExecutor: 'message' is required");
        }

        String channel = getString(payload, "channel");
        if (channel == null || channel.isBlank()) channel = DEFAULT_CHANNEL;

        // Look up recipient by email
        Optional<User> recipientOpt = userRepository.findByEmail(userEmail);
        if (recipientOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "NotificationJobExecutor: no user found with email '" + userEmail + "'");
        }
        User recipient = recipientOpt.get();

        log.info("[NotificationJobExecutor] Job {} -- Sending {} notification to {}",
                job.getId(), channel, userEmail);

        // Simulate channel-specific delivery (replace with real FCM/APNs/SMS)
        Thread.sleep(deliveryDelayMs(channel));

        // Persist to inbox
        Notification notification = Notification.builder()
                .user(recipient)
                .title("Job Scheduler Notification")
                .message(message)
                .channel(channel)
                .build();
        notificationRepository.save(notification);

        Instant deliveredAt = Instant.now();
        log.info("[NotificationJobExecutor] Job {} -- Delivered via {} to {}",
                job.getId(), channel, userEmail);

        // Store result on the job
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",      "delivered");
        result.put("userEmail",   userEmail);
        result.put("channel",     channel);
        result.put("message",     message);
        result.put("deliveredAt", deliveredAt.toString());
        jobService.storeResult(job.getId(), result);
    }

    private long deliveryDelayMs(String channel) {
        return switch (channel.toLowerCase()) {
            case "push" -> 200;
            case "sms"  -> 250;
            default     -> 100;
        };
    }

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
