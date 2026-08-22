package com.jobscheduler.repository;

import com.jobscheduler.entity.JobAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobAttemptRepository extends JpaRepository<JobAttempt, Long> {

    List<JobAttempt> findByJobIdOrderByStartedAtDesc(UUID jobId);

    long countByJobId(UUID jobId);

    @Query("""
            SELECT a FROM JobAttempt a
            JOIN a.job j
            JOIN j.queue q
            JOIN q.project p
            WHERE p.user.id = :userId
            ORDER BY a.startedAt DESC
            """)
    List<JobAttempt> findRecentAttemptsByUserId(@Param("userId") UUID userId, Pageable pageable);
}
