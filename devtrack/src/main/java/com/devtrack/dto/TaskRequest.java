package com.devtrack.dto;

import com.devtrack.entity.TaskPriority;
import com.devtrack.entity.TaskStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        TaskStatus status,        // null -> TODO on create, unchanged on update
        TaskPriority priority,    // null -> MEDIUM on create, unchanged on update
        Long assigneeId,          // null -> unassigned
        LocalDate dueDate
) {}
