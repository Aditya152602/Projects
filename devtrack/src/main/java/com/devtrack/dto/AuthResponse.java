package com.devtrack.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInMs) {}
