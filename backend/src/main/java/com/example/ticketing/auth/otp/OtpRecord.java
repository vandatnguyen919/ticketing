package com.example.ticketing.auth.otp;

import java.time.Instant;

public record OtpRecord(String codeHash, Instant createdAt, int attempts) {
}
