package com.jobscheduler.repository;

import com.jobscheduler.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByQueueId(UUID queueId, Pageable pageable);

    Page<Job> findByQueueIdAndStatus(UUID queueId, Job.JobStatus status, Pageable pageable);

    long countByQueueIdAndStatus(UUID queueId, Job.JobStatus status);

    /**
     * Core polling query — atomically claims up to `limit` runnable jobs for a queue
     * using FOR UPDATE SKIP LOCKED to prevent double-claiming across concurrent workers.
     *
     * Logic:
     *  1. SELECT jobs WHERE status IN (pending, scheduled) AND scheduled_at <= NOW()
     *     ORDER BY priority DESC, scheduled_at ASC
     *     FOR UPDATE SKIP LOCKED
     *     LIMIT :limit
     *  2. UPDATE those rows to status=claimed, locked_by=:workerName, locked_at=NOW()
     *
     * Returns the UUIDs of claimed jobs so the caller can load full entities.
     */
    @Modifying
    @Query(value = """
            UPDATE jobs
            SET status     = 'claimed',
                locked_by  = :workerName,
                locked_at  = NOW(),
                updated_at = NOW()
            WHERE id IN (
                SELECT id FROM jobs
                WHERE queue_id   = :queueId
                  AND status     IN ('pending', 'scheduled')
                  AND scheduled_at <= NOW()
                ORDER BY priority DESC, scheduled_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
            )
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimJobs(@Param("queueId") UUID queueId,
                         @Param("workerName") String workerName,
                         @Param("limit") int limit);

    /**
     * Promote scheduled jobs whose scheduled_at has passed to pending.
     * Called by CronScheduler every 10 seconds.
     */
    @Modifying
    @Query(value = """
            UPDATE jobs
            SET status = 'pending', updated_at = NOW()
            WHERE status = 'scheduled'
              AND scheduled_at <= NOW()
            """, nativeQuery = true)
    int promoteScheduledJobs();

    /**
     * Reaper: find claimed jobs locked longer than the timeout threshold.
     */
    @Query("""
            SELECT j FROM Job j
            WHERE j.status = 'claimed'
              AND j.lockedAt < :threshold
            """)
    List<Job> findStaleClaimed(@Param("threshold") OffsetDateTime threshold);

    /**
     * Mark a job as running once the worker actually starts executing it.
     */
    @Modifying
    @Query("""
            UPDATE Job j
            SET j.status = 'running',
                j.attemptCount = j.attemptCount + 1
            WHERE j.id = :jobId AND j.lockedBy = :workerName
            """)
    int markRunning(@Param("jobId") UUID jobId, @Param("workerName") String workerName);

    /**
     * Dashboard: counts grouped by status for a set of queue IDs.
     */
    @Query("""
            SELECT j.status AS status, COUNT(j) AS cnt
            FROM Job j
            WHERE j.queue.id IN :queueIds
            GROUP BY j.status
            """)
    List<Object[]> countByStatusForQueues(@Param("queueIds") List<UUID> queueIds);

    /**
     * Fetch jobs owned (transitively through queue→project→user) by a user.
     */
    @Query("""
            SELECT j FROM Job j
            JOIN j.queue q
            JOIN q.project p
            WHERE p.user.id = :userId
            ORDER BY j.createdAt DESC
            """)
    Page<Job> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    Optional<Job> findByIdAndQueueId(UUID id, UUID queueId);
}
