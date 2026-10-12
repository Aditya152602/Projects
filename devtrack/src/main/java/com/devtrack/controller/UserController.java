package com.devtrack.controller;

import com.devtrack.dto.UserResponse;
import com.devtrack.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) { // user injected from the JWT filter's SecurityContext
        return UserResponse.from(user);
    }
}
