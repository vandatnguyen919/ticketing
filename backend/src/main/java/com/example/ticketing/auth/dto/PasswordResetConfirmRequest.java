package com.example.ticketing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
    @NotBlank String resetTicket,
    @NotBlank @Size(min = 10, max = 72) String newPassword
) {
}
