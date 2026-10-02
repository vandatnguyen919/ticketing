package com.example.ticketing.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.model.BookingResponse;
import com.example.ticketing.model.EventResponse;
import com.example.ticketing.service.EventService;

@RestController
@RequestMapping(path = "/api/{version}", version = "1.0")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public List<EventResponse> getEvents() {
        return eventService.findAll();
    }

    @PostMapping("/events/{eventId}/book")
    public ResponseEntity<BookingResponse> book(@PathVariable Long eventId, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authentication cookie is required.");
        }
        BookingResponse response = eventService.bookTicket(eventId, jwt.getSubject());
        return ResponseEntity.ok(response);
    }
}
