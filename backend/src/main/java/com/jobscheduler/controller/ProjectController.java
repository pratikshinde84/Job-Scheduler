package com.jobscheduler.controller;

import com.jobscheduler.dto.PageResponse;
import com.jobscheduler.dto.ProjectRequest;
import com.jobscheduler.dto.ProjectResponse;
import com.jobscheduler.entity.User;
import com.jobscheduler.security.UserContext;
import com.jobscheduler.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** GET /api/projects — list all projects for the authenticated user */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list() {
        User user = UserContext.get();
        List<ProjectResponse> projects = projectService.listForUser(user.getId())
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(projects);
    }

    /** GET /api/projects/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable UUID id) {
        User user = UserContext.get();
        return ResponseEntity.ok(
                ProjectResponse.from(projectService.getForUser(id, user.getId())));
    }

    /** POST /api/projects */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest req) {
        User user = UserContext.get();
        ProjectResponse created = ProjectResponse.from(
                projectService.create(user, req.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/projects/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> rename(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest req) {
        User user = UserContext.get();
        return ResponseEntity.ok(
                ProjectResponse.from(projectService.rename(id, user.getId(), req.name())));
    }

    /** DELETE /api/projects/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        User user = UserContext.get();
        projectService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
