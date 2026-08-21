package com.jobscheduler.repository;

import com.jobscheduler.entity.DeadLetterEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntry, UUID> {

    List<DeadLetterEntry> findByJobId(UUID jobId);

    /**
     * Fetch dead-letter entries for jobs belonging to a user's queues.
     */
    @Query("""
            SELECT d FROM DeadLetterEntry d
            JOIN d.job j
            JOIN j.queue q
            JOIN q.project p
            WHERE p.user.id = :userId
            ORDER BY d.diedAt DESC
            """)
    Page<DeadLetterEntry> findByUserId(@Param("userId") UUID userId, Pageable pageable);
}
