package com.devtrack.security;

import com.devtrack.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;        // HMAC signing key
    private final long expirationMs;    // token lifetime

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret)); // throws if key < 256 bits
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())                   // "who" the token is for
                .claim("role", user.getRole().name())       // extra info for clients (server still reloads user from DB)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)                              // signature -> tampering is detected
                .compact();                                 // -> "xxxxx.yyyyy.zzzzz"
    }

    // Verifies signature AND expiry. Throws JwtException if anything is wrong.
    public String extractEmail(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }

    public long getExpirationMs() { return expirationMs; }
}
