package com.example.ticketing.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.model.BookingResponse;
import com.example.ticketing.model.EventResponse;

@Service
public class EventService {

    private final JdbcTemplate jdbcTemplate;

    public EventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EventResponse> findAll() {
        return jdbcTemplate.query(
            """
            SELECT id, title, event_date, remaining_tickets
            FROM events
            ORDER BY event_date ASC
            """,
            (rs, rowNum) -> new EventResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getTimestamp("event_date").toInstant(),
                rs.getInt("remaining_tickets")
            )
        );
    }

    @Transactional
    public BookingResponse bookTicket(Long eventId, String userEmail) {
        Integer remaining = jdbcTemplate.queryForObject(
            "SELECT remaining_tickets FROM events WHERE id = ? FOR UPDATE",
            Integer.class,
            eventId
        );

        if (remaining == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        if (remaining <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is sold out");
        }

        jdbcTemplate.update(
            "UPDATE events SET remaining_tickets = remaining_tickets - 1 WHERE id = ? AND remaining_tickets > 0",
            eventId
        );

        jdbcTemplate.update(
            "INSERT INTO bookings (event_id, user_email, booked_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
            eventId,
            userEmail
        );

        Integer updatedRemaining = jdbcTemplate.queryForObject(
            "SELECT remaining_tickets FROM events WHERE id = ?",
            Integer.class,
            eventId
        );

        return new BookingResponse(eventId, userEmail, updatedRemaining);
    }
}
