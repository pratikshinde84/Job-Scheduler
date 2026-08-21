package com.jobscheduler.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "queues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Queue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "concurrency_limit")
    @Builder.Default
    private Integer concurrencyLimit = 5;

    @Column(name = "is_paused")
    @Builder.Default
    private Boolean isPaused = false;

    /**
     * JSONB column storing retry strategy.
     * Example: {"strategy":"exponential","base_delay_seconds":1,"max_attempts":3}
     */
    @Type(JsonBinaryType.class)
    @Column(name = "default_retry_config", columnDefinition = "jsonb")
    private Map<String, Object> defaultRetryConfig;

    @OneToMany(mappedBy = "queue", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Job> jobs = new ArrayList<>();
}
