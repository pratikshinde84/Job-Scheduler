package com.jobscheduler.service;

import com.jobscheduler.entity.Project;
import com.jobscheduler.entity.Queue;
import com.jobscheduler.repository.ProjectRepository;
import com.jobscheduler.repository.QueueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<Queue> listForProject(UUID projectId, UUID userId) {
        verifyOwnership(projectId, userId);
        return queueRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Queue getForProject(UUID queueId, UUID projectId, UUID userId) {
        verifyOwnership(projectId, userId);
        return queueRepository.findByIdAndProjectId(queueId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("Queue not found: " + queueId));
    }

    @Transactional
    public Queue create(UUID projectId, UUID userId, String name,
                        int concurrencyLimit, Map<String, Object> retryConfig) {
        Project project = verifyOwnership(projectId, userId);

        if (queueRepository.existsByProjectIdAndName(projectId, name)) {
            throw new IllegalArgumentException(
                    "Queue '" + name + "' already exists in project " + projectId);
        }

        Queue queue = Queue.builder()
                .project(project)
                .name(name)
                .concurrencyLimit(concurrencyLimit)
                .defaultRetryConfig(retryConfig)
                .build();
        return queueRepository.save(queue);
    }

    @Transactional
    public Queue update(UUID queueId, UUID projectId, UUID userId,
                        Integer concurrencyLimit, Boolean isPaused,
                        Map<String, Object> retryConfig) {
        Queue queue = getForProject(queueId, projectId, userId);

        if (concurrencyLimit != null) queue.setConcurrencyLimit(concurrencyLimit);
        if (isPaused != null) queue.setIsPaused(isPaused);
        if (retryConfig != null) queue.setDefaultRetryConfig(retryConfig);

        return queueRepository.save(queue);
    }

    @Transactional
    public void pause(UUID queueId, UUID projectId, UUID userId) {
        Queue queue = getForProject(queueId, projectId, userId);
        queue.setIsPaused(true);
        queueRepository.save(queue);
    }

    @Transactional
    public void resume(UUID queueId, UUID projectId, UUID userId) {
        Queue queue = getForProject(queueId, projectId, userId);
        queue.setIsPaused(false);
        queueRepository.save(queue);
    }

    @Transactional
    public void delete(UUID queueId, UUID projectId, UUID userId) {
        Queue queue = getForProject(queueId, projectId, userId);
        queueRepository.delete(queue);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Project verifyOwnership(UUID projectId, UUID userId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Project not found or not owned by user: " + projectId));
    }
}
