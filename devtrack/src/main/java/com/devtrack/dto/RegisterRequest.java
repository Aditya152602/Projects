package com.devtrack.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest( // record = immutable DTO, no boilerplate
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72) String password // 72 = BCrypt's max input bytes
) {}
