package com.devtrack.controller;

import com.devtrack.dto.*;
import com.devtrack.entity.TaskPriority;
import com.devtrack.entity.TaskStatus;
import com.devtrack.entity.User;
import com.devtrack.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable Long projectId, @Valid @RequestBody TaskRequest req,
                               @AuthenticationPrincipal User user) {
        return taskService.create(projectId, req, user);
    }

    // GET /api/projects/1/tasks?status=IN_PROGRESS&priority=HIGH&page=0&size=10&sort=dueDate,asc
    @GetMapping("/projects/{projectId}/tasks")
    public PageResponse<TaskResponse> listForProject(
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,       // optional filters (bad value -> 400)
            @RequestParam(required = false) TaskPriority priority,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User user) {
        return taskService.listForProject(projectId, status, priority, pageable, user);
    }

    // GET /api/tasks?status=TODO  -> tasks assigned to me, across all projects
    @GetMapping("/tasks")
    public PageResponse<TaskResponse> myTasks(
            @RequestParam(required = false) TaskStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User user) {
        return taskService.listAssignedToMe(status, pageable, user);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskResponse get(@PathVariable Long taskId, @AuthenticationPrincipal User user) {
        return taskService.get(taskId, user);
    }

    @PutMapping("/tasks/{taskId}")
    public TaskResponse update(@PathVariable Long taskId, @Valid @RequestBody TaskRequest req,
                               @AuthenticationPrincipal User user) {
        return taskService.update(taskId, req, user);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long taskId, @AuthenticationPrincipal User user) {
        taskService.delete(taskId, user);
    }
}
