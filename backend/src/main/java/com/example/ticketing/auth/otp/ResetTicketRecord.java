package com.example.ticketing.auth.otp;

import java.time.Instant;

public record ResetTicketRecord(String accountEmail, Instant createdAt) {
}
