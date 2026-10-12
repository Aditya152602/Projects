package com.devtrack.service;

import com.devtrack.dto.*;
import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import com.devtrack.exception.ApiException;
import com.devtrack.repository.UserRepository;
import com.devtrack.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();               // normalize so A@x.com == a@x.com
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");   // -> HTTP 409
        }
        User user = User.builder()
                .name(req.name().trim())
                .email(email)
                .password(passwordEncoder.encode(req.password()))      // BCrypt hash
                .role(Role.USER)                                       // public signup is always USER, never ADMIN
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest req) {
        // Checks email+password against DB hash. Throws BadCredentialsException on failure -> 401
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email().trim().toLowerCase(), req.password()));
        User user = (User) auth.getPrincipal();                        // our User entity (implements UserDetails)
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpirationMs());
    }
}
