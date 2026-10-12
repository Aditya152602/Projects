package com.devtrack.config;

import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import com.devtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner { // runs once after startup

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}") private String adminEmail;
    @Value("${app.admin.password:}") private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) return;  // seeding is optional
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) return;              // idempotent
        userRepository.save(User.builder()
                .name("Admin")
                .email(email)
                .password(passwordEncoder.encode(adminPassword))      // hash, never store raw
                .role(Role.ADMIN)
                .build());
    }
}
