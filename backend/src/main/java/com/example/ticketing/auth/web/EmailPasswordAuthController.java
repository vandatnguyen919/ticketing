package com.example.ticketing.auth.web;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.auth.dto.LoginRequest;
import com.example.ticketing.auth.dto.RegisterRequest;
import com.example.ticketing.auth.service.EmailPasswordUserService;
import com.example.ticketing.model.AuthResponse;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.service.JwtService;

@RestController
@RequestMapping(path = "/api/{version}/auth", version = "1.0")
public class EmailPasswordAuthController {

    private final EmailPasswordUserService userService;
    private final JwtService jwtService;
    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public EmailPasswordAuthController(
        EmailPasswordUserService userService,
        JwtService jwtService,
        @Value("${app.security.cookie.name}") String cookieName,
        @Value("${app.security.cookie.path}") String cookiePath,
        @Value("${app.security.cookie.secure}") boolean cookieSecure,
        @Value("${app.security.cookie.same-site}") String cookieSameSite
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletResponse response
    ) {
        UserProfile profile = userService.register(request);
        issueCredentialCookie(profile, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(profile));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        UserProfile profile = userService.login(request);
        issueCredentialCookie(profile, response);
        return ResponseEntity.ok(new AuthResponse(profile));
    }

    private void issueCredentialCookie(UserProfile profile, HttpServletResponse response) {
        JwtService.IssuedToken token = jwtService.generateToken(profile);
        ResponseCookie cookie = ResponseCookie.from(cookieName, token.value())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path(cookiePath)
            .maxAge(Duration.between(Instant.now(), token.expiresAt()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
