package com.example.ticketing.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetVerifyRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(max = 32) String code
) {
}
