package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * EmailJobExecutor
 *
 * Sends a real email using Spring's JavaMailSender (configured via
 * spring.mail.* properties in application.properties).
 *
 * Expected payload:
 * {
 *   "to"      : "user@example.com"   — recipient address (required)
 *   "subject" : "Welcome"            — email subject    (required)
 *   "body"    : "Hello!"             — plain-text body  (required)
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailJobExecutor implements JobExecutor {

    private final JavaMailSender mailSender;

    @Override
    public String queueName() {
        return "email";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        String to      = getString(payload, "to");
        String subject = getString(payload, "subject");
        String body    = getString(payload, "body");

        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("EmailJobExecutor: 'to' field is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("EmailJobExecutor: 'subject' field is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("EmailJobExecutor: 'body' field is required");
        }

        log.info("[EmailJobExecutor] Job {} — Sending email to={} subject='{}'",
                job.getId(), to, subject);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        // 'from' address is set via spring.mail.properties.mail.smtp.from or spring.mail.username

        mailSender.send(message);

        log.info("[EmailJobExecutor] Job {} — Email sent successfully to {}", job.getId(), to);
    }

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
