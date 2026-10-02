package com.example.ticketing.model;

import java.time.Instant;

public record EventResponse(Long id, String title, Instant eventDate, Integer remainingTickets) {
}
