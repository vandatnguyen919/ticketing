package com.example.ticketing.model;

public record BookingResponse(Long eventId, String email, Integer remainingTickets) {
}
