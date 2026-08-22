package com.jobscheduler.controller;

import com.jobscheduler.entity.Notification;
import com.jobscheduler.entity.User;
import com.jobscheduler.repository.NotificationRepository;
import com.jobscheduler.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /** GET /api/notifications — all notifications for the current user */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> list() {
        User user = UserContext.get();
        List<NotificationDto> list = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(NotificationDto::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    /** GET /api/notifications/unread-count */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        User user = UserContext.get();
        long count = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** POST /api/notifications/mark-all-read */
    @PostMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<Void> markAllRead() {
        User user = UserContext.get();
        notificationRepository.markAllReadForUser(user.getId());
        return ResponseEntity.noContent().build();
    }

    /** POST /api/notifications/{id}/mark-read */
    @PostMapping("/{id}/mark-read")
    @Transactional
    public ResponseEntity<Void> markOneRead(@PathVariable UUID id) {
        User user = UserContext.get();
        notificationRepository.markOneRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Response DTO ─────────────────────────────────────────────────────────

    public record NotificationDto(
            UUID            id,
            String          title,
            String          message,
            String          channel,
            boolean         isRead,
            OffsetDateTime  createdAt
    ) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(
                    n.getId(),
                    n.getTitle(),
                    n.getMessage(),
                    n.getChannel(),
                    Boolean.TRUE.equals(n.getIsRead()),
                    n.getCreatedAt()
            );
        }
    }
}
