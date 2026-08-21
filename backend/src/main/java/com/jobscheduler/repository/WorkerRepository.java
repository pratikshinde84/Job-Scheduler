package com.jobscheduler.repository;

import com.jobscheduler.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<Worker> findByName(String name);

    List<Worker> findByStatus(Worker.WorkerStatus status);

    /**
     * Mark a worker's heartbeat and status in one update.
     * Uses a native query with explicit cast to satisfy PostgreSQL's worker_status enum.
     */
    @Modifying
    @Query(value = """
            UPDATE workers
            SET last_heartbeat_at = :now,
                status = CAST(:status AS worker_status)
            WHERE name = :name
            """, nativeQuery = true)
    int upsertHeartbeat(@Param("name") String name,
                        @Param("now") OffsetDateTime now,
                        @Param("status") String status);

    /**
     * Find workers whose heartbeat has gone stale (potential offline detection).
     */
    @Query("""
            SELECT w FROM Worker w
            WHERE w.status = 'active'
            AND w.lastHeartbeatAt < :threshold
            """)
    List<Worker> findStaleWorkers(@Param("threshold") OffsetDateTime threshold);
}
