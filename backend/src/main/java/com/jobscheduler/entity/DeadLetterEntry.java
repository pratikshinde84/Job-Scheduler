package com.jobscheduler.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Nullable — the original job may be deleted while this record is kept. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @Type(JsonBinaryType.class)
    @Column(name = "original_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> originalPayload;

    @Column(name = "error", columnDefinition = "text", nullable = false)
    private String error;

    @Column(name = "died_at")
    @Builder.Default
    private OffsetDateTime diedAt = OffsetDateTime.now();
}
