package com.example.ticketing.auth.dto;

public record PasswordResetRequestResponse(String status, String message) {

    public static PasswordResetRequestResponse accepted() {
        return new PasswordResetRequestResponse(
            "RESET_REQUEST_ACCEPTED",
            "If an account exists for this email, a reset code has been sent."
        );
    }
}
