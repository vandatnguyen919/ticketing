package com.example.ticketing.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.example.ticketing.model.UserProfile;

@Service
public class JwtService {

    private final String secret;
    private final long expirationMs;

    public JwtService(
        @Value("${app.security.jwt-secret}") String secret,
        @Value("${app.security.jwt-expiration-ms}") long expirationMs
    ) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public IssuedToken generateToken(UserProfile profile) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);
        String token = Jwts.builder()
            .subject(profile.email())
            .issuer("ticketing-backend")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("provider", profile.provider())
            .claim("providerId", profile.providerId())
            .claim("name", profile.name())
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
        return new IssuedToken(token, expiresAt);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
