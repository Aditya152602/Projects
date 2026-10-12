package com.devtrack.dto;

import com.devtrack.entity.Project;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponse(Long id, String name, String description, LocalDateTime createdAt,
                              UserResponse owner, List<UserResponse> members) {
    // Call only inside a @Transactional method: owner/members are LAZY
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getCreatedAt(),
                UserResponse.from(p.getOwner()),
                p.getMembers().stream().map(UserResponse::from).toList());
    }
}
