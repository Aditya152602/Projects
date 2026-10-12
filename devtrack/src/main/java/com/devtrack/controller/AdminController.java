package com.devtrack.controller;

import com.devtrack.dto.PageResponse;
import com.devtrack.dto.UserResponse;
import com.devtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")   // SecurityConfig: /api/admin/** requires ROLE_ADMIN -> normal users get 403
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository; // thin read-only endpoint, so repo used directly

    @GetMapping("/users")
    public PageResponse<UserResponse> allUsers(@PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
    }
}
