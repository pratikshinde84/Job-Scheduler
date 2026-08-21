package com.jobscheduler.service;

import com.jobscheduler.entity.Project;
import com.jobscheduler.entity.User;
import com.jobscheduler.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<Project> listForUser(UUID userId) {
        return projectRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Project getForUser(UUID projectId, UUID userId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Project not found: " + projectId));
    }

    @Transactional
    public Project create(User owner, String name) {
        Project project = Project.builder()
                .user(owner)
                .name(name)
                .apiKeyPrefix(generateKeyPrefix())
                .build();
        return projectRepository.save(project);
    }

    @Transactional
    public Project rename(UUID projectId, UUID userId, String newName) {
        Project project = getForUser(projectId, userId);
        project.setName(newName);
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(UUID projectId, UUID userId) {
        Project project = getForUser(projectId, userId);
        projectRepository.delete(project);
    }

    private String generateKeyPrefix() {
        return "js_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
