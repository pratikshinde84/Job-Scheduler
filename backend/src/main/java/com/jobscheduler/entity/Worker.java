package com.jobscheduler.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    /**
     * Maps to the worker_status column (VARCHAR + CHECK constraint).
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WorkerStatus status = WorkerStatus.offline;

    public enum WorkerStatus {
        active, draining, offline
    }
}
