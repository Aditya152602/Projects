package com.devtrack.security;

import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // Build base64 secrets in code: 48 identical chars = 384 bits, enough for HS256
    private static String secretOf(char c) {
        return Base64.getEncoder().encodeToString(String.valueOf(c).repeat(48).getBytes());
    }

    private final User user = User.builder().email("a@x.com").role(Role.USER).build();

    @Test
    void generatedTokenCanBeParsedBackToEmail() {
        JwtService service = new JwtService(secretOf('a'), 60_000);   // 1 min lifetime
        String token = service.generateToken(user);
        assertEquals("a@x.com", service.extractEmail(token));         // subject round-trips
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String forged = new JwtService(secretOf('b'), 60_000).generateToken(user); // attacker key
        JwtService real = new JwtService(secretOf('a'), 60_000);
        assertThrows(JwtException.class, () -> real.extractEmail(forged));         // signature mismatch
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(secretOf('a'), -1_000);   // already expired when created
        String token = service.generateToken(user);
        assertThrows(ExpiredJwtException.class, () -> service.extractEmail(token));
    }

    @Test
    void garbageTokenIsRejected() {
        JwtService service = new JwtService(secretOf('a'), 60_000);
        assertThrows(JwtException.class, () -> service.extractEmail("not.a.jwt"));
    }

    @Test
    void tooShortSecretFailsFast() {
        String weak = Base64.getEncoder().encodeToString("short".getBytes()); // 40 bits < 256 bits
        assertThrows(RuntimeException.class, () -> new JwtService(weak, 60_000));
    }
}
