package com.devtrack.controller;

import com.devtrack.dto.*;
import com.devtrack.entity.User;
import com.devtrack.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest req, @AuthenticationPrincipal User user) {
        return projectService.create(req, user);
    }

    // GET /api/projects?page=0&size=10&sort=createdAt,desc
    @GetMapping
    public PageResponse<ProjectResponse> list(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User user) {
        return projectService.list(user, pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return projectService.get(id, user);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req,
                                  @AuthenticationPrincipal User user) {
        return projectService.update(id, req, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)                       // 204
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        projectService.delete(id, user);
    }

    @PostMapping("/{id}/members")
    public ProjectResponse addMember(@PathVariable Long id, @Valid @RequestBody MemberRequest req,
                                     @AuthenticationPrincipal User user) {
        return projectService.addMember(id, req, user);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ProjectResponse removeMember(@PathVariable Long id, @PathVariable Long userId,
                                        @AuthenticationPrincipal User user) {
        return projectService.removeMember(id, userId, user);
    }
}
