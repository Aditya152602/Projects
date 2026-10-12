package com.devtrack.dto;

import jakarta.validation.constraints.*;

public record ProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description
) {}
