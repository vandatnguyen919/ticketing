package com.example.ticketing.auth.dto;

public record PasswordResetVerifyResponse(String resetTicket, long expiresInSeconds) {
}
