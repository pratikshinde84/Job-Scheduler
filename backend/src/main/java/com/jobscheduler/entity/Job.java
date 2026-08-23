package com.jobscheduler.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    /**
     * Maps to the job status column (VARCHAR + CHECK constraint).
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private JobStatus status = JobStatus.pending;

    @Column(name = "priority")
    @Builder.Default
    private Short priority = 0;

    @Column(name = "scheduled_at")
    @Builder.Default
    private OffsetDateTime scheduledAt = OffsetDateTime.now();

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    /** Stores workers.name of the worker that claimed this job. */
    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "attempt_count")
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 3;

    /**
     * Per-job retry override. Overrides the queue's default_retry_config.
     * Example: {"strategy":"fixed","base_delay_seconds":5,"max_attempts":5}
     */
    @Type(JsonBinaryType.class)
    @Column(name = "retry_config", columnDefinition = "jsonb")
    private Map<String, Object> retryConfig;

    /**
     * Arbitrary job arguments passed to the worker.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    /**
     * Stores executor output (e.g. calculation result). Null for jobs with no
     * output.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobAttempt> attempts = new ArrayList<>();

    public enum JobStatus {
        pending, scheduled, claimed, running, completed, failed, dead
    }
}
