package com.jobscheduler.controller;

import com.jobscheduler.dto.QueueRequest;
import com.jobscheduler.dto.QueueResponse;
import com.jobscheduler.entity.User;
import com.jobscheduler.security.UserContext;
import com.jobscheduler.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /** GET /api/projects/{projectId}/queues */
    @GetMapping
    public ResponseEntity<List<QueueResponse>> list(@PathVariable UUID projectId) {
        User user = UserContext.get();
        List<QueueResponse> queues = queueService.listForProject(projectId, user.getId())
                .stream()
                .map(QueueResponse::from)
                .toList();
        return ResponseEntity.ok(queues);
    }

    /** GET /api/projects/{projectId}/queues/{queueId} */
    @GetMapping("/{queueId}")
    public ResponseEntity<QueueResponse> get(
            @PathVariable UUID projectId,
            @PathVariable UUID queueId) {
        User user = UserContext.get();
        return ResponseEntity.ok(
                QueueResponse.from(queueService.getForProject(queueId, projectId, user.getId())));
    }

    /** POST /api/projects/{projectId}/queues */
    @PostMapping
    public ResponseEntity<QueueResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody QueueRequest req) {
        User user = UserContext.get();
        QueueResponse created = QueueResponse.from(
                queueService.create(projectId, user.getId(),
                        req.name(),
                        req.concurrencyLimit() > 0 ? req.concurrencyLimit() : 5,
                        req.defaultRetryConfig()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PATCH /api/projects/{projectId}/queues/{queueId} */
    @PatchMapping("/{queueId}")
    public ResponseEntity<QueueResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID queueId,
            @RequestBody Map<String, Object> body) {
        User user = UserContext.get();

        Integer concurrency = body.containsKey("concurrencyLimit")
                ? ((Number) body.get("concurrencyLimit")).intValue() : null;
        Boolean paused = body.containsKey("isPaused")
                ? (Boolean) body.get("isPaused") : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> retryConfig = body.containsKey("defaultRetryConfig")
                ? (Map<String, Object>) body.get("defaultRetryConfig") : null;

        return ResponseEntity.ok(QueueResponse.from(
                queueService.update(queueId, projectId, user.getId(),
                        concurrency, paused, retryConfig)));
    }

    /** POST /api/projects/{projectId}/queues/{queueId}/pause */
    @PostMapping("/{queueId}/pause")
    public ResponseEntity<Void> pause(
            @PathVariable UUID projectId,
            @PathVariable UUID queueId) {
        User user = UserContext.get();
        queueService.pause(queueId, projectId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /** POST /api/projects/{projectId}/queues/{queueId}/resume */
    @PostMapping("/{queueId}/resume")
    public ResponseEntity<Void> resume(
            @PathVariable UUID projectId,
            @PathVariable UUID queueId) {
        User user = UserContext.get();
        queueService.resume(queueId, projectId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/projects/{projectId}/queues/{queueId} */
    @DeleteMapping("/{queueId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID queueId) {
        User user = UserContext.get();
        queueService.delete(queueId, projectId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
