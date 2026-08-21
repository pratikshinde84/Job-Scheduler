package com.jobscheduler.repository;

import com.jobscheduler.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT p FROM Project p JOIN FETCH p.queues WHERE p.id = :id AND p.user.id = :userId")
    Optional<Project> findByIdAndUserIdWithQueues(@Param("id") UUID id, @Param("userId") UUID userId);
}
