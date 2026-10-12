package com.devtrack.dto;

import com.devtrack.entity.Role;
import com.devtrack.entity.User;

// Never expose the entity directly -> password hash must not leak into JSON
public record UserResponse(Long id, String name, String email, Role role) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
