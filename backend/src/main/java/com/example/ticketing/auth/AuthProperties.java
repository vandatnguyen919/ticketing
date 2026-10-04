package com.example.ticketing.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    Otp otp,
    ResetTicket resetTicket,
    Duration resetRequestCooldown,
    Mail mail
) {

    public record Otp(Duration ttl, int maxAttempts, int length) {
    }

    public record ResetTicket(Duration ttl) {
    }

    public record Mail(String from) {
    }
}
