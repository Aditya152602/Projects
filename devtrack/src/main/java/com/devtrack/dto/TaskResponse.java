package com.devtrack.dto;

import com.devtrack.entity.Task;
import com.devtrack.entity.TaskPriority;
import com.devtrack.entity.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(Long id, String title, String description, TaskStatus status,
                           TaskPriority priority, UserResponse assignee, LocalDate dueDate,
                           LocalDateTime createdAt, LocalDateTime updatedAt, Long projectId) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(),
                t.getPriority(),
                t.getAssignee() == null ? null : UserResponse.from(t.getAssignee()),
                t.getDueDate(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getProject().getId());  // reading the id of a lazy proxy does not hit the DB
    }
}
