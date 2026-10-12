package com.devtrack.service;

import com.devtrack.dto.PageResponse;
import com.devtrack.dto.TaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.entity.*;
import com.devtrack.exception.ApiException;
import com.devtrack.repository.TaskRepository;
import com.devtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;   // reuse project access rules

    @Transactional
    public TaskResponse create(Long projectId, TaskRequest req, User current) {
        Project project = projectService.findProjectForUser(projectId, current); // 404/403 checks
        Task task = new Task();
        task.setProject(project);
        apply(task, req, project);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listForProject(Long projectId, TaskStatus status, TaskPriority priority,
                                                     Pageable pageable, User current) {
        projectService.findProjectForUser(projectId, current);                   // access check only
        return PageResponse.from(taskRepository.search(projectId, status, priority, pageable)
                .map(TaskResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listAssignedToMe(TaskStatus status, Pageable pageable, User current) {
        return PageResponse.from(taskRepository.findAssignedTo(current.getId(), status, pageable)
                .map(TaskResponse::from));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long taskId, User current) {
        Task task = findTask(taskId);
        projectService.findProjectForUser(task.getProject().getId(), current);   // must have access to its project
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(Long taskId, TaskRequest req, User current) {
        Task task = findTask(taskId);
        Project project = projectService.findProjectForUser(task.getProject().getId(), current);
        apply(task, req, project);
        return TaskResponse.from(task);                                          // dirty-checking saves it on commit
    }

    @Transactional
    public void delete(Long taskId, User current) {
        Task task = findTask(taskId);
        Project project = projectService.findProjectForUser(task.getProject().getId(), current);
        projectService.requireOwnerOrAdmin(project, current);                    // only owner/admin deletes tasks
        taskRepository.delete(task);
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    // Copy request fields onto entity, validating assignee belongs to the project
    private void apply(Task task, TaskRequest req, Project project) {
        task.setTitle(req.title().trim());
        task.setDescription(req.description());
        task.setDueDate(req.dueDate());
        if (req.status() != null) task.setStatus(req.status());         // null keeps current/default
        if (req.priority() != null) task.setPriority(req.priority());

        if (req.assigneeId() == null) {
            task.setAssignee(null);                                     // full-replace semantics: null = unassign
        } else {
            User assignee = userRepository.findById(req.assigneeId())
                    .orElseThrow(() -> ApiException.badRequest("Assignee does not exist"));
            if (!projectService.isParticipant(project, assignee)) {
                throw ApiException.badRequest("Assignee must be the owner or a member of the project");
            }
            task.setAssignee(assignee);
        }
    }
}
