package com.jobscheduler.repository;

import com.jobscheduler.entity.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobAttemptRepository extends JpaRepository<JobAttempt, Long> {

    List<JobAttempt> findByJobIdOrderByStartedAtDesc(UUID jobId);

    long countByJobId(UUID jobId);
}
