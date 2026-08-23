package com.jobscheduler.repository;

import com.jobscheduler.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueRepository extends JpaRepository<Queue, UUID> {

    @Query("""
            SELECT q FROM Queue q
            WHERE q.project.id = :projectId
            ORDER BY CASE WHEN LOWER(q.name) LIKE '%email%' THEN 1 ELSE 0 END ASC, q.name ASC
            """)
    List<Queue> findByProjectId(@Param("projectId") UUID projectId);

    Optional<Queue> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<Queue> findByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    /**
     * Fetch all active (non-paused) queues. Used by the JobPoller.
     */
    @Query("SELECT q FROM Queue q WHERE q.isPaused = false")
    List<Queue> findAllActive();

    /**
     * Fetch queues belonging to projects owned by a given user.
     */
    @Query("""
            SELECT q FROM Queue q
            JOIN q.project p
            WHERE p.user.id = :userId
            """)
    List<Queue> findByUserId(@Param("userId") UUID userId);
}
